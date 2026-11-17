/**
 * author : 王星星
 * date : 2026-11-17 16:35
 * email : 1099420259@qq.com
 * description : 日志测试支撑；用内存 sink 收集 AppLogger 实际输出的每一行，供断言字段白名单与脱敏结果
 */
package com.wangxingxing.clipbox.logging

internal class RecordingSink : LogSink {

    data class Line(val level: LogLevel, val tag: String, val message: String)

    val lines = mutableListOf<Line>()

    val messages: List<String> get() = lines.map { it.message }

    val joined: String get() = messages.joinToString("\n")

    override fun write(level: LogLevel, tag: String, message: String) {
        lines += Line(level, tag, message)
    }
}
