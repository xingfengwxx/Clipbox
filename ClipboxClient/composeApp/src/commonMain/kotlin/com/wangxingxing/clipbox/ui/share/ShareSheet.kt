/**
 * author : 王星星
 * date : 2026-11-17 14:05
 * email : 1099420259@qq.com
 * description : 分享流程 S07 / S08：先选有效期再生成，成功后展示服务端返回的完整 URL、到期时间与一次性标识。
 *               注意：URL 原样展示与复制，客户端不拼接；结果只保存在历史页作用域的内存状态里，不写本地分享记录；离线/未授权时禁止生成
 */
package com.wangxingxing.clipbox.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.ShareInfo
import com.wangxingxing.clipbox.model.SyncStatus
import com.wangxingxing.clipbox.repository.ShareCreateResult
import com.wangxingxing.clipbox.repository.ShareRepository
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.share_copy_link
import com.wangxingxing.clipbox.resources.share_error_failed
import com.wangxingxing.clipbox.resources.share_error_not_synced
import com.wangxingxing.clipbox.resources.share_error_offline
import com.wangxingxing.clipbox.resources.share_error_rate_limited
import com.wangxingxing.clipbox.resources.share_error_retry
import com.wangxingxing.clipbox.resources.share_error_unauthorized
import com.wangxingxing.clipbox.resources.share_generate
import com.wangxingxing.clipbox.resources.share_generating
import com.wangxingxing.clipbox.resources.share_result_back
import com.wangxingxing.clipbox.resources.share_result_expires
import com.wangxingxing.clipbox.resources.share_result_one_time
import com.wangxingxing.clipbox.resources.share_result_reusable
import com.wangxingxing.clipbox.resources.share_result_title
import com.wangxingxing.clipbox.resources.share_sheet_title
import com.wangxingxing.clipbox.resources.share_tier_1h
import com.wangxingxing.clipbox.resources.share_tier_24h
import com.wangxingxing.clipbox.resources.share_tier_7d
import com.wangxingxing.clipbox.resources.share_tier_one_time
import com.wangxingxing.clipbox.resources.share_tier_one_time_hint
import com.wangxingxing.clipbox.ui.i18n.LocalAppLanguage
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import com.wangxingxing.clipbox.util.formatAbsoluteTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** 四档固定有效期，取值与 REQ-SHARE-002 一致；阅后即焚复用 24 小时 TTL 并打开 oneTime。 */
enum class ShareTier(val ttlSeconds: Long, val oneTime: Boolean) {
    OneHour(3_600, false),
    TwentyFourHours(86_400, false),
    SevenDays(604_800, false),
    BurnAfterReading(86_400, true),
}

sealed interface ShareSheetError {
    /** 本地记录尚未同步或已删除；界面提示同步后再分享，不提供重试。 */
    data object NotSynced : ShareSheetError

    data class Failed(val failure: ApiFailure) : ShareSheetError
}

data class ShareState(
    val visible: Boolean = false,
    val clipId: String = "",
    val tier: ShareTier = ShareTier.TwentyFourHours,
    val submitting: Boolean = false,
    val error: ShareSheetError? = null,
    val result: ShareInfo? = null,
)

/**
 * 历史页作用域的分享状态：同一记录关闭弹层后保留已生成结果，切换到其他记录时整体重置。
 * 网络请求由 repository 负责；这里只做状态编排，失败不落任何本地记录。
 */
