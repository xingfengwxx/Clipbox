/**
 * author : 王星星
 * date : 2026-11-17 09:56
 * email : 1099420259@qq.com
 * description : iOS 平台设备信息；只提供配对页默认设备名，设备令牌与设备 ID 由设置仓库在配对成功后保存，不在此持有
 */
package com.wangxingxing.clipbox

import platform.UIKit.UIDevice

internal object IosDeviceInfo {

    // UIDevice.model 只返回 iPhone / iPad 家族名，与 Android Build.MODEL 的粒度一致，足以让用户区分设备
    val defaultDeviceName: String = "iOS · ${UIDevice.currentDevice.model}"
}
