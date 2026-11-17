/**
 * author : 王星星
 * date : 2026-11-17 13:53
 * email : 1099420259@qq.com
 * description : iOS 分享草稿存储；读取分享扩展写入 App Group 容器的 share-draft.json，解析 id / text / createdAt 供主 App 预填，用户确认发送后删除。
 *               注意：正文上限 64 KiB，只在 openURL 与回前台时同步读取一次，不做轮询；容器不可用或解析失败返回 null，不影响启动。
 */
package com.wangxingxing.clipbox

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSNumber

internal data class ShareDraft(
    val id: String,
    val text: String,
    val createdAt: Long,
)

@OptIn(ExperimentalForeignApi::class)
internal class ShareDraftStore {

    fun read(): ShareDraft? {
        val path = draftPath() ?: return null
        val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return null
        val payload = NSJSONSerialization.JSONObjectWithData(data, options = 0u, error = null) as? Map<*, *>
            ?: return null
        val id = payload["id"] as? String
        val text = payload["text"] as? String
        val createdAt = (payload["createdAt"] as? NSNumber)?.longLongValue
        if (id.isNullOrBlank() || text == null || createdAt == null) return null
        return ShareDraft(id = id, text = text, createdAt = createdAt)
    }

    fun clear() {
        val path = draftPath() ?: return
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    private fun draftPath(): String? =
        NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID)
            ?.URLByAppendingPathComponent(DRAFT_FILE_NAME)
            ?.path

    private companion object {
        // 与分享扩展的 entitlements、ShareViewController 中的 group 标识保持一致
        const val APP_GROUP_ID = "group.com.wangxingxing.clipbox"
        const val DRAFT_FILE_NAME = "share-draft.json"
    }
}
