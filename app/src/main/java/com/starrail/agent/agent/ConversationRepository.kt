package com.starrail.agent.agent

import com.starrail.agent.agent.llm.LlmMessage
import com.starrail.agent.agent.llm.LlmRole
import com.starrail.agent.agent.llm.LlmToolCall
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 对话持久化仓库
 * 将 LLM 对话历史保存为 JSON 文件，支持重启恢复
 */
class ConversationRepository(private val storageDir: File) {

    private val convDir: File = File(storageDir, "conversations").also { it.mkdirs() }

    companion object {
        private const val FILE_PREFIX = "conv_"
        private const val FILE_SUFFIX = ".json"
    }

    /** 保存对话到文件 */
    fun saveConversation(convId: String, messages: List<LlmMessage>) {
        try {
            val json = JSONObject().apply {
                put("id", convId)
                put("updated_at", System.currentTimeMillis())
                put("messages", JSONArray().apply {
                    for (msg in messages) {
                        // 跳过 SYSTEM 消息（不持久化）
                        if (msg.role == LlmRole.SYSTEM) continue
                        put(JSONObject().apply {
                            put("role", msg.role.name)
                            put("content", msg.content ?: "")
                            msg.name?.let { put("name", it) }
                            msg.toolCallId?.let { put("tool_call_id", it) }
                            msg.reasoningContent?.let { put("reasoning_content", it) }
                            msg.toolCalls?.let { calls ->
                                put("tool_calls", JSONArray().apply {
                                    for (tc in calls) {
                                        put(JSONObject().apply {
                                            put("id", tc.id)
                                            put("function_name", tc.functionName)
                                            put("arguments", tc.arguments)
                                        })
                                    }
                                })
                            }
                        })
                    }
                })
            }
            val file = File(convDir, "$FILE_PREFIX${sanitizeFileName(convId)}$FILE_SUFFIX")
            file.writeText(json.toString(2))
        } catch (_: Exception) { /* 静默失败，不影响主流程 */ }
    }

    /** 从文件加载对话 */
    fun loadConversation(convId: String): List<LlmMessage>? {
        return try {
            val file = File(convDir, "$FILE_PREFIX${sanitizeFileName(convId)}$FILE_SUFFIX")
            if (!file.exists()) return null
            val json = JSONObject(file.readText())
            val messages = mutableListOf<LlmMessage>()
            val arr = json.getJSONArray("messages")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                messages.add(LlmMessage(
                    role = LlmRole.valueOf(obj.getString("role")),
                    content = obj.optString("content", ""),
                    name = obj.optString("name", null),
                    toolCallId = obj.optString("tool_call_id", null),
                    reasoningContent = obj.optString("reasoning_content", null),
                    toolCalls = if (obj.has("tool_calls")) {
                        val calls = mutableListOf<LlmToolCall>()
                        val tcArr = obj.getJSONArray("tool_calls")
                        for (j in 0 until tcArr.length()) {
                            val tc = tcArr.getJSONObject(j)
                            calls.add(LlmToolCall(
                                id = tc.getString("id"),
                                functionName = tc.getString("function_name"),
                                arguments = tc.optString("arguments", "{}")
                            ))
                        }
                        calls
                    } else null
                ))
            }
            messages
        } catch (_: Exception) { null }
    }

    /** 列出所有已持久化的对话 ID */
    fun listConversations(): List<String> {
        return convDir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }
            ?.map { it.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX) }
            ?: emptyList()
    }

    /** 删除对话文件 */
    fun deleteConversation(convId: String) {
        val file = File(convDir, "$FILE_PREFIX${sanitizeFileName(convId)}$FILE_SUFFIX")
        file.delete()
    }

    /** 清空所有持久化对话 */
    fun deleteAll() {
        convDir.listFiles()?.forEach { it.delete() }
    }

    private fun sanitizeFileName(id: String): String {
        return id.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    }
}