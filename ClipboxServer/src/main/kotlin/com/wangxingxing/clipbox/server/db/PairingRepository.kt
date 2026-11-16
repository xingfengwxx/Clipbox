/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : 配对码仓储基础读写；used_at 为空表示未使用。
 *               注意：所有方法必须在事务内调用，标记使用与设备创建需由上层放在同一事务。
 */
package com.wangxingxing.clipbox.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

data class PairingCodeRow(
    val code: String,
    val createdAt: Long,
    val expiresAt: Long,
    val usedAt: Long?,
)

object PairingRepository {

    fun insert(code: String, createdAt: Long, expiresAt: Long) {
        PairingCodesTable.insert {
            it[this.code] = code
            it[this.createdAt] = createdAt
            it[this.expiresAt] = expiresAt
        }
    }

    fun findByCode(code: String): PairingCodeRow? =
        PairingCodesTable.selectAll()
            .where { PairingCodesTable.code eq code }
            .singleOrNull()
            ?.toPairingCodeRow()

    fun markUsed(code: String, usedAt: Long): Int =
        PairingCodesTable.update({ PairingCodesTable.code eq code }) {
            it[this.usedAt] = usedAt
        }

    private fun ResultRow.toPairingCodeRow() = PairingCodeRow(
        code = this[PairingCodesTable.code],
        createdAt = this[PairingCodesTable.createdAt],
        expiresAt = this[PairingCodesTable.expiresAt],
        usedAt = this[PairingCodesTable.usedAt],
    )
}
