/**
 * author : 王星星
 * date : 2026-11-17 15:22
 * email : 1099420259@qq.com
 * description : Desktop 剪贴板实现；AWT 系统剪贴板读写，500ms 轮询只在自动发送开启时由控制器启动。注意：Linux/Wayland 下事件不可靠，轮询是主路径，不能改成 FlavorListener
 */
package com.wangxingxing.clipbox.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import javax.swing.SwingUtilities

class DesktopClipboardService : ClipboardService {

    private var watchScope: CoroutineScope? = null
    private var watchJob: Job? = null

    override suspend fun readText(): ClipboardReadResult = try {
        val text = readRaw()
        if (text.isNullOrEmpty()) ClipboardReadResult.Empty else ClipboardReadResult.Success(text)
    } catch (_: Throwable) {
        ClipboardReadResult.Failed
    }

    override suspend fun writeText(text: String): ClipboardWriteResult = try {
        onEdt {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(text), null)
        }
        ClipboardWriteResult.Success
    } catch (_: Throwable) {
        ClipboardWriteResult.Failed
    }

    override fun startWatching(onText: (String) -> Unit) {
        if (watchJob?.isActive == true) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        watchScope = scope
        watchJob = scope.launch {
            // 以启动时的剪贴板为基线，避免把开启自动发送前的内容立即上传
            var lastSeen = readRaw()
            while (isActive) {
                delay(POLL_MILLIS)
                val current = try {
                    readRaw()
                } catch (_: Throwable) {
                    null
                }
                if (current != null && current != lastSeen) {
                    lastSeen = current
                    onText(current)
                }
            }
        }
    }

    override fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        watchScope?.cancel()
        watchScope = null
    }

    private fun readRaw(): String? {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return null
        return clipboard.getData(DataFlavor.stringFlavor) as? String
    }

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeAndWait(block)
        }
    }

    private companion object {
        const val POLL_MILLIS = 500L
    }
}
