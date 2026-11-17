/**
 * author : 王星星
 * date : 2026-11-16 15:52
 * email : 1099420259@qq.com
 * description : Desktop 数据库构建器；按系统约定选择用户数据目录，与 DataStore 共用同一应用数据根目录
 */
package com.wangxingxing.clipbox.db

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

fun createClipboxDatabaseBuilder(): RoomDatabase.Builder<ClipboxDatabase> {
    val directory = desktopDatabaseDirectory()
    directory.mkdirs()
    return Room.databaseBuilder<ClipboxDatabase>(name = File(directory, DATABASE_NAME).absolutePath)
}

private fun desktopDatabaseDirectory(): File {
    val home = System.getProperty("user.home")
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    val appDirectory = when {
        osName.contains("mac") -> File(home, "Library/Application Support/Theca")
        osName.contains("win") -> File(System.getenv("APPDATA") ?: home, "Theca")
        else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, "theca")
    }
    return appDirectory
}

/** 平台注入入口：构建器类型只留在 shared 内，避免 composeApp 直接依赖 Room API */
fun createClipboxDatabase(): ClipboxDatabase = buildClipboxDatabase(createClipboxDatabaseBuilder())
