/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : 剪贴记录的本地同步状态；枚举名直接作为 Room syncState 列的持久化值，改名等同数据库结构变更
 */
package com.wangxingxing.clipbox.model

enum class SyncState {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    FAILED_CREATE,
    FAILED_UPDATE,
    FAILED_DELETE,
}
