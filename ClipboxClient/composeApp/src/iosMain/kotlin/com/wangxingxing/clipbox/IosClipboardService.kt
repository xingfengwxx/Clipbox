/**
 * author : 王星星
 * date : 2026-11-17 13:48
 * email : 1099420259@qq.com
 * description : iOS 剪贴板服务；写入只由用户显式复制触发，读取只由用户显式粘贴触发，不做轮询与后台探测。
 *               注意：iOS 16+ 首次读取会弹系统粘贴权限提示，用户拒绝时 UIPasteboard 返回 null，
 *                     无法与「设备剪贴板确实为空」区分，因此 null 一律按可重试失败处理。
 */
package com.wangxingxing.clipbox

import platform.UIKit.UIPasteboard

sealed interface ClipboardReadResult {

    data class Success(val text: String) : ClipboardReadResult

    /** 系统粘贴权限被拒绝或读取异常；调用方提示用户重试或长按输入框手动粘贴。 */
    data object Failed : ClipboardReadResult
}

internal class IosClipboardService {

    fun writeText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    suspend fun readText(): ClipboardReadResult = try {
        val text = UIPasteboard.generalPasteboard.string
        if (text == null) ClipboardReadResult.Failed else ClipboardReadResult.Success(text)
    } catch (t: Throwable) {
        // 粘贴权限被拒时系统可能抛异常而不是返回 null；失败可重试，不允许崩溃
        ClipboardReadResult.Failed
    }
}
