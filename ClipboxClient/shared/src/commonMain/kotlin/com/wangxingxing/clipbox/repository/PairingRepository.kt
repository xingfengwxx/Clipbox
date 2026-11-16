/**
 * author : 王星星
 * date : 2026-11-16 17:12
 * email : 1099420259@qq.com
 * description : 配对编排：校验地址与设备名、调用公开的 POST /pair、成功后写入 serverUrl 与设备凭据；不打印请求响应内容
 */
package com.wangxingxing.clipbox.repository

import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.network.ApiResult
import com.wangxingxing.clipbox.network.ApiService
import com.wangxingxing.clipbox.network.createApiHttpClient
import com.wangxingxing.clipbox.settings.ServerUrlValidation
import com.wangxingxing.clipbox.settings.ServerUrlValidator
import com.wangxingxing.clipbox.settings.SettingsRepository

const val PAIRING_CODE_LENGTH = 6
const val DEVICE_NAME_MAX_LENGTH = 64

class PairingRepository(
    private val settingsRepository: SettingsRepository,
) {

    suspend fun pair(serverUrl: String, code: String, deviceName: String): PairResult {
        val url = when (val validation = ServerUrlValidator.validate(serverUrl)) {
            is ServerUrlValidation.Invalid -> return PairResult.Failure(PairingError.ServerUrl)
            is ServerUrlValidation.Valid -> validation.url
        }
        val name = deviceName.trim()
        if (name.isEmpty() || name.length > DEVICE_NAME_MAX_LENGTH) {
            return PairResult.Failure(PairingError.DeviceName)
        }
        // 服务端对格式错误的码返回 VALIDATION_ERROR，本地先拦截以免误报设备名问题
        if (code.length != PAIRING_CODE_LENGTH || code.any { it !in '0'..'9' }) {
            return PairResult.Failure(PairingError.InvalidCode)
        }

        // 配对接口公开，tokenProvider 固定返回 null，客户端不携带既有令牌
        val client = createApiHttpClient(url, tokenProvider = { null })
        val response = try {
            ApiService(client).pair(code = code, deviceName = name)
        } finally {
            client.close()
        }

        return when (response) {
            is ApiResult.Success -> {
                settingsRepository.setServerUrl(url)
                settingsRepository.setPairingInfo(
                    deviceToken = response.value.token,
                    deviceId = response.value.deviceId,
                    deviceName = name,
                    lastServerUrl = url,
                )
                PairResult.Success
            }

            is ApiResult.Failure -> PairResult.Failure(response.failure.toPairingError())
        }
    }
}

sealed interface PairResult {
    data object Success : PairResult
    data class Failure(val error: PairingError) : PairResult
}

sealed interface PairingError {
    data object InvalidCode : PairingError
    data object CodeExpired : PairingError
    data object CodeUsed : PairingError
    data object DeviceName : PairingError
    data object Network : PairingError
    data object ServerUrl : PairingError
    data object Server : PairingError
    data class RateLimited(val retryAfterSeconds: Long?) : PairingError
}

private fun ApiFailure.toPairingError(): PairingError = when (this) {
    is ApiFailure.Permanent -> when (code) {
        ApiErrorCode.INVALID_CODE -> PairingError.InvalidCode
        ApiErrorCode.CODE_EXPIRED -> PairingError.CodeExpired
        ApiErrorCode.CODE_USED -> PairingError.CodeUsed
        ApiErrorCode.VALIDATION_ERROR -> PairingError.DeviceName
        else -> PairingError.Server
    }

    is ApiFailure.RateLimited -> PairingError.RateLimited(retryAfterSeconds)
    // 429 未带 Retry-After 时由 UI 使用默认冷却时长
    is ApiFailure.Retryable ->
        if (code == ApiErrorCode.RATE_LIMITED) PairingError.RateLimited(null) else PairingError.Server

    is ApiFailure.Unauthorized -> PairingError.Server
    ApiFailure.Offline -> PairingError.Network
}
