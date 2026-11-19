/**
 * author : 王星星
 * date : 2026-11-19 11:20
 * email : 1099420259@qq.com
 * description : 平台安装能力接口；由 Android / Desktop 实现，iOS 不提供实现，更新弹窗按只读展示。
 *               注意：实现不得静默安装；下载与校验失败必须自行清理残留文件，且不得把 URL、路径或说明写入日志
 */
package com.wangxingxing.clipbox.update

interface UpdateInstaller {

    /** 当前设备对应的平台条目 id，用于从更新描述中挑选安装包 */
    val platformId: UpdatePlatformId

    /**
     * 下载安装包并落盘；阻塞到下载结束，进度通过 [onProgress] 上报已下载与总字节。
     * 返回 true 表示文件可进入校验；失败返回 false，并自行清理半成品。
     */
    suspend fun download(
        platform: UpdatePlatform,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Boolean

    /** 按更新描述中的 SHA-256 校验；不匹配时删除文件并返回 false */
    suspend fun verify(expectedSha256: String): Boolean

    /** 拉起系统安装器或打开安装包；只在用户明确点击安装后调用 */
    suspend fun install(): UpdateInstallResult

    /** 取消进行中的下载并清理半成品；应用退出时调用 */
    suspend fun cancel()

    /** 清理已下载的安装包 */
    suspend fun cleanup()
}

sealed interface UpdateInstallResult {
    data object Started : UpdateInstallResult

    /** 未知来源安装未授权：保持 Ready，由用户授权后再次点击安装 */
    data object PermissionRequired : UpdateInstallResult

    data object Unavailable : UpdateInstallResult

    data object Failed : UpdateInstallResult
}
