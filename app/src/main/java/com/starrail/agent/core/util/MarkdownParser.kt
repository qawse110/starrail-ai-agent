package com.starrail.agent.core.util

/**
 * Markdown 解析器
 * 将 Markdown 文本解析为样式化片段列表，不依赖 Compose UI 框架
 */
object MarkdownParser {

    /** 样式化文本片段 */
    data class Segment(
        val text: String,
        val style: Style = Style.NORMAL,
        val fontSize: Float = 0f  // 0表示使用默认字号
    )

    /** 样式枚举 */
    enum class Style {
        NORMAL, BOLD, ITALIC, CODE, HEADING1, HEADING2, HEADING3, BULLET, ORDERED
    }

    /** 解析完整 Markdown 文本，返回片段列表 */
    fun parse(text: String): List<Segment> {
        val result = mutableListOf<Segment>()
        val lines = text.split("\n")
        for ((lineIdx, line) in lines.withIndex()) {
            if (lineIdx > 0) result.add(Segment("\n", Style.NORMAL))
            
            when {
                line.startsWith("### ") -> {
                    result.add(Segment(line.removePrefix("### "), Style.HEADING3, 16f))
                }
                line.startsWith("## ") -> {
                    result.add(Segment(line.removePrefix("## "), Style.HEADING2, 17f))
                }
                line.startsWith("# ") -> {
                    result.add(Segment(line.removePrefix("# "), Style.HEADING1, 19f))
                }
                line.matches(Regex("^-{3,}$")) -> {
                    result.add(Segment("─".repeat(30), Style.NORMAL))
                }
                else -> {
                    val listMatch = Regex("""^(\s*)([-*+]|\d+\.)\s+(.*)""").find(line)
                    if (listMatch != null) {
                        val indent = listMatch.groupValues[1]
                        val marker = listMatch.groupValues[2]
                        val content = listMatch.groupValues[3]
                        val isOrdered = Regex("""\d+\.""").matches(marker)
                        val prefix = if (isOrdered) "$marker " else "• "
                        val bulletPrefix = if (indent.isNotEmpty()) "  ".repeat(indent.length / 2) + prefix else prefix
                        result.add(Segment(bulletPrefix, if (isOrdered) Style.ORDERED else Style.BULLET))
                        result.addAll(parseInline(content))
                    } else {
                        result.addAll(parseInline(line))
                    }
                }
            }
        }
        return result
    }

    /** 解析行内样式，返回片段列表 */
    fun parseInline(line: String): List<Segment> {
        val result = mutableListOf<Segment>()
        var remaining = line
        while (remaining.isNotEmpty()) {
            val boldClose = remaining.indexOf("**")
            val italicClose = remaining.indexOf("*")
            val codeClose = remaining.indexOf("`")

            val firstPos = listOf(
                if (boldClose >= 0) boldClose else Int.MAX_VALUE,
                if (italicClose >= 0 && (boldClose < 0 || italicClose < boldClose)) italicClose else Int.MAX_VALUE,
                if (codeClose >= 0) codeClose else Int.MAX_VALUE
            ).minOrNull() ?: Int.MAX_VALUE

            if (firstPos == Int.MAX_VALUE) {
                result.add(Segment(remaining, Style.NORMAL))
                break
            }

            if (firstPos > 0) result.add(Segment(remaining.substring(0, firstPos), Style.NORMAL))

            val (open, close) = when (firstPos) {
                boldClose -> "**" to "**"
                italicClose -> "*" to "*"
                else -> "`" to "`"
            }

            val end = remaining.indexOf(close, firstPos + open.length)
            if (end >= 0) {
                val content = remaining.substring(firstPos + open.length, end)
                val style = when (open) {
                    "**" -> Style.BOLD
                    "*" -> Style.ITALIC
                    else -> Style.CODE
                }
                result.add(Segment(content, style))
                remaining = remaining.substring(end + close.length)
            } else {
                result.add(Segment(remaining.substring(firstPos), Style.NORMAL))
                break
            }
        }
        return result
    }

    /** 检测是否包含代码块 */
    fun hasCodeBlock(text: String): Boolean = text.contains("```")

    /** 提取代码块内容 */
    fun extractCodeBlocks(text: String): List<CodeBlock> {
        val result = mutableListOf<CodeBlock>()
        val segments = text.split(Regex("(?<=```)|(?=```)"))
        var isCode = false
        for (segment in segments) {
            when {
                segment == "```" -> isCode = !isCode
                isCode -> {
                    val code = segment.removePrefix("kotlin\n").removePrefix("python\n")
                        .removePrefix("json\n").removePrefix("bash\n").removePrefix("plaintext\n").trim()
                    result.add(CodeBlock(code, segment.startsWith("kotlin")))
                }
            }
        }
        return result
    }

    /** 代码块数据 */
    data class CodeBlock(
        val code: String,
        val isKotlin: Boolean = false
    )
}