/**
 * author : 王星星
 * date : 2026-11-16 15:52
 * email : 1099420259@qq.com
 * description : Android 数据库构建器；数据库落在应用私有 databases 目录，不申请任何存储权限
 */
package com.wangxingxing.clipbox.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun createClipboxDatabaseBuilder(context: Context): RoomDatabase.Builder<ClipboxDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(DATABASE_NAME)
    dbFile.parentFile?.mkdirs()
    return Room.databaseBuilder<ClipboxDatabase>(
        context = appContext,
        name = dbFile.absolutePath,
    )
}
