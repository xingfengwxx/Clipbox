/**
 * author : 王星星
 * date : 2026-11-17 16:22
 * email : 1099420259@qq.com
 * description : iOS 日志输出，仅写入系统日志（NSLog）；不落盘、不上传。
 *               注意：Kotlin/Native 无法直接调用可变参数 os_log，改用 NSLog 保持同一系统日志通道
 */
package com.wangxingxing.clipbox.logging

import platform.Foundation.NSLog

class IosLogSink : LogSink {

    override fun write(level: LogLevel, tag: String, message: String) {
        NSLog("%@", "${level.name}/$tag: $message")
    }
}
