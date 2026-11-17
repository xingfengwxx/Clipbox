/**
 * author : 王星星
 * date : 2026-11-17 15:05
 * email : 1099420259@qq.com
 * description : 剪贴板平台抽象；移动端 startWatching/stopWatching 保持空实现，Desktop 才允许轮询。读取结果区分空内容与权限失败，避免把系统拒绝误判成剪贴板为空
 */
package com.wangxingxing.clipbox.platform

sealed interface ClipboardReadResult {
    data class Success(val text: String) : ClipboardReadResult
    data object Empty : ClipboardReadResult

    /** 系统权限被拒绝或平台读取异常；调用方提示用户重试或长按输入框手动粘贴 */
    data object Failed : ClipboardReadResult
}

sealed interface ClipboardWriteResult {
    data object Success : ClipboardWriteResult
    data object Failed : ClipboardWriteResult
}

interface ClipboardService {
    suspend fun readText(): ClipboardReadResult
    suspend fun writeText(text: String): ClipboardWriteResult

    /** Desktop 自动发送开启时才调用；移动端实现必须为空操作 */
    fun startWatching(onText: (String) -> Unit)
    fun stopWatching()
}
