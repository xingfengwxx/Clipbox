/**
 * author : 王星星
 * date : 2026-11-16 18:05
 * email : 1099420259@qq.com
 * description : 历史记录行：最多 3 行正文预览、来源设备与时间、字节数、待同步 / 失败角标和置顶 / 更多入口；整行点击复制，行内按钮不触发行级点击
 */
package com.wangxingxing.clipbox.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.model.Clip
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.history_act_more
import com.wangxingxing.clipbox.resources.history_act_pin
import com.wangxingxing.clipbox.resources.history_act_unpin
import com.wangxingxing.clipbox.resources.history_bytes
import com.wangxingxing.clipbox.resources.history_device_local
import com.wangxingxing.clipbox.resources.history_device_unknown
import com.wangxingxing.clipbox.resources.history_state_failed
import com.wangxingxing.clipbox.resources.history_state_pending
import com.wangxingxing.clipbox.resources.history_time_hours
import com.wangxingxing.clipbox.resources.history_time_just_now
import com.wangxingxing.clipbox.resources.history_time_minutes
import com.wangxingxing.clipbox.ui.i18n.LocalAppLanguage
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import com.wangxingxing.clipbox.util.ClipTimeLabel
import com.wangxingxing.clipbox.util.TextSize
import com.wangxingxing.clipbox.util.clipTimeLabel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ClipRow(
    clip: Clip,
    isLocalDevice: Boolean,
    nowMillis: Long,
    onCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val typography = ClipboxTokens.typography
    val device = when {
        isLocalDevice -> stringResource(Res.string.history_device_local)
        clip.sourceDeviceName.isBlank() -> stringResource(Res.string.history_device_unknown)
        else -> clip.sourceDeviceName
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(horizontal = spacing.l, vertical = spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = device, style = typography.mono, color = colors.ink2)
            Spacer(Modifier.width(spacing.s))
            Text(
                text = timeText(clip.createdAt, nowMillis),
                style = typography.mono,
                color = colors.ink3,
            )
            SyncBadge(clip.syncState)
        }
        Text(
            text = clip.content,
            modifier = Modifier.padding(vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    Res.string.history_bytes,
                    TextSize.formatByteCount(TextSize.utf8ByteCount(clip.content)),
                ),
                style = typography.mono,
                color = colors.ink3,
            )
            Spacer(Modifier.weight(1f))
            RowAction(
                icon = RowIcons.Pin,
                contentDescription = stringResource(
                    if (clip.pinned) Res.string.history_act_unpin else Res.string.history_act_pin,
                ),
                tint = if (clip.pinned) colors.accent else colors.ink2,
                onClick = onTogglePin,
            )
            RowAction(
                icon = RowIcons.More,
                contentDescription = stringResource(Res.string.history_act_more),
                tint = colors.ink2,
                onClick = onMore,
            )
        }
    }
}

@Composable
private fun RowAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SyncBadge(state: SyncState) {
    if (state == SyncState.SYNCED) return
    val colors = ClipboxTokens.colors
    val failed = state == SyncState.FAILED_CREATE ||
        state == SyncState.FAILED_UPDATE ||
        state == SyncState.FAILED_DELETE
    val color = if (failed) colors.danger else colors.warn

    Text(
        text = stringResource(
            if (failed) Res.string.history_state_failed else Res.string.history_state_pending,
        ),
        modifier = Modifier
            .padding(start = ClipboxTokens.spacing.s)
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = ClipboxTokens.typography.mono,
        color = color,
    )
}

@Composable
private fun timeText(createdAtMillis: Long, nowMillis: Long): String =
    when (val label = clipTimeLabel(createdAtMillis, nowMillis, LocalAppLanguage.current)) {
        ClipTimeLabel.JustNow -> stringResource(Res.string.history_time_just_now)
        is ClipTimeLabel.MinutesAgo -> pluralStringResource(
            Res.plurals.history_time_minutes,
            label.minutes,
            label.minutes,
        )

        is ClipTimeLabel.HoursAgo -> pluralStringResource(
            Res.plurals.history_time_hours,
            label.hours,
            label.hours,
        )

        is ClipTimeLabel.Absolute -> label.text
    }

private object RowIcons {

    // 图钉：圆头加针脚，置顶时由 tint 变为 accent，无需第二套图标
    val Pin: ImageVector = strokeIcon("Pin") {
        moveTo(8f, 2.4f)
        curveTo(9.4f, 2.4f, 10.5f, 3.5f, 10.5f, 4.9f)
        curveTo(10.5f, 6.3f, 9.4f, 7.4f, 8f, 7.4f)
        curveTo(6.6f, 7.4f, 5.5f, 6.3f, 5.5f, 4.9f)
        curveTo(5.5f, 3.5f, 6.6f, 2.4f, 8f, 2.4f)
        close()
        moveTo(8f, 7.4f)
        lineTo(8f, 12.8f)
        moveTo(5.8f, 12.8f)
        lineTo(10.2f, 12.8f)
    }

    val More: ImageVector = ImageVector.Builder(
        name = "More",
        defaultWidth = 16.dp,
        defaultHeight = 16.dp,
        viewportWidth = 16f,
        viewportHeight = 16f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            dot(3.2f, 8f)
            dot(8f, 8f)
            dot(12.8f, 8f)
        }
    }.build()

    private fun strokeIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply {
            // 占位黑色，实际显示色由 Icon tint 覆盖
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.dot(centerX: Float, centerY: Float) {
        val radius = 1.1f
        moveTo(centerX - radius, centerY)
        arcTo(
            horizontalEllipseRadius = radius,
            verticalEllipseRadius = radius,
            theta = 0f,
            isMoreThanHalf = true,
            isPositiveArc = true,
            x1 = centerX + radius,
            y1 = centerY,
        )
        arcTo(
            horizontalEllipseRadius = radius,
            verticalEllipseRadius = radius,
            theta = 0f,
            isMoreThanHalf = true,
            isPositiveArc = true,
            x1 = centerX - radius,
            y1 = centerY,
        )
        close()
    }
}
