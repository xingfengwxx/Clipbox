/**
 * author : 王星星
 * date : 2026-11-16 16:05
 * email : 1099420259@qq.com
 * description : 数据库测试公共设施；统一使用内存库与 bundled 驱动，保证 JVM 与 iOS 模拟器行为一致
 */
package com.wangxingxing.clipbox.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.wangxingxing.clipbox.model.SyncState

fun createInMemoryDatabase(): ClipboxDatabase =
    Room.inMemoryDatabaseBuilder<ClipboxDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

fun testClip(
    id: String,
    createdAt: Long,
    content: String = "content-$id",
    pinned: Boolean = false,
    syncState: SyncState = SyncState.SYNCED,
    deleted: Boolean = false,
): ClipEntity = ClipEntity(
    id = id,
    content = content,
    sourceDeviceId = "device-1",
    sourceDeviceName = "Pixel",
    pinned = pinned,
    createdAt = createdAt,
    syncState = syncState,
    deleted = deleted,
)
