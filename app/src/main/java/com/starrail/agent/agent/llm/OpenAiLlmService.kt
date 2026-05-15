package com.starrail.agent.agent.llm

import com.starrail.agent.agent.tool.ToolDefinition
import com.starrail.agent.agent.tool.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI API 实现
 * 支持 GPT-4o-mini / GPT-4o 等模型的 Function Calling
 */
class OpenAiLlmService(private var config: LlmConfig = LlmConfig()) : LlmService {

    fun updateConfig(newConfig: LlmConfig) { config = newConfig }

    override fun isAvailable(): Boolean = config.apiKey.isNotBlank()

    override suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<LlmToolDef>,
        config: LlmConfig
    ): LlmResponse = withContext(Dispatchers.IO) {
        try {
            val activeConfig = if (config.apiKey.isNotBlank()) config else this@OpenAiLlmService.config
            if (activeConfig.apiKey.isBlank()) {
                return@withContext LlmResponse(false, error = "API Key 未配置")
            }

            val payload = JSONObject().apply {
                put("model", activeConfig.model)
                put("temperature", activeConfig.temperature)
                put("max_tokens", activeConfig.maxTokens)

                // 消息
                put("messages", JSONArray().apply {
                    for (msg in messages) {
                        put(JSONObject().apply {
                            put("role", msg.role.name.lowercase())
                            // content 可为空（assistant 发起 tool_calls 时）
                            put("content", if (msg.role == LlmRole.ASSISTANT && msg.toolCalls != null) "" else (msg.content ?: ""))
                            msg.name?.let { put("name", it) }
                            
                            // DeepSeek: 回传 reasoning_content（thinking mode 必须）
                            if (msg.role == LlmRole.ASSISTANT && msg.reasoningContent != null) {
                                put("reasoning_content", msg.reasoningContent)
                            }
                            
                            // TOOL 角色需要 tool_call_id
                            if (msg.role == LlmRole.TOOL) {
                                put("tool_call_id", msg.toolCallId ?: "")
                            }
                            
                            // ASSISTANT 角色有 tool_calls 时需要序列化
                            if (msg.role == LlmRole.ASSISTANT && msg.toolCalls != null) {
                                put("tool_calls", JSONArray().apply {
                                    for (tc in msg.toolCalls) {
                                        put(JSONObject().apply {
                                            put("id", tc.id)
                                            put("type", "function")
                                            put("function", JSONObject().apply {
                                                put("name", tc.functionName)
                                                put("arguments", tc.arguments)
                                            })
                                        })
                                    }
                                })
                            }
                        })
                    }
                })

                // 工具定义
                if (tools.isNotEmpty()) {
                    put("tools", JSONArray().apply {
                        for (tool in tools) {
                            put(JSONObject().apply {
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", JSONObject().apply {
                                        put("type", tool.parameters.type)
                                        put("properties", JSONObject().apply {
                                            for ((key, prop) in tool.parameters.properties) {
                                                put(key, JSONObject().apply {
                                                    put("type", prop.type)
                                                    put("description", prop.description)
                                                    prop.enumValues?.let {
                                                        put("enum", JSONArray(it))
                                                    }
                                                })
                                            }
                                        })
                                        put("required", JSONArray(tool.parameters.required))
                                    })
                                })
                            })
                        }
                    })
                } else {
                    // 无工具时强制模型生成文本回复，不调用工具
                    put("tool_choice", "none")
                }
            }

            // 发送请求
            val conn = URL(activeConfig.baseUrl + "/chat/completions").openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${activeConfig.apiKey}")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 60000
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "未知错误"
                return@withContext LlmResponse(false, error = "API 错误 ($responseCode): $errorBody")
            }

