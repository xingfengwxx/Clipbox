/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : REST 接口封装，只做契约映射与错误分类；不写本地数据库、不自动重试；日志经 AppLogger 统一输出并按白名单脱敏
 */
package com.wangxingxing.clipbox.network

import com.wangxingxing.clipbox.logging.AppLogger
import com.wangxingxing.clipbox.logging.LogEvent
import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.BootstrapRequest
import com.wangxingxing.clipbox.model.CreateClipRequest
import com.wangxingxing.clipbox.model.CreateClipResponse
import com.wangxingxing.clipbox.model.CreateShareRequest
import com.wangxingxing.clipbox.model.CreateShareResponse
import com.wangxingxing.clipbox.model.DeleteClipResponse
import com.wangxingxing.clipbox.model.PairRequest
import com.wangxingxing.clipbox.model.PairResponse
import com.wangxingxing.clipbox.model.PairingCodeResponse
import com.wangxingxing.clipbox.model.PatchPinnedRequest
import com.wangxingxing.clipbox.model.PatchPinnedResponse
import com.wangxingxing.clipbox.model.SyncResponse
import com.wangxingxing.clipbox.util.clipNowMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ApiService(
    private val client: HttpClient,
    private val json: Json = ApiJson,
    private val logger: AppLogger? = null,
    private val clock: () -> Long = ::clipNowMillis,
) {

    suspend fun pair(code: String, deviceName: String): ApiResult<PairResponse> {
        val body = PairRequest(code = code, deviceName = deviceName)
        return request(
            method = "POST",
            pathTemplate = "/pair",
            operation = "pair",
            requestBody = json.encodeToString(body),
        ) {
            client.post("pair") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    suspend fun bootstrap(deviceName: String): ApiResult<PairResponse> {
        val body = BootstrapRequest(deviceName = deviceName)
        return request(
            method = "POST",
            pathTemplate = "/bootstrap",
            operation = "bootstrap",
            requestBody = json.encodeToString(body),
        ) {
            client.post("bootstrap") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    // 需要设备认证；响应体含明文配对码，诊断日志依赖 LogRedactor 的 code 字段脱敏
    suspend fun createPairingCode(): ApiResult<PairingCodeResponse> =
        request(
            method = "POST",
            pathTemplate = "/devices/me/pairing-code",
            operation = "createPairingCode",
        ) {
            client.post("devices/me/pairing-code")
        }

    suspend fun getClips(since: Long, limit: Int = 200, full: Boolean = false): ApiResult<SyncResponse> =
        request(
            method = "GET",
            pathTemplate = "/clips",
            operation = "sync",
        ) {
            client.get("clips") {
                parameter("since", since)
                parameter("limit", limit)
                // full 只用于全量分页；增量请求省略该参数，保持契约规定的 query 形状
                if (full) parameter("full", true)
            }
        }

    // 服务端对同 id 同内容重发返回 200，同样属于成功
    suspend fun createClip(id: String, content: String, createdAt: Long): ApiResult<CreateClipResponse> {
        val body = CreateClipRequest(id = id, content = content, createdAt = createdAt)
        return request(
            method = "POST",
            pathTemplate = "/clips",
            operation = "createClip",
            clipId = id,
            requestBody = json.encodeToString(body),
        ) {
            client.post("clips") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    suspend fun updateClip(id: String, pinned: Boolean): ApiResult<PatchPinnedResponse> {
        val body = PatchPinnedRequest(pinned = pinned)
        return request(
            method = "PATCH",
            pathTemplate = "/clips/{id}",
            operation = "updateClip",
            clipId = id,
            requestBody = json.encodeToString(body),
        ) {
            client.patch("clips/$id") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    suspend fun deleteClip(id: String): ApiResult<DeleteClipResponse> =
        request(
            method = "DELETE",
            pathTemplate = "/clips/{id}",
            operation = "deleteClip",
            clipId = id,
        ) {
            client.delete("clips/$id")
        }

    suspend fun createShare(
        clipId: String,
        ttlSeconds: Long = 86_400,
        oneTime: Boolean = false,
    ): ApiResult<CreateShareResponse> {
        val body = CreateShareRequest(clipId = clipId, ttlSeconds = ttlSeconds, oneTime = oneTime)
        return request(
            method = "POST",
            pathTemplate = "/shares",
            operation = "createShare",
            clipId = clipId,
            requestBody = json.encodeToString(body),
        ) {
            client.post("shares") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    private suspend inline fun <reified T> request(
        method: String,
        pathTemplate: String,
        operation: String,
        clipId: String? = null,
        requestBody: String? = null,
        crossinline execute: suspend () -> HttpResponse,
    ): ApiResult<T> {
        val startedAt = clock()
        val response = try {
            execute()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 连接失败、超时、DNS 失败等底层异常统一视为离线，由 Outbox 按退避重试
            logNetwork(
                method, pathTemplate, operation, status = null, startedAt, clipId, requestBody,
                errorCode = "OFFLINE",
            )
            return ApiResult.Failure(ApiFailure.Offline)
        }

        // 先读文本再解码，保证响应体只被消费一次且诊断日志可复用同一份内容
        val bodyText = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ""
        }

        if (response.status.isSuccess()) {
            val decoded = try {
                json.decodeFromString<T>(bodyText)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logNetwork(
                    method, pathTemplate, operation, response.status.value, startedAt, clipId,
                    requestBody, bodyText, errorCode = ApiErrorCode.INVALID_RESPONSE.wireValue,
                    authorization = response.authorizationHeader(),
                )
                return ApiResult.Failure(ApiFailure.Retryable(ApiErrorCode.INVALID_RESPONSE))
            }
            logNetwork(
                method, pathTemplate, operation, response.status.value, startedAt, clipId,
                requestBody, bodyText,
                authorization = response.authorizationHeader(),
            )
            return ApiResult.Success(decoded)
        }

        val envelope = parseErrorEnvelope(bodyText, json)
        logNetwork(
            method, pathTemplate, operation, response.status.value, startedAt, clipId,
            requestBody, bodyText,
            errorCode = ApiErrorCode.fromWire(envelope?.error?.code).wireValue.ifEmpty {
                "HTTP_${response.status.value}"
            },
            authorization = response.authorizationHeader(),
        )
        return ApiResult.Failure(
            mapHttpFailure(
                statusCode = response.status.value,
                code = ApiErrorCode.fromWire(envelope?.error?.code),
                message = envelope?.error?.message,
                retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
            )
        )
    }

    private fun logNetwork(
        method: String,
        pathTemplate: String,
        operation: String,
        status: Int?,
        startedAt: Long,
        clipId: String?,
        requestBody: String?,
        responseBody: String? = null,
        errorCode: String? = null,
        authorization: String? = null,
    ) {
        logger?.log(
            LogEvent.Network(
                method = method,
                pathTemplate = pathTemplate,
                operation = operation,
                status = status,
                durationMillis = clock() - startedAt,
                errorCode = errorCode,
                clipId = clipId,
                requestBody = requestBody,
                responseBody = responseBody,
                authorization = authorization,
            )
        )
    }

    // 认证插件在发送阶段注入头，响应对象上读取到的才是实际发送值；脱敏由 LogRedactor 统一完成
    private fun HttpResponse.authorizationHeader(): String? =
        request.headers[HttpHeaders.Authorization]
}
