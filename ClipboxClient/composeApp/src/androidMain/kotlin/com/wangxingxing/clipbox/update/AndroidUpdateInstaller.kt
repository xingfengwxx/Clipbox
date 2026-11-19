/**
 * author : 王星星
 * date : 2026-11-19 12:05
 * email : 1099420259@qq.com
 * description : Android 更新安装器；用系统 DownloadManager 下载 APK、查询进度与持久化下载 ID，校验 SHA-256 后由用户点击拉起系统安装器。
 *               注意：通知由系统下载器发布，应用不申请通知权限；未知来源授权只跳系统设置，不做静默安装或绕过
 */
package com.wangxingxing.clipbox.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.update_download_notification_description
import com.wangxingxing.clipbox.resources.update_download_notification_title
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import java.io.File

private const val APK_MIME = "application/vnd.android.package-archive"
private const val PREFS_NAME = "update_download"
private const val KEY_DOWNLOAD_ID = "downloadId"
private const val KEY_FILE_NAME = "fileName"
private const val DOWNLOAD_DIR = "updates"
private const val POLL_INTERVAL_MS = 500L

internal class AndroidUpdateInstaller(private val context: Context) : UpdateInstaller {

    override val platformId: UpdatePlatformId = UpdatePlatformId.ANDROID

    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val receiverLock = Any()

    private var activeReceiver: BroadcastReceiver? = null
    private var completionSignal: CompletableDeferred<Long>? = null

    override suspend fun download(
        platform: UpdatePlatform,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Boolean {
        val url = platform.downloadUrl ?: return false
        val fileName = platform.file?.let { File(it).name }?.takeIf { it.isNotBlank() } ?: return false
        val file = targetFile(fileName) ?: return false
        cleanupStaleFiles(keep = fileName)

        val persistedId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        val persistedName = prefs.getString(KEY_FILE_NAME, null)
        if (persistedId != -1L) {
            if (persistedName == fileName) {
                val snapshot = query(persistedId)
                if (snapshot == null) {
                    clearPersisted()
                } else {
                    when (snapshot.status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            if (file.exists()) {
                                onProgress(file.length(), file.length())
                                return true
                            }
                            clearPersisted()
                        }
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PENDING -> return observe(persistedId, file, onProgress)
                        else -> {
                            runCatching { downloadManager.remove(persistedId) }
                            clearPersisted()
                        }
                    }
                }
            } else {
                runCatching { downloadManager.remove(persistedId) }
                clearPersisted()
            }
        }

        file.delete()
        val title = getString(Res.string.update_download_notification_title)
        val description = getString(Res.string.update_download_notification_description)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription(description)
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "$DOWNLOAD_DIR/$fileName")
        val id = runCatching { downloadManager.enqueue(request) }.getOrNull() ?: return false
        prefs.edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putString(KEY_FILE_NAME, fileName)
            .apply()
        return observe(id, file, onProgress)
    }

    override suspend fun verify(expectedSha256: String): Boolean {
        val file = currentFile()?.takeIf { it.isFile } ?: return false
        val actual = file.inputStream().use { stream ->
            sha256Hex { buffer -> stream.read(buffer) }
        }
        if (actual.equals(expectedSha256.trim(), ignoreCase = true)) return true
        file.delete()
        clearPersisted()
        return false
    }

    override suspend fun install(): UpdateInstallResult {
        val file = currentFile()?.takeIf { it.isFile } ?: return UpdateInstallResult.Unavailable
        if (!context.packageManager.canRequestPackageInstalls()) {
            // 只跳系统授权页；用户返回后再次点击安装，权限判定重新执行
            return runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                UpdateInstallResult.PermissionRequired
            }.getOrDefault(UpdateInstallResult.Failed)
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            UpdateInstallResult.Started
        }.getOrDefault(UpdateInstallResult.Failed)
    }

    override suspend fun cancel() {
        unregisterCompletion()
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id != -1L) runCatching { downloadManager.remove(id) }
        cleanup()
    }

    override suspend fun cleanup() {
        updatesDir()?.listFiles()?.forEach { it.delete() }
        clearPersisted()
    }

    private suspend fun observe(
        id: Long,
        file: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Boolean {
        val signal = registerCompletion(id)
        try {
            while (true) {
                val snapshot = query(id) ?: return false
                when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        if (!file.isFile) return false
                        onProgress(file.length(), file.length())
                        return true
                    }
                    DownloadManager.STATUS_FAILED,
                    DownloadManager.STATUS_PAUSED -> return false
                    else -> {
                        onProgress(snapshot.downloadedBytes, snapshot.totalBytes)
                        // 广播到达即刻醒来，未到达则按轮询间隔刷新进度
                        withTimeoutOrNull(POLL_INTERVAL_MS) { signal.await() }
                    }
                }
            }
        } finally {
            unregisterCompletion()
        }
    }

    private fun registerCompletion(id: Long): CompletableDeferred<Long> {
        val signal = CompletableDeferred<Long>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) == id) {
                    signal.complete(id)
                }
            }
        }
        synchronized(receiverLock) {
            activeReceiver = receiver
            completionSignal = signal
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        return signal
    }

    private fun unregisterCompletion() {
        val signal: CompletableDeferred<Long>?
        val receiver: BroadcastReceiver?
        synchronized(receiverLock) {
            receiver = activeReceiver
            signal = completionSignal
            activeReceiver = null
            completionSignal = null
        }
        signal?.cancel()
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
    }

    private fun query(id: Long): DownloadSnapshot? {
        val cursor = runCatching {
            downloadManager.query(DownloadManager.Query().setFilterById(id))
        }.getOrNull() ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            return DownloadSnapshot(
                status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloadedBytes = it.getLong(
                    it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                ),
                totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }
    }

    private fun currentFile(): File? {
        val name = prefs.getString(KEY_FILE_NAME, null) ?: return null
        return targetFile(File(name).name)
    }

    private fun targetFile(fileName: String): File? = updatesDir()?.let { File(it, fileName) }

    private fun updatesDir(): File? {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        return File(external, DOWNLOAD_DIR)
    }

    private fun cleanupStaleFiles(keep: String) {
        updatesDir()?.listFiles()?.forEach { file ->
            if (file.name != keep) file.delete()
        }
    }

    private fun clearPersisted() {
        prefs.edit().clear().apply()
    }

    private data class DownloadSnapshot(
        val status: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )
}
