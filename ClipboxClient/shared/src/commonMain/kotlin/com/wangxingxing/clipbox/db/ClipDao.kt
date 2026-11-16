/**
 * author : 王星星
 * date : 2026-11-16 15:52
 * email : 1099420259@qq.com
 * description : clips 表查询与写入；历史列表、搜索、单条读写与本地裁剪的唯一入口，所有写操作由 Room 事务保证原子性
 */
package com.wangxingxing.clipbox.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {

    // 置顶记录全部展示；非置顶只取最近 limitNonPinned 条，避免把全量正文送进 UI
    @Query(
        """
        SELECT * FROM clips
        WHERE deleted = 0 AND (
            pinned = 1 OR id IN (
                SELECT id FROM clips
                WHERE pinned = 0 AND deleted = 0
                ORDER BY createdAt DESC, id DESC
                LIMIT :limitNonPinned
            )
        )
        ORDER BY pinned DESC, createdAt DESC, id DESC
        """
    )
    fun observeHistory(limitNonPinned: Int = DEFAULT_NON_PINNED_LIMIT): Flow<List<ClipEntity>>

    // 转义 LIKE 的通配符，保证搜索 "%" / "_" 时按字面匹配
    @Query(
        """
        SELECT * FROM clips
        WHERE deleted = 0
          AND content LIKE '%' || REPLACE(REPLACE(REPLACE(:query, '\', '\\'), '%', '\%'), '_', '\_') || '%' ESCAPE '\'
        ORDER BY pinned DESC, createdAt DESC, id DESC
        """
    )
    fun search(query: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE id = :id")
    suspend fun getById(id: String): ClipEntity?

    @Upsert
    suspend fun upsert(clip: ClipEntity)

    // 仅标记本地删除，等待 DELETE 确认后由 deleteById 物理移除
    @Query("UPDATE clips SET deleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteById(id: String)

    // 只裁剪已同步的非置顶记录；待同步、失败、置顶与已删除记录一律保留
    @Query(
        """
        DELETE FROM clips WHERE id IN (
            SELECT id FROM clips
            WHERE pinned = 0 AND deleted = 0 AND syncState = 'SYNCED'
            ORDER BY createdAt DESC, id DESC
            LIMIT -1 OFFSET :keep
        )
        """
    )
    suspend fun trimNonPinned(keep: Int = DEFAULT_NON_PINNED_LIMIT): Int

    companion object {
        const val DEFAULT_NON_PINNED_LIMIT = 200
    }
}