class ShareStateHolder(
    private val shareRepository: ShareRepository,
    private val scope: CoroutineScope,
) {

    var state by mutableStateOf(ShareState())
        private set

    fun open(clipId: String) {
        state = if (state.clipId == clipId) {
            state.copy(visible = true, error = null)
        } else {
            ShareState(visible = true, clipId = clipId)
        }
    }

    fun dismiss() {
        state = state.copy(visible = false)
    }

    fun selectTier(tier: ShareTier) {
        if (state.submitting) return
        state = state.copy(tier = tier, error = null)
    }

    fun backToOptions() {
        if (state.submitting) return
        state = state.copy(result = null, error = null)
    }

    fun generate() {
        val current = state
        if (current.submitting || current.clipId.isEmpty() || current.result != null) return
        val clipId = current.clipId
        state = current.copy(submitting = true, error = null)
        scope.launch {
            val outcome = shareRepository.createShare(
                clipId = clipId,
                ttlSeconds = current.tier.ttlSeconds,
                oneTime = current.tier.oneTime,
            )
            // 弹层已切到其他记录时，旧请求的结果必须丢弃，避免把 A 的链接展示成 B 的
            if (state.clipId != clipId) return@launch
            state = when (outcome) {
                is ShareCreateResult.Success -> state.copy(submitting = false, result = outcome.share)
                is ShareCreateResult.Failed -> state.copy(
                    submitting = false,
                    error = ShareSheetError.Failed(outcome.failure),
                )

                ShareCreateResult.NotSynced -> state.copy(
                    submitting = false,
                    error = ShareSheetError.NotSynced,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    state: ShareState,
    syncStatus: SyncStatus,
    onSelectTier: (ShareTier) -> Unit,
    onGenerate: () -> Unit,
    onBack: () -> Unit,
    onCopyLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.visible) return
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val shape = RoundedCornerShape(ClipboxTokens.radii.button)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.l)
                .padding(bottom = spacing.xl),
        ) {
            val result = state.result
            if (result == null) {
                Text(
                    text = stringResource(Res.string.share_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                )
                Spacer(Modifier.height(spacing.m))
                ShareTier.entries.forEach { tier ->
                    TierRow(
                        tier = tier,
                        selected = state.tier == tier,
                        enabled = !state.submitting,
                        onSelect = { onSelectTier(tier) },
                    )
                }
                state.error?.let { error ->
                    Spacer(Modifier.height(spacing.s))
                    Text(
                        text = shareErrorMessage(error),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.danger,
                    )
                }
                // 离线/未授权时入口本就置灰，这里覆盖打开弹层后状态变化的情况
                val blockedReason = when (syncStatus) {
                    is SyncStatus.Offline -> stringResource(Res.string.share_error_offline)
                    is SyncStatus.Unauthorized -> stringResource(Res.string.share_error_unauthorized)
                    else -> null
                }
                if (blockedReason != null) {
                    Spacer(Modifier.height(spacing.s))
                    Text(
                        text = blockedReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink3,
                    )
                }
                Spacer(Modifier.height(spacing.l))
                Button(
                    onClick = onGenerate,
                    enabled = blockedReason == null && !state.submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = shape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                        disabledContainerColor = colors.panel2,
                        disabledContentColor = colors.ink2,
                    ),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.ink2,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(spacing.s))
                        Text(
                            text = stringResource(Res.string.share_generating),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.share_generate),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(Res.string.share_result_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                )
                Spacer(Modifier.height(spacing.m))
                SelectionContainer {
                    Text(
                        text = result.url,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.panel)
                            .border(1.dp, colors.line, shape)
                            .padding(spacing.m),
                        style = ClipboxTokens.typography.mono,
                        color = colors.ink,
                    )
                }
                Spacer(Modifier.height(spacing.s))
                Text(
                    text = stringResource(
                        Res.string.share_result_expires,
                        formatAbsoluteTime(result.expiresAt, LocalAppLanguage.current),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                )
                Text(
                    text = stringResource(
                        if (result.oneTime) {
                            Res.string.share_result_one_time
                        } else {
                            Res.string.share_result_reusable
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.oneTime) colors.warn else colors.ink3,
                )
                Spacer(Modifier.height(spacing.l))
                Button(
                    onClick = { onCopyLink(result.url) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = shape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                    ),
                    contentPadding = PaddingValues(horizontal = spacing.l),
                ) {
                    Text(
                        text = stringResource(Res.string.share_copy_link),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
                TextButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.share_result_back),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.ink2,
                    )
                }
            }
        }
    }
}

@Composable
private fun TierRow(
    tier: ShareTier,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = enabled,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.accent,
                unselectedColor = colors.ink3,
            ),
        )
        Column {
            Text(
                text = stringResource(tier.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) colors.ink else colors.ink3,
            )
            if (tier.oneTime) {
                Text(
                    text = stringResource(Res.string.share_tier_one_time_hint),
                    style = ClipboxTokens.typography.mono,
                    color = colors.ink3,
                )
            }
        }
    }
}

private fun ShareTier.labelRes() = when (this) {
    ShareTier.OneHour -> Res.string.share_tier_1h
    ShareTier.TwentyFourHours -> Res.string.share_tier_24h
    ShareTier.SevenDays -> Res.string.share_tier_7d
    ShareTier.BurnAfterReading -> Res.string.share_tier_one_time
}

@Composable
private fun shareErrorMessage(error: ShareSheetError): String = when (error) {
    ShareSheetError.NotSynced -> stringResource(Res.string.share_error_not_synced)
    is ShareSheetError.Failed -> when (val failure = error.failure) {
        ApiFailure.Offline -> stringResource(Res.string.share_error_offline)
        is ApiFailure.Unauthorized -> stringResource(Res.string.share_error_unauthorized)
        is ApiFailure.RateLimited -> stringResource(Res.string.share_error_rate_limited)
        is ApiFailure.Retryable -> stringResource(Res.string.share_error_retry)
        is ApiFailure.Permanent -> stringResource(Res.string.share_error_failed)
    }
}
