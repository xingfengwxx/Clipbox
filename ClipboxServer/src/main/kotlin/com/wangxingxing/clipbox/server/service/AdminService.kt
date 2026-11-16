/**
 * author : 王星星
 * date : 2026-11-16 14:56
 * email : 1099420259@qq.com
 * description : 管理端配对码服务：校验管理令牌、生成 6 位一次性码、启动时补发自助配对码。
 *               注意：本类只返回明文码，是否打印由调用方决定；管理接口生成的码不得进入常规日志。
 */
package com.wangxingxing.clipbox.server.service

import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.PairingRepository
import com.wangxingxing.clipbox.server.util.Hashing
import java.security.MessageDigest
import java.security.SecureRandom

data class AdminPairingCode(
    val code: String,
    val expiresAt: Long,
)

class AdminService(
    private val databaseFactory: DatabaseFactory,
    private val adminToken: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val random = SecureRandom()

    /**
     * 常量时间比较：先对双方取 SHA-256，使比较长度固定，不泄露令牌长度。
     */
    fun verifyToken(provided: String?): Boolean {
        if (provided == null) return false
        val expected = Hashing.sha256Hex(adminToken).toByteArray(Charsets.UTF_8)
        val actual = Hashing.sha256Hex(provided).toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(expected, actual)
    }

    suspend fun createPairingCode(): AdminPairingCode = databaseFactory.writeTransaction {
        val createdAt = now()
        val expiresAt = createdAt + CODE_TTL_MILLIS
        val code = generateUniqueCode(createdAt, expiresAt)
        AdminPairingCode(code = code, expiresAt = expiresAt)
    }

    /**
     * 启动时若不存在未使用且未过期的码，则生成一个返回给调用方用于打印；否则返回 null。
     * 检查与写入放在同一写事务，避免重启瞬间重复补发。
     */
    suspend fun ensureStartupCode(): String? = databaseFactory.writeTransaction {
        val createdAt = now()
        if (PairingRepository.existsUnusedUnexpired(createdAt)) {
            return@writeTransaction null
        }
        generateUniqueCode(createdAt, createdAt + CODE_TTL_MILLIS)
    }

    private fun generateUniqueCode(createdAt: Long, expiresAt: Long): String {
        repeat(MAX_GENERATION_ATTEMPTS) {
            val code = random.nextInt(CODE_SPACE).toString().padStart(CODE_LENGTH, '0')
            if (PairingRepository.findByCode(code) == null) {
                PairingRepository.insert(code = code, createdAt = createdAt, expiresAt = expiresAt)
                return code
            }
        }
        error("连续 $MAX_GENERATION_ATTEMPTS 次生成配对码均发生主键冲突")
    }

    companion object {
        const val CODE_TTL_MILLIS = 600_000L
        private const val CODE_LENGTH = 6
        private const val CODE_SPACE = 1_000_000
        private const val MAX_GENERATION_ATTEMPTS = 10
    }
}
