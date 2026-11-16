/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : meta 键值仓储，承载 server_seq 与 sync_floor；所有方法必须在事务内调用。
 */
package com.wangxingxing.clipbox.server.db

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

object MetaRepository {

    const val SERVER_SEQ_KEY = "server_seq"
    const val SYNC_FLOOR_KEY = "sync_floor"

    fun get(key: String): String? =
        MetaTable.selectAll()
            .where { MetaTable.key eq key }
            .singleOrNull()
            ?.get(MetaTable.value)

    fun getLong(key: String): Long? = get(key)?.toLongOrNull()

    fun put(key: String, value: String) {
        val updated = MetaTable.update({ MetaTable.key eq key }) {
            it[MetaTable.value] = value
        }
        if (updated == 0) {
            MetaTable.insert {
                it[MetaTable.key] = key
                it[MetaTable.value] = value
            }
        }
    }
}
