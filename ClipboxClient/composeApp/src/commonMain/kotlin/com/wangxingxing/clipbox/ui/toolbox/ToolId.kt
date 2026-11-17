/**
 * author : 王星星
 * date : 2026-11-17 16:20
 * email : 1099420259@qq.com
 * description : 工具箱五个本地工具的路由标识与三语名称；详情页只按本枚举取标题，未知 id 一律回退工具箱，避免出现第 6 个未定义工具
 */
package com.wangxingxing.clipbox.ui.toolbox

import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.tool_base64
import com.wangxingxing.clipbox.resources.tool_base64_desc
import com.wangxingxing.clipbox.resources.tool_json
import com.wangxingxing.clipbox.resources.tool_json_desc
import com.wangxingxing.clipbox.resources.tool_regex
import com.wangxingxing.clipbox.resources.tool_regex_desc
import com.wangxingxing.clipbox.resources.tool_time
import com.wangxingxing.clipbox.resources.tool_time_desc
import com.wangxingxing.clipbox.resources.tool_url
import com.wangxingxing.clipbox.resources.tool_url_desc
import org.jetbrains.compose.resources.StringResource

enum class ToolId(
    val routeSegment: String,
    val title: StringResource,
    val description: StringResource,
) {
    Json("json", Res.string.tool_json, Res.string.tool_json_desc),
    Base64("base64", Res.string.tool_base64, Res.string.tool_base64_desc),
    Url("url", Res.string.tool_url, Res.string.tool_url_desc),
    Time("time", Res.string.tool_time, Res.string.tool_time_desc),
    Regex("regex", Res.string.tool_regex, Res.string.tool_regex_desc),
    ;

    companion object {
        fun fromSegment(segment: String?): ToolId? =
            entries.firstOrNull { it.routeSegment == segment }
    }
}
