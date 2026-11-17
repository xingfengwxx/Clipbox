/**
 * author : 王星星
 * date : 2026-11-17 16:31
 * email : 1099420259@qq.com
 * description : 正则匹配的纯逻辑：三个标志开关、匹配次数与起止区间、编号分组内容；
 *               只返回区间与分组值，不拼接或修改原文，高亮由 UI 按区间渲染。
 *               注意：命名分组不承诺跨平台一致，这里只暴露编号分组；空匹配按平台 Regex 语义计入次数
 */
package com.wangxingxing.clipbox.toolbox

sealed interface RegexResult {
    /** 正则语法错误，reason 取平台错误信息的首个非空行，可能为空 */
    data class Invalid(val reason: String) : RegexResult

    data object NoMatch : RegexResult

    data class Matches(val matches: List<RegexMatch>) : RegexResult
}

data class RegexMatch(
    /** 0 基、闭区间；空匹配满足 range.isEmpty() */
    val range: IntRange,
    val value: String,
    /** 只含编号分组，index 从 1 开始；未参与匹配的分组 range/value 为 null */
    val groups: List<RegexGroup>,
)

data class RegexGroup(
    val index: Int,
    val range: IntRange?,
    val value: String?,
)

object RegexTool {
    fun search(
        pattern: String,
        text: String,
        ignoreCase: Boolean = false,
        multiline: Boolean = false,
        dotMatchesAll: Boolean = false,
    ): RegexResult {
        val options = buildSet {
            if (ignoreCase) add(RegexOption.IGNORE_CASE)
            if (multiline) add(RegexOption.MULTILINE)
            if (dotMatchesAll) add(RegexOption.DOT_MATCHES_ALL)
        }
        val regex = try {
            Regex(pattern, options)
        } catch (error: IllegalArgumentException) {
            return RegexResult.Invalid(reasonOf(error))
        }
        val matches = regex.findAll(text).map { match ->
            RegexMatch(
                range = match.range,
                value = match.value,
                groups = match.groups.drop(1).mapIndexed { index, group ->
                    RegexGroup(
                        index = index + 1,
                        range = group?.range,
                        value = group?.value,
                    )
                },
            )
        }.toList()
        return if (matches.isEmpty()) RegexResult.NoMatch else RegexResult.Matches(matches)
    }

    private fun reasonOf(error: IllegalArgumentException): String =
        error.message
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(160)
            .orEmpty()
}
