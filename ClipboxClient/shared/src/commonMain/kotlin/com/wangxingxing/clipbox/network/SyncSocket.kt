/**
 * author : 王星星
 * date : 2026-11-17 13:26
 * email : 1099420259@qq.com
 * description : WS 同步传输与连接状态机；负责 ws/wss 地址推导、首帧令牌认证、events 缓冲补拉、关闭码策略与指数退避重连，不直接写数据库。注意：令牌只允许出现在 auth 首帧，不得拼进 URL、头或日志；4401/4001 必须停止自动重连，避免互相踢下线
 */
package com.wangxingxing.clipbox.network

import com.wangxingxing.clipbox.logging.AppLogger
import com.wangxingxing.clipbox.logging.LogEvent
import com.wangxingxing.clipbox.model.ClipDto
import com.wangxingxing.clipbox.model.WsAuthOk
import com.wangxingxing.clipbox.model.WsAuthRequest
import com.wangxingxing.clipbox.model.WsClipEvent
import com.wangxingxing.clipbox.model.WsErrorFrame
import com.wangxingxing.clipbox.model.WsMessageType
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.sync.Backoff
import com.wangxingxing.clipbox.sync.PullResult
import com.wangxingxing.clipbox.sync.SyncEventResult
import com.wangxingxing.clipbox.sync.SyncGateway
import com.wangxingxing.clipbox.sync.SyncStatusReporter
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.Volatile
import kotlin.random.Random

/** 连接帧的最小抽象；生产实现是 Ktor WebSocket，测试用假会话驱动状态机 */
interface SyncSocketFrame {
    data class Text(val text: String) : SyncSocketFrame
    data class Closed(val code: Int?) : SyncSocketFrame
}

interface SyncSocketSession {
    suspend fun send(text: String)

    /** 远端关闭或连接中断时返回 [SyncSocketFrame.Closed]；正常 close 后可能直接抛出通道异常 */
    suspend fun receive(): SyncSocketFrame

    fun close()
}

fun interface SyncSocketTransport {
    suspend fun connect(url: String): SyncSocketSession?
}

class KtorSyncSocketTransport(private val client: HttpClient) : SyncSocketTransport {

    override suspend fun connect(url: String): SyncSocketSession? = try {
        KtorSyncSocketSession(client.webSocketSession(url))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }
}

