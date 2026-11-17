/**
 * author : 王星星
 * date : 2026-11-17 14:30
 * email : 1099420259@qq.com
 * description : 同步状态推导表测试；覆盖优先级、失败优先于待同步、401 与离线的区别以及恢复路径
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusTest {

    @Test
    fun priorityOrderIsUnauthorizedResyncingSyncingOfflineFailedPendingIdle() {
        assertEquals(
            SyncStatus.Unauthorized,
            resolveSyncStatus(SyncConnection.Unauthorized, pending = 9, failed = 9),
        )
        assertEquals(
            SyncStatus.Resyncing,
            resolveSyncStatus(SyncConnection.Resyncing, pending = 9, failed = 9),
        )
        assertEquals(
            SyncStatus.Syncing,
            resolveSyncStatus(SyncConnection.Syncing, pending = 9, failed = 9),
        )
        assertEquals(
            SyncStatus.Offline,
            resolveSyncStatus(SyncConnection.Offline, pending = 9, failed = 9),
        )
        assertEquals(
            SyncStatus.Failed(9),
            resolveSyncStatus(SyncConnection.Connected, pending = 9, failed = 9),
        )
        assertEquals(
            SyncStatus.Pending(9),
            resolveSyncStatus(SyncConnection.Connected, pending = 9, failed = 0),
        )
        assertEquals(
            SyncStatus.Idle,
            resolveSyncStatus(SyncConnection.Connected, pending = 0, failed = 0),
        )
    }

    @Test
    fun failureCountTakesPriorityOverPendingCount() {
        assertEquals(
            SyncStatus.Failed(2),
            resolveSyncStatus(SyncConnection.Connected, pending = 5, failed = 2),
        )
    }

    @Test
    fun unauthorizedIsDistinctFromOffline() {
        assertEquals(
            SyncStatus.Unauthorized,
            resolveSyncStatus(SyncConnection.Unauthorized, pending = 0, failed = 0),
        )
        assertEquals(
            SyncStatus.Offline,
            resolveSyncStatus(SyncConnection.Offline, pending = 0, failed = 0),
        )
    }

    @Test
    fun stateRecoversAfterFailureAndQueueDrains() {
        assertEquals(
            SyncStatus.Offline,
            resolveSyncStatus(SyncConnection.Offline, pending = 3, failed = 0),
        )
        assertEquals(
            SyncStatus.Pending(3),
            resolveSyncStatus(SyncConnection.Connected, pending = 3, failed = 0),
        )
        assertEquals(
            SyncStatus.Failed(1),
            resolveSyncStatus(SyncConnection.Connected, pending = 0, failed = 1),
        )
        assertEquals(
            SyncStatus.Idle,
            resolveSyncStatus(SyncConnection.Connected, pending = 0, failed = 0),
        )
    }
}
