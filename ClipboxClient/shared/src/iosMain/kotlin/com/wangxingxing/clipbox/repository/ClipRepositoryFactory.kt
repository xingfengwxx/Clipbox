/**
 * author : 王星星
 * date : 2026-11-16 18:20
 * email : 1099420259@qq.com
 * description : iOS 端记录仓库工厂；把 Room 构建细节留在 shared，composeApp 不直接依赖 Room 类型
 */
package com.wangxingxing.clipbox.repository

import com.wangxingxing.clipbox.db.buildClipboxDatabase
import com.wangxingxing.clipbox.db.createClipboxDatabaseBuilder
import com.wangxingxing.clipbox.settings.SettingsRepository

fun createClipRepository(settingsRepository: SettingsRepository): ClipRepository = ClipRepository(
    database = buildClipboxDatabase(createClipboxDatabaseBuilder()),
    settingsRepository = settingsRepository,
)
