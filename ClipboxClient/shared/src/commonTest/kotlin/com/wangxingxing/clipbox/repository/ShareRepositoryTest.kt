/**
 * author : 王星星
 * date : 2026-11-17 13:32
 * email : 1099420259@qq.com
 * description : 分享仓库约束测试：仅 SYNCED 记录可创建分享、未同步与已删除记录零网络请求、离线与错误映射、四档参数原样转发。
 *               注意：测试通过假 ClipLookup 注入本地记录，不引入 Room 依赖；MockEngine 断言请求体与请求数。
 */
package com.wangxingxing.clipbox.repository

import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.Clip
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.network.ApiService
import com.wangxingxing.clipbox.network.bodyText
import com.wangxingxing.clipbox.network.respondJson
import com.wangxingxing.clipbox.network.stubService
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShareRepositoryTest {

    private fun clip(syncState: SyncState, deleted: Boolean = false) = Clip(
        id = CLIP_ID,
        content = "hello share",
        sourceDeviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2",
        sourceDeviceName = "Pixel 8",
        pinned = false,
        createdAt = 1_730_000_000_000L,
        syncState = syncState,
        serverSeq = if (syncState == SyncState.SYNCED) 41 else 0,
        deleted = deleted,
    )

    private fun repository(service: ApiService, local: Clip?) =
        ShareRepository(service, ClipLookup { id -> local?.takeIf { it.id == id } })

    private fun stubCreated(requests: MutableList<HttpRequestData>) = stubService(requests = requests) {
        respondJson(
            """
                {
                  "token": "share-token",
                  "url": "http://203.0.113.10:8080/s/share-token",
                  "expiresAt": 1730086400000
                }
            """.trimIndent(),
            status = HttpStatusCode.Created,
        )
    }

    @Test
    fun `synced clip creates share and passes server url through verbatim`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = stubCreated(requests)

        val result = repository(service, clip(SyncState.SYNCED)).createShare(CLIP_ID, 3_600, oneTime = true)

        val success = assertIs<ShareCreateResult.Success>(result)
        assertEquals("http://203.0.113.10:8080/s/share-token", success.share.url)
        assertEquals(1_730_086_400_000L, success.share.expiresAt)
        assertTrue(success.share.oneTime)
        assertEquals(1, requests.size)
        assertTrue(requests.single().bodyText().contains("\"clipId\":\"$CLIP_ID\""))
    }

    @Test
    fun `unsynced missing or deleted clips are rejected without network`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = stubCreated(requests)

        val blockedStates = listOf(
            SyncState.PENDING_CREATE,
            SyncState.PENDING_UPDATE,
            SyncState.PENDING_DELETE,
            SyncState.FAILED_CREATE,
            SyncState.FAILED_UPDATE,
            SyncState.FAILED_DELETE,
        )
        blockedStates.forEach { state ->
            assertEquals(
                ShareCreateResult.NotSynced,
                repository(service, clip(state)).createShare(CLIP_ID, 86_400, oneTime = false),
                state.name,
            )
        }
        assertEquals(
            ShareCreateResult.NotSynced,
            repository(service, clip(SyncState.SYNCED, deleted = true)).createShare(CLIP_ID, 86_400, false),
            "已删除记录不得分享",
        )
        assertEquals(
            ShareCreateResult.NotSynced,
            repository(service, null).createShare(CLIP_ID, 86_400, false),
            "本地不存在的记录不得分享",
        )
        assertTrue(requests.isEmpty(), "未同步/已删除/缺失记录必须零网络请求")
    }

    @Test
    fun `offline failure is surfaced as offline`() = runTest {
        val service = stubService {
            throw IllegalStateException("connection refused")
        }

        val result = repository(service, clip(SyncState.SYNCED)).createShare(CLIP_ID, 86_400, oneTime = false)

        assertEquals(ShareCreateResult.Failed(ApiFailure.Offline), result)
    }

    @Test
    fun `server errors keep their failure type`() = runTest {
        val service = stubService {
            respondJson(
                """{"error":{"code":"NOT_FOUND","message":"clip missing"}}""",
                status = HttpStatusCode.NotFound,
            )
        }

        val result = repository(service, clip(SyncState.SYNCED)).createShare(CLIP_ID, 86_400, oneTime = false)

        val failed = assertIs<ShareCreateResult.Failed>(result)
        val permanent = assertIs<ApiFailure.Permanent>(failed.failure)
        assertEquals(ApiErrorCode.NOT_FOUND, permanent.code)
    }

    @Test
    fun `four share tiers are forwarded verbatim`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = stubCreated(requests)
        val repo = repository(service, clip(SyncState.SYNCED))

        val tiers = listOf(
            Triple(3_600L, false, "\"ttlSeconds\":3600"),
            Triple(86_400L, false, "\"ttlSeconds\":86400"),
            Triple(604_800L, false, "\"ttlSeconds\":604800"),
            Triple(86_400L, true, "\"oneTime\":true"),
        )
        tiers.forEach { (ttl, oneTime, expectedBody) ->
            assertIs<ShareCreateResult.Success>(repo.createShare(CLIP_ID, ttl, oneTime))
            assertTrue(requests.last().bodyText().contains(expectedBody), expectedBody)
        }
        assertEquals(tiers.size, requests.size)
    }

    companion object {
        private const val CLIP_ID = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
    }
}
