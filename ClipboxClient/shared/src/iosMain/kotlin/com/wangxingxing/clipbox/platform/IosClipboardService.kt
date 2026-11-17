/**
 * author : 王星星
 * date : 2026-11-17 15:24
 * email : 1099420259@qq.com
 * description : iOS 剪贴板实现；写入只由用户显式复制触发，读取只由用户显式粘贴触发，不轮询、不后台探测。
 *               注意：iOS 16+ 首次读取会弹系统粘贴权限提示，拒绝时 UIPasteboard 返回 null 且无法与空剪贴板区分，故一律按可重试失败处理
 */
package com.wangxingxing.clipbox.platform

import platform.UIKit.UIPasteboard

class IosClipboardService : ClipboardService {

    override suspend fun readText(): ClipboardReadResult = try {
        val text = UIPasteboard.generalPasteboard.string
        when {
            text == null -> ClipboardReadResult.Failed
            text.isEmpty() -> ClipboardReadResult.Empty
            else -> ClipboardReadResult.Success(text)
        }
    } catch (_: Throwable) {
        // 粘贴权限被拒时系统可能抛异常而不是返回 null；失败可重试，不允许崩溃
        ClipboardReadResult.Failed
    }

    override suspend fun writeText(text: String): ClipboardWriteResult = try {
        UIPasteboard.generalPasteboard.string = text
        ClipboardWriteResult.Success
    } catch (_: Throwable) {
        ClipboardWriteResult.Failed
    }

    // iOS v1.0.0 不提供自动发送与后台剪贴板能力
    override fun startWatching(onText: (String) -> Unit) = Unit

    override fun stopWatching() = Unit
}
