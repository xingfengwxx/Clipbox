/**
 * author : 王星星
 * date : 2026-11-17 13:52
 * email : 1099420259@qq.com
 * description : 端到端集成测试：配对、创建、第二设备增量拉取、置顶/删除、WebSocket 事件、分享创建与一次性访问。
 *               注意：全部通过真实 HTTP/WebSocket 与临时 SQLite 验证，不 mock 服务层；断言令牌只以哈希形态落库。
 */
package com.wangxingxing.clipbox.server.integration

import com.wangxingxing.clipbox.server.db.SharesTable
import com.wangxingxing.clipbox.server.service.SHARE_PAGE_CSP
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.close
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir

class SyncFlowIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `two device sync share and one time consumption flow`() = integrationApp(tempDir) {
        val health = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, health.status)
        val healthBody = json(health)
        assertEquals("ok", healthBody.str("status"))
        assertEquals("1.0.0", healthBody.str("version"))

        val phone = pairDevice("Pixel 8")
        val mac = pairDevice("MacBook")
        assertEquals(43, phone.token.length)
        assertTrue(DEVICE_TOKEN_PATTERN.matches(phone.token))
        assertTrue(ULID_PATTERN.matches(phone.deviceId))

        val clipId = clipIdFor(1)
        val created = createClip(phone.token, clipId, content = "hello 世界")
        assertEquals(HttpStatusCode.Created, created.status)
        val createdBody = json(created)
        assertEquals(
            setOf("id", "serverSeq", "createdAt", "pinned", "deleted"),
            createdBody.keys,
            "创建响应字段必须与 api.md 一致",
        )
        assertEquals(clipId, createdBody.str("id"))
        assertEquals(CREATED_AT, createdBody.long("createdAt"))
        assertEquals(1L, createdBody.long("serverSeq"))
        assertFalse(createdBody.bool("pinned"))
        assertFalse(createdBody.bool("deleted"))

        val initial = clips(mac.token, since = 0)
        assertEquals(
            setOf("items", "nextSince", "hasMore", "resyncRequired", "minAvailableSeq", "serverMaxSeq"),
            initial.keys,
        )
        assertEquals(1, initial.items().size)
        assertEquals(1L, initial.long("nextSince"))
        assertEquals(1L, initial.long("serverMaxSeq"))
        assertEquals(1L, initial.long("minAvailableSeq"))
        assertFalse(initial.bool("hasMore"))
        assertFalse(initial.bool("resyncRequired"))
        val firstItem = initial.items().single()
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
            firstItem.keys,
        )
        assertEquals("hello 世界", firstItem.str("content"))
        assertEquals(phone.deviceId, firstItem.str("sourceDeviceId"))
        assertEquals("Pixel 8", firstItem.str("sourceDeviceName"))

        val pinned = pinClip(phone.token, clipId, pinned = true)
        assertEquals(HttpStatusCode.OK, pinned.status)
        assertEquals(2L, json(pinned).long("serverSeq"))
        val incremental = clips(mac.token, since = 1)
        assertEquals(1, incremental.items().size)
        assertEquals(2L, incremental.long("nextSince"))
        assertTrue(incremental.items().single().bool("pinned"))

        val shareClipId = clipIdFor(2)
        val shareClipCreated = createClip(phone.token, shareClipId, content = "另一台设备发送")
        assertEquals(HttpStatusCode.Created, shareClipCreated.status)
        assertEquals(3L, json(shareClipCreated).long("serverSeq"))

        // 分享创建不参与同步序号、不触发广播，因此先建分享记录再连 WS，事件序列保持可控。
        val macSession = openSocket(mac.token)

        val deleted = deleteClip(phone.token, clipId)
        assertEquals(HttpStatusCode.OK, deleted.status)
        val event = macSession.receiveJson()
        assertEquals("clip.deleted", event.str("type"))
        assertEquals(4L, event.long("seq"))
        val eventData = event.getValue("data").jsonObject
        assertEquals(setOf("id", "serverSeq", "deleted"), eventData.keys, "墓碑事件只带三个字段")
        assertEquals(clipId, eventData.str("id"))
        assertTrue(eventData.bool("deleted"))

        val afterDelete = clips(mac.token, since = 3)
        assertEquals(1, afterDelete.items().size)
        val tombstone = afterDelete.items().single()
        assertEquals(setOf("id", "serverSeq", "deleted"), tombstone.keys, "墓碑条目只带三个字段")
        assertEquals(4L, afterDelete.long("nextSince"))

        val shareCreated = createShare(phone.token, shareClipId, ttlSeconds = 3_600, oneTime = false)
        assertEquals(HttpStatusCode.Created, shareCreated.status)
        val shareBody = json(shareCreated)
        assertEquals(setOf("token", "url", "expiresAt"), shareBody.keys)
        val shareToken = shareBody.str("token")
        assertTrue(SHARE_TOKEN_PATTERN.matches(shareToken))
        assertEquals("$PUBLIC_BASE_URL/s/$shareToken", shareBody.str("url"))
        assertTrue(shareBody.long("expiresAt") > System.currentTimeMillis())

        val storedHashes = factory.readTransaction {
            SharesTable.selectAll().map { it[SharesTable.tokenHash] }
        }
        assertTrue(storedHashes.isNotEmpty(), "分享必须落库")
        assertTrue(storedHashes.none { it == shareToken }, "库中不得出现明文分享令牌")

        val page = openShare(shareToken)
        assertEquals(HttpStatusCode.OK, page.status)
        assertEquals(SHARE_PAGE_CSP, page.headers["Content-Security-Policy"])
        val html = page.bodyAsText()
        assertTrue(html.contains("另一台设备发送"))
        assertTrue(html.contains("只读分享"))
        assertEquals(HttpStatusCode.OK, openShare(shareToken).status, "非一次性分享可重复访问")

        val oneTimeToken = json(
            createShare(phone.token, shareClipId, ttlSeconds = 86_400, oneTime = true),
        ).str("token")
        assertEquals(HttpStatusCode.OK, openShare(oneTimeToken).status)
        val second = openShare(oneTimeToken)
        assertEquals(HttpStatusCode.Gone, second.status)
        val secondHtml = second.bodyAsText()
        assertTrue(secondHtml.contains("链接已被使用"))
        assertFalse(secondHtml.contains("另一台设备发送"), "已消费页面不得泄露正文")

        macSession.close()
    }

    companion object {
        private val DEVICE_TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
        private val SHARE_TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
        private val ULID_PATTERN = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
    }
}
