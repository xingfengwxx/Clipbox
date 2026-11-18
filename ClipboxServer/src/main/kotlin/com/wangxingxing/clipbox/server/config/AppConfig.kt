/**
 * author : 王星星
 * date : 2026-11-16 10:33
 * email : 1099420259@qq.com
 * description : 服务端启动配置的读取与校验；非法配置在启动前抛出 ConfigException。
 *               注意：错误信息只描述缺失项或格式约束，不得回显 ADMIN_TOKEN 明文；
 *                     站点与下载目录只解析路径、不创建也不校验存在性，缺少目录时由路由层跳过托管。
 */
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
    val webDir: Path = Paths.get(DEFAULT_WEB_DIR),
    val downloadDir: Path = Paths.get(DEFAULT_DOWNLOAD_DIR),
) {
    companion object {
        private const val DEFAULT_PORT = 8080
        private const val DEFAULT_DATA_DIR = "./data"
        private const val DEFAULT_WEB_DIR = "./web"
        private const val DEFAULT_DOWNLOAD_DIR = "./downloads"
        private const val MIN_ADMIN_TOKEN_LENGTH = 32

        fun fromEnv(env: Map<String, String>): AppConfig {
            val adminToken = env["ADMIN_TOKEN"]?.takeIf { it.isNotBlank() }
                ?: throw ConfigException("ADMIN_TOKEN 未配置")
            // 只报告长度约束，不把令牌值带进异常信息，避免启动日志泄露管理凭证。
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

            // 本机与测试默认写相对目录；容器部署由 compose 显式注入 DATA_DIR=/data。
            val dataDirRaw = env["DATA_DIR"]?.takeIf { it.isNotBlank() } ?: DEFAULT_DATA_DIR
            val dataDir = resolveWritableDataDir(dataDirRaw)

            val webDir = resolveStaticDir(
                env["WEB_DIR"]?.takeIf { it.isNotBlank() } ?: DEFAULT_WEB_DIR,
                "WEB_DIR",
            )
            val downloadDir = resolveStaticDir(
                env["DOWNLOAD_DIR"]?.takeIf { it.isNotBlank() } ?: DEFAULT_DOWNLOAD_DIR,
                "DOWNLOAD_DIR",
            )

            return AppConfig(
                port = port,
                dataDir = dataDir,
                adminToken = adminToken,
                publicBaseUrl = publicBaseUrl,
                webDir = webDir,
                downloadDir = downloadDir,
            )
        }

        // 分享链接按 PUBLIC_BASE_URL + "/s/" + token 直接拼接：尾斜杠、查询串或片段会破坏拼接结果。
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

        // 站点与下载目录允许缺失：测试夹具、本地 API 调试与未装站点的部署都要能启动。
        private fun resolveStaticDir(raw: String, name: String): Path =
            try {
                Paths.get(raw)
            } catch (e: InvalidPathException) {
                throw ConfigException("$name 不是合法路径：$raw", e)
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
