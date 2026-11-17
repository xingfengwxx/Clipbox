/**
 * author : 王星星
 * date : 2026-11-16 15:52
 * email : 1099420259@qq.com
 * description : meta 表读写；未写入过的键按 0 返回，调用方无需再处理 null（0 同时表示尚未同步）
 */
package com.wangxingxing.clipbox.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MetaDao {

    @Query("SELECT COALESCE((SELECT value FROM meta WHERE `key` = :key), 0)")
    suspend fun get(key: String): Long

    // 未写入过时返回 null，调用方可据此区分“从未同步”和“同步时间为 0”
    @Query("SELECT value FROM meta WHERE `key` = :key")
    fun observe(key: String): Flow<Long?>

    @Query("INSERT OR REPLACE INTO meta (`key`, value) VALUES (:key, :value)")
    suspend fun put(key: String, value: Long)
}
