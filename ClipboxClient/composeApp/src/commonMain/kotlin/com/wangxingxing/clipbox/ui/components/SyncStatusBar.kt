/**
 * author : 王星星
 * date : 2026-11-17 14:40
 * email : 1099420259@qq.com
 * description : 历史页顶部同步状态条 S04：展示同步中 / 已同步 / 离线 / 待同步 N 条 / 失败 N 条 / 未配对；离线与失败可点击重试。
 *               注意：状态只来自 SyncStatusHolder 的聚合结果，界面不自行拼接；未配对时无论内部状态如何都按「未配对」展示
 */
package com.wangxingxing.clipbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.model.SyncStatus
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.history_act_retry
import com.wangxingxing.clipbox.resources.history_sync_failed_count
import com.wangxingxing.clipbox.resources.history_sync_offline
import com.wangxingxing.clipbox.resources.history_sync_pending_count
import com.wangxingxing.clipbox.resources.history_sync_synced
import com.wangxingxing.clipbox.resources.history_sync_syncing
import com.wangxingxing.clipbox.resources.history_sync_unpaired
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SyncStatusBar(
    status: SyncStatus,
    paired: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val effective = if (paired) status else SyncStatus.Unauthorized
    val label = when (effective) {
        SyncStatus.Idle -> stringResource(Res.string.history_sync_synced)
        SyncStatus.Syncing, SyncStatus.Resyncing -> stringResource(Res.string.history_sync_syncing)
        SyncStatus.Offline -> stringResource(Res.string.history_sync_offline)
        SyncStatus.Unauthorized -> stringResource(Res.string.history_sync_unpaired)
        is SyncStatus.Pending -> pluralStringResource(
            Res.plurals.history_sync_pending_count,
            effective.count,
            effective.count,
        )

        is SyncStatus.Failed -> pluralStringResource(
            Res.plurals.history_sync_failed_count,
            effective.count,
            effective.count,
        )
    }
    val color = when (effective) {
        SyncStatus.Idle -> colors.ok
        SyncStatus.Syncing, SyncStatus.Resyncing -> colors.ink3
        SyncStatus.Offline -> colors.warn
        SyncStatus.Unauthorized -> colors.ink3
        is SyncStatus.Pending -> colors.warn
        is SyncStatus.Failed -> colors.danger
    }
    val retryable = effective is SyncStatus.Offline || effective is SyncStatus.Failed

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = retryable, onClick = onRetry)
            .padding(horizontal = spacing.l, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(spacing.s))
        Text(
            text = label,
            style = ClipboxTokens.typography.mono,
            color = color,
        )
        if (retryable) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.history_act_retry),
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
    }
}
