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

/** 分享页访问结果；正文只在成功打开时返回，错误分支不得携带快照。 */
sealed interface SharePageResult {
    data class Page(
        val content: String,
        val expiresAt: Long,
        val oneTime: Boolean,
    ) : SharePageResult

    data object NotFound : SharePageResult
    data object Expired : SharePageResult
    data object Consumed : SharePageResult
}

class ShareService(
    private val databaseFactory: DatabaseFactory,
    private val publicBaseUrl: String,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun create(clipId: String, ttlSeconds: Long, oneTime: Boolean): CreatedShare {        validateInput(clipId, ttlSeconds)

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

    /**
     * 打开分享页。一次性链接在写事务内完成“重新读取 + 原子标记”，
     * 保证并发访问只有一次能拿到正文；失败分支不返回快照。
     */
    suspend fun openPage(token: String): SharePageResult {
        if (!SHARE_TOKEN_PATTERN.matches(token)) return SharePageResult.NotFound
        val tokenHash = Hashing.sha256Hex(token)

        val row = databaseFactory.readTransaction {
            ShareRepository.findByTokenHash(tokenHash)
        } ?: return SharePageResult.NotFound
        val current = now()
        if (row.oneTime && row.openedAt != null) return SharePageResult.Consumed
        if (row.expiresAt <= current) return SharePageResult.Expired
        if (!row.oneTime) return SharePageResult.Page(row.content, row.expiresAt, oneTime = false)

        return databaseFactory.writeTransaction {
            val latest = ShareRepository.findByTokenHash(tokenHash)
                ?: return@writeTransaction SharePageResult.NotFound
            if (latest.openedAt != null) return@writeTransaction SharePageResult.Consumed
            val openedAt = now()
            if (latest.expiresAt <= openedAt) {
                return@writeTransaction SharePageResult.Expired
            }
            val updated = ShareRepository.markOpenedIfOneTime(tokenHash, openedAt)
            if (updated == 1) {
                SharePageResult.Page(latest.content, latest.expiresAt, oneTime = true)
            } else {
                // 理论上写锁串行化后不会走到这里；按当前行状态兜底，避免误渲染正文。
                val after = ShareRepository.findByTokenHash(tokenHash)
                when {
                    after == null -> SharePageResult.NotFound
                    after.openedAt != null -> SharePageResult.Consumed
                    after.expiresAt <= now() -> SharePageResult.Expired
                    else -> SharePageResult.NotFound
                }
            }
        }
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
        // 32 字节随机数的 base64url 无填充恰好 43 字符；格式不符直接 404，不查库。
        private val SHARE_TOKEN_PATTERN = Regex("^[A-Za-z0-9_-]{43}$")
    }
}
