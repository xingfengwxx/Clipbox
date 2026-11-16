/**
 * author : 王星星
 * date : 2026-11-16 15:42
 * email : 1099420259@qq.com
 * description : 剪贴写接口契约测试：创建幂等/冲突、UTF-8 字节上限、置顶与软删除幂等、序号只随真实变更推进。
 *               注意：每个用例使用独立临时目录与真实 SQLite；序号断言以 meta.server_seq 与记录 server_seq 为准。
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
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir

class ClipWriteTest {

    @TempDir
    lateinit var tempDir: Path

    private val adminToken = "a".repeat(32)
    private val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
    private val deviceToken = "d".repeat(43)

    @Test
    fun `first create returns 201 and allocates one server seq`() = withApp { factory, client ->
        val response = client.createClip(createBody(content = "  hello  "))
        assertEquals(HttpStatusCode.Created, response.status)

        val body = response.jsonBody()
        assertEquals(setOf("id", "serverSeq", "createdAt", "pinned", "deleted"), body.keys)
        assertEquals(CLIP_ID, body.string("id"))
        assertEquals(1L, body.long("serverSeq"))
        assertEquals(CREATED_AT, body.long("createdAt"))
        assertFalse(body.boolean("pinned"))
        assertFalse(body.boolean("deleted"))

        val stored = assertNotNull(factory.clip(CLIP_ID))
        assertEquals("  hello  ", stored.content, "正文必须原样保存，不去除首尾空白")
        assertEquals(deviceId, stored.sourceDeviceId)
        assertFalse(stored.pinned)
        assertNull(stored.deletedAt)
        assertEquals(1L, stored.serverSeq)
        assertEquals(1L, factory.serverSeq())
        assertEquals(1L, factory.clipCount())
    }

    @Test
    fun `replaying the same id and content is idempotent`() = withApp { factory, client ->
        val first = client.createClip(createBody())
        assertEquals(HttpStatusCode.Created, first.status)
        val firstSeq = first.jsonBody().long("serverSeq")

        val replay = client.createClip(createBody())
        assertEquals(HttpStatusCode.OK, replay.status)
        assertEquals(firstSeq, replay.jsonBody().long("serverSeq"))
        assertEquals(firstSeq, factory.serverSeq())
        assertEquals(1L, factory.clipCount())
    }

    @Test
    fun `same id with different content conflicts without touching data`() = withApp { factory, client ->
        client.createClip(createBody(content = "hello"))

        val conflict = client.createClip(createBody(content = "different"))
        assertEquals(HttpStatusCode.Conflict, conflict.status)
        assertEquals("CONFLICT", conflict.errorCode())

        val stored = assertNotNull(factory.clip(CLIP_ID))
        assertEquals("hello", stored.content)
        assertEquals(1L, stored.serverSeq)
        assertEquals(1L, factory.serverSeq())
    }

    @Test
    fun `recreating a deleted clip conflicts and does not resurrect it`() = withApp { factory, client ->
        client.createClip(createBody())
        val deleted = client.deleteClip(CLIP_ID)
        assertEquals(HttpStatusCode.OK, deleted.status)

        val recreate = client.createClip(createBody())
        assertEquals(HttpStatusCode.Conflict, recreate.status)
        assertEquals("CONFLICT", recreate.errorCode())

        val stored = assertNotNull(factory.clip(CLIP_ID))
        assertNotNull(stored.deletedAt, "已删除记录不得被创建请求复活")
        assertEquals(2L, stored.serverSeq)
        assertEquals(2L, factory.serverSeq())
    }

    @Test
    fun `content limit is measured in UTF-8 bytes`() = withApp { factory, client ->
        val maxAscii = "a".repeat(65536)
        assertEquals(
            HttpStatusCode.Created,
            client.createClip(createBody(id = clipId('1'), content = maxAscii)).status,
            "65536 字节必须通过",
        )

        val overAscii = client.createClip(createBody(id = clipId('2'), content = "a".repeat(65537)))
        assertEquals(HttpStatusCode.BadRequest, overAscii.status)
        assertEquals("VALIDATION_ERROR", overAscii.errorCode())

        // 汉字在 UTF-8 中占 3 字节：21845 个字符是 65535 字节，21846 个字符是 65538 字节。
        assertEquals(
            HttpStatusCode.Created,
            client.createClip(createBody(id = clipId('3'), content = "汉".repeat(21845))).status,
        )
        val overBytes = client.createClip(createBody(id = clipId('4'), content = "汉".repeat(21846)))
        assertEquals(HttpStatusCode.BadRequest, overBytes.status)
        assertEquals("VALIDATION_ERROR", overBytes.errorCode())

        assertNull(factory.clip(clipId('2')))
        assertNull(factory.clip(clipId('4')))
        assertEquals(2L, factory.serverSeq())
    }

    @Test
    fun `blank content and invalid id or createdAt are rejected`() = withApp { factory, client ->
        val blank = client.createClip(createBody(content = " \n\t "))
        assertEquals(HttpStatusCode.BadRequest, blank.status)
        assertEquals("VALIDATION_ERROR", blank.errorCode())

        val invalidIds = listOf(
            "01j8z6z3k4m5n6p7q8r9s0t1v2",
            "01J8Z6Z3K4M5N6P7Q8R9S0T1V",
            "01J8Z6Z3K4M5N6P7Q8R9S0T1I2",
            "01J8Z6Z3K4M5N6P7Q8R9S0T1U2",
        )
        invalidIds.forEach { invalidId ->
            val response = client.createClip(createBody(id = invalidId))
            assertEquals(HttpStatusCode.BadRequest, response.status, invalidId)
            assertEquals("VALIDATION_ERROR", response.errorCode(), invalidId)
        }

        listOf(0L, -1L).forEach { createdAt ->
            val response = client.createClip(createBody(createdAt = createdAt))
            assertEquals(HttpStatusCode.BadRequest, response.status, "createdAt=$createdAt")
            assertEquals("VALIDATION_ERROR", response.errorCode(), "createdAt=$createdAt")
        }

        assertEquals(0L, factory.serverSeq())
    }

    @Test
    fun `source device comes from auth and unknown fields are ignored`() = withApp { factory, client ->
        val body = createBody(
            extra = mapOf(
                "sourceDeviceId" to JsonPrimitive("01J8Z6Z3K4M5N6P7Q8R9S0T1V9"),
                "pinned" to JsonPrimitive(true),
                "deleted" to JsonPrimitive(true),
            ),
        )
        assertEquals(HttpStatusCode.Created, client.createClip(body).status)

        val stored = assertNotNull(factory.clip(CLIP_ID))
        assertEquals(deviceId, stored.sourceDeviceId)
        assertFalse(stored.pinned)
        assertNull(stored.deletedAt)
    }

    @Test
    fun `missing fields and malformed json are validation errors`() = withApp { factory, client ->
        val bodies = listOf(
            "{}",
            """{"id":"$CLIP_ID"}""",
            """{"id":"$CLIP_ID","content":"x"}""",
            """{"content":"x","createdAt":1}""",
            """{"id":"$CLIP_ID","content":"x","createdAt":"1"}""",
            "{",
        )
        bodies.forEach { body ->
            val response = client.createClip(body)
            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            assertEquals("VALIDATION_ERROR", response.errorCode(), body)
        }
        assertEquals(0L, factory.serverSeq())
    }

    @Test
    fun `pinning the same value is idempotent and a change advances once`() = withApp { factory, client ->
        client.createClip(createBody())

        val unchanged = client.patchClip(CLIP_ID, """{"pinned":false}""")
        assertEquals(HttpStatusCode.OK, unchanged.status)
        val unchangedBody = unchanged.jsonBody()
        assertEquals(setOf("id", "pinned", "serverSeq", "deleted"), unchangedBody.keys)
        assertEquals(1L, unchangedBody.long("serverSeq"))
        assertFalse(unchangedBody.boolean("pinned"))
        assertFalse(unchangedBody.boolean("deleted"))
        assertEquals(1L, factory.serverSeq())

        val pinned = client.patchClip(CLIP_ID, """{"pinned":true}""")
        assertEquals(HttpStatusCode.OK, pinned.status)
        assertEquals(2L, pinned.jsonBody().long("serverSeq"))
        assertTrue(pinned.jsonBody().boolean("pinned"))
        assertEquals(2L, factory.serverSeq())

        val pinnedAgain = client.patchClip(CLIP_ID, """{"pinned":true}""")
        assertEquals(HttpStatusCode.OK, pinnedAgain.status)
        assertEquals(2L, pinnedAgain.jsonBody().long("serverSeq"))
        assertEquals(2L, factory.serverSeq())

        val unpinned = client.patchClip(CLIP_ID, """{"pinned":false}""")
        assertEquals(3L, unpinned.jsonBody().long("serverSeq"))
        assertEquals(3L, factory.serverSeq())
        assertFalse(assertNotNull(factory.clip(CLIP_ID)).pinned)
    }

    @Test
    fun `patch requires a boolean pinned field`() = withApp { _, client ->
        client.createClip(createBody())

        listOf("{}", """{"pinned":"true"}""", """{"pinned":1}""", "{").forEach { body ->
            val response = client.patchClip(CLIP_ID, body)
            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            assertEquals("VALIDATION_ERROR", response.errorCode(), body)
        }
    }

    @Test
    fun `patching missing or deleted clips returns not found`() = withApp { factory, client ->
        val missing = client.patchClip(clipId('9'), """{"pinned":true}""")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("NOT_FOUND", missing.errorCode())

        client.createClip(createBody())
        client.deleteClip(CLIP_ID)
        val deleted = client.patchClip(CLIP_ID, """{"pinned":true}""")
        assertEquals(HttpStatusCode.NotFound, deleted.status)
        assertEquals("NOT_FOUND", deleted.errorCode())
        assertEquals(2L, factory.serverSeq())
    }

    @Test
    fun `delete advances once and repeating it is idempotent`() = withApp { factory, client ->
        client.createClip(createBody())

        val first = client.deleteClip(CLIP_ID)
        assertEquals(HttpStatusCode.OK, first.status)
        val firstBody = first.jsonBody()
        assertEquals(setOf("id", "deleted", "serverSeq"), firstBody.keys)
        assertEquals(CLIP_ID, firstBody.string("id"))
        assertTrue(firstBody.boolean("deleted"))
        assertEquals(2L, firstBody.long("serverSeq"))

        val deletedAt = assertNotNull(factory.clip(CLIP_ID)).deletedAt
        assertNotNull(deletedAt)
        assertEquals(2L, factory.serverSeq())

        val second = client.deleteClip(CLIP_ID)
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(2L, second.jsonBody().long("serverSeq"))
        assertEquals(deletedAt, assertNotNull(factory.clip(CLIP_ID)).deletedAt)
        assertEquals(2L, factory.serverSeq())
    }

    @Test
    fun `deleting a clip that never existed returns not found`() = withApp { factory, client ->
        val response = client.deleteClip(clipId('9'))
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("NOT_FOUND", response.errorCode())
        assertEquals(0L, factory.serverSeq())
    }

    // --- 夹具 ---

    private fun createBody(
        id: String? = CLIP_ID,
        content: String = "hello",
        createdAt: Long? = CREATED_AT,
        extra: Map<String, JsonElement> = emptyMap(),
    ): String = buildJsonObject {
        if (id != null) put("id", id)
        put("content", content)
        if (createdAt != null) put("createdAt", createdAt)
        extra.forEach { (key, value) -> put(key, value) }
    }.toString()

    private fun clipId(suffix: Char) = "01J8Z6Z3K4M5N6P7Q8R9S0T1V$suffix"

    private suspend fun HttpClient.createClip(body: String): HttpResponse =
        post("/api/v1/clips") {
            header("Authorization", "Bearer $deviceToken")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpClient.patchClip(id: String, body: String): HttpResponse =
        patch("/api/v1/clips/$id") {
            header("Authorization", "Bearer $deviceToken")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpClient.deleteClip(id: String): HttpResponse =
        delete("/api/v1/clips/$id") {
            header("Authorization", "Bearer $deviceToken")
        }

    private suspend fun HttpResponse.jsonBody(): JsonObject =
        Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.errorCode(): String =
        jsonBody().getValue("error").jsonObject.getValue("code").jsonPrimitive.content

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long

    private fun JsonObject.boolean(key: String) = getValue(key).jsonPrimitive.boolean

    private suspend fun DatabaseFactory.clip(id: String): ClipRow? =
        readTransaction { ClipRepository.findById(id) }

    private suspend fun DatabaseFactory.serverSeq(): Long =
        readTransaction { MetaRepository.getLong(MetaRepository.SERVER_SEQ_KEY) ?: -1L }

    private suspend fun DatabaseFactory.clipCount(): Long =
        readTransaction { ClipsTable.selectAll().count() }

    private fun withApp(
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
        }
        application { module(testConfig(), factory) }
        client.get("/healthz")
        block(factory, client)
    }

    private fun testConfig() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        adminToken = adminToken,
        publicBaseUrl = "http://127.0.0.1:8080",
    )

    companion object {
        private const val CLIP_ID = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
        private const val CREATED_AT = 1_730_000_000_000L
    }
}
