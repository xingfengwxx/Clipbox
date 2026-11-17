/**
 * author : 王星星
 * date : 2026-11-16 11:17
 * email : 1099420259@qq.com
 * description : Android 宿主 Activity；装载共享根 Composable，并在启动或收到新分享时把 DataStore 草稿交给历史页预填
 */
package com.wangxingxing.clipbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingShareDraft = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(
                incomingShareDraft = pendingShareDraft.value,
                onShareDraftConsumed = ::consumeShareDraft,
            )
        }
        // 只在本 Activity 全新创建时读取草稿；旋转等重建由历史页 rememberSaveable 恢复当前输入
        if (savedInstanceState == null) {
            loadShareDraft()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadShareDraft()
    }

    private fun loadShareDraft() {
        lifecycleScope.launch {
            pendingShareDraft.value = ShareDraftStore.read(applicationContext)
        }
    }

    private fun consumeShareDraft() {
        pendingShareDraft.value = null
        lifecycleScope.launch {
            ShareDraftStore.clear(applicationContext)
        }
    }
}
