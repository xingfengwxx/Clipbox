/**
 * author : 王星星
 * date : 2026-11-19 12:30
 * email : 1099420259@qq.com
 * description : Desktop 更新安装器；Ktor 流式下载到应用数据目录 updates/<file>.part，完成后原子改名，校验 SHA-256 后用系统默认程序打开 DMG / MSI / DEB。
 *               注意：关闭更新弹窗不取消下载，只有退出应用经 UpdateController.close() 才取消并删除 .part；不静默执行安装包
 */
package com.wangxingxing.clipbox.update

import com.wangxingxing.clipbox.network.defaultHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

class DesktopUpdateInstaller : UpdateInstaller {

    private val osName = System.getProperty("os.name").orEmpty().lowercase()

    override val platformId: UpdatePlatformId = when {
        osName.contains("mac") -> UpdatePlatformId.MACOS
        osName.contains("win") -> UpdatePlatformId.WINDOWS
        else -> UpdatePlatformId.LINUX
    }

    private val client = HttpClient(defaultHttpClientEngine()) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
            // 安装包下载时长不可预估，只限制连接与空闲；取消由协程 job 负责
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
        }
    }

    @Volatile
    private var activePart: File? = null

    @Volatile
    private var currentTarget: File? = null

    init {
        // Desktop 不持久化下载状态，上次进程遗留的安装包与半成品没有再续传的语义，启动即清理
        updatesDir().listFiles()?.forEach { it.delete() }
    }

    override suspend fun download(
        platform: UpdatePlatform,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Boolean {
        val url = platform.downloadUrl ?: return false
        val fileName = platform.file?.let { File(it).name }?.takeIf { it.isNotBlank() } ?: return false
        val directory = updatesDir()
        directory.mkdirs()
        val target = File(directory, fileName)
        val part = File(directory, "$fileName.part")
        cleanupStaleFiles(keep = fileName)
        part.delete()

        activePart = part
        currentTarget = target
        return try {
            val response = client.get(url)
            if (!response.status.isSuccess()) return false
            val declaredTotal = platform.sizeBytes ?: 0L
            var downloaded = 0L
            withContext(Dispatchers.IO) {
                FileOutputStream(part).use { output ->
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = channel.readAvailable(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, if (declaredTotal > 0) declaredTotal else downloaded)
                    }
                    output.fd.sync()
                }
            }
            if (!part.renameTo(target)) {
                part.delete()
                false
            } else {
                onProgress(target.length(), target.length())
                true
            }
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (t: Throwable) {
            part.delete()
            false
        } finally {
            activePart = null
        }
    }

    override suspend fun verify(expectedSha256: String): Boolean {
        val file = currentTarget?.takeIf { it.isFile } ?: return false
        val actual = file.inputStream().use { stream ->
            sha256Hex { buffer -> stream.read(buffer) }
        }
        if (actual.equals(expectedSha256.trim(), ignoreCase = true)) return true
        file.delete()
        currentTarget = null
        return false
    }

    override suspend fun install(): UpdateInstallResult {
        val file = currentTarget?.takeIf { it.isFile } ?: return UpdateInstallResult.Unavailable
        return runCatching {
            if (!Desktop.isDesktopSupported()) return@runCatching UpdateInstallResult.Failed
            Desktop.getDesktop().open(file)
            UpdateInstallResult.Started
        }.getOrDefault(UpdateInstallResult.Failed)
    }

    override suspend fun cancel() {
        activePart?.delete()
        activePart = null
    }

    override suspend fun cleanup() {
        updatesDir().listFiles()?.forEach { it.delete() }
        currentTarget = null
    }

    private fun cleanupStaleFiles(keep: String) {
        updatesDir().listFiles()?.forEach { file ->
            if (file.name != keep) file.delete()
        }
    }

    private fun updatesDir(): File {
        val home = System.getProperty("user.home")
        val root = when {
            osName.contains("mac") -> File(home, "Library/Application Support/Theca")
            osName.contains("win") -> File(System.getenv("APPDATA") ?: home, "Theca")
            else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, "theca")
        }
        return File(root, "updates")
    }
}
