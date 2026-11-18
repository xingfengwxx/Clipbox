/**
 * author : 王星星
 * date : 2026-11-17 11:36
 * email : 1099420259@qq.com
 * description : 分享创建接口契约测试：TTL 边界、记录可用性、令牌哈希与快照落库、URL 拼接、认证与不支持方法。
 *               注意：每个用例使用独立临时目录与真实 SQLite；快照解耦通过创建后直接修改 clips 再回查 shares 断言。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.ClipRepository
import com.wangxingxing.clipbox.server.db.ClipRow
import com.wangxingxing.clipbox.server.db.ClipsTable
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.db.MetaRepository
import com.wangxingxing.clipbox.server.db.SeqAllocator
import com.wangxingxing.clipbox.server.db.ShareRepository
import com.wangxingxing.clipbox.server.db.ShareRow
import com.wangxingxing.clipbox.server.db.SharesTable
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.io.TempDir

class ShareCreateTest {

    @TempDir
    lateinit var tempDir: Path

    private val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
    private val deviceToken = "d".repeat(43)

    @Test
    fun `creating a share returns token url and expiry and stores a hashed snapshot`() = withApp { factory, client ->
        val before = System.currentTimeMillis()
        val response = client.createShare("""{"clipId":"$CLIP_ID","ttlSeconds":86400,"oneTime":false}""")
        val after = System.currentTimeMillis()

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.jsonBody()
        assertEquals(setOf("token", "url", "expiresAt"), body.keys)
        val token = body.string("token")
        assertTrue(TOKEN_PATTERN.matches(token), "分享令牌必须是 43 位 base64url 无填充")
        assertEquals("$BASE_URL/s/$token", body.string("url"))
        val expiresAt = body.long("expiresAt")
        assertTrue(expiresAt in before + ONE_DAY_MS..after + ONE_DAY_MS, "expiresAt 必须落在请求前后区间内")

        val row = assertNotNull(factory.share(token))
        assertEquals(Hashing.sha256Hex(token), row.tokenHash)
        assertEquals(CLIP_ID, row.clipId)
        assertEquals(CONTENT, row.content)
        assertEquals(expiresAt, row.expiresAt)
        assertEquals(ONE_DAY_MS, row.expiresAt - row.createdAt)
        assertFalse(row.oneTime)
        assertNull(row.openedAt)

        assertEquals(
            setOf("token_hash", "clip_id", "content", "created_at", "expires_at", "one_time", "opened_at"),
            SharesTable.columns.map { it.name }.toSet(),
            "shares 表不得新增明文令牌列",
        )
        val hashes = factory.readTransaction { SharesTable.selectAll().map { it[SharesTable.tokenHash] } }
        assertEquals(listOf(Hashing.sha256Hex(token)), hashes)
        assertFalse(hashes.contains(token), "库中不得出现明文分享令牌")
    }

    @Test
    fun `ttl boundaries are enforced and defaults apply`() = withApp { factory, client ->
        listOf(60L, 604_800L).forEach { ttl ->
            val response = client.createShare("""{"clipId":"$CLIP_ID","ttlSeconds":$ttl}""")
            assertEquals(HttpStatusCode.Created, response.status, "ttl=$ttl")
            val row = assertNotNull(factory.share(response.jsonBody().string("token")))
            assertEquals(ttl * 1000, row.expiresAt - row.createdAt, "ttl=$ttl")
        }

        listOf(59L, 0L, -1L, 604_801L).forEach { ttl ->
            val response = client.createShare("""{"clipId":"$CLIP_ID","ttlSeconds":$ttl}""")
            assertEquals(HttpStatusCode.BadRequest, response.status, "ttl=$ttl")
            assertEquals("VALIDATION_ERROR", response.errorCode(), "ttl=$ttl")
        }

        val asString = client.createShare("""{"clipId":"$CLIP_ID","ttlSeconds":"3600"}""")
        assertEquals(HttpStatusCode.BadRequest, asString.status, "字符串 ttlSeconds 不得被静默转成数字")
        assertEquals("VALIDATION_ERROR", asString.errorCode())

        val defaulted = client.createShare("""{"clipId":"$CLIP_ID"}""")
        assertEquals(HttpStatusCode.Created, defaulted.status)
        val defaultRow = assertNotNull(factory.share(defaulted.jsonBody().string("token")))
        assertEquals(ONE_DAY_MS, defaultRow.expiresAt - defaultRow.createdAt)
        assertFalse(defaultRow.oneTime)
    }

    @Test
    fun `one time flag must be boolean and is stored as given`() = withApp { factory, client ->
        val response = client.createShare("""{"clipId":"$CLIP_ID","ttlSeconds":3600,"oneTime":true}""")
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(assertNotNull(factory.share(response.jsonBody().string("token"))).oneTime)

        listOf("""{"clipId":"$CLIP_ID","oneTime":"false"}""", """{"clipId":"$CLIP_ID","oneTime":1}""").forEach { body ->
            val invalid = client.createShare(body)
            assertEquals(HttpStatusCode.BadRequest, invalid.status, body)
            assertEquals("VALIDATION_ERROR", invalid.errorCode(), body)
        }

        val unknownField = client.createShare("""{"clipId":"$CLIP_ID","ignored":"x"}""")
        assertEquals(HttpStatusCode.Created, unknownField.status, "未知字段应被忽略")
    }

    @Test
    fun `invalid missing or deleted clips cannot be shared`() = withApp(clipContent = null) { _, client ->
        listOf(
            "01j8z6z3k4m5n6p7q8r9s0t1v2",
            "01J8Z6Z3K4M5N6P7Q8R9S0T1V",
            "01J8Z6Z3K4M5N6P7Q8R9S0T1I2",
        ).forEach { invalidId ->
            val response = client.createShare("""{"clipId":"$invalidId"}""")
            assertEquals(HttpStatusCode.BadRequest, response.status, invalidId)
            assertEquals("VALIDATION_ERROR", response.errorCode(), invalidId)
        }

        val missingClip = client.createShare("""{"clipId":"$CLIP_ID"}""")
        assertEquals(HttpStatusCode.NotFound, missingClip.status)
        assertEquals("NOT_FOUND", missingClip.errorCode())
    }

    @Test
    fun `soft deleted clips cannot be shared`() = withApp(deleted = true) { _, client ->
        val response = client.createShare("""{"clipId":"$CLIP_ID"}""")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("NOT_FOUND", response.errorCode())
    }

    @Test
    fun `share creation requires device auth and has no list endpoint`() = withApp { _, client ->
        val anonymous = client.post("/api/v1/shares") {
            contentType(ContentType.Application.Json)
            setBody("""{"clipId":"$CLIP_ID"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertEquals("UNAUTHORIZED", anonymous.errorCode())

        val list = client.get("/api/v1/shares") {
            header("Authorization", "Bearer $deviceToken")
        }
        assertEquals(HttpStatusCode.MethodNotAllowed, list.status)
    }

    @Test
    fun `each creation issues a distinct token without touching the sync sequence`() = withApp { factory, client ->
        val first = client.createShare("""{"clipId":"$CLIP_ID"}""")
        val second = client.createShare("""{"clipId":"$CLIP_ID"}""")
        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals(HttpStatusCode.Created, second.status)
        assertNotEquals(first.jsonBody().string("token"), second.jsonBody().string("token"))

        val count = factory.readTransaction { SharesTable.selectAll().count() }
        assertEquals(2L, count)
        assertEquals(1L, factory.serverSeq(), "分享创建不得推进同步序号")
    }

    @Test
    fun `share content is a snapshot decoupled from later clip changes`() = withApp { factory, client ->
        val response = client.createShare("""{"clipId":"$CLIP_ID"}""")
        assertEquals(HttpStatusCode.Created, response.status)
        val token = response.jsonBody().string("token")

        factory.writeTransaction {
            ClipsTable.update({ ClipsTable.id eq CLIP_ID }) {
                it[content] = "changed after share"
                it[deletedAt] = SHARE_DELETE_AT
            }
        }

        val row = assertNotNull(factory.share(token))
        assertEquals(CONTENT, row.content)
        assertEquals(CLIP_ID, row.clipId)
        assertEquals(1L, factory.serverSeq())
    }

    // --- 夹具 ---

    private suspend fun HttpClient.createShare(body: String): HttpResponse =
        post("/api/v1/shares") {
            header("Authorization", "Bearer $deviceToken")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpResponse.jsonBody(): JsonObject =
        Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.errorCode(): String =
        jsonBody().getValue("error").jsonObject.getValue("code").jsonPrimitive.content

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long

    private suspend fun DatabaseFactory.share(token: String): ShareRow? =
        readTransaction { ShareRepository.findByTokenHash(Hashing.sha256Hex(token)) }

    private suspend fun DatabaseFactory.serverSeq(): Long =
        readTransaction { MetaRepository.getLong(MetaRepository.SERVER_SEQ_KEY) ?: -1L }

    private fun withApp(
        clipContent: String? = CONTENT,
        deleted: Boolean = false,
        block: suspend (DatabaseFactory, HttpClient) -> Unit,
    ) = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        factory.writeTransaction {
            DeviceRepository.insert(
                DeviceRow(
                    id = deviceId,
                    name = "Pixel 8",
                    tokenHash = Hashing.sha256Hex(deviceToken),
                    createdAt = 1_000,
                    lastSeenAt = 1_000,
                ),
            )
            if (clipContent != null) {
                ClipRepository.insert(
                    ClipRow(
                        id = CLIP_ID,
                        content = clipContent,
                        sourceDeviceId = deviceId,
                        pinned = false,
                        createdAt = CREATED_AT,
                        deletedAt = if (deleted) CREATED_AT + 1 else null,
                        serverSeq = SeqAllocator.nextSeq(),
                    ),
                )
            }
        }
        application { module(testConfig(), factory) }
        client.get("/healthz")
        block(factory, client)
    }

    private fun testConfig() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        publicBaseUrl = BASE_URL,
    )

    companion object {
        private const val CLIP_ID = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
        private const val CREATED_AT = 1_730_000_000_000L
        private const val SHARE_DELETE_AT = 1_730_000_001_000L
        private const val CONTENT = "hello world"
        private const val BASE_URL = "http://127.0.0.1:8080"
        private const val ONE_DAY_MS = 86_400_000L
        private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}
