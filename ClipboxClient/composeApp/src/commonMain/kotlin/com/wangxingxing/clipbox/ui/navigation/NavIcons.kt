/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 侧栏用的 16dp 线稿图标，路径取自设计稿内联 SVG；自建 ImageVector 以避免引入图标依赖，颜色由 Icon tint 决定
 */
package com.wangxingxing.clipbox.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object NavIcons {

    val History: ImageVector = strokeIcon("History") {
        strokePath {
            moveTo(4f, 2.4f)
            horizontalLineTo(9f)
            lineTo(12f, 5.4f)
            verticalLineTo(13.6f)
            horizontalLineTo(4f)
            close()
            moveTo(9f, 2.4f)
            verticalLineTo(5.4f)
            horizontalLineTo(12f)
        }
    }

    val Toolbox: ImageVector = strokeIcon("Toolbox") {
        strokePath {
            moveTo(6.4f, 2.4f)
            curveToRelative(-1.5f, 0f, -2f, 0.8f, -2f, 2f)
            verticalLineToRelative(1.4f)
            curveToRelative(0f, 0.9f, -0.5f, 1.6f, -1.4f, 1.6f)
            curveToRelative(0.9f, 0f, 1.4f, 0.7f, 1.4f, 1.6f)
            verticalLineToRelative(1.4f)
            curveToRelative(0f, 1.2f, 0.5f, 2f, 2f, 2f)
            moveTo(9.6f, 2.4f)
            curveToRelative(1.5f, 0f, 2f, 0.8f, 2f, 2f)
            verticalLineToRelative(1.4f)
            curveToRelative(0f, 0.9f, 0.5f, 1.6f, 1.4f, 1.6f)
            curveToRelative(-0.9f, 0f, -1.4f, 0.7f, -1.4f, 1.6f)
            verticalLineToRelative(1.4f)
            curveToRelative(0f, 1.2f, -0.5f, 2f, -2f, 2f)
        }
    }

    val Settings: ImageVector = strokeIcon("Settings") {
        strokePath {
            moveTo(2.8f, 3f)
            horizontalLineTo(13.2f)
            verticalLineTo(11f)
            horizontalLineTo(2.8f)
            close()
            moveTo(5.6f, 13.6f)
            horizontalLineTo(10.4f)
            moveTo(8f, 11f)
            verticalLineTo(13.6f)
        }
    }

    val Back: ImageVector = strokeIcon("Back") {
        strokePath {
            moveTo(7f, 3f)
            lineTo(2.5f, 8f)
            lineTo(7f, 13f)
            moveTo(2.5f, 8f)
            horizontalLineTo(13.5f)
        }
    }

    private fun strokeIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply(block).build()

    // 线条统一使用占位黑色，实际显示色由 Icon 的 tint 覆盖
    private fun ImageVector.Builder.strokePath(block: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }
}
