/**
 * author : 王星星
 * date : 2026-11-17 16:06
 * email : 1099420259@qq.com
 * description : 日志脱敏；令牌、配对码与分享链接在默认与诊断模式下都必须脱敏，诊断模式只额外保留请求/响应正文。
 *               注意：配对码只匹配 6 位数字字段，不能误伤错误码字段
 */
package com.wangxingxing.clipbox.logging

object LogRedactor {

    const val MASK = "<redacted>"

    private val BEARER_TOKEN = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
    private val SECRET_JSON_FIELD = Regex(
        """("(?:token|deviceToken|shareToken|accessToken|authToken|pairingCode|secret)"\s*:\s*")[^"]*(")""",
    )
    private val PAIRING_CODE_FIELD = Regex("""("code"\s*:\s*")\d{6}(")""")
    private val SHARE_PATH_TOKEN = Regex("""(/s/)[A-Za-z0-9_-]{16,}""")

    // WS 认证令牌以 `token=xxx` 键值形式渲染；Bearer 前缀一并吞掉，避免只遮蔽前缀后漏出真实令牌
    private val TOKEN_ASSIGNMENT = Regex("""(?i)\b(token|authorization)\s*=\s*(?:Bearer\s+)?(\S+)""")

    fun redact(text: String): String = text
        .replace(TOKEN_ASSIGNMENT) { match -> match.groupValues[1] + "=" + MASK }
        .replace(BEARER_TOKEN, "Bearer $MASK")
        .replace(SECRET_JSON_FIELD) { match -> match.groupValues[1] + MASK + match.groupValues[2] }
        .replace(PAIRING_CODE_FIELD) { match -> match.groupValues[1] + MASK + match.groupValues[2] }
        .replace(SHARE_PATH_TOKEN) { match -> match.groupValues[1] + MASK }
}
