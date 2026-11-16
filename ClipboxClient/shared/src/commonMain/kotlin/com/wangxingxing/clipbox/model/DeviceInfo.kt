/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : 本机设备身份模型；token 为设备令牌，只允许保存在本机 DataStore，禁止写入日志或界面
 */
package com.wangxingxing.clipbox.model

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val token: String,
)
