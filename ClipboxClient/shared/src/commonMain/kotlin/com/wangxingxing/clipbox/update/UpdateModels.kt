/**
 * author : 王星星
 * date : 2026-11-19 11:08
 * email : 1099420259@qq.com
 * description : 应用更新描述接口的共享模型与结果类型；只服务官方发行服务器，不参与同步链路。
 *               注意：notes 缺失 zh-TW / en 时在解码阶段回退 zh-CN，UI 不再处理空说明
 */
package com.wangxingxing.clipbox.update

import com.wangxingxing.clipbox.settings.AppLanguage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
data class UpdateDescriptor(
    val version: String,
    val publishedAt: String,
    val notes: UpdateNotes,
    val platforms: List<UpdatePlatform>,
) {
    fun platform(id: UpdatePlatformId): UpdatePlatform? = platforms.firstOrNull { it.id == id.wire }
}

@Serializable(with = UpdateNotesSerializer::class)
data class UpdateNotes(
    @SerialName("zh-CN") val zhCN: String,
    @SerialName("zh-TW") val zhTW: String,
    val en: String,
) {
    fun forLanguage(language: AppLanguage): String = when (language) {
        AppLanguage.ZH_HANS -> zhCN
        AppLanguage.ZH_TW -> zhTW
        AppLanguage.EN -> en
    }
}

@Serializable
data class UpdatePlatform(
    val id: String,
    val available: Boolean,
    val file: String? = null,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val downloadUrl: String? = null,
)

enum class UpdatePlatformId(val wire: String) {
    ANDROID("android"),
    IOS("ios"),
    MACOS("macos"),
    WINDOWS("windows"),
    LINUX("linux"),
    ;

    companion object {
        fun fromWire(value: String?): UpdatePlatformId? = entries.firstOrNull { it.wire == value }
    }
}

sealed interface UpdateCheckResult {
    data class Available(val descriptor: UpdateDescriptor) : UpdateCheckResult

    data class Latest(val descriptor: UpdateDescriptor) : UpdateCheckResult

    /** 版本号不是纯数字点分格式，无法判断新旧；不得据此提示有更新 */
    data class Incomparable(val descriptor: UpdateDescriptor) : UpdateCheckResult
}

sealed interface UpdateApiResult {
    data class Success(val descriptor: UpdateDescriptor) : UpdateApiResult

    data class Failure(val reason: UpdateFailureReason) : UpdateApiResult
}

enum class UpdateFailureReason {
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    INVALID_RESPONSE,
}

private object UpdateNotesSerializer : KSerializer<UpdateNotes> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
        "com.wangxingxing.clipbox.update.UpdateNotes",
    ) {
        element<String>("zh-CN")
        element<String>("zh-TW")
        element<String>("en")
    }

    override fun deserialize(decoder: Decoder): UpdateNotes {
        val json = decoder as? JsonDecoder
            ?: throw SerializationException("UpdateNotes 只支持 JSON 解码")
        val source = json.decodeJsonElement().jsonObject
        val fallback = stringOrNull(source["zh-CN"])
            ?: throw SerializationException("更新说明缺少 zh-CN")
        return UpdateNotes(
            zhCN = fallback,
            zhTW = stringOrNull(source["zh-TW"]) ?: fallback,
            en = stringOrNull(source["en"]) ?: fallback,
        )
    }

    override fun serialize(encoder: Encoder, value: UpdateNotes) {
        val json = encoder as? JsonEncoder
            ?: throw SerializationException("UpdateNotes 只支持 JSON 编码")
        json.encodeJsonElement(
            buildJsonObject {
                put("zh-CN", value.zhCN)
                put("zh-TW", value.zhTW)
                put("en", value.en)
            },
        )
    }

    // 类型不符的说明按缺失处理并回退，避免单个错误键让整次检查失败
    private fun stringOrNull(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content
}
