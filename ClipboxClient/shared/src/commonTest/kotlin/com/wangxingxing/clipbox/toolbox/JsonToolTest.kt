/**
 * author : 王星星
 * date : 2026-11-17 16:18
 * email : 1099420259@qq.com
 * description : JSON 工具纯逻辑测试：格式化/压缩/校验、键顺序、Unicode、行列定位与超大输入
 */
package com.wangxingxing.clipbox.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonToolTest {

    @Test
    fun formatUsesTwoSpacesAndKeepsKeyOrder() {
        val result = JsonTool.format("""{"b":1,"a":[true,null,"你好"],"empty":{},"list":[]}""")
        val success = assertIs<JsonTransformResult.Success>(result)
        assertEquals(
            """
            {
              "b": 1,
              "a": [
                true,
                null,
                "你好"
              ],
              "empty": {},
              "list": []
            }
            """.trimIndent(),
            success.output,
        )
    }

    @Test
    fun minifyRemovesInsignificantWhitespace() {
        val result = JsonTool.minify("{ \"a\" : 1, \"b\" : [ 2, 3 ] }")
        val success = assertIs<JsonTransformResult.Success>(result)
        assertEquals("""{"a":1,"b":[2,3]}""", success.output)
    }

    @Test
    fun nestedValuesAndEscapesSurvive() {
        val result = JsonTool.format("""{"emoji":"😀","quote":"a\"b","nested":{"k":[{"x":null}]}}""")
        val success = assertIs<JsonTransformResult.Success>(result)
        assertEquals(
            """
            {
              "emoji": "😀",
              "quote": "a\"b",
              "nested": {
                "k": [
                  {
                    "x": null
                  }
                ]
              }
            }
            """.trimIndent(),
            success.output,
        )
    }

    @Test
    fun missingRightBraceReportsLineAndColumn() {
        val result = JsonTool.minify("""{"a":1,"b":[2,3]""")
        val invalid = assertIs<JsonTransformResult.Invalid>(result)
        assertEquals(1, invalid.error.line)
        assertEquals(17, invalid.error.column)
        assertEquals(JsonErrorReason.MissingCommaOrBrace, invalid.error.reason)
    }

    @Test
    fun missingCommaReportsMultilinePosition() {
        val result = JsonTool.validate(
            """
            {
              "a": 1
              "b": 2
            }
            """.trimIndent(),
        )
        val error = assertIs<JsonSyntaxError>(result)
        assertEquals(3, error.line)
        assertEquals(4, error.column)
        assertEquals(JsonErrorReason.MissingCommaOrBrace, error.reason)
    }

    @Test
    fun missingColonReportsReason() {
        val error = JsonTool.validate("""{"a" 1}""")
        val syntaxError = assertIs<JsonSyntaxError>(error)
        assertEquals(1, syntaxError.line)
        assertEquals(6, syntaxError.column)
        assertEquals(JsonErrorReason.MissingColon, syntaxError.reason)
    }

    @Test
    fun emptyInputReportsEarlyEnd() {
        val error = JsonTool.validate("")
        val syntaxError = assertIs<JsonSyntaxError>(error)
        assertEquals(1, syntaxError.line)
        assertEquals(1, syntaxError.column)
        assertEquals(JsonErrorReason.EarlyEnd, syntaxError.reason)
    }

    @Test
    fun validateReturnsNullForValidJson() {
        assertNull(JsonTool.validate("""{"a":[1,2,3]}"""))
    }

    @Test
    fun largeInputStillFormats() {
        val input = (1..20_000).joinToString(prefix = "[", postfix = "]")
        val result = JsonTool.format(input)
        val success = assertIs<JsonTransformResult.Success>(result)
        assertTrue(success.output.length > input.length)
        assertTrue(success.output.startsWith("[\n  1,"))
    }
}
