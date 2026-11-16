/**
 * author : 王星星
 * date : 2026-11-16 15:52
 * email : 1099420259@qq.com
 * description : Room 数据库定义与统一构建入口；版本 1 不写迁移，后续升级时才补 Migration。注意：写事务使用 useWriterConnection + immediateTransaction，跨记录写入必须与 Outbox 入队同事务
 */
package com.wangxingxing.clipbox.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

const val DATABASE_NAME = "clipbox.db"

@Database(
    entities = [ClipEntity::class, OutboxEntity::class, MetaEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(ClipboxDatabaseConstructor::class)
abstract class ClipboxDatabase : RoomDatabase() {
    abstract fun clipDao(): ClipDao
    abstract fun outboxDao(): OutboxDao
    abstract fun metaDao(): MetaDao
}

@Suppress("KotlinNoActualForExpect")
expect object ClipboxDatabaseConstructor : RoomDatabaseConstructor<ClipboxDatabase> {
    override fun initialize(): ClipboxDatabase
}

// 三端统一使用 bundled 驱动：Android 8 的系统 SQLite 版本过旧，不支持 @Upsert 依赖的 UPSERT 语法
// 查询上下文沿用 Room 的平台默认值：Kotlin/Native 没有 Dispatchers.IO
fun buildClipboxDatabase(builder: RoomDatabase.Builder<ClipboxDatabase>): ClipboxDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .build()
