/**
 * author : 王星星
 * date : 2026-11-17 13:55
 * email : 1099420259@qq.com
 * description : 契约测试：同步分页字段、墓碑形状、错误码与状态码、严格类型校验、限流 Retry-After 与全局安全响应头。
 *               注意：断言严格对齐 api.md；不得为通过用例放宽服务端校验，也不得接受被静默转换的字符串布尔/数字。
 */
package com.wangxingxing.clipbox.server.integration

import com.wangxingxing.clipbox.server.plugins.RateLimitSettings
import com.wangxingxing.clipbox.server.service.SHARE_PAGE_CSP
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.io.TempDir

class ContractTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `sync page exposes documented fields and tombstone shape`() = integrationApp(tempDir) {
        val device = pairDevice()
        assertEquals(HttpStatusCode.Created, createClip(device.token, clipIdFor(1), "first").status)
        assertEquals(HttpStatusCode.Created, createClip(device.token, clipIdFor(2), "second").status)
        assertEquals(HttpStatusCode.OK, deleteClip(device.token, clipIdFor(1)).status)

        val page = clips(device.token, since = 1, limit = 10)
        assertEquals(
            setOf("items", "nextSince", "hasMore", "resyncRequired", "minAvailableSeq", "serverMaxSeq"),
            page.keys,
        )
        val items = page.items()
        assertEquals(2, items.size)
        assertEquals(
            setOf(
                "id",
                "content",
                "sourceDeviceId",
                "sourceDeviceName",
                "pinned",
                "createdAt",
                "serverSeq",
                "deleted",
            ),
            items[0].keys,
        )
        assertEquals(setOf("id", "serverSeq", "deleted"), items[1].keys)
        assertEquals(2L, items[0].long("serverSeq"))
        assertEquals(3L, items[1].long("serverSeq"))
        assertEquals(3L, page.long("nextSince"))
        assertEquals(3L, page.long("serverMaxSeq"))
        assertEquals(1L, page.long("minAvailableSeq"))
        assertFalse(page.bool("hasMore"))
        assertFalse(page.bool("resyncRequired"))

        val paged = clips(device.token, since = 0, limit = 1)
        assertEquals(1, paged.items().size)
        assertTrue(paged.bool("hasMore"))
        assertEquals(2L, paged.long("nextSince"), "单页游标只能推进到本页最后一条")

