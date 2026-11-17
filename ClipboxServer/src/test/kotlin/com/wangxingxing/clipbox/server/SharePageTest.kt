/**
 * author : 王星星
 * date : 2026-11-17 13:26
 * email : 1099420259@qq.com
 * description : 分享页访问契约测试：HTML 转义、安全响应头、非一次性重复访问、一次性原子消费与并发、过期/已用/非法状态码。
 *               注意：页面正文与错误页均不得出现脚本、外部资源或令牌片段；一次性并发只允许一个请求拿到正文。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.ShareRepository
import com.wangxingxing.clipbox.server.db.ShareRow
import com.wangxingxing.clipbox.server.db.SharesTable
import com.wangxingxing.clipbox.server.service.SHARE_PAGE_CSP
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir

class SharePageTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `regular share page renders escaped content with security headers and is repeatable`() =
        withApp { factory, client ->
            factory.insertShare(token = TOKEN, oneTime = false, expiresAt = future())

            val first = client.get("/s/$TOKEN")
            assertEquals(HttpStatusCode.OK, first.status)
            assertHtmlHeaders(first)
            val html = first.bodyAsText()
            assertTrue(html.contains(CONTENT), "页面必须展示快照正文")
            assertTrue(html.contains("只读分享"), "非一次性页面必须标注只读分享")
            assertTrue(html.contains("noindex"), "分享页必须禁止索引")

            val second = client.get("/s/$TOKEN")
            assertEquals(HttpStatusCode.OK, second.status, "非一次性分享可重复访问")
            val row = factory.readTransaction {
                shareRows().single()
            }
            assertNull(row.openedAt, "非一次性访问不得写入 opened_at")
        }

    @Test
    fun `html content is strictly escaped and external payloads never render as markup`() =
        withApp { factory, client ->
            val payload = """<script>alert('x')</script><img src="http://evil.test/x" onerror="alert(1)">&"""
            factory.insertShare(token = TOKEN, oneTime = false, expiresAt = future(), content = payload)

            val response = client.get("/s/$TOKEN")
            assertEquals(HttpStatusCode.OK, response.status)
            val html = response.bodyAsText()
            assertFalse(html.contains("<script"), "脚本标签必须被转义")
            assertFalse(html.contains("<img"), "图片标签必须被转义")
            assertFalse(html.contains("onerror=\"alert"), "事件属性必须随标签一起转义")
            assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"), "转义结果必须完整保留原文")
            assertTrue(html.contains("&amp;"), "& 必须转义")
        }

    @Test
    fun `one time share is served once and consumed afterwards`() = withApp { factory, client ->
        factory.insertShare(token = TOKEN, oneTime = true, expiresAt = future())

        val first = client.get("/s/$TOKEN")
        assertEquals(HttpStatusCode.OK, first.status)
        assertTrue(first.bodyAsText().contains(CONTENT))
        assertTrue(first.bodyAsText().contains("一次性链接"))

        val second = client.get("/s/$TOKEN")
        assertEquals(HttpStatusCode.Gone, second.status)
        assertHtmlHeaders(second)
        val html = second.bodyAsText()
        assertTrue(html.contains("链接已被使用"))
        assertFalse(html.contains(CONTENT), "已用页面不得泄露正文")

        assertTrue(factory.readTransaction { shareRows().single().openedAt } != null)
    }

    @Test
    fun `concurrent one time access yields exactly one page and one consumed response`() =
        withApp { factory, client ->
            factory.insertShare(token = TOKEN, oneTime = true, expiresAt = future())

            val statuses = coroutineScope {
                val results = List(4) { async { client.get("/s/$TOKEN").status } }
                results.map { it.await() }
            }

            assertEquals(1, statuses.count { it == HttpStatusCode.OK }, "并发一次性访问只能有一个 200")
            assertEquals(3, statuses.count { it == HttpStatusCode.Gone }, "其余请求必须返回 410")
        }

    @Test
    fun `expired share returns gone without content`() = withApp { factory, client ->
        factory.insertShare(token = TOKEN, oneTime = false, expiresAt = PAST)

        val response = client.get("/s/$TOKEN")
        assertEquals(HttpStatusCode.Gone, response.status)
        assertHtmlHeaders(response)
        val html = response.bodyAsText()
        assertTrue(html.contains("链接已过期"))
        assertFalse(html.contains(CONTENT), "过期页面不得泄露正文")
    }

    @Test
    fun `already opened one time share stays consumed even after expiry`() = withApp { factory, client ->
        factory.insertShare(token = TOKEN, oneTime = true, expiresAt = PAST, openedAt = PAST - 1_000)

        val response = client.get("/s/$TOKEN")
        assertEquals(HttpStatusCode.Gone, response.status)
        assertTrue(response.bodyAsText().contains("链接已被使用"), "已消费状态优先于过期提示")
    }

    @Test
    fun `unknown and malformed tokens return not found without leaking anything`() = withApp { _, client ->
        val unknown = client.get("/s/$TOKEN")
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertHtmlHeaders(unknown)
        assertTrue(unknown.bodyAsText().contains("链接无效或已被删除"))

        listOf("short", "!".repeat(43), "A".repeat(44)).forEach { malformed ->
            val response = client.get("/s/$malformed")
            assertEquals(HttpStatusCode.NotFound, response.status, malformed)
            assertFalse(response.bodyAsText().contains(CONTENT), malformed)
        }
    }

    // --- 夹具 ---

    private fun assertHtmlHeaders(response: HttpResponse) {
        assertEquals(
            ContentType.Text.Html.withCharset(Charsets.UTF_8),
            response.contentType(),
            "分享页必须声明 text/html; charset=utf-8",
        )
        assertEquals(SHARE_PAGE_CSP, response.headers["Content-Security-Policy"], "CSP 必须逐响应追加")
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("no-referrer", response.headers["Referrer-Policy"])
        assertEquals("no-store", response.headers["Cache-Control"])
    }

    private fun shareRows() = SharesTable.selectAll().map { row ->
        ShareRow(
            tokenHash = row[SharesTable.tokenHash],
            clipId = row[SharesTable.clipId],
            content = row[SharesTable.content],
            createdAt = row[SharesTable.createdAt],
            expiresAt = row[SharesTable.expiresAt],
            oneTime = row[SharesTable.oneTime] != 0,
            openedAt = row[SharesTable.openedAt],
        )
    }

    private suspend fun DatabaseFactory.insertShare(
        token: String,
        oneTime: Boolean,
        expiresAt: Long,
        openedAt: Long? = null,
        content: String = CONTENT,
    ) = writeTransaction {
        ShareRepository.insert(
            ShareRow(
                tokenHash = Hashing.sha256Hex(token),
                clipId = CLIP_ID,
                content = content,
                createdAt = CREATED_AT,
                expiresAt = expiresAt,
                oneTime = oneTime,
                openedAt = openedAt,
            ),
        )
    }

    private fun withApp(block: suspend (DatabaseFactory, HttpClient) -> Unit) = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        application { module(testConfig(), factory) }
        block(factory, client)
    }

    private fun testConfig() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        adminToken = "a".repeat(32),
        publicBaseUrl = "http://127.0.0.1:8080",
    )

    companion object {
        private const val CLIP_ID = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
        private const val CREATED_AT = 1_730_000_000_000L
        private const val PAST = 1_000L
        private const val CONTENT = "hello share page"
        private val TOKEN = "t".repeat(43)

        // 服务端按真实时钟判断过期，未来时间必须相对当前时刻计算，不能用固定时间戳。
        private fun future() = System.currentTimeMillis() + 86_400_000L
    }
}