// 响应解析
            val responseBody = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(responseBody)
            val choice = json.getJSONArray("choices").getJSONObject(0)
            val message = choice.getJSONObject("message")

            // 提取内容
            val content = message.optString("content", null)
            // DeepSeek thinking mode: 捕获 reasoning_content
            val reasoningContent = if (message.has("reasoning_content")) {
                message.optString("reasoning_content", null)
            } else null

            // 解析工具调用（标准 OpenAI JSON 格式）
            val toolCalls = mutableListOf<LlmToolCall>()
            if (message.has("tool_calls")) {
                val calls = message.getJSONArray("tool_calls")
                for (i in 0 until calls.length()) {
                    val tc = calls.getJSONObject(i)
                    val func = tc.getJSONObject("function")
                    toolCalls.add(LlmToolCall(
                        id = tc.getString("id"),
                        functionName = func.getString("name"),
                        arguments = func.getString("arguments")
                    ))
                }
            }
            
            // 解析 DeepSeek DSML 格式（当标准 tool_calls 为空时尝试）
            if (toolCalls.isEmpty() && content != null && content.contains("<||")) {
                val dsmlCalls = parseDsmlToolCalls(content)
                toolCalls.addAll(dsmlCalls)
            }

            // 用量统计
            val usage = if (json.has("usage")) {
                val u = json.getJSONObject("usage")
                LlmUsage(u.optInt("prompt_tokens"), u.optInt("completion_tokens"), u.optInt("total_tokens"))
            } else null

            LlmResponse(
                success = true,
                content = content,
                toolCalls = toolCalls,
                usage = usage,
                reasoningContent = reasoningContent
            )

        } catch (e: Exception) {
            LlmResponse(false, error = "请求异常: ${e.message}")
        }
    }

    /** 解析 DeepSeek DSML 格式的工具调用 */
    private fun parseDsmlToolCalls(content: String): List<LlmToolCall> {
        val results = mutableListOf<LlmToolCall>()
        val lines = content.split("\n")
        var currentFunc = ""
        val params = mutableMapOf<String, String>()
        var idx = 0
        
        for (line in lines) {
            val trimmed = line.trim()
            // 匹配 invoke 标签: <|| DSML1 | invoke name="func_name">
            val invokeMatch = Regex("""invoke\s+name="([^"]+)""" ).find(trimmed)
            if (invokeMatch != null) {
                currentFunc = invokeMatch.groupValues[1]
                continue
            }
            // 匹配 parameter 标签: name="param_name" ...>param_value</
            val paramMatch = Regex("""name="([^"]+)"\s+string="true">([^<]*)""").find(trimmed)
            if (paramMatch != null && currentFunc.isNotEmpty()) {
                params[paramMatch.groupValues[1]] = paramMatch.groupValues[2]
                continue
            }
            // 匹配结束标签: </|| DSML | | invoke>
            if (trimmed.contains("</") && currentFunc.isNotEmpty()) {
                val argsJson = JSONObject(params.toMap()).toString()
                results.add(LlmToolCall(
                    id = "call_dsml_${results.size}_${System.currentTimeMillis()}",
                    functionName = currentFunc,
                    arguments = argsJson
                ))
                currentFunc = ""
                params.clear()
            }
            idx++
        }
        return results
    }
}

/**
 * 工具定义 → LLM Function 转换器
 */
object ToolToLlmFunction {

    /** 将 ToolExecutor 的工具定义转换为 LLM Function Calling 格式 */
    fun convert(toolDefs: List<ToolDefinition>): List<LlmToolDef> {
        return toolDefs.map { tool ->
            val props = mutableMapOf<String, LlmParameterProperty>()
            val required = mutableListOf<String>()

            for (param in tool.parameters) {
                val typeName = when (param.type.name) {
                    "INT" -> "integer"
                    "DOUBLE" -> "number"
                    "BOOLEAN" -> "boolean"
                    "LIST" -> "array"
                    "OBJECT" -> "object"
                    else -> "string"
                }
                props[param.name] = LlmParameterProperty(
                    type = typeName,
                    description = param.description,
                    enumValues = param.enumValues
                )
                if (param.required) required.add(param.name)
            }

            LlmToolDef(
                name = tool.name,
                description = tool.description,
                parameters = LlmToolParameters(
                    type = "object",
                    properties = props,
                    required = required
                )
            )
        }
    }
}

/**
 * LLM 驱动的意图解析器
 * 当规则匹配不足时，使用 LLM 辅助判断
 */
class LlmIntentResolver(private val llmService: LlmService) {

    /** 系统提示词 */
    private val systemPrompt = """
你是一个《崩坏：星穹铁道》游戏助手。你需要根据用户输入判断意图并提取信息。

可识别的意图包括：
- SIMULATE_BATTLE: 战斗模拟/伤害计算
- SCORE_CHARACTER_RELICS: 遗器评分
- RECOMMEND_RELIC_SET: 遗器套装推荐
- SUGGEST_RELIC_UPGRADE: 遗器升级建议
- ANALYZE_TEAM: 队伍分析
- COMPARE_TEAMS: 队伍对比
- SUGGEST_TEAM: 配队推荐
- ANALYZE_EIDOLON: 星魂分析
- ANALYZE_LIGHTCONE: 光锥分析
- COMPARE_UPGRADE_PATH: 升级路径对比
- QUERY_INFO: 信息查询
- GREETING: 问候
- UNKNOWN: 无法识别

请以 JSON 格式返回：
{"intent": "意图名称", "confidence": 0.0-1.0, "entities": {"characters": [], "enemies": [], "targetContent": null}}
    """.trimIndent()

    /** 使用 LLM 解析意图 */
    suspend fun resolve(input: String): LlmIntentResult? {
        try {
            val messages = listOf(
                LlmMessage(LlmRole.SYSTEM, systemPrompt),
                LlmMessage(LlmRole.USER, input)
            )
            val response = llmService.chat(messages, config = LlmConfig(maxTokens = 512))
            if (!response.success || response.content == null) return null

            // 解析 JSON 响应
            val json = JSONObject(response.content)
            return LlmIntentResult(
                intent = json.getString("intent"),
                confidence = json.optDouble("confidence", 0.5),
                entities = json.optJSONObject("entities")?.let { e ->
                    LlmExtractedEntities(
                        characters = e.optJSONArray("characters")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                        enemies = e.optJSONArray("enemies")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                        targetContent = e.optString("targetContent", null)
                    )
                }
            )
        } catch (_: Exception) {
            return null
        }
    }
}

/** LLM 意图解析结果 */
data class LlmIntentResult(
    val intent: String,
    val confidence: Double,
    val entities: LlmExtractedEntities? = null
)

/** LLM 提取的实体 */
data class LlmExtractedEntities(
    val characters: List<String> = emptyList(),
    val enemies: List<String> = emptyList(),
    val targetContent: String? = null
)