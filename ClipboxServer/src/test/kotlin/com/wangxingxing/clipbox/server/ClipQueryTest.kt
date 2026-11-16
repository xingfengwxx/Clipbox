/**
 * author : 王星星
 * date : 2026-11-16 15:21
 * email : 1099420259@qq.com
 * description : GET /api/v1/clips 契约测试：分页与 hasMore、空页游标、墓碑字段集、两种重同步触发、来源设备名与参数校验。
 *               注意：每个用例使用独立临时目录与真实 SQLite；断言只依赖 HTTP 响应与仓储写入结果。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.ClipRepository
import com.wangxingxing.clipbox.server.db.ClipRow
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.db.MetaRepository
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.io.TempDir

class ClipQueryTest {

    @TempDir
    lateinit var tempDir: Path

    private val adminToken = "a".repeat(32)
    private val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
    private val deviceToken = "d".repeat(43)

    @Test
    fun `pages advance by serverSeq and report hasMore`() = withApp(
        seed = { factory ->
            seedDevice(factory)
            (1..5).forEach { seedClip(factory, id = "CLIP$it", seq = it.toLong(), sourceDeviceId = deviceId) }
            seedMeta(factory, serverSeq = 5, syncFloor = 0)
        },
    ) { _, client ->
        val first = client.sync(since = 0, limit = 2)
        assertEquals(listOf("CLIP1", "CLIP2"), first.itemIds())
        assertEquals(2L, first.long("nextSince"))
        assertEquals(true, first.boolean("hasMore"))
        assertEquals(false, first.boolean("resyncRequired"))
        assertEquals(1L, first.long("minAvailableSeq"))
        assertEquals(5L, first.long("serverMaxSeq"))

        val second = first.nextPage(client, limit = 2)
        assertEquals(listOf("CLIP3", "CLIP4"), second.itemIds())
        assertEquals(4L, second.long("nextSince"))
        assertEquals(true, second.boolean("hasMore"))

        val third = second.nextPage(client, limit = 2)
        assertEquals(listOf("CLIP5"), third.itemIds())
        assertEquals(5L, third.long("nextSince"))
        assertEquals(false, third.boolean("hasMore"))

        val empty = third.nextPage(client, limit = 2)
        assertTrue(empty.itemIds().isEmpty())
        assertEquals(5L, empty.long("nextSince"), "空页 nextSince 必须等于请求的 since")
        assertEquals(false, empty.boolean("hasMore"))
    }

    @Test
    fun `default since is zero and default limit is 200`() = withApp(
        seed = { factory ->
            seedDevice(factory)
            (1..201).forEach { seedClip(factory, id = "CLIP$it", seq = it.toLong(), sourceDeviceId = deviceId) }
            seedMeta(factory, serverSeq = 201, syncFloor = 0)
        },
    ) { _, client ->
        val response = client.get(CLIPS) { header("Authorization", "Bearer $deviceToken") }
        assertEquals(HttpStatusCode.OK, response.status)
        val page = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(200, page.getValue("items").jsonArray.size)
        assertEquals(200L, page.long("nextSince"))
        assertEquals(true, page.boolean("hasMore"))
    }

    @Test
    fun `active items carry the full contract field set`() = withApp(
        seed = { factory ->
            seedDevice(factory)
            seedClip(factory, id = "CLIP1", seq = 1, content = "hello", pinned = true, createdAt = 1730000000000)
            seedMeta(factory, serverSeq = 1, syncFloor = 0)
        },
    ) { _, client ->
        val item = client.sync(since = 0, limit = 200).items().single()
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
            item.keys,
        )
        assertEquals("CLIP1", item.getValue("id").jsonPrimitive.content)
        assertEquals("hello", item.getValue("content").jsonPrimitive.content)
        assertEquals(deviceId, item.getValue("sourceDeviceId").jsonPrimitive.content)
        assertEquals("Pixel 8", item.getValue("sourceDeviceName").jsonPrimitive.content)
        assertEquals(true, item.getValue("pinned").jsonPrimitive.boolean)
        assertEquals(1730000000000L, item.getValue("createdAt").jsonPrimitive.long)
        assertEquals(1L, item.getValue("serverSeq").jsonPrimitive.long)
        assertEquals(false, item.getValue("deleted").jsonPrimitive.boolean)
    }

    @Test
    fun `tombstones expose only id serverSeq and deleted`() = withApp(
        seed = { factory ->
            seedDevice(factory)
            seedClip(factory, id = "GONE", seq = 7, deletedAt = 2_000)
            seedMeta(factory, serverSeq = 7, syncFloor = 0)
        },
    ) { _, client ->
        val item = client.sync(since = 0, limit = 200).items().single()
        assertEquals(setOf("id", "serverSeq", "deleted"), item.keys)
        assertEquals("GONE", item.getValue("id").jsonPrimitive.content)
        assertEquals(7L, item.getValue("serverSeq").jsonPrimitive.long)
        assertEquals(true, item.getValue("deleted").jsonPrimitive.boolean)
    }

    @Test
    fun `ordering follows serverSeq rather than createdAt`() = withApp(
        seed = { factory ->
            seedDevice(factory)
            seedClip(factory, id = "OLD_BUMP", seq = 1, createdAt = 9_000, sourceDeviceId = deviceId)
            seedClip(factory, id = "NEW_LATE", seq = 2, createdAt = 1_000, sourceDeviceId = deviceId)
            seedMeta(factory, serverSeq = 2, syncFloor = 0)
        },
    ) { _, client ->
        assertEquals(listOf("OLD_BUMP", "NEW_LATE"), client.sync(since = 0, limit = 200).itemIds())
    }

    @Test
    fun `cursor below sync floor requires full resync`() = withApp(
        seed = { factory ->
            seedDevice(factory)
            seedClip(factory, id = "CLIP6", seq = 6, sourceDeviceId = deviceId)
            seedMeta(factory, serverSeq = 20, syncFloor = 10)
        },
    ) { _, client ->
        val page = client.sync(since = 5, limit = 200)
        assertTrue(page.itemIds().isEmpty())
        assertEquals(true, page.boolean("resyncRequired"))
        assertEquals(5L, page.long("nextSince"))
        assertEquals(false, page.boolean("hasMore"))
        assertEquals(11L, page.long("minAvailableSeq"))
        assertEquals(20L, page.long("serverMaxSeq"))
    }

    @Test
    fun `cursor beyond server max seq requires full resync`() = withApp(
        seed = { factory ->
            seedDevice(factory)
            seedClip(factory, id = "CLIP1", seq = 1, sourceDeviceId = deviceId)
            seedMeta(factory, serverSeq = 10, syncFloor = 0)
        },
    ) { _, client ->
        val page = client.sync(since = 11, limit = 200)
        assertTrue(page.itemIds().isEmpty())
        assertEquals(true, page.boolean("resyncRequired"))
        assertEquals(11L, page.long("nextSince"))
        assertEquals(false, page.boolean("hasMore"))
        assertEquals(1L, page.long("minAvailableSeq"))
        assertEquals(10L, page.long("serverMaxSeq"))
    }

    @Test
    fun `since zero never requires resync even with non zero sync floor`() = withApp(
        seed = { factory ->
            seedDevice(factory)
            seedClip(factory, id = "CLIP11", seq = 11, sourceDeviceId = deviceId)
            seedMeta(factory, serverSeq = 20, syncFloor = 10)
        },
    ) { _, client ->
        val page = client.sync(since = 0, limit = 200)
        assertEquals(false, page.boolean("resyncRequired"))
        assertEquals(listOf("CLIP11"), page.itemIds())
        assertEquals(11L, page.long("minAvailableSeq"))
    }

    @Test
    fun `missing source device falls back to unknown device`() = withApp(
        seed = { factory ->
            seedDevice(factory)
            seedClip(factory, id = "ORPHAN", seq = 1, sourceDeviceId = null)
            seedMeta(factory, serverSeq = 1, syncFloor = 0)
        },
    ) { _, client ->
        val item = client.sync(since = 0, limit = 200).items().single()
        assertEquals("未知设备", item.getValue("sourceDeviceName").jsonPrimitive.content)
        assertEquals(JsonNull, item.getValue("sourceDeviceId"))
    }

    @Test
    fun `invalid cursor and limit are validation errors`() = withApp(
        seed = { factory ->
            seedDevice(factory)
            seedClip(factory, id = "CLIP1", seq = 1, sourceDeviceId = deviceId)
            seedMeta(factory, serverSeq = 1, syncFloor = 0)
        },
    ) { _, client ->
        val invalidQueries = listOf(
            "since=-1",
            "since=abc",
            "limit=0",
            "limit=501",
            "limit=abc",
        )
        invalidQueries.forEach { query ->
            val response = client.get("$CLIPS?$query") { header("Authorization", "Bearer $deviceToken") }
            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertEquals("VALIDATION_ERROR", errorCode(response.bodyAsText()), query)
        }

        client.sync(since = 0, limit = 500)
        val maxLimit = client.get("$CLIPS?limit=500") { header("Authorization", "Bearer $deviceToken") }
        assertEquals(HttpStatusCode.OK, maxLimit.status)
    }

    // --- 夹具 ---

    private suspend fun seedDevice(factory: DatabaseFactory) {
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
    }

    private suspend fun seedClip(
        factory: DatabaseFactory,
        id: String,
        seq: Long,
        content: String = "content-$id",
        pinned: Boolean = false,
        createdAt: Long = seq,
        deletedAt: Long? = null,
        sourceDeviceId: String? = deviceId,
    ) {
        factory.writeTransaction {
            ClipRepository.insert(
                ClipRow(
                    id = id,
                    content = content,
                    sourceDeviceId = sourceDeviceId,
                    pinned = pinned,
                    createdAt = createdAt,
                    deletedAt = deletedAt,
                    serverSeq = seq,
                ),
            )
        }
    }

    private suspend fun seedMeta(factory: DatabaseFactory, serverSeq: Long, syncFloor: Long) {
        factory.writeTransaction {
            MetaRepository.put(MetaRepository.SERVER_SEQ_KEY, serverSeq.toString())
            MetaRepository.put(MetaRepository.SYNC_FLOOR_KEY, syncFloor.toString())
        }
    }

    private fun withApp(
        seed: suspend (DatabaseFactory) -> Unit = {},
        block: suspend (DatabaseFactory, HttpClient) -> Unit,
    ) = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        seed(factory)
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

    private suspend fun HttpClient.sync(since: Long, limit: Int): JsonObject {
        val response = get("$CLIPS?since=$since&limit=$limit") {
            header("Authorization", "Bearer $deviceToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private suspend fun JsonObject.nextPage(client: HttpClient, limit: Int): JsonObject =
        client.sync(since = long("nextSince"), limit = limit)

    private fun JsonObject.items() = getValue("items").jsonArray.map { it.jsonObject }

    private fun JsonObject.itemIds() = items().map { it.getValue("id").jsonPrimitive.content }

    private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long

    private fun JsonObject.boolean(key: String) = getValue(key).jsonPrimitive.boolean

    private fun errorCode(body: String): String =
        Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    companion object {
        private const val CLIPS = "/api/v1/clips"
    }
}
