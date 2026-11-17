/**
 * author : 王星星
 * date : 2026-11-17 09:10
 * email : 1099420259@qq.com
 * description : Android 分享草稿 DataStore 存储；透明接收页写入、主界面读取，发送成功后清除。注意：同一进程必须复用 preferencesDataStore 单实例，正文不得写入日志。
 */
package com.wangxingxing.clipbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private const val SHARE_DRAFT_STORE_NAME = "clipbox_share_draft"
private val SHARE_DRAFT_KEY = stringPreferencesKey("text")

// 委托按属性在进程内缓存 DataStore 实例，两个 Activity 共用同一文件；统一取 applicationContext 避免持有页面
private val Context.shareDraftDataStore: DataStore<Preferences> by
    preferencesDataStore(name = SHARE_DRAFT_STORE_NAME)

internal object ShareDraftStore {

    suspend fun read(context: Context): String? =
        context.applicationContext.shareDraftDataStore.data.first()[SHARE_DRAFT_KEY]

    suspend fun write(context: Context, text: String) {
        context.applicationContext.shareDraftDataStore.edit { it[SHARE_DRAFT_KEY] = text }
    }

    suspend fun clear(context: Context) {
        context.applicationContext.shareDraftDataStore.edit { it.remove(SHARE_DRAFT_KEY) }
    }
}
