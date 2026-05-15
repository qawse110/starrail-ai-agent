package com.starrail.agent.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.starrail.agent.agent.llm.LlmMessage
import com.starrail.agent.agent.llm.LlmRole
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 对话导出工具
 */
object ConversationExporter {

    private const val EXPORT_DIR = "exports"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    /** 将对话导出为 TXT 文件 */
    fun exportAsText(context: Context, messages: List<LlmMessage>, fileName: String? = null): File {
        val dir = File(context.filesDir, EXPORT_DIR).also { it.mkdirs() }
        val name = fileName ?: "conversation_${dateFormat.format(Date())}"
        val file = File(dir, "${name}.txt")

        val content = buildString {
            appendLine("═══ 星穹铁道 AI 助手 对话记录 ═══")
            appendLine("导出时间: ${dateFormat.format(Date())}")
            appendLine("消息数: ${messages.count { it.role != LlmRole.SYSTEM }}")
            appendLine("═══════════════════════════════")
            appendLine()

            for (msg in messages) {
                when (msg.role) {
                    LlmRole.USER -> {
                        appendLine("【用户】")
                        appendLine(msg.content ?: "")
                        appendLine()
                    }
                    LlmRole.ASSISTANT -> {
                        appendLine("【AI 助手】")
                        // 去掉 Markdown 标记的简化版本
                        val clean = (msg.content ?: "").replace(Regex("[*_`#]"), "")
                        appendLine(clean)
                        appendLine()
                    }
                    LlmRole.TOOL -> {
                        // 工具调用结果不显示在导出中
                    }
                    LlmRole.SYSTEM -> { /* 不导出系统提示词 */ }
                }
            }
            appendLine("═══ 导出结束 ═══")
        }

        file.writeText(content, Charsets.UTF_8)
        return file
    }

    /** 将对话导出为 JSON（完整数据） */
    fun exportAsJson(context: Context, convId: String, messages: List<LlmMessage>): File {
        val dir = File(context.filesDir, EXPORT_DIR).also { it.mkdirs() }
        val file = File(dir, "conversation_${convId.take(8)}_${dateFormat.format(Date())}.json")

        val json = org.json.JSONObject().apply {
            put("conversation_id", convId)
            put("exported_at", System.currentTimeMillis())
            put("message_count", messages.size)
            put("messages", org.json.JSONArray().apply {
                for (msg in messages) {
                    if (msg.role == LlmRole.SYSTEM) continue
                    put(org.json.JSONObject().apply {
                        put("role", msg.role.name)
                        put("content", msg.content ?: "")
                        msg.reasoningContent?.let { put("reasoning_content", it) }
                    })
                }
            })
        }

        file.writeText(json.toString(2), Charsets.UTF_8)
        return file
    }

    /** 分享文件 */
    fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享对话记录"))
        } catch (_: Exception) {
            // 如果没有 FileProvider，尝试直接分享文本
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, file.readText())
            }
            context.startActivity(Intent.createChooser(shareIntent, "分享对话记录"))
        }
    }
}