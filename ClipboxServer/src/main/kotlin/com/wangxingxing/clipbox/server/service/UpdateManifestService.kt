/**
 * author : 王星星
 * date : 2026-11-19 11:04
 * email : 1099420259@qq.com
 * description : 读取下载目录中的 downloads.json（schema 2），校验后投影为公开更新描述；每次请求重新读取，不做进程内缓存。
 *               注意：任一校验失败都返回 null，由路由统一映射为 503；错误信息不得携带磁盘路径、下载 URL 与发布说明；
 *                     下载 URL 只能由 publicBaseUrl 拼接，不能信任清单中的同名字段。
 */
package com.wangxingxing.clipbox.server.service

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
data class UpdateNotes(
    @SerialName("zh-CN") val zhCN: String,
    @SerialName("zh-TW") val zhTW: String,
    val en: String,
)

/** 不可用平台只输出 id 与 available；其余字段为 null 时由序列化默认值省略。 */
@Serializable
data class UpdatePlatform(
    val id: String,
    val available: Boolean,
    val file: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val downloadUrl: String? = null,
)

@Serializable
data class UpdateDescriptor(
    val version: String,
    val publishedAt: String,
    val notes: UpdateNotes,
    val platforms: List<UpdatePlatform>,
)

class UpdateManifestService(
    private val downloadDir: Path,
    private val publicBaseUrl: String,
) {
    fun latest(): UpdateDescriptor? = readManifest()?.let(::project)

    private fun readManifest(): JsonObject? {
        val path = downloadDir.resolve(MANIFEST_FILE)
        return try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_MANIFEST_BYTES) return null
            Json.parseToJsonElement(Files.readString(path)) as? JsonObject
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun project(manifest: JsonObject): UpdateDescriptor? {
        if (manifest.integer("schemaVersion") != 2) return null
        val version = manifest.string("version")?.takeIf(VERSION_PATTERN::matches) ?: return null
        val generatedAt = manifest.string("generatedAt")?.takeIf(::isUtcTimestamp) ?: return null
        val notes = (manifest["notes"] as? JsonObject)?.let(::projectNotes) ?: return null
        val platformArray = manifest["platforms"] as? JsonArray ?: return null
        if (platformArray.size != PLATFORM_IDS.size) return null

        val byId = LinkedHashMap<String, JsonObject>()
        for (element in platformArray) {
            val item = element as? JsonObject ?: return null
            val id = item.string("id") ?: return null
            if (id !in PLATFORM_IDS || byId.put(id, item) != null) return null
        }

        val platforms = PLATFORM_IDS.map { id ->
            projectPlatform(id, byId.getValue(id)) ?: return null
        }
        return UpdateDescriptor(version, generatedAt, notes, platforms)
    }

    private fun projectNotes(notes: JsonObject): UpdateNotes? {
        val zhCN = notes.note("zh-CN") ?: return null
        if (zhCN.isBlank() || utf8Size(zhCN) > MAX_NOTE_BYTES) return null
        val zhTW = notes.noteOrFallback("zh-TW", zhCN) ?: return null
        val en = notes.noteOrFallback("en", zhCN) ?: return null
        return UpdateNotes(zhCN = zhCN, zhTW = zhTW, en = en)
    }

    private fun projectPlatform(id: String, item: JsonObject): UpdatePlatform? {
        val available = item.boolean("available") ?: return null
        if (!available) return UpdatePlatform(id = id, available = false)
        val file = item.string("file")?.takeIf(FILE_PATTERN::matches) ?: return null
        val sizeBytes = item.positiveLong("sizeBytes") ?: return null
        val sha256 = item.string("sha256")?.takeIf(SHA256_PATTERN::matches) ?: return null
        return UpdatePlatform(
            id = id,
            available = true,
            file = file,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            downloadUrl = "$publicBaseUrl/downloads/$file",
        )
    }

    // 缺失或空白说明回退 zh-CN；类型错误仍视为清单非法，不能静默吞掉。
    private fun JsonObject.noteOrFallback(key: String, fallback: String): String? {
        val element = this[key] ?: return fallback
        val value = (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        if (value.isBlank()) return fallback
        if (utf8Size(value) > MAX_NOTE_BYTES) return null
        return value
    }

    private fun JsonObject.note(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.integer(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.positiveLong(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it > 0 }

    private fun isUtcTimestamp(value: String): Boolean =
        value.endsWith("Z") && runCatching { Instant.parse(value) }.isSuccess

    private fun utf8Size(value: String): Int = value.encodeToByteArray().size

    private companion object {
        const val MANIFEST_FILE = "downloads.json"
        const val MAX_MANIFEST_BYTES = 1024L * 1024
        const val MAX_NOTE_BYTES = 64 * 1024
        val PLATFORM_IDS = listOf("android", "ios", "macos", "windows", "linux")
        val VERSION_PATTERN = Regex("""^\d+\.\d+\.\d+$""")
        val FILE_PATTERN = Regex("""^Theca-[0-9A-Za-z._-]+$""")
        val SHA256_PATTERN = Regex("""^[0-9a-f]{64}$""")
    }
}
