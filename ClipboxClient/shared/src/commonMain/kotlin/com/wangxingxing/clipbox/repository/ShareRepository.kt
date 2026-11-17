/**
 * author : 王星星
 * date : 2026-11-17 13:31
 * email : 1099420259@qq.com
 * description : 分享创建入口；仅允许本地已同步记录发起服务端分享，未同步/已删除记录在本地直接拒绝且不产生网络请求。
 *               注意：分享 URL 必须原样透传服务端返回值，客户端不拼接；不缓存分享结果、不写本地分享历史。
 */
package com.wangxingxing.clipbox.repository

import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.Clip
import com.wangxingxing.clipbox.model.ShareInfo
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.network.ApiResult
import com.wangxingxing.clipbox.network.ApiService

/** 本地记录读取抽象；生产实现委托 [ClipRepository.findClip]，测试注入假实现避免引入 Room。 */
fun interface ClipLookup {
    suspend fun find(id: String): Clip?
}

sealed interface ShareCreateResult {
    data class Success(val share: ShareInfo) : ShareCreateResult

    /** 本地记录缺失、已删除或尚未同步；界面据此提示「同步后可分享」。 */
    data object NotSynced : ShareCreateResult

    data class Failed(val failure: ApiFailure) : ShareCreateResult
}

class ShareRepository(
    private val apiService: ApiService,
    private val clips: ClipLookup,
) {

    suspend fun createShare(clipId: String, ttlSeconds: Long, oneTime: Boolean): ShareCreateResult {
        val clip = clips.find(clipId)
        // 服务端按已同步记录生成快照；本地未同步或已删除时必须先拒绝，否则会得到 404 或分享出错内容。
        if (clip == null || clip.deleted || clip.syncState != SyncState.SYNCED) {
            return ShareCreateResult.NotSynced
        }

        return when (val result = apiService.createShare(clipId, ttlSeconds, oneTime)) {
            is ApiResult.Success -> ShareCreateResult.Success(
                ShareInfo(
                    url = result.value.url,
                    expiresAt = result.value.expiresAt,
                    oneTime = oneTime,
                )
            )

            is ApiResult.Failure -> ShareCreateResult.Failed(result.failure)
        }
    }
}
