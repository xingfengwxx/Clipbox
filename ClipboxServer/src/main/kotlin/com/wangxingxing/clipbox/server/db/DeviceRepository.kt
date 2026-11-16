/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : 设备仓储基础读写；token_hash 为唯一索引，查询只按哈希进行。
 *               注意：所有方法必须在事务内调用。
 */
package com.wangxingxing.clipbox.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

data class DeviceRow(
    val id: String,
    val name: String,
    val tokenHash: String,
    val createdAt: Long,
    val lastSeenAt: Long?,
)

object DeviceRepository {

    fun insert(device: DeviceRow) {
        DevicesTable.insert {
            it[id] = device.id
            it[name] = device.name
            it[tokenHash] = device.tokenHash
            it[createdAt] = device.createdAt
            it[lastSeenAt] = device.lastSeenAt
        }
    }

    fun findById(id: String): DeviceRow? =
        DevicesTable.selectAll()
            .where { DevicesTable.id eq id }
            .singleOrNull()
            ?.toDeviceRow()

    fun findByTokenHash(tokenHash: String): DeviceRow? =
        DevicesTable.selectAll()
            .where { DevicesTable.tokenHash eq tokenHash }
            .singleOrNull()
            ?.toDeviceRow()

    fun touchLastSeen(id: String, at: Long): Int =
        DevicesTable.update({ DevicesTable.id eq id }) {
            it[lastSeenAt] = at
        }

    private fun ResultRow.toDeviceRow() = DeviceRow(
        id = this[DevicesTable.id],
        name = this[DevicesTable.name],
        tokenHash = this[DevicesTable.tokenHash],
        createdAt = this[DevicesTable.createdAt],
        lastSeenAt = this[DevicesTable.lastSeenAt],
    )
}
