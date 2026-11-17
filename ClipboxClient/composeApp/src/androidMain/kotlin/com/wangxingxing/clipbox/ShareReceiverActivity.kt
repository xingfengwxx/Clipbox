/**
 * author : 王星星
 * date : 2026-11-17 09:10
 * email : 1099420259@qq.com
 * description : Android 系统分享与文本选中菜单的透明入口；只读取 text/plain 正文、写入 DataStore 草稿后转交 MainActivity，不展示界面。注意：ACTION_PROCESS_TEXT 必须立即回传原文，避免替换用户选中的文本；正文不得写入日志。
 */
package com.wangxingxing.clipbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action
        val text = extractSharedText(intent)
        // 草稿写完再启动主界面，避免 MainActivity 读到旧值；写入失败只影响预填，不阻断打开
        lifecycleScope.launch {
            if (!text.isNullOrEmpty()) {
                ShareDraftStore.write(applicationContext, text)
            }
            if (action == Intent.ACTION_PROCESS_TEXT) {
                setResult(
                    if (text.isNullOrEmpty()) RESULT_CANCELED else RESULT_OK,
                    if (text.isNullOrEmpty()) {
                        null
                    } else {
                        Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                    },
                )
            }
            startActivity(
                Intent(this@ShareReceiverActivity, MainActivity::class.java).apply {
                    // 复用已打开的主界面，让后续分享通过 onNewIntent 更新草稿
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                },
            )
            finish()
        }
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent == null) return null
        val raw = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)

            else -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        }
        return raw?.toString()?.takeIf { it.isNotEmpty() }
    }
}
