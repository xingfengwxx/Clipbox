/**
 * author : 王星星
 * date : 2026-11-18 16:35
 * email : 1099420259@qq.com
 * description : 已配对设备取码：调用认证接口生成一次性 6 位码，只在内存中把明文交给 UI，不写设置、数据库或日志。
 *               注意：本类刻意不依赖 SettingsRepository 与 Room，配对码生命周期完全由调用方内存状态管理。
 */
package com.wangxingxing.clipbox.repository

import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.network.ApiResult
import com.wangxingxing.clipbox.network.ApiService

data class PairingCode(
    val code: String,
    val expiresAt: Long,
)

class PairingCodeRepository(
    private val apiService: ApiService,
) {

    suspend fun create(): PairingCodeResult = when (val response = apiService.createPairingCode()) {
        is ApiResult.Success -> PairingCodeResult.Success(
            PairingCode(code = response.value.code, expiresAt = response.value.expiresAt),
        )

        is ApiResult.Failure -> PairingCodeResult.Failure(response.failure.toPairingCodeError())
    }
}

sealed interface PairingCodeResult {
    data class Success(val pairingCode: PairingCode) : PairingCodeResult
    data class Failure(val error: PairingCodeError) : PairingCodeResult
}

sealed interface PairingCodeError {
    data object Unauthorized : PairingCodeError
    data object Network : PairingCodeError
    data object Server : PairingCodeError
    data class RateLimited(val retryAfterSeconds: Long?) : PairingCodeError
}

private fun ApiFailure.toPairingCodeError(): PairingCodeError = when (this) {
    is ApiFailure.Unauthorized -> PairingCodeError.Unauthorized
    is ApiFailure.RateLimited -> PairingCodeError.RateLimited(retryAfterSeconds)
    is ApiFailure.Retryable ->
        if (code == ApiErrorCode.RATE_LIMITED) PairingCodeError.RateLimited(null) else PairingCodeError.Server

    is ApiFailure.Permanent -> PairingCodeError.Server
    ApiFailure.Offline -> PairingCodeError.Network
}
