/**
 * author : 王星星
 * date : 2026-11-17 16:16
 * email : 1099420259@qq.com
 * description : JSON 工具的纯逻辑：格式化（2 空格）、压缩与校验；基于 kotlinx.serialization 解析，保留键顺序；
 *               注意：错误行列由解析异常的 offset 换算，跨平台依赖同一异常文案格式，无法取得 offset 时按输入末尾定位
 */
package com.wangxingxing.clipbox.toolbox

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class JsonErrorReason {
    EarlyEnd,
    MissingCommaOrBrace,
    MissingColon,
    MissingQuotation,
    UnexpectedToken,
}

data class JsonSyntaxError(
    val line: Int,
    val column: Int,
    val reason: JsonErrorReason,
)

sealed interface JsonTransformResult {
    data class Success(val output: String) : JsonTransformResult
    data class Invalid(val error: JsonSyntaxError) : JsonTransformResult
}

object JsonTool {

    private const val INDENT = "  "
    private val OffsetRegex = Regex("""offset (\d+)""")

    fun format(input: String): JsonTransformResult =
        parse(input).map { element -> render(element, pretty = true) }

    fun minify(input: String): JsonTransformResult =
        parse(input).map { element -> render(element, pretty = false) }

    /** 合法返回 null；非法返回行列与简短原因，调用方负责映射文案。 */
    fun validate(input: String): JsonSyntaxError? =
        when (val parsed = parse(input)) {
            is ParseOutcome.Parsed -> null
            is ParseOutcome.Failed -> parsed.error
        }

    private sealed interface ParseOutcome {
        data class Parsed(val element: JsonElement) : ParseOutcome
        data class Failed(val error: JsonSyntaxError) : ParseOutcome
    }

    private fun parse(input: String): ParseOutcome = try {
        ParseOutcome.Parsed(Json.parseToJsonElement(input))
    } catch (e: SerializationException) {
        ParseOutcome.Failed(syntaxError(input, e.message.orEmpty()))
    }

    private inline fun ParseOutcome.map(transform: (JsonElement) -> String): JsonTransformResult =
        when (this) {
            is ParseOutcome.Parsed -> JsonTransformResult.Success(transform(element))
            is ParseOutcome.Failed -> JsonTransformResult.Invalid(error)
        }

    private fun syntaxError(input: String, message: String): JsonSyntaxError {
        // 截断输入的异常不带 offset，按输入末尾定位更接近用户看到的位置
        val offset = OffsetRegex.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: input.length
        val (line, column) = lineAndColumn(input, offset)
        return JsonSyntaxError(line = line, column = column, reason = classify(message))
    }

    private fun lineAndColumn(input: String, offset: Int): Pair<Int, Int> {
        val end = offset.coerceIn(0, input.length)
        var line = 1
        var lineStart = 0
        for (index in 0 until end) {
            if (input[index] == '\n') {
                line++
                lineStart = index + 1
            }
        }
        return line to (end - lineStart + 1)
    }

    private fun classify(message: String): JsonErrorReason {
        val lower = message.lowercase()
        return when {
            "end of the input" in lower || "end of input" in lower -> JsonErrorReason.EarlyEnd
            "end of the object" in lower || "end of the array" in lower ||
                "closing brace" in lower || "closing bracket" in lower ||
                "expected '}'" in lower || "expected ']'" in lower -> JsonErrorReason.MissingCommaOrBrace
            "colon" in lower || "':'" in lower -> JsonErrorReason.MissingColon
            "quotation" in lower || "expected '\"'" in lower -> JsonErrorReason.MissingQuotation
            else -> JsonErrorReason.UnexpectedToken
        }
    }

    private fun render(element: JsonElement, pretty: Boolean): String =
        buildString { appendElement(element, pretty, depth = 0) }

    private fun StringBuilder.appendElement(element: JsonElement, pretty: Boolean, depth: Int) {
        when (element) {
            is JsonObject -> appendObject(element, pretty, depth)
            is JsonArray -> appendArray(element, pretty, depth)
            is JsonPrimitive -> append(element.toString())
        }
    }

    private fun StringBuilder.appendObject(obj: JsonObject, pretty: Boolean, depth: Int) {
        if (obj.isEmpty()) {
            append("{}")
            return
        }
        append('{')
        var index = 0
        obj.entries.forEach { (key, value) ->
            if (pretty) {
                append('\n')
                repeat(depth + 1) { append(INDENT) }
            }
            append(JsonPrimitive(key).toString())
            append(':')
            if (pretty) append(' ')
            appendElement(value, pretty, depth + 1)
            if (++index < obj.size) append(',')
        }
        if (pretty) {
            append('\n')
            repeat(depth) { append(INDENT) }
        }
        append('}')
    }

    private fun StringBuilder.appendArray(array: JsonArray, pretty: Boolean, depth: Int) {
        if (array.isEmpty()) {
            append("[]")
            return
        }
        append('[')
        var index = 0
        array.forEach { value ->
            if (pretty) {
                append('\n')
                repeat(depth + 1) { append(INDENT) }
            }
            appendElement(value, pretty, depth + 1)
            if (++index < array.size) append(',')
        }
        if (pretty) {
            append('\n')
            repeat(depth) { append(INDENT) }
        }
        append(']')
    }
}