private class KtorSyncSocketSession(
    private val session: DefaultClientWebSocketSession,
) : SyncSocketSession {

    override suspend fun send(text: String) {
        session.send(Frame.Text(text))
    }

    override suspend fun receive(): SyncSocketFrame = try {
        when (val frame = session.incoming.receive()) {
            is Frame.Text -> SyncSocketFrame.Text(frame.readText())
            is Frame.Close -> SyncSocketFrame.Closed(closeCode())
            // 服务端只发文本与 ping/pong，二进制帧视为协议异常，交由外层按断线重连处理
            else -> SyncSocketFrame.Closed(null)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        SyncSocketFrame.Closed(closeCode())
    }

    override fun close() {
        session.cancel()
    }

    private suspend fun closeCode(): Int? =
        runCatching { session.closeReason.await()?.code?.toInt() }.getOrNull()
}

class SyncSocket(
    private val transport: SyncSocketTransport,
    private val gateway: SyncGateway,
    private val settingsRepository: SettingsRepository,
    private val defaultServerUrl: String,
    private val statusReporter: SyncStatusReporter = SyncStatusReporter.None,
    private val logger: AppLogger? = null,
    private val jitterSample: () -> Double = { Random.nextDouble() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val wakeUps = Channel<Unit>(Channel.CONFLATED)
    private val realtimeCreatedFlow = MutableSharedFlow<ClipDto>(extraBufferCapacity = 64)
    private var startedScope: CoroutineScope? = null
    private var loopJob: Job? = null

    /** 仅包含 WS 实时到达且已成功应用到本地库的 clip.created；REST / 缺口补拉不会进入此流 */
    val realtimeCreated: SharedFlow<ClipDto> = realtimeCreatedFlow.asSharedFlow()

    @Volatile
    private var foreground = true

    // 只由连接循环协程读写；认证成功即清零，长时间在线后的断线从 1s 重新退避
    private var retryCount = 0

    fun start(scope: CoroutineScope) {
        startedScope = scope
        ensureLoop()
    }

    fun stop() = onBackground()

    /** 前台恢复：先 REST 补拉再建立连接；重复调用幂等，不会产生多条连接 */
    fun onForeground() {
        foreground = true
        ensureLoop()
        wakeUps.trySend(Unit)
    }

    /** 切后台：立即断开并停止重连，把补拉交给下一次前台恢复 */
    fun onBackground() {
        foreground = false
        loopJob?.cancel()
        loopJob = null
    }

    /** 外部同步动作完成后调用，让处于未配对等待或退避中的循环立即重试 */
    fun requestConnect() {
        ensureLoop()
        wakeUps.trySend(Unit)
    }

    private fun ensureLoop() {
        if (!foreground) return
        if (loopJob?.isActive == true) return
        val scope = startedScope ?: return
        loopJob = scope.launch(dispatcher) { connectionLoop() }
    }

    private suspend fun connectionLoop() {
        while (currentCoroutineContext().isActive && foreground) {
            val token = settingsRepository.current().deviceToken?.takeIf { it.isNotEmpty() }
            if (token == null) {
                logger?.log(LogEvent.Socket(event = "wait-pairing", detail = "no device token"))
                statusReporter.onUnauthorized()
                // 配对成功由 SyncCoordinator 调用 onForeground 唤醒，不轮询
                wakeUps.receive()
                continue
            }

            // 前台恢复先补拉，失败也继续尝试连接：REST 短暂失败不代表 WS 不可用
            statusReporter.onSyncing()
            when (gateway.pull()) {
                is PullResult.Success -> statusReporter.onSynced()
                is PullResult.Failure -> statusReporter.onOffline()
                PullResult.WaitingForPairing -> {
                    statusReporter.onUnauthorized()
                    wakeUps.receive()
                    continue
                }
            }

            val session = transport.connect(syncSocketUrl(defaultServerUrl))
            if (session == null) {
                logger?.log(LogEvent.Socket(event = "connect-failed", detail = "transport returned null"))
                statusReporter.onOffline()
                retryCount++
                delayBackoff()
                continue
            }

            val outcome = try {
                runSession(session, token)
            } finally {
                session.close()
            }

            when (outcome) {
                SessionOutcome.Unauthorized -> {
                    logger?.log(LogEvent.Socket(event = "auth-failed", detail = "stop reconnect"))
                    statusReporter.onUnauthorized()
                    return
                }

                // 服务端正常关闭或 4001 设备被顶替：不自动重连，等待下一次前台或配对
                SessionOutcome.Stop -> {
                    logger?.log(LogEvent.Socket(event = "closed", detail = "reconnect disabled"))
                    statusReporter.onOffline()
                    return
                }

                SessionOutcome.Retry -> {
                    logger?.log(LogEvent.Socket(event = "closed", detail = "will retry"))
                    statusReporter.onOffline()
                    retryCount++
                    delayBackoff()
                }
            }
        }
    }

    private suspend fun runSession(session: SyncSocketSession, token: String): SessionOutcome =
        coroutineScope {
            session.send(ApiJson.encodeToString(WsAuthRequest.auth(token)))
            // 令牌仅进入首帧；日志中由 LogRedactor 统一替换为 <redacted>
            logger?.log(
                LogEvent.Socket(event = "auth-sent", detail = "waiting auth.ok", authToken = token),
            )
            // 5 秒内必须收到 auth.ok；超时或错误帧按 4401 处理，等待重新配对
            val auth = withTimeoutOrNull(AUTH_TIMEOUT_MILLIS) { awaitAuth(session) }
            when (auth) {
                null -> {
                    logger?.log(LogEvent.Socket(event = "auth-timeout", detail = "no auth.ok in 5s"))
                    return@coroutineScope SessionOutcome.Unauthorized
                }

                AuthOutcome.Ok -> Unit
                AuthOutcome.Error -> {
                    logger?.log(LogEvent.Socket(event = "auth-rejected", detail = "error frame or 4401"))
                    return@coroutineScope SessionOutcome.Unauthorized
                }

                is AuthOutcome.Closed -> return@coroutineScope auth.outcome
            }
            retryCount = 0
            logger?.log(LogEvent.Socket(event = "auth-ok", detail = "session established"))
            statusReporter.onSynced()

            // auth.ok 后事件可能立刻到达；补拉期间先入内存缓冲，避免把并发新事件误判为缺口
            val events = Channel<WsClipEvent>(Channel.UNLIMITED)
            var closeCode: Int? = null
            val reader = launch {
                try {
                    while (true) {
                        val frame = try {
                            session.receive()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            SyncSocketFrame.Closed(null)
                        }
                        when (frame) {
                            is SyncSocketFrame.Text -> parseEvent(frame.text)?.let { events.send(it) }
                            is SyncSocketFrame.Closed -> {
                                closeCode = frame.code
                                break
                            }
                        }
                    }
                } finally {
                    events.close()
                }
            }

            when (gateway.pull()) {
                is PullResult.Success -> statusReporter.onSynced()
                is PullResult.Failure -> statusReporter.onOffline()
                PullResult.WaitingForPairing -> {
                    reader.cancel()
                    return@coroutineScope SessionOutcome.Unauthorized
                }
            }

            // 先消费缓冲再持续处理实时事件；事件应用在 SyncEngine 内与 REST 共用互斥锁
            for (event in events) {
                val result = gateway.applyEvent(event)
                // 只有真正应用成功的实时新建事件才允许触发 Desktop 自动粘贴；补拉历史不触发
                if (result is SyncEventResult.Applied && event.type == WsMessageType.CLIP_CREATED) {
                    realtimeCreatedFlow.tryEmit(event.data)
                }
            }
            reader.join()

            when (closeCode) {
                CLOSE_UNAUTHORIZED, CLOSE_DEVICE_REPLACED -> SessionOutcome.Unauthorized
                CLOSE_NORMAL -> SessionOutcome.Stop
                else -> SessionOutcome.Retry
            }
        }

    private suspend fun awaitAuth(session: SyncSocketSession): AuthOutcome {
        while (true) {
            val frame = try {
                session.receive()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                SyncSocketFrame.Closed(null)
            }
            when (frame) {
                is SyncSocketFrame.Text -> when (frameType(frame.text)) {
                    WsMessageType.AUTH_OK ->
                        return if (parseAuthOk(frame.text) != null) AuthOutcome.Ok else AuthOutcome.Error

                    WsMessageType.ERROR -> return AuthOutcome.Error
                    // 认证完成前不应收到其他帧；忽略未知类型，继续等待到超时
                    else -> Unit
                }

                is SyncSocketFrame.Closed ->
                    return when (frame.code) {
                        CLOSE_UNAUTHORIZED, CLOSE_DEVICE_REPLACED -> AuthOutcome.Error
                        else -> AuthOutcome.Closed(SessionOutcome.Retry)
                    }
            }
        }
    }

    private suspend fun delayBackoff() {
        val delayMillis = Backoff.delayMillis(retryCount.coerceAtLeast(1), jitterSample())
        logger?.log(LogEvent.Socket(event = "backoff", detail = "retry=$retryCount delayMs=$delayMillis"))
        delay(delayMillis)
    }

    private sealed interface AuthOutcome {
        data object Ok : AuthOutcome
        data object Error : AuthOutcome
        data class Closed(val outcome: SessionOutcome) : AuthOutcome
    }

    private enum class SessionOutcome { Unauthorized, Stop, Retry }

    private companion object {
        const val AUTH_TIMEOUT_MILLIS = 5_000L
        const val CLOSE_NORMAL = 1000
        const val CLOSE_UNAUTHORIZED = 4401
        const val CLOSE_DEVICE_REPLACED = 4001
    }
}

/** http → ws、https → wss，统一追加同步路径；不携带查询参数，令牌只走首帧 */
internal fun syncSocketUrl(serverUrl: String): String {
    val trimmed = serverUrl.trimEnd('/')
    val separator = trimmed.indexOf("://")
    if (separator <= 0) return trimmed
    val scheme = trimmed.substring(0, separator).lowercase()
    val wsScheme = if (scheme == "https") "wss" else "ws"
    return "$wsScheme://${trimmed.substring(separator + 3)}$SYNC_PATH"
}

private const val SYNC_PATH = "/api/v1/sync"

private fun frameType(text: String): String? = runCatching {
    ApiJson.parseToJsonElement(text).jsonObject["type"]?.jsonPrimitive?.content
}.getOrNull()

// 只接受三类记录事件；auth.ok / error 等控制帧在读取阶段已消费
private fun parseEvent(text: String): WsClipEvent? {
    val type = frameType(text) ?: return null
    if (type != WsMessageType.CLIP_CREATED &&
        type != WsMessageType.CLIP_UPDATED &&
        type != WsMessageType.CLIP_DELETED
    ) {
        return null
    }
    return runCatching { ApiJson.decodeFromString<WsClipEvent>(text) }.getOrNull()
}

// 认证帧反序列化只为校验结构；serverSeq 由随后的 REST 补拉对齐，不使用该值推进游标
private fun parseAuthOk(text: String): WsAuthOk? =
    runCatching { ApiJson.decodeFromString<WsAuthOk>(text) }.getOrNull()
