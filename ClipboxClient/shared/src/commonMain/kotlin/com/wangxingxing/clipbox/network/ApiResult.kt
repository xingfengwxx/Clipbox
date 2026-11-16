/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : API 调用结果封装；失败统一携带 ApiFailure，调用方按类型分支，不以 null 表示失败
 */
package com.wangxingxing.clipbox.network

import com.wangxingxing.clipbox.model.ApiFailure

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val failure: ApiFailure) : ApiResult<Nothing>
}
