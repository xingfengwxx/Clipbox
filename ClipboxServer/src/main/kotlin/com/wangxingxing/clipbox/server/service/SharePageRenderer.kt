/**
 * author : 王星星
 * date : 2026-11-17 13:20
 * email : 1099420259@qq.com
 * description : 分享页 HTML 渲染与严格转义；页面只含快照正文、到期提示与页脚，不加载脚本、样式表或任何外部资源。
 *               注意：正文必须先转义再插入模板，错误页不得包含正文或任何令牌片段。
 */
package com.wangxingxing.clipbox.server.service

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val SHARE_PAGE_CSP =
    "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

object SharePageRenderer {

    private const val NOT_FOUND_MESSAGE = "链接无效或已被删除"
    private const val EXPIRED_MESSAGE = "链接已过期"
    private const val CONSUMED_MESSAGE = "链接已被使用"

    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun renderPage(content: String, expiresAt: Long, oneTime: Boolean): String = buildPage(
        title = "字匣分享",
        body = """
            <main>
              <p class="meta">${if (oneTime) "一次性链接，打开后即失效" else "只读分享"}</p>
              <pre class="content">${escapeHtml(content)}</pre>
              <p class="meta">此链接将在 ${formatTime(expiresAt)} 过期</p>
            </main>
        """.trimIndent(),
    )

    fun renderNotFound(): String = renderError(NOT_FOUND_MESSAGE)
    fun renderExpired(): String = renderError(EXPIRED_MESSAGE)
    fun renderConsumed(): String = renderError(CONSUMED_MESSAGE)

    private fun renderError(message: String): String = buildPage(
        title = "字匣分享",
        body = """<main><p class="error">${escapeHtml(message)}</p></main>""",
    )

    private fun buildPage(title: String, body: String): String = """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <meta name="robots" content="noindex, nofollow">
          <title>${escapeHtml(title)}</title>
          <style>
            :root { color-scheme: light dark; }
            body { margin: 0; background: #f3f0e8; color: #191712; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
            main { max-width: 680px; margin: 0 auto; padding: 32px 20px 48px; }
            .content { white-space: pre-wrap; overflow-wrap: anywhere; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 15px; line-height: 1.6; background: #fbf9f3; border: 1px solid #d0caba; border-radius: 8px; padding: 20px; margin: 20px 0; }
            .meta { color: #6b6455; font-size: 12px; }
            .error { color: #a03024; font-size: 16px; }
            footer { max-width: 680px; margin: 0 auto; padding: 0 20px 32px; color: #6b6455; font-size: 12px; }
            @media (prefers-color-scheme: dark) {
              body { background: #17150f; color: #ede9dc; }
              .content { background: #1e1b14; border-color: #3a352b; }
              .meta, footer { color: #9a9384; }
              .error { color: #f08a75; }
            }
          </style>
        </head>
        <body>
          $body
          <footer>由字匣分享 · 只读页面</footer>
        </body>
        </html>
    """.trimIndent()

    private fun formatTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormatter)

    private fun escapeHtml(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
}
