package com.starrail.agent.agent.llm

/**
 * LLM 服务接口
 * 所有 LLM 提供商（OpenAI、Claude、本地模型等）的统一抽象
 */
interface LlmService {
    /**
     * 发送聊天请求并获取回复
     * @param messages 对话历史
     * @param tools 可用工具定义（Function Calling）
     * @param config 请求配置
     */
    suspend fun chat(
        messages: List<LlmMessage>,
        tools: List<LlmToolDef> = emptyList(),
        config: LlmConfig = LlmConfig()
    ): LlmResponse

    /** 检查服务是否可用 */
    fun isAvailable(): Boolean
}

/** LLM 消息 */
data class LlmMessage(
    val role: LlmRole,
    val content: String,
    val name: String? = null,
    /** 工具调用ID（仅 TOOL 角色消息需要，对应原 assistant 的 tool_call id） */
    val toolCallId: String? = null,
    /** 工具调用列表（仅 ASSISTANT 角色消息需要，表示 LLM 发起了一次函数调用） */
    val toolCalls: List<LlmToolCall>? = null,
    /** DeepSeek 推理内容（thinking mode），回传时必须原样带回 */
    val reasoningContent: String? = null
)

/** 消息角色 */
enum class LlmRole { SYSTEM, USER, ASSISTANT, TOOL }

/** LLM 请求配置 */
data class LlmConfig(
    val model: String = "gpt-4o-mini",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1"
)

/** LLM 响应 */
data class LlmResponse(
    val success: Boolean,
    val content: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val error: String? = null,
    val usage: LlmUsage? = null,
    /** DeepSeek 推理内容，回传时必须原样带回 */
    val reasoningContent: String? = null
)

/** LLM 用量统计 */
data class LlmUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

/** LLM 工具调用 */
data class LlmToolCall(
    val id: String,
    val functionName: String,
    val arguments: String  // JSON 字符串
)

/** LLM 工具定义（OpenAI Function Calling 格式） */
data class LlmToolDef(
    val name: String,
    val description: String,
    val parameters: LlmToolParameters
)

/** 工具参数定义（JSON Schema） */
data class LlmToolParameters(
    val type: String = "object",
    val properties: Map<String, LlmParameterProperty> = emptyMap(),
    val required: List<String> = emptyList()
)

/** 参数属性定义 */
data class LlmParameterProperty(
    val type: String,           // "string", "number", "integer", "boolean", "array"
    val description: String = "",
    val enumValues: List<String>? = null
)