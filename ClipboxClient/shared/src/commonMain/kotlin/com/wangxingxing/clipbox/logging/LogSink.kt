/**
 * author : 王星星
 * date : 2026-11-17 16:05
 * email : 1099420259@qq.com
 * description : 日志输出抽象；平台实现只负责写入本机系统日志，不落盘、不上传。注意：禁止在业务代码中绕过 AppLogger 直接输出
 */
package com.wangxingxing.clipbox.logging

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

fun interface LogSink {
    fun write(level: LogLevel, tag: String, message: String)
}
