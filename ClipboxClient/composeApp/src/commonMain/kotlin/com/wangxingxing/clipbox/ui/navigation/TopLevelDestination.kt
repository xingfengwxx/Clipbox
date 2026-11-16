/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 一级导航目的地；route 与架构 §7.1 的路由表一致，新增路由前需先确认文档
 */
package com.wangxingxing.clipbox.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.nav_history
import com.wangxingxing.clipbox.resources.nav_settings
import com.wangxingxing.clipbox.resources.nav_toolbox
import org.jetbrains.compose.resources.StringResource

enum class TopLevelDestination(
    val route: String,
    val label: StringResource,
    val icon: ImageVector,
) {
    History("history", Res.string.nav_history, NavIcons.History),
    Toolbox("toolbox", Res.string.nav_toolbox, NavIcons.Toolbox),
    Settings("settings", Res.string.nav_settings, NavIcons.Settings),
    ;

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}
