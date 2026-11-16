/**
 * author : 王星星
 * date : 2026-11-16 15:52
 * email : 1099420259@qq.com
 * description : iOS 数据库构建器；数据库与 DataStore 同放在 Application Support 应用目录，跟随系统备份策略
 */
package com.wangxingxing.clipbox.db

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun createClipboxDatabaseBuilder(): RoomDatabase.Builder<ClipboxDatabase> {
    val basePath = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: error("Application Support directory is unavailable")

    val directory = "$basePath/Theca"
    val fileManager = NSFileManager.defaultManager
    if (!fileManager.fileExistsAtPath(directory)) {
        fileManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    return Room.databaseBuilder<ClipboxDatabase>(name = "$directory/$DATABASE_NAME")
}
