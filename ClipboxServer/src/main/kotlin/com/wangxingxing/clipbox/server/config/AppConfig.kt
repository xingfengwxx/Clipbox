package com.wangxingxing.clipbox.server.config

import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

class ConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class AppConfig(
    val port: Int,
    val dataDir: Path,
    val adminToken: String,
    val publicBaseUrl: String,
) {
    companion object {
        private const val DEFAULT_PORT = 8080
        private const val DEFAULT_DATA_DIR = "./data"
        private const val MIN_ADMIN_TOKEN_LENGTH = 32

        fun fromEnv(env: Map<String, String>): AppConfig {
            val adminToken = env["ADMIN_TOKEN"]?.takeIf { it.isNotBlank() }
                ?: throw ConfigException("ADMIN_TOKEN 未配置")
            if (adminToken.length < MIN_ADMIN_TOKEN_LENGTH) {
                throw ConfigException("ADMIN_TOKEN 长度必须不少于 $MIN_ADMIN_TOKEN_LENGTH 个字符")
            }

            val publicBaseUrl = env["PUBLIC_BASE_URL"]?.takeIf { it.isNotBlank() }
                ?: throw ConfigException("PUBLIC_BASE_URL 未配置，需要完整的 http:// 或 https:// 绝对地址")
            validatePublicBaseUrl(publicBaseUrl)

            val port = env["PORT"]?.takeIf { it.isNotBlank() }?.let { raw ->
                val parsed = raw.toIntOrNull()
                if (parsed == null || parsed !in 1..65535) {
                    throw ConfigException("PORT 必须是 1-65535 之间的整数，当前值：$raw")
                }
                parsed
            } ?: DEFAULT_PORT

            val dataDirRaw = env["DATA_DIR"]?.takeIf { it.isNotBlank() } ?: DEFAULT_DATA_DIR
            val dataDir = resolveWritableDataDir(dataDirRaw)

            return AppConfig(
                port = port,
                dataDir = dataDir,
                adminToken = adminToken,
                publicBaseUrl = publicBaseUrl,
            )
        }

        private fun validatePublicBaseUrl(value: String) {
            val uri = try {
                URI(value)
            } catch (e: URISyntaxException) {
                throw ConfigException("PUBLIC_BASE_URL 需要完整的 http:// 或 https:// 绝对地址", e)
            }
            val scheme = uri.scheme?.lowercase()
            val valid = (scheme == "http" || scheme == "https") &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null &&
                !value.endsWith("/")
            if (!valid) {
                throw ConfigException("PUBLIC_BASE_URL 需要完整的 http:// 或 https:// 绝对地址（含主机、无尾斜杠）")
            }
        }

        private fun resolveWritableDataDir(raw: String): Path {
            val path = try {
                Paths.get(raw)
            } catch (e: InvalidPathException) {
                throw ConfigException("DATA_DIR 不是合法路径：$raw", e)
            }
            try {
                Files.createDirectories(path)
            } catch (e: IOException) {
                throw ConfigException("DATA_DIR 无法创建：$raw（${e.message}）", e)
            }
            if (!Files.isWritable(path)) {
                throw ConfigException("DATA_DIR 不可写：$raw")
            }
            return path
        }
    }
}
