/**
 * author : 王星星
 * date : 2026-11-17 15:08
 * email : 1099420259@qq.com
 * description : 剪贴板回环与稳定窗口纯逻辑；远端写入后 2 秒内监听命中 lastAppliedHash 才跳过上传，lastSentHash 只记录不参与跳过。
 *               注意：哈希必须跨平台稳定，不能使用 String.hashCode 或随机盐
 */
package com.wangxingxing.clipbox.clipboard

object ClipboardHash {
    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L

    fun of(text: String): Long {
        var hash = FNV_OFFSET_BASIS
        for (byte in text.encodeToByteArray()) {
            hash = hash xor (byte.toLong() and 0xFF)
            hash *= FNV_PRIME
        }
        return hash
    }
}

class ClipboardGuard(
    private val windowMillis: Long = 2_000L,
) {

    private var lastAppliedHash: Long? = null
    private var lastAppliedAt: Long = 0
    private var lastSentHash: Long? = null

    fun onRemoteApplied(text: String, now: Long) {
        lastAppliedHash = ClipboardHash.of(text)
        lastAppliedAt = now
    }

    fun onSent(text: String) {
        lastSentHash = ClipboardHash.of(text)
    }

    /**
     * 只按需求使用 lastAppliedHash + 时间窗跳过；lastSentHash 保留用于诊断，
     * 不能因为本机发过同一内容就吞掉用户重新复制的内容。
     */
    fun shouldSkipUpload(text: String, now: Long): Boolean {
        val hash = ClipboardHash.of(text)
        val appliedHash = lastAppliedHash ?: return false
        val elapsed = now - lastAppliedAt
        return hash == appliedHash && elapsed in 0..windowMillis
    }

    fun reset() {
        lastAppliedHash = null
        lastAppliedAt = 0
        lastSentHash = null
    }
}

/**
 * 800ms 稳定窗口；新内容会重置截止时间，只有窗口结束后仍为最后一条内容才允许上传。
 */
class ClipboardDebouncer(
    private val windowMillis: Long = 800L,
) {

    private var pending: String? = null
    private var deadline: Long = 0

    fun offer(text: String, now: Long) {
        pending = text
        deadline = now + windowMillis
    }

    /** 未到截止时间返回 null；返回文本表示可以发送，并清空待处理内容 */
    fun poll(now: Long): String? {
        val text = pending ?: return null
        if (now < deadline) return null
        pending = null
        return text
    }

    fun reset() {
        pending = null
        deadline = 0
    }
}
