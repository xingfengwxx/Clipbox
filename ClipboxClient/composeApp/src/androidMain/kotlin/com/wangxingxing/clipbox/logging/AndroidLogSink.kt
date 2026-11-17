/**
 * author : 王星星
 * date : 2026-11-17 16:20
 * email : 1099420259@qq.com
 * description : Android 日志输出，仅写入 Logcat；不落盘、不上传
 */
package com.wangxingxing.clipbox.logging

import android.util.Log

class AndroidLogSink : LogSink {

    override fun write(level: LogLevel, tag: String, message: String) {
        val priority = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, tag, message)
    }
}
