package com.starrail.agent.core.util

import org.junit.Assert.*
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun testPlainText() {
        val segments = MarkdownParser.parse("你好世界")
        assertEquals(1, segments.size)
        assertEquals("你好世界", segments[0].text)
        assertEquals(MarkdownParser.Style.NORMAL, segments[0].style)
    }

    @Test
    fun testBoldText() {
        val segments = MarkdownParser.parseInline("这是**粗体**文字")
        assertEquals(3, segments.size)
        assertEquals("这是", segments[0].text)
        assertEquals(MarkdownParser.Style.NORMAL, segments[0].style)
        assertEquals("粗体", segments[1].text)
        assertEquals(MarkdownParser.Style.BOLD, segments[1].style)
        assertEquals("文字", segments[2].text)
    }

    @Test
    fun testItalicText() {
        val segments = MarkdownParser.parseInline("这是*斜体*文字")
        assertEquals(3, segments.size)
        assertEquals("斜体", segments[1].text)
        assertEquals(MarkdownParser.Style.ITALIC, segments[1].style)
    }

    @Test
    fun testInlineCode() {
        val segments = MarkdownParser.parseInline("使用`code()`函数")
        assertEquals(3, segments.size)
        assertEquals("code()", segments[1].text)
        assertEquals(MarkdownParser.Style.CODE, segments[1].style)
    }

    @Test
    fun testHeading1() {
        val segments = MarkdownParser.parse("# 标题一")
        assertEquals(1, segments.size)
        assertEquals("标题一", segments[0].text)
        assertEquals(MarkdownParser.Style.HEADING1, segments[0].style)
        assertEquals(19f, segments[0].fontSize, 0f)
    }

    @Test
    fun testHeading2() {
        val segments = MarkdownParser.parse("## 标题二")
        assertEquals(1, segments.size)
        assertEquals("标题二", segments[0].text)
        assertEquals(MarkdownParser.Style.HEADING2, segments[0].style)
    }

    @Test
    fun testHeading3() {
        val segments = MarkdownParser.parse("### 标题三")
        assertEquals(1, segments.size)
        assertEquals("标题三", segments[0].text)
        assertEquals(MarkdownParser.Style.HEADING3, segments[0].style)
    }

    @Test
    fun testHorizontalRule() {
        val segments = MarkdownParser.parse("---")
        assertEquals(1, segments.size)
        assertEquals(30, segments[0].text.length)
    }

    @Test
    fun testUnorderedList() {
        val segments = MarkdownParser.parse("- 列表项")
        assertEquals(2, segments.size)
        assertEquals("• ", segments[0].text)
        assertEquals(MarkdownParser.Style.BULLET, segments[0].style)
        assertEquals("列表项", segments[1].text)
    }

    @Test
    fun testOrderedList() {
        val segments = MarkdownParser.parse("1. 第一步")
        assertEquals(2, segments.size)
        assertTrue(segments[0].text.startsWith("1."))
        assertEquals(MarkdownParser.Style.ORDERED, segments[0].style)
        assertEquals("第一步", segments[1].text)
    }

    @Test
    fun testMultiLine() {
        val text = "# 标题\n\n正文内容"
        val segments = MarkdownParser.parse(text)
        assertTrue(segments.size >= 3)
        assertEquals("标题", segments[0].text)
        assertEquals(MarkdownParser.Style.HEADING1, segments[0].style)
    }

    @Test
    fun testBoldWithinList() {
        val segments = MarkdownParser.parse("- **粗体项**")
        assertTrue(segments.any { it.style == MarkdownParser.Style.BOLD })
        assertTrue(segments.any { it.text == "粗体项" })
    }

    @Test
    fun testHasCodeBlock() {
        assertTrue(MarkdownParser.hasCodeBlock("```kotlin\nval x = 1\n```"))
        assertFalse(MarkdownParser.hasCodeBlock("普通文本"))
    }

    @Test
    fun testExtractCodeBlocks() {
        val blocks = MarkdownParser.extractCodeBlocks("```\nhello\n```")
        assertEquals(1, blocks.size)
        assertEquals("hello", blocks[0].code)
    }

    @Test
    fun testExtractCodeBlocks_withLanguage() {
        val blocks = MarkdownParser.extractCodeBlocks("```kotlin\nfun main() {}\n```")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].isKotlin)
    }

    @Test
    fun testEmptyText() {
        val segments = MarkdownParser.parse("")
        assertTrue(segments.isEmpty())
    }

    @Test
    fun testNestedBoldInInline() {
        val segments = MarkdownParser.parseInline("**粗体**和`代码`混合")
        assertEquals(4, segments.size)
        assertEquals("粗体", segments[0].text)
        assertEquals(MarkdownParser.Style.BOLD, segments[0].style)
        assertEquals("和", segments[1].text)
        assertEquals("代码", segments[2].text)
        assertEquals(MarkdownParser.Style.CODE, segments[2].style)
        assertEquals("混合", segments[3].text)
    }

    @Test
    fun testUnclosedBold() {
        val segments = MarkdownParser.parseInline("**未关闭")
        assertEquals(1, segments.size)
        assertEquals("**未关闭", segments[0].text)  // 原样输出
    }

    @Test
    fun testMultipleLines() {
        val text = "行1\n行2\n行3"
        val segments = MarkdownParser.parse(text)
        assertTrue(segments.size >= 5) // 行1 \n 行2 \n 行3
        assertEquals("行1", segments[0].text)
        assertEquals("\n", segments[1].text)
        assertEquals("行2", segments[2].text)
    }
}