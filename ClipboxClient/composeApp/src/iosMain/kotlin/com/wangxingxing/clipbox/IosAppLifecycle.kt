/**
 * author : 王星星
 * date : 2026-11-17 14:02
 * email : 1099420259@qq.com
 * description : iOS 前后台生命周期桥：供 SwiftUI 壳在 scenePhase 变化时驱动同步运行时。
 *               注意：前台恢复只由同一 WS 状态机先 REST 补拉再连接，重复 active 回调不会建立多条连接；
 *                     后台只断开 WS 与停止重连，不注册后台任务、不读取剪贴板、不触发推送。
 */
package com.wangxingxing.clipbox

import com.wangxingxing.clipbox.di.iosAppGraph

object IosAppLifecycle {

    /** scenePhase == .active：唤醒补拉与 WS；首次启动由运行时装配自行建立连接，此处幂等。 */
    fun onForeground() {
        iosAppGraph().syncCoordinator.onForeground()
    }

    /** scenePhase == .background：立即断开 WS 并停止重连；.inactive 不调用，避免切换控制中心时断连。 */
    fun onBackground() {
        iosAppGraph().syncCoordinator.onBackground()
    }
}
