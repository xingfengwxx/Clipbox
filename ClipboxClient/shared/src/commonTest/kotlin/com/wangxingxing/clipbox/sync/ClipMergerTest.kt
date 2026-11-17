/**
 * author : 王星星
 * date : 2026-11-17 13:34
 * email : 1099420259@qq.com
 * description : ClipMerger 状态矩阵测试；覆盖全部本地同步状态 × 活动记录 / 墓碑 × 待发 / 失败操作，锁定"本地意图不被远端回退、墓碑优先于失败记录"的边界
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.db.ClipEntity
import com.wangxingxing.clipbox.db.OutboxEntity
import com.wangxingxing.clipbox.db.OutboxOpType
import com.wangxingxing.clipbox.model.ClipDto
import com.wangxingxing.clipbox.model.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipMergerTest {

    @Test
    fun activeRemoteMergesEveryLocalState() {
        val cases = listOf(
            MergeCase(name = "无本地记录", local = null, expected = Kind.UPSERT),
            MergeCase(name = "已同步", local = local(SyncState.SYNCED), expected = Kind.UPSERT),
            MergeCase(name = "待创建", local = local(SyncState.PENDING_CREATE), expected = Kind.UPSERT),
            MergeCase(name = "待更新", local = local(SyncState.PENDING_UPDATE), expected = Kind.UPSERT),
            MergeCase(name = "待删除", local = local(SyncState.PENDING_DELETE), expected = Kind.IGNORE),
            MergeCase(name = "创建失败", local = local(SyncState.FAILED_CREATE), expected = Kind.IGNORE),
            MergeCase(name = "更新失败", local = local(SyncState.FAILED_UPDATE), expected = Kind.IGNORE),
            MergeCase(name = "删除失败", local = local(SyncState.FAILED_DELETE), expected = Kind.IGNORE),
        )

        cases.forEach { case ->
            val result = ClipMerger.merge(case.local, remote(), outboxOps = emptyList())
            assertEquals(case.expected, result.kind(), "活动记录 + ${case.name}")
        }
    }

    @Test
    fun tombstoneMergesEveryLocalStateAndOutboxCondition() {
        val cases = listOf(
            MergeCase(name = "无本地记录", local = null, ops = emptyList(), expected = Kind.IGNORE),
            MergeCase(name = "已同步无操作", local = local(SyncState.SYNCED), expected = Kind.DELETE),
            MergeCase(
                name = "待创建有未发送操作",
                local = local(SyncState.PENDING_CREATE),
                ops = listOf(op(OutboxOpType.CREATE)),
                expected = Kind.IGNORE,
            ),
            MergeCase(
                name = "待更新有未发送操作",
                local = local(SyncState.PENDING_UPDATE),
                ops = listOf(op(OutboxOpType.PATCH_PIN)),
                expected = Kind.IGNORE,
            ),
            MergeCase(
                name = "待删除有未发送操作",
                local = local(SyncState.PENDING_DELETE),
                ops = listOf(op(OutboxOpType.DELETE)),
                expected = Kind.IGNORE,
            ),
            MergeCase(
                name = "创建失败仅剩失败操作",
                local = local(SyncState.FAILED_CREATE),
                ops = listOf(op(OutboxOpType.CREATE, errorCode = ERROR_CODE)),
                expected = Kind.DELETE,
            ),
            MergeCase(
                name = "更新失败仅剩失败操作",
                local = local(SyncState.FAILED_UPDATE),
                ops = listOf(op(OutboxOpType.PATCH_PIN, errorCode = ERROR_CODE)),
                expected = Kind.DELETE,
            ),
            MergeCase(
                name = "删除失败仅剩失败操作",
                local = local(SyncState.FAILED_DELETE),
                ops = listOf(op(OutboxOpType.DELETE, errorCode = ERROR_CODE)),
                expected = Kind.DELETE,
            ),
            MergeCase(
                name = "失败记录仍有后续未发送操作",
                local = local(SyncState.FAILED_UPDATE),
                ops = listOf(
                    op(OutboxOpType.CREATE, errorCode = ERROR_CODE),
                    op(OutboxOpType.PATCH_PIN),
                ),
                expected = Kind.IGNORE,
            ),
            MergeCase(
                name = "已同步但有未发送操作",
                local = local(SyncState.SYNCED),
                ops = listOf(op(OutboxOpType.PATCH_PIN)),
                expected = Kind.IGNORE,
            ),
        )

        cases.forEach { case ->
            val result = ClipMerger.merge(case.local, remote(deleted = true), outboxOps = case.ops)
            assertEquals(case.expected, result.kind(), "墓碑 + ${case.name}")
        }
    }

    @Test
    fun remoteUpsertForMissingRecordBecomesSynced() {
        val result = assertIs<MergeResult.Upsert>(ClipMerger.merge(null, remote(pinned = true), emptyList()))

        with(result.entity) {
            assertEquals(ID, id)
            assertEquals(REMOTE_CONTENT, content)
            assertEquals("device-2", sourceDeviceId)
            assertEquals("Mac", sourceDeviceName)
            assertTrue(pinned)
            assertEquals(2, createdAt)
            assertEquals(9, serverSeq)
            assertEquals(SyncState.SYNCED, syncState)
            assertEquals(false, deleted)
            assertNull(lastErrorCode)
            assertNull(lastErrorAt)
        }
    }

    @Test
    fun syncedRecordIsFullyOverwrittenByRemote() {
        val local = local(SyncState.SYNCED, content = "stale", pinned = true)
        val result = assertIs<MergeResult.Upsert>(
            ClipMerger.merge(local, remote(content = REMOTE_CONTENT, pinned = false), emptyList())
        )

        assertEquals(REMOTE_CONTENT, result.entity.content)
        assertEquals(false, result.entity.pinned)
        assertEquals(9, result.entity.serverSeq)
        assertEquals(SyncState.SYNCED, result.entity.syncState)
    }

    @Test
    fun pendingCreateKeepsLocalIntentAndAdoptsServerSeq() {
        val local = local(SyncState.PENDING_CREATE, content = REMOTE_CONTENT, pinned = true)
        val result = assertIs<MergeResult.Upsert>(ClipMerger.merge(local, remote(), emptyList()))

        assertEquals(REMOTE_CONTENT, result.entity.content)
        assertTrue(result.entity.pinned)
        assertEquals(SyncState.PENDING_CREATE, result.entity.syncState)
        assertNull(result.entity.lastErrorCode)
        assertEquals(9, result.entity.serverSeq)
    }

    @Test
    fun pendingCreateWithSameServerSeqNeedsNoWrite() {
        val local = local(SyncState.PENDING_CREATE, content = REMOTE_CONTENT).copy(serverSeq = 9)

        val result = ClipMerger.merge(local, remote(serverSeq = 9), emptyList())

        assertIs<MergeResult.Ignore>(result)
    }

    @Test
    fun pendingCreateWithDifferentContentKeepsLocalCopy() {
        val local = local(SyncState.PENDING_CREATE, content = "local-content")

        // 冲突由 CREATE 重放的 409 暴露，合并层不得静默覆盖本地正文
        assertIs<MergeResult.Ignore>(ClipMerger.merge(local, remote(content = REMOTE_CONTENT), emptyList()))
    }

    @Test
    fun pendingUpdateKeepsLocalContentAndPinnedIntent() {
        val local = local(SyncState.PENDING_UPDATE, content = "local-content", pinned = true)
        val result = assertIs<MergeResult.Upsert>(
            ClipMerger.merge(local, remote(content = REMOTE_CONTENT, pinned = false, serverSeq = 11), emptyList())
        )

        assertEquals("local-content", result.entity.content)
        assertTrue(result.entity.pinned)
        assertEquals(SyncState.PENDING_UPDATE, result.entity.syncState)
        assertEquals(11, result.entity.serverSeq)
    }

    @Test
    fun pendingDeleteIsNotResurrectedByActiveRemote() {
        val local = local(SyncState.PENDING_DELETE, content = REMOTE_CONTENT)

        assertIs<MergeResult.Ignore>(
            ClipMerger.merge(local, remote(pinned = true), listOf(op(OutboxOpType.DELETE)))
        )
    }

    @Test
    fun failedStatesKeepErrorAndContentUntilTombstone() {
        SyncState.entries.filter { it.name.startsWith("FAILED") }.forEach { state ->
            val local = local(state, content = "local-content")

            assertIs<MergeResult.Ignore>(ClipMerger.merge(local, remote(content = REMOTE_CONTENT), emptyList()), state.name)
            assertIs<MergeResult.Delete>(
                ClipMerger.merge(local, remote(deleted = true), listOf(op(OutboxOpType.PATCH_PIN, ERROR_CODE))),
                state.name,
            )
        }
    }

    private data class MergeCase(
        val name: String,
        val local: ClipEntity?,
        val ops: List<OutboxEntity> = emptyList(),
        val expected: Kind,
    )

    private enum class Kind { UPSERT, DELETE, IGNORE }

    private fun MergeResult.kind(): Kind = when (this) {
        is MergeResult.Upsert -> Kind.UPSERT
        MergeResult.Delete -> Kind.DELETE
        MergeResult.Ignore -> Kind.IGNORE
    }

    private fun local(
        state: SyncState,
        content: String = REMOTE_CONTENT,
        pinned: Boolean = false,
    ): ClipEntity = ClipEntity(
        id = ID,
        content = content,
        sourceDeviceId = "device-1",
        sourceDeviceName = "Pixel",
        pinned = pinned,
        createdAt = 1,
        serverSeq = 0,
        deleted = state == SyncState.PENDING_DELETE || state == SyncState.FAILED_DELETE,
        syncState = state,
        lastErrorCode = if (state.name.startsWith("FAILED")) ERROR_CODE else null,
        lastErrorAt = if (state.name.startsWith("FAILED")) 100 else null,
    )

    private fun remote(
        deleted: Boolean = false,
        content: String = REMOTE_CONTENT,
        pinned: Boolean = false,
        serverSeq: Long = 9,
    ): ClipDto = ClipDto(
        id = ID,
        content = if (deleted) null else content,
        sourceDeviceId = if (deleted) null else "device-2",
        sourceDeviceName = if (deleted) null else "Mac",
        pinned = if (deleted) null else pinned,
        createdAt = if (deleted) null else 2,
        serverSeq = serverSeq,
        deleted = deleted,
    )

    private fun op(
        type: OutboxOpType,
        errorCode: String? = null,
    ): OutboxEntity = OutboxEntity(
        clipId = ID,
        opType = type,
        payload = "{}",
        createdAt = 1,
        lastErrorCode = errorCode,
    )

    private companion object {
        const val ID = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
        const val REMOTE_CONTENT = "remote-content"
        const val ERROR_CODE = "VALIDATION_ERROR"
    }
}
