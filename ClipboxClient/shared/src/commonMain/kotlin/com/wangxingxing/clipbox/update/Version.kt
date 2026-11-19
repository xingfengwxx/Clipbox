/**
 * author : 王星星
 * date : 2026-11-19 11:08
 * email : 1099420259@qq.com
 * description : 纯数字点分版本比较与新版本判定；非法版本返回不可比较，不抛异常，避免异常版本号打断更新检查
 */
package com.wangxingxing.clipbox.update

private val NUMERIC_VERSION = Regex("^\\d+\\.\\d+\\.\\d+$")

/**
 * 逐段按数值比较 MAJOR.MINOR.PATCH；返回负数 / 0 / 正数。
 * 任一方不是纯数字点分、或任一段超出 Long 范围时返回 null，表示不可比较。
 */
fun compareVersions(left: String, right: String): Int? {
    if (!NUMERIC_VERSION.matches(left) || !NUMERIC_VERSION.matches(right)) return null
    val leftParts = left.split('.').map { it.toLongOrNull() ?: return null }
    val rightParts = right.split('.').map { it.toLongOrNull() ?: return null }
    for (index in 0..2) {
        val order = leftParts[index].compareTo(rightParts[index])
        if (order != 0) return order
    }
    return 0
}

fun checkForUpdate(currentVersion: String, descriptor: UpdateDescriptor): UpdateCheckResult {
    val order = compareVersions(descriptor.version, currentVersion)
        ?: return UpdateCheckResult.Incomparable(descriptor)
    return if (order > 0) {
        UpdateCheckResult.Available(descriptor)
    } else {
        UpdateCheckResult.Latest(descriptor)
    }
}
