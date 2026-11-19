/**
 * author : 王星星
 * date : 2026-11-18 15:40
 * email : 1099420259@qq.com
 * description : 配对码生成：6 位数字、600 秒有效、单 issuer 单活码；只把 SHA-256 摘要落库。
 *               issuer 为已配对设备 id 或未配对设备共用的匿名槽位。
 *               注意：明文码只随返回值交给路由，生成过程零日志；重新生成必须在同一写事务内覆盖旧码。
 */
package com.wangxingxing.clipbox.server.service

import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.PairingRepository
import com.wangxingxing.clipbox.server.util.Hashing
import java.security.SecureRandom

data class IssuedPairingCode(
    val code: String,
    val expiresAt: Long,
)

class DevicePairingCodeService(
    private val databaseFactory: DatabaseFactory,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val random = SecureRandom()

    suspend fun create(issuerId: String): IssuedPairingCode = databaseFactory.writeTransaction {
        val createdAt = now()
        val expiresAt = createdAt + CODE_TTL_MILLIS
        repeat(MAX_GENERATION_ATTEMPTS) {
            val code = random.nextInt(CODE_SPACE).toString().padStart(CODE_LENGTH, '0')
            val codeHash = Hashing.sha256Hex(code)
            // 写事务已串行化；仍检查哈希冲突，避免 UNIQUE 约束把正常重试变成 500。
            if (PairingRepository.findByCodeHash(codeHash) == null) {
                PairingRepository.upsertCode(
                    issuerId = issuerId,
                    codeHash = codeHash,
                    createdAt = createdAt,
                    expiresAt = expiresAt,
                )
                return@writeTransaction IssuedPairingCode(code = code, expiresAt = expiresAt)
            }
        }
        error("连续 $MAX_GENERATION_ATTEMPTS 次生成配对码均发生哈希冲突")
    }

    companion object {
        // 未配对设备没有设备 id，共用一个固定槽位；ULID 为大写 Crockford，不会与该值冲突。
        const val ANONYMOUS_ISSUER = "anonymous"
        const val CODE_TTL_MILLIS = 600_000L
        private const val CODE_LENGTH = 6
        private const val CODE_SPACE = 1_000_000
        private const val MAX_GENERATION_ATTEMPTS = 10
    }
}
