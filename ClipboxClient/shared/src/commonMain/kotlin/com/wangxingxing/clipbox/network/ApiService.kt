/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : REST 接口封装，只做契约映射与错误分类；不写本地数据库、不自动重试、不打印请求响应内容
 */
package com.wangxingxing.clipbox.network

import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.CreateClipRequest
import com.wangxingxing.clipbox.model.CreateClipResponse
import com.wangxingxing.clipbox.model.CreateShareRequest
import com.wangxingxing.clipbox.model.CreateShareResponse
import com.wangxingxing.clipbox.model.DeleteClipResponse
import com.wangxingxing.clipbox.model.PairRequest
import com.wangxingxing.clipbox.model.PairResponse
import com.wangxingxing.clipbox.model.PatchPinnedRequest
import com.wangxingxing.clipbox.model.PatchPinnedResponse
import com.wangxingxing.clipbox.model.SyncResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class ApiService(
    private val client: HttpClient,
    private val json: Json = ApiJson,
) {

    suspend fun pair(code: String, deviceName: String): ApiResult<PairResponse> =
        request {
            client.post("pair") {
                contentType(ContentType.Application.Json)
                setBody(PairRequest(code = code, deviceName = deviceName))
            }
        }

    suspend fun getClips(since: Long, limit: Int = 200): ApiResult<SyncResponse> =
        request {
            client.get("clips") {
                parameter("since", since)
                parameter("limit", limit)
            }
        }

    // 服务端对同 id 同内容重发返回 200，同样属于成功
    suspend fun createClip(id: String, content: String, createdAt: Long): ApiResult<CreateClipResponse> =
        request {
            client.post("clips") {
                contentType(ContentType.Application.Json)
                setBody(CreateClipRequest(id = id, content = content, createdAt = createdAt))
            }
        }

    suspend fun updateClip(id: String, pinned: Boolean): ApiResult<PatchPinnedResponse> =
        request {
            client.patch("clips/$id") {
                contentType(ContentType.Application.Json)
                setBody(PatchPinnedRequest(pinned = pinned))
            }
        }

    suspend fun deleteClip(id: String): ApiResult<DeleteClipResponse> =
        request {
            client.delete("clips/$id")
        }

    suspend fun createShare(
        clipId: String,
        ttlSeconds: Long = 86_400,
        oneTime: Boolean = false,
    ): ApiResult<CreateShareResponse> =
        request {
            client.post("shares") {
                contentType(ContentType.Application.Json)
                setBody(CreateShareRequest(clipId = clipId, ttlSeconds = ttlSeconds, oneTime = oneTime))
            }
        }

    private suspend inline fun <reified T> request(
        crossinline execute: suspend () -> HttpResponse,
    ): ApiResult<T> {
        val response = try {
            execute()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 连接失败、超时、DNS 失败等底层异常统一视为离线，由 Outbox 按退避重试
            return ApiResult.Failure(ApiFailure.Offline)
        }

        if (response.status.isSuccess()) {
            return try {
                ApiResult.Success(response.body<T>())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ApiResult.Failure(ApiFailure.Retryable(ApiErrorCode.INVALID_RESPONSE))
            }
        }

        val bodyText = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ""
        }
        val envelope = parseErrorEnvelope(bodyText, json)
        return ApiResult.Failure(
            mapHttpFailure(
                statusCode = response.status.value,
                code = ApiErrorCode.fromWire(envelope?.error?.code),
                message = envelope?.error?.message,
                retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
            )
        )
    }
}
