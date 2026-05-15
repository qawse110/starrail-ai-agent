package com.starrail.agent.agent

import com.starrail.agent.agent.intent.*
import com.starrail.agent.agent.tool.*
import com.starrail.agent.agent.report.*
import com.starrail.agent.agent.llm.*
import com.starrail.agent.core.datasource.InMemoryGameDataSource
import com.starrail.agent.relic.*
import com.starrail.agent.team.*
import com.starrail.agent.upgrade.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * AI Agent 编排器
 *
 * 两种运行模式：
 * 1. LLM 模式（推荐）：通过 `chat()` 调用，AI 生成全自然语言回复
 * 2. 规则模式（离线）：通过 `process()` 调用，规则匹配 + 模板报告
 */
class StarRailAgent(
    private val llmService: LlmService? = null
) {
    
    private val gameDataSource = InMemoryGameDataSource()
    private val intentResolver = IntentResolver()
    private val toolExecutor = ToolExecutor(
        relicScorer = RelicScorer(),
        teamAnalyzer = TeamAnalyzer(),
        eidolonAnalyzer = EidolonAnalyzer(),
        lightConeAnalyzer = LightConeAnalyzer(),
        costBenefitAnalyzer = CostBenefitAnalyzer(),
        dataSource = gameDataSource
    )
    private val reportGenerator = ReportGenerator()
    
    /** LLM 驱动的对话历史 */
    private val llmConversations = mutableMapOf<String, MutableList<LlmMessage>>()
    
    companion object {
        private const val MAX_MESSAGES_PER_CONVERSATION = 50
    }

    // ============================================================
    // 模式一：LLM 驱动的全 AI 回复
    // ============================================================

    /** 系统提示词：包含完整的游戏知识库 */
    private val systemPrompt = buildString {
        appendLine("你是一个《崩坏：星穹铁道》AI 助手。")
        appendLine()
        appendLine("## 你可以提供的功能")
        appendLine("1. **战斗模拟** — 模拟角色对敌人的战斗表现")
        appendLine("2. **遗器分析** — 评分角色遗器、推荐套装、升级建议")
        appendLine("3. **配队分析** — 分析队伍协同、对比队伍、推荐配队")
        appendLine("4. **星魂/精炼分析** — 分析星魂收益、光锥精炼、升级路径对比")
        appendLine("5. **信息查询** — 角色详情、遗器套装、光锥信息、敌人数据")
        appendLine()
        appendLine("## 游戏基础知识")
        appendLine("- 命途：毁灭、巡猎、智识、同谐、虚无、存护、丰饶")
        appendLine("- 属性：物理、火、冰、雷、风、虚数、量子")
        appendLine("- 角色稀有度：4星 / 5星")
        appendLine("- 遗器槽位：头部、手部、躯干、脚部、位面球、连结绳")
        appendLine("- 遗器套装分两种：遗器（4件套）和位面饰品（2件套）")
        appendLine("- 星魂等级：0~6魂，关键星魂通常为 1、2、4、6")
        appendLine("- 光锥精炼：1~5阶")
        appendLine()
        appendLine("## 可用的工具")
        appendLine("当用户询问游戏数据时，使用对应工具查询。")
        appendLine("工具会返回结构化的游戏数据，你基于这些数据生成自然语言回答。")
        appendLine()
        appendLine("## 回复风格")
        appendLine("- 专业但友好，使用中文")
        appendLine("- 回答结构清晰，适当使用分段和要点")
        appendLine("- 给出具体数字和分析，不只是泛泛而谈")
        appendLine("- 如果不确定具体数据，诚实告知用户")
    }

    /**
     * LLM 驱动对话——完全由 AI 生成回复
     * @param userInput 用户输入
     * @param conversationId 会话ID（不传则新建）
     * @return AI 生成的文本回复
     */
    suspend fun chat(userInput: String, conversationId: String? = null): String {
        val convId = conversationId ?: UUID.randomUUID().toString()
        val history = llmConversations.getOrPut(convId) { mutableListOf() }

        // 首次对话时注入系统提示词
        if (history.isEmpty()) {
            history.add(LlmMessage(LlmRole.SYSTEM, systemPrompt))
        }

        // 添加用户消息
        history.add(LlmMessage(LlmRole.USER, userInput))

        // 限制历史长度
        val messages = listOf(history.first()) + history.drop(1).takeLast(MAX_MESSAGES_PER_CONVERSATION)

        return try {
            // 调用 LLM
            val llmDefs = getToolDefinitions()
            val response = withContext(Dispatchers.IO) {
                llmService?.chat(messages, llmDefs) ?: throw IllegalStateException("LLM 未配置")
            }

            if (!response.success) {
                throw IllegalStateException(response.error ?: "LLM 调用失败")
            }

            // 处理工具调用
            if (response.toolCalls.isNotEmpty()) {
                handleToolCalls(convId, history, response.toolCalls, messages, response.reasoningContent)
            } else {
                // 纯文本回复
                val reply = response.content ?: "抱歉，我没有理解您的问题。"
                history.add(LlmMessage(
                    role = LlmRole.ASSISTANT,
                    content = reply,
                    reasoningContent = response.reasoningContent  // 保存推理内容
                ))
                reply
            }
        } catch (e: Exception) {
            // LLM 不可用时回退到规则引擎
            val fallback = process(userInput, convId)
            val text = fallback.report?.toFormattedText() ?: "处理失败"
            history.add(LlmMessage(LlmRole.ASSISTANT, text))
            text
        }
    }

    /** 处理 LLM 的工具调用 */
    private suspend fun handleToolCalls(
        convId: String,
        history: MutableList<LlmMessage>,
        toolCalls: List<LlmToolCall>,
        messages: List<LlmMessage>,
        reasoningContent: String? = null  // DeepSeek thinking mode
    ): String {
        // 添加 assistant 消息（包含 tool_calls 信息以及 reasoning_content）
        history.add(LlmMessage(
            role = LlmRole.ASSISTANT,
            content = "",
            toolCalls = toolCalls,
            reasoningContent = reasoningContent  // 回传时必须原样带回
        ))

        for (tc in toolCalls) {
            // 执行工具
            val params = parseJsonArgs(tc.arguments)
            val result = toolExecutor.execute(tc.functionName, params)

            // 将工具结果返回给 LLM（带 tool_call_id）
            val resultText = if (result.success) {
                result.data?.toString() ?: "ok"
            } else {
                "错误: ${result.error}"
            }
            history.add(LlmMessage(
                role = LlmRole.TOOL,
                content = resultText,
                toolCallId = tc.id  // 关键：关联对应的 tool_call_id
            ))
        }

        // 将工具结果发给 LLM 生成最终回复
        val finalMessages = listOf(history.first()) + history.drop(1).takeLast(MAX_MESSAGES_PER_CONVERSATION)
        val finalResponse = withContext(Dispatchers.IO) {
            llmService?.chat(finalMessages, emptyList()) ?: throw IllegalStateException("LLM 不可用")
        }

        val reply = if (finalResponse.success) {
            finalResponse.content ?: "处理完成"
        } else {
            "处理失败: ${finalResponse.error}"
        }
        history.add(LlmMessage(LlmRole.ASSISTANT, reply))
        return reply
    }

    /** 解析 JSON 参数 */
    private fun parseJsonArgs(json: String): Map<String, Any?> {
        return try {
            val obj = org.json.JSONObject(json)
            val map = mutableMapOf<String, Any?>()
            for (key in obj.keys()) {
                map[key] = obj.get(key)
            }
            map
        } catch (_: Exception) { emptyMap() }
    }

    /** 获取 LLM Function Calling 格式的工具定义 */
    fun getToolDefinitions(): List<LlmToolDef> {
        return ToolToLlmFunction.convert(toolExecutor.getAvailableTools())
    }

    // ============================================================
    // 模式二：规则引擎（离线回退）
    // ============================================================

    /**
     * 处理用户输入（规则引擎，无 LLM）
     */
    fun process(userInput: String, conversationId: String? = null): AgentResponse {
        try {
            val convId = conversationId ?: UUID.randomUUID().toString()
            val context = conversations[convId]
            
            val resolvedIntent = intentResolver.resolve(userInput, context)
            
            if (resolvedIntent.clarificationNeeded) {
                return AgentResponse(convId, true, resolvedIntent.clarificationQuestion, null)
            }
            
            val availableTools = toolExecutor.getAvailableTools(resolvedIntent.intent)
            val toolResults = mutableListOf<ToolResult>()
            for (tool in availableTools) {
                val params = buildParameters(resolvedIntent)
                val result = toolExecutor.execute(tool.name, params)
                toolResults.add(result)
                if (result.success) break
            }
            
            val report = reportGenerator.generate(resolvedIntent.intent, toolResults, resolvedIntent.entities)
            
            val newMessages = (context?.messages?.plus(
                ChatMessage(MessageRole.USER, userInput)
            ) ?: listOf(ChatMessage(MessageRole.USER, userInput)))
                .takeLast(MAX_MESSAGES_PER_CONVERSATION)
            
            conversations[convId] = ConversationContext(
                conversationId = convId, messages = newMessages, currentIntent = resolvedIntent
            )
            
            return AgentResponse(convId, false, null, report)
        } catch (e: Exception) {
            return AgentResponse(
                conversationId = conversationId ?: UUID.randomUUID().toString(),
                needsClarification = false, clarificationQuestion = null,
                report = AnalysisReport("⚠️ 处理异常", "抱歉，处理请求时出现错误",
                    listOf(ReportSection("错误详情", e.message ?: "未知错误", SectionType.TEXT, 5)),
                    listOf("系统遇到临时错误"), listOf("请重试您的请求"))
            )
        }
    }

    // ============================================================
    // 共享方法
    // ============================================================

    private val conversations = mutableMapOf<String, ConversationContext>()

    private fun buildParameters(intent: ResolvedIntent): Map<String, Any?> {
        val params = mutableMapOf<String, Any?>()
        val entities = intent.entities
        
        when (intent.intent) {
            Intent.SIMULATE_BATTLE, Intent.CALCULATE_DAMAGE -> {
                entities.primaryCharacter?.let { params["character_id"] = it }
                entities.primaryEnemy?.let { params["target_enemy"] = it }
            }
            Intent.SCORE_CHARACTER_RELICS, Intent.RECOMMEND_RELIC_SET, Intent.SUGGEST_RELIC_UPGRADE -> {
                entities.primaryCharacter?.let { params["character_id"] = it }
            }
            Intent.ANALYZE_TEAM -> params["team_id"] = entities.characters.joinToString(",")
            Intent.COMPARE_TEAMS -> params["teams"] = entities.characters
            Intent.SUGGEST_TEAM -> {
                entities.primaryCharacter?.let { params["main_dps"] = it }
                intent.parameters["target_content"]?.let { params["target_content"] = it }
            }
            Intent.ANALYZE_EIDOLON, Intent.ANALYZE_LIGHTCONE, Intent.COMPARE_UPGRADE_PATH -> {
                entities.primaryCharacter?.let { params["character_id"] = it }
            }
            else -> {}
        }
        return params
    }

    fun getCharacters(): List<String> = gameDataSource.getAllCharacters().map { "${it.name} (${it.path.displayName})" }
    fun searchCharacters(query: String): List<String> = gameDataSource.searchCharacters(query).map { "${it.name} - ${it.element.name}属性 - ${it.path.displayName}" }
    fun getRelicSets(): List<String> = gameDataSource.getAllRelicSets().map { it.name }
    fun getLightCones(): List<String> = gameDataSource.getAllLightCones().map { "${it.name} (${it.path.displayName})" }
    fun getEnemies(): List<String> = gameDataSource.getAllEnemies().map { "${it.name} - Lv.${it.level}" }
    fun clearConversation(conversationId: String) { conversations.remove(conversationId); llmConversations.remove(conversationId) }
}

data class AgentResponse(
    val conversationId: String,
    val needsClarification: Boolean,
    val clarificationQuestion: String?,
    val report: AnalysisReport?
) {
    fun getTextResponse(): String = when {
        needsClarification -> clarificationQuestion ?: "需要更多信息"
        report != null -> report.toFormattedText()
        else -> "处理失败"
    }
}