/**
 * author : 王星星
 * date : 2026-11-17 11:36
 * email : 1099420259@qq.com
 * description : 分享创建服务：校验剪贴记录可用性、签发限时或一次性分享令牌，并把当前正文快照写入 shares。
 *               注意：令牌明文只在创建响应出现，库中只存 SHA-256 哈希；快照与 clips 解耦，创建后修改或删除记录不影响分享；
 *                     分享不参与同步序号分配，也不触发 WebSocket 广播。
 */
package com.wangxingxing.clipbox.server.service

import com.wangxingxing.clipbox.server.db.ClipRepository
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.ShareRepository
import com.wangxingxing.clipbox.server.db.ShareRow
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.http.HttpStatusCode

/** 分享创建的输入校验或记录状态错误；由路由映射为统一错误 JSON。 */
class ShareCreateFailure(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : Exception(message)

/** 创建结果；token 与 url 只在本次响应返回，调用方不得记录。 */
data class CreatedShare(
    val token: String,
    val url: String,
    val expiresAt: Long,
)

class ShareService(
    private val databaseFactory: DatabaseFactory,
    private val publicBaseUrl: String,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun create(clipId: String, ttlSeconds: Long, oneTime: Boolean): CreatedShare {
        validateInput(clipId, ttlSeconds)

        // 令牌生成与摘要不接触数据库，放在事务外以缩短单写者临界区。
        val token = Hashing.randomToken()
        val tokenHash = Hashing.sha256Hex(token)
        // createdAt 与 expiresAt 必须来自同一时刻，否则库内差值无法与 ttlSeconds 对上。
        val createdAt = now()
        val expiresAt = createdAt + ttlSeconds * 1000

        databaseFactory.writeTransaction {
            val clip = ClipRepository.findById(clipId)
            if (clip == null || clip.deletedAt != null) throw shareClipNotFound()
            ShareRepository.insert(
                ShareRow(
                    tokenHash = tokenHash,
                    clipId = clip.id,
                    content = clip.content,
                    createdAt = createdAt,
                    expiresAt = expiresAt,
                    oneTime = oneTime,
                    openedAt = null,
                ),
            )
        }

        return CreatedShare(
            token = token,
            url = "$publicBaseUrl/s/$token",
            expiresAt = expiresAt,
        )
    }

    private fun validateInput(clipId: String, ttlSeconds: Long) {
        // 与剪贴写入共用同一 ULID 规则；不引用 ClipService 的私有常量，避免为校验改动既有服务。
        if (!ULID_PATTERN.matches(clipId)) {
            throw validationError("clipId 必须是 26 位大写 Crockford Base32 ULID")
        }
        if (ttlSeconds !in MIN_TTL_SECONDS..MAX_TTL_SECONDS) {
            throw validationError("ttlSeconds 必须在 $MIN_TTL_SECONDS-$MAX_TTL_SECONDS 之间")
        }
    }

    private fun validationError(message: String) =
        ShareCreateFailure(HttpStatusCode.BadRequest, "VALIDATION_ERROR", message)

    private fun shareClipNotFound() =
        ShareCreateFailure(HttpStatusCode.NotFound, "NOT_FOUND", "剪贴记录不存在或已删除")

    companion object {
        private const val MIN_TTL_SECONDS = 60L
        private const val MAX_TTL_SECONDS = 604_800L
        private val ULID_PATTERN = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
    }
}
