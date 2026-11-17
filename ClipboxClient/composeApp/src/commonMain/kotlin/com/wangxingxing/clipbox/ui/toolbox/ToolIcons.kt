/**
 * author : 王星星
 * date : 2026-11-17 16:20
 * email : 1099420259@qq.com
 * description : 工具卡的 16dp 线稿图标，路径取自设计稿 S15 内联 SVG；自建 ImageVector，颜色由 Icon tint 决定
 */
package com.wangxingxing.clipbox.ui.toolbox

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object ToolIcons {

    val Json: ImageVector = strokeIcon("Json") {
        strokePath {
            moveTo(6.4f, 2.4f)
            curveToRelative(-1.5f, 0f, -2f, 0.8f, -2f, 2f)
            verticalLineToRelative(1.4f)
            curveToRelative(0f, 0.9f, -0.5f, 1.6f, -1.4f, 1.6f)
            curveToRelative(0.9f, 0f, 1.4f, 0.7f, 1.4f, 1.6f)
            verticalLineToRelative(1.4f)
            curveToRelative(0f, 1.2f, 0.5f, 2f, 2f, 2f)
        }
        strokePath {
            moveTo(9.6f, 2.4f)
            curveToRelative(1.5f, 0f, 2f, 0.8f, 2f, 2f)
            verticalLineToRelative(1.4f)
            curveToRelative(0f, 0.9f, 0.5f, 1.6f, 1.4f, 1.6f)
            curveToRelative(-0.9f, 0f, -1.4f, 0.7f, -1.4f, 1.6f)
            verticalLineToRelative(1.4f)
            curveToRelative(0f, 1.2f, -0.5f, 2f, -2f, 2f)
        }
    }

    val Base64: ImageVector = strokeIcon("Base64") {
        strokePath {
            moveTo(5.8f, 4.2f)
            lineTo(2.4f, 8f)
            lineTo(5.8f, 11.8f)
        }
        strokePath {
            moveTo(10.2f, 4.2f)
            lineTo(13.6f, 8f)
            lineTo(10.2f, 11.8f)
        }
    }

    val Url: ImageVector = strokeIcon("Url") {
        strokePath {
            moveTo(6.6f, 9.4f)
            lineTo(9.4f, 6.6f)
        }
        strokePath {
            moveTo(7.4f, 5.4f)
            lineTo(8.4f, 4.4f)
            arcToRelative(2.6f, 2.6f, 0f, false, true, 3.7f, 3.7f)
            lineTo(11.1f, 9.1f)
        }
        strokePath {
            moveTo(8.6f, 10.6f)
            lineTo(7.6f, 11.6f)
            arcToRelative(2.6f, 2.6f, 0f, false, true, -3.7f, -3.7f)
            lineTo(4.9f, 6.9f)
        }
    }

    val Time: ImageVector = strokeIcon("Time") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.2f,
            pathBuilder = {
                // 圆形在 PathBuilder 里用两段半圆闭合，避免额外依赖
                moveTo(2.4f, 8f)
                arcToRelative(5.6f, 5.6f, 0f, false, true, 11.2f, 0f)
                arcToRelative(5.6f, 5.6f, 0f, false, true, -11.2f, 0f)
                close()
            },
        )
        strokePath {
            moveTo(8f, 4.8f)
            verticalLineTo(8f)
            lineTo(10.2f, 9.4f)
        }
    }

    val Regex: ImageVector = strokeIcon("Regex") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.2f,
            strokeLineCap = StrokeCap.Round,
            pathBuilder = {
                moveTo(2.6f, 7f)
                arcToRelative(4.4f, 4.4f, 0f, false, true, 8.8f, 0f)
                arcToRelative(4.4f, 4.4f, 0f, false, true, -8.8f, 0f)
                close()
            },
        )
        strokePath {
            moveTo(10.3f, 10.3f)
            lineTo(14f, 14f)
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
