/**
 * author : 王星星
 * date : 2026-11-16 14:56
 * email : 1099420259@qq.com
 * description : 配对与空库初始化服务：按哈希校验设备码、在同一事务内消费配对码并签发一次性设备令牌。
 *               注意：设备令牌明文只随返回值交给路由，服务端只持久化 SHA-256 摘要；配对码与令牌不得写入日志。
 */
package com.wangxingxing.clipbox.server.service

import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.db.PairingRepository
import com.wangxingxing.clipbox.server.util.Hashing
import com.wangxingxing.clipbox.server.util.Ulid
import io.ktor.http.HttpStatusCode

/** 配对失败的业务错误；由路由映射为统一错误 JSON。 */
class PairingFailure(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : Exception(message)

data class IssuedDevice(
    val deviceId: String,
    val token: String,
    val deviceName: String,
)

class PairingService(
    private val databaseFactory: DatabaseFactory,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun pair(code: String, deviceName: String): IssuedDevice {
        if (!CODE_PATTERN.matches(code)) {
            throw PairingFailure(
                HttpStatusCode.BadRequest,
                "VALIDATION_ERROR",
                "配对码必须是 6 位数字",
            )
        }
        val normalizedName = normalizeDeviceName(deviceName)
        val codeHash = Hashing.sha256Hex(code)

        return databaseFactory.writeTransaction {
            val issuedAt = now()
            val record = PairingRepository.findByCodeHash(codeHash)
                ?: throw PairingFailure(HttpStatusCode.BadRequest, "INVALID_CODE", "配对码不存在")
            // 先判已使用再判过期：已消费的码即使过期，也应提示用户重新获取而不是等待。
            if (record.usedAt != null) {
                throw PairingFailure(HttpStatusCode.BadRequest, "CODE_USED", "配对码已被使用")
            }
            if (record.expiresAt <= issuedAt) {
                throw PairingFailure(HttpStatusCode.BadRequest, "CODE_EXPIRED", "配对码已过期")
            }
            if (PairingRepository.markUsedIfUnused(codeHash, issuedAt) != 1) {
                throw PairingFailure(HttpStatusCode.BadRequest, "CODE_USED", "配对码已被使用")
            }
            issueDevice(normalizedName, issuedAt)
        }
    }

    suspend fun initialize(deviceName: String): IssuedDevice {
        val normalizedName = normalizeDeviceName(deviceName)
        return databaseFactory.writeTransaction {
            // 写事务由 DatabaseFactory 串行化，判空与建设备之间不会插入第二个首台设备。
            if (DeviceRepository.count() > 0) {
                throw PairingFailure(
                    HttpStatusCode.Conflict,
                    "ALREADY_INITIALIZED",
                    "服务器已初始化，请输入已配对设备的配对码",
                )
            }
            issueDevice(normalizedName, now())
        }
    }

    private fun issueDevice(deviceName: String, issuedAt: Long): IssuedDevice {
        val token = Hashing.randomToken()
        val deviceId = Ulid.generate(issuedAt)
        DeviceRepository.insert(
            DeviceRow(
                id = deviceId,
                name = deviceName,
                tokenHash = Hashing.sha256Hex(token),
                createdAt = issuedAt,
                lastSeenAt = null,
            ),
        )
        return IssuedDevice(deviceId = deviceId, token = token, deviceName = deviceName)
    }

    private fun normalizeDeviceName(deviceName: String): String {
        val normalized = deviceName.trim()
        if (normalized.length !in 1..MAX_DEVICE_NAME_LENGTH) {
            throw PairingFailure(
                HttpStatusCode.BadRequest,
                "VALIDATION_ERROR",
                "设备名去除首尾空白后必须为 1-$MAX_DEVICE_NAME_LENGTH 个字符",
            )
        }
        return normalized
    }

    companion object {
        private val CODE_PATTERN = Regex("^\\d{6}$")
        private const val MAX_DEVICE_NAME_LENGTH = 64
    }
}
