/**
 * author : 王星星
 * date : 2026-11-17 15:20
 * email : 1099420259@qq.com
 * description : Android 剪贴板实现；只响应用户显式复制/粘贴，不注册监听、不后台读取。注意：读取空 ClipData 与系统拒绝必须区分，失败不能导致崩溃
 */
package com.wangxingxing.clipbox.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class AndroidClipboardService(context: Context) : ClipboardService {

    private val appContext = context.applicationContext
    private val clipboard = appContext.getSystemService(ClipboardManager::class.java)

    override suspend fun readText(): ClipboardReadResult = try {
        val clip = clipboard?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            ClipboardReadResult.Empty
        } else {
            val text = clip.getItemAt(0).coerceToText(appContext)?.toString().orEmpty()
            if (text.isEmpty()) ClipboardReadResult.Empty else ClipboardReadResult.Success(text)
        }
    } catch (_: SecurityException) {
        ClipboardReadResult.Failed
    } catch (_: Throwable) {
        ClipboardReadResult.Failed
    }

    override suspend fun writeText(text: String): ClipboardWriteResult = try {
        val manager = clipboard ?: return ClipboardWriteResult.Failed
        manager.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
        ClipboardWriteResult.Success
    } catch (_: Throwable) {
        ClipboardWriteResult.Failed
    }

    // Android v1.0.0 不提供后台剪贴板能力，自动发送只在 Desktop 生效
    override fun startWatching(onText: (String) -> Unit) = Unit

    override fun stopWatching() = Unit

    private companion object {
        const val CLIP_LABEL = "Clipbox"
    }
}
