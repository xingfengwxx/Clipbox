/**
 * author : 王星星
 * date : 2026-11-16 15:07
 * email : 1099420259@qq.com
 * description : 服务器地址校验与规范化；只校验 scheme 与主机，不强制 HTTPS，学习版允许 http:// 明文地址
 */
package com.wangxingxing.clipbox.settings

enum class ServerUrlError {
    MISSING_SCHEME,
    UNSUPPORTED_SCHEME,
    MISSING_HOST,
}

sealed interface ServerUrlValidation {
    data class Valid(val url: String, val insecure: Boolean) : ServerUrlValidation
    data class Invalid(val error: ServerUrlError) : ServerUrlValidation
}

object ServerUrlValidator {

    fun validate(raw: String): ServerUrlValidation {
        val trimmed = raw.trim()
        val schemeSeparator = trimmed.indexOf("://")
        if (schemeSeparator <= 0) {
            return ServerUrlValidation.Invalid(ServerUrlError.MISSING_SCHEME)
        }

        val scheme = trimmed.substring(0, schemeSeparator).lowercase()
        if (scheme != "http" && scheme != "https") {
            return ServerUrlValidation.Invalid(ServerUrlError.UNSUPPORTED_SCHEME)
        }

        val address = trimmed.substring(schemeSeparator + 3).trimEnd('/')
        if (address.isEmpty()) {
            return ServerUrlValidation.Invalid(ServerUrlError.MISSING_HOST)
        }

        // 只取 authority 判断主机是否为空；路径、查询串不影响保存，交给后续请求拼接
        val authority = address.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').let { hostPort ->
            if (hostPort.startsWith("[")) {
                // IPv6 字面量必须成对出现方括号，否则视为缺少主机
                hostPort.takeIf { it.contains(']') }?.substringBefore(']')?.removePrefix("[")
            } else {
                hostPort.substringBefore(':')
            }
        }
        if (host.isNullOrEmpty() || host.any { it.isWhitespace() }) {
            return ServerUrlValidation.Invalid(ServerUrlError.MISSING_HOST)
        }

        return ServerUrlValidation.Valid(
            url = "$scheme://$address",
            insecure = scheme == "http",
        )
    }
}

/** 设置页与配对页据此展示「未加密连接，仅用于学习」警示，不阻止保存 */
fun isInsecureServerUrl(url: String?): Boolean =
    url != null && url.trim().startsWith("http://", ignoreCase = true)
