/**
 * author : 王星星
 * date : 2026-11-17 16:22
 * email : 1099420259@qq.com
 * description : iOS 日志输出，仅写入系统日志（NSLog）；不落盘、不上传。
 *               注意：NSLog 的可变参数不会自动把 Kotlin String 桥成 NSString，必须显式构造 NSString，
 *                     否则服务端不可用时的第一条网络日志会因格式化参数非法而崩溃
 */
package com.wangxingxing.clipbox.logging

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.create

class IosLogSink : LogSink {

    @OptIn(ExperimentalForeignApi::class)
    override fun write(level: LogLevel, tag: String, message: String) {
        val text = NSString.create(string = "${level.name}/$tag: $message")
        NSLog("%@", text)
    }
}
