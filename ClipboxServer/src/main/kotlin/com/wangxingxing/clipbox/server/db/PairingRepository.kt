/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : 设备自有配对码仓储：按 device_id 单行覆盖实现“同一设备仅一个有效码”，只读写 SHA-256 摘要。
 *               注意：所有方法必须在事务内调用；覆盖旧码与标记使用都必须与设备创建处于同一写事务。
 */
package com.wangxingxing.clipbox.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

data class DevicePairingCodeRow(
    val deviceId: String,
    val codeHash: String,
    val createdAt: Long,
    val expiresAt: Long,
    val usedAt: Long?,
)

object PairingRepository {

    /**
     * 写入设备码。同一设备已有记录时原位覆盖，旧码随 code_hash 更新立即失效；
     * 主键不变，因此并发重新生成不会留下第二条有效码。
     */
    fun upsertCode(deviceId: String, codeHash: String, createdAt: Long, expiresAt: Long) {
        val updated = DevicePairingCodesTable.update({ DevicePairingCodesTable.deviceId eq deviceId }) {
            it[this.codeHash] = codeHash
            it[this.createdAt] = createdAt
            it[this.expiresAt] = expiresAt
            it[this.usedAt] = null
        }
        if (updated == 0) {
            DevicePairingCodesTable.insert {
                it[this.deviceId] = deviceId
                it[this.codeHash] = codeHash
                it[this.createdAt] = createdAt
                it[this.expiresAt] = expiresAt
            }
        }
    }

    fun findByCodeHash(codeHash: String): DevicePairingCodeRow? =
        DevicePairingCodesTable.selectAll()
            .where { DevicePairingCodesTable.codeHash eq codeHash }
            .singleOrNull()
            ?.toDevicePairingCodeRow()

    /**
     * 仅当码仍未被使用时写入 used_at，返回受影响行数。
     * 条件更新保证并发或重放场景下同一个码最多被消费一次。
     */
    fun markUsedIfUnused(codeHash: String, usedAt: Long): Int =
        DevicePairingCodesTable.update({
            (DevicePairingCodesTable.codeHash eq codeHash) and DevicePairingCodesTable.usedAt.isNull()
        }) {
            it[this.usedAt] = usedAt
        }

    fun deleteExpired(cutoff: Long): Int =
        DevicePairingCodesTable.deleteWhere { DevicePairingCodesTable.expiresAt less cutoff }

    private fun ResultRow.toDevicePairingCodeRow() = DevicePairingCodeRow(
        deviceId = this[DevicePairingCodesTable.deviceId],
        codeHash = this[DevicePairingCodesTable.codeHash],
        createdAt = this[DevicePairingCodesTable.createdAt],
        expiresAt = this[DevicePairingCodesTable.expiresAt],
        usedAt = this[DevicePairingCodesTable.usedAt],
    )
}
