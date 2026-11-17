/**
 * author : 王星星
 * date : 2026-11-17 16:21
 * email : 1099420259@qq.com
 * description : Desktop 日志输出，仅写入 stdout；不落盘、不上传
 */
package com.wangxingxing.clipbox.logging

class DesktopLogSink : LogSink {

    override fun write(level: LogLevel, tag: String, message: String) {
        System.out.println("${level.name}/$tag: $message")
    }
}