        val full = clips(device.token, since = 0, limit = 500)
        assertEquals(2, full.items().size, "软删除只保留墓碑一条，不复现已删除正文")
        assertFalse(full.bool("hasMore"))
    }

    @Test
    fun `invalid query and write bodies are rejected`() = integrationApp(tempDir) {
        val device = pairDevice()
        val id = clipIdFor(1)
        assertEquals(HttpStatusCode.Created, createClip(device.token, id, "original").status)

        assertValidationError(clipsResponse(device.token, since = -1))
        assertValidationError(clipsResponse(device.token, limit = 0))
        assertValidationError(clipsResponse(device.token, limit = 501))
        val badSince = client.get("/api/v1/clips") {
            bearer(device.token)
            parameter("since", "abc")
        }
        assertValidationError(badSince)

        assertValidationError(
            postJsonRaw("/api/v1/clips", """{"id":"${clipIdFor(9)}","createdAt":$CREATED_AT}""", device.token),
        )
        assertValidationError(
            postJsonRaw(
                "/api/v1/clips",
                """{"id":"${clipIdFor(10)}","content":"x","createdAt":"$CREATED_AT"}""",
                device.token,
            ),
        )
        assertValidationError(
            postJsonRaw(
                "/api/v1/clips",
                """{"id":"${clipIdFor(11)}","content":"   ","createdAt":$CREATED_AT}""",
                device.token,
            ),
        )
        assertValidationError(
            postJsonRaw(
                "/api/v1/clips",
                """{"id":"${id.lowercase()}","content":"x","createdAt":$CREATED_AT}""",
                device.token,
            ),
        )
        assertValidationError(patchJsonRaw("/api/v1/clips/$id", """{"pinned":"true"}""", device.token))
        assertValidationError(patchJsonRaw("/api/v1/clips/$id", """{}""", device.token))

        val unknownId = clipIdFor(499)
        val patchMissing = patchJsonRaw("/api/v1/clips/$unknownId", """{"pinned":true}""", device.token)
        assertEquals(HttpStatusCode.NotFound, patchMissing.status)
        assertEquals("NOT_FOUND", json(patchMissing).errorCode())
        val deleteMissing = deleteClip(device.token, unknownId)
        assertEquals(HttpStatusCode.NotFound, deleteMissing.status)
        assertEquals("NOT_FOUND", json(deleteMissing).errorCode())

        val duplicateSame = createClip(device.token, id, "original")
        assertEquals(HttpStatusCode.OK, duplicateSame.status, "同 ID 同正文重放返回 200")
        assertEquals(1L, json(duplicateSame).long("serverSeq"), "幂等命中不得推进序号")
        val duplicateDifferent = createClip(device.token, id, "changed")
        assertEquals(HttpStatusCode.Conflict, duplicateDifferent.status)
        assertEquals("CONFLICT", json(duplicateDifferent).errorCode())

        assertEquals(HttpStatusCode.OK, deleteClip(device.token, id).status)
        val resurrect = createClip(device.token, id, "original")
        assertEquals(HttpStatusCode.Conflict, resurrect.status, "墓碑不可通过重放复活")

        val wrongMethod = client.put("/api/v1/clips") {
            bearer(device.token)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.MethodNotAllowed, wrongMethod.status)
        assertEquals("METHOD_NOT_ALLOWED", json(wrongMethod).errorCode())

        val unknownPath = client.get("/api/v1/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, unknownPath.status)
        assertEquals("NOT_FOUND", json(unknownPath).errorCode())
    }

    @Test
    fun `unauthenticated requests share one error envelope`() = integrationApp(tempDir) {
        assertUnauthorized(client.get("/api/v1/clips"))
        assertUnauthorized(
            client.get("/api/v1/clips") {
                header(HttpHeaders.Authorization, "Basic abc")
            },
        )
        assertUnauthorized(
            client.get("/api/v1/clips") {
                bearer("u".repeat(43))
            },
        )
    }

    @Test
    fun `share creation validates ttl and clip availability`() = integrationApp(tempDir) {
        val device = pairDevice()
        val id = clipIdFor(3)
        assertEquals(HttpStatusCode.Created, createClip(device.token, id, "share me").status)

        assertValidationError(createShare(device.token, id, ttlSeconds = 59))
        assertValidationError(createShare(device.token, id, ttlSeconds = 604_801))
        assertValidationError(
            postJsonRaw("/api/v1/shares", """{"clipId":"$id","ttlSeconds":"3600"}""", device.token),
        )
        assertValidationError(
            postJsonRaw("/api/v1/shares", """{"clipId":"$id","oneTime":"true"}""", device.token),
        )
        assertValidationError(postJsonRaw("/api/v1/shares", """{"ttlSeconds":3600}""", device.token))
        assertValidationError(
            postJsonRaw("/api/v1/shares", """{"clipId":"${id.lowercase()}"}""", device.token),
        )

        val unknownClip = postJsonRaw("/api/v1/shares", """{"clipId":"${clipIdFor(499)}"}""", device.token)
        assertEquals(HttpStatusCode.NotFound, unknownClip.status)
        assertEquals("NOT_FOUND", json(unknownClip).errorCode())

        val now = System.currentTimeMillis()
        val defaulted = createShare(device.token, id)
        assertEquals(HttpStatusCode.Created, defaulted.status)
        val share = json(defaulted)
        assertEquals(setOf("token", "url", "expiresAt"), share.keys)
        assertTrue(share.long("expiresAt") in (now + 86_400_000 - 5_000)..(now + 86_400_000 + 5_000))
        assertEquals("$PUBLIC_BASE_URL/s/${share.str("token")}", share.str("url"))

        assertEquals(HttpStatusCode.OK, deleteClip(device.token, id).status)
        val deletedClip = createShare(device.token, id)
        assertEquals(HttpStatusCode.NotFound, deletedClip.status, "已删除记录不可分享")
    }

    @Test
    fun `pair rate limit returns retry after`() = integrationApp(tempDir, STRICT_PAIR_LIMITS) {
        val first = postJsonRaw("/api/v1/pair", "{}")
        assertEquals(HttpStatusCode.BadRequest, first.status)
        val second = postJsonRaw("/api/v1/pair", "{}")
        assertEquals(HttpStatusCode.TooManyRequests, second.status)
        assertEquals("RATE_LIMITED", json(second).errorCode())
        val retryAfter = second.headers[HttpHeaders.RetryAfter]
        assertNotNull(retryAfter, "429 必须带 Retry-After")
        assertTrue(retryAfter.toLong() in 1L..60L, "Retry-After 必须在窗口范围内：$retryAfter")
    }

    @Test
    fun `device rate limit returns retry after`() = integrationApp(tempDir, STRICT_DEVICE_LIMITS) {
        val device = pairDevice()
        assertEquals(HttpStatusCode.OK, clipsResponse(device.token).status)
        assertEquals(HttpStatusCode.OK, clipsResponse(device.token).status)
        val denied = clipsResponse(device.token)
        assertEquals(HttpStatusCode.TooManyRequests, denied.status)
        assertEquals("RATE_LIMITED", json(denied).errorCode())
        assertNotNull(denied.headers[HttpHeaders.RetryAfter])
    }

    @Test
    fun `security headers guard api and share responses`() = integrationApp(tempDir) {
        assertSecurityHeaders(client.get("/healthz"))
        assertSecurityHeaders(client.get("/api/v1/clips"))
        assertSecurityHeaders(client.get("/api/v1/does-not-exist"))

        val device = pairDevice()
        val id = clipIdFor(4)
        assertEquals(HttpStatusCode.Created, createClip(device.token, id, "头部检查").status)
        val shareToken = json(createShare(device.token, id)).str("token")

        val page = openShare(shareToken)
        assertEquals(HttpStatusCode.OK, page.status)
        assertSecurityHeaders(page)
        assertEquals(SHARE_PAGE_CSP, page.headers["Content-Security-Policy"])
        val html = page.bodyAsText()
        assertFalse(html.contains("<script"), "分享页不得包含脚本")
        assertFalse(html.contains("onerror="), "用户正文不得渲染为事件处理器")

        val missing = openShare("z".repeat(43))
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertSecurityHeaders(missing)
        assertEquals(SHARE_PAGE_CSP, missing.headers["Content-Security-Policy"])
    }

    private suspend fun assertValidationError(response: HttpResponse) {
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("VALIDATION_ERROR", json(response).errorCode())
    }

    private suspend fun assertUnauthorized(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = json(response)
        assertEquals(setOf("error"), body.keys)
        assertEquals(setOf("code", "message"), body.getValue("error").jsonObject.keys)
        assertEquals("UNAUTHORIZED", body.errorCode())
    }

    private fun assertSecurityHeaders(response: HttpResponse) {
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("no-referrer", response.headers["Referrer-Policy"])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    companion object {
        private val STRICT_PAIR_LIMITS = RateLimitSettings(
            pairPerMinute = 1,
            adminPerMinute = 100,
            devicePerMinute = 100,
            sharePagePerMinute = 100,
            healthPerMinute = 100,
            webSocketAttemptsPerMinute = 100,
        )

        private val STRICT_DEVICE_LIMITS = RateLimitSettings(
            pairPerMinute = 100,
            adminPerMinute = 100,
            devicePerMinute = 2,
            sharePagePerMinute = 100,
            healthPerMinute = 100,
            webSocketAttemptsPerMinute = 100,
        )
    }
}
