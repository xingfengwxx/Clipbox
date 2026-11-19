/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : 剪贴正文的 UTF-8 字节计数；64 KiB 限额按字节判断，不能用字符数或 String.length 近似
 */
package com.wangxingxing.clipbox.util

object TextSize {
    const val MAX_CLIP_UTF8_BYTES: Int = 65_536

    /** 90% 预警线向上取整，与 UI 预警共用同一口径，避免发送器自行计算阈值 */
    const val WARN_CLIP_UTF8_BYTES: Int = 58_983

    fun utf8ByteCount(text: String): Int = text.encodeToByteArray().size

    /** 千分位展示，设计稿里的字节计数写作 65,536。 */
    fun formatByteCount(bytes: Int): String = bytes.toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()

    /** 安装包体积按 1024 进制展示；MB 保留一位小数，避免大文件只剩整数位 */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kilo = 1024L
        val mega = kilo * 1024
        return when {
            bytes >= mega -> {
                val tenths = bytes * 10 / mega
                if (tenths % 10 == 0L) "${tenths / 10} MB" else "${tenths / 10}.${tenths % 10} MB"
            }

            bytes >= kilo -> "${bytes / kilo} KB"
            else -> "$bytes B"
        }
    }
}
