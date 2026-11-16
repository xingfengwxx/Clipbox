/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : 分享页信息模型；url 必须原样使用服务端返回值，客户端不拼接分享链接
 */
package com.wangxingxing.clipbox.model

data class ShareInfo(
    val url: String,
    val expiresAt: Long,
    val oneTime: Boolean,
)
