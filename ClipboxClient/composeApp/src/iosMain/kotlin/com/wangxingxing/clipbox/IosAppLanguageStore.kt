/**
 * author : 王星星
 * date : 2026-11-17 21:30
 * email : 1099420259@qq.com
 * description : 把主 App 当前语言镜像到 App Group，供分享扩展读取；只写语言标签，不写正文或令牌。
 *               注意：键名与值域必须与 ShareViewController 的读取逻辑保持一致，未设置时扩展回退简体中文
 */
package com.wangxingxing.clipbox

import platform.Foundation.NSUserDefaults

internal object IosAppLanguageStore {

    private const val APP_GROUP_ID = "group.com.wangxingxing.clipbox"
    private const val KEY_APP_LANGUAGE = "appLanguage"

    fun write(tag: String) {
        NSUserDefaults(suiteName = APP_GROUP_ID)?.setObject(tag, forKey = KEY_APP_LANGUAGE)
    }
}
