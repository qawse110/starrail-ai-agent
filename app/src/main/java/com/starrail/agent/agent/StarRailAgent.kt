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
    private val llmService: LlmService? = null,
    private val conversationRepo: ConversationRepository? = null
) {
    
    /** 游戏数据源 */
    val gameDataSource = InMemoryGameDataSource()
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
    
    init {
        // 启动时从持久化仓库加载对话
        conversationRepo?.let { repo ->
            for (convId in repo.listConversations()) {
                val messages = repo.loadConversation(convId)
                if (messages != null && messages.isNotEmpty()) {
                    llmConversations[convId] = messages.toMutableList()
                }
            }
        }
    }
    
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
        appendLine("- 角色稀有度：4星 / 5星 — 共40名角色")
        appendLine("- 遗器槽位：头部、手部、躯干、脚部、位面球、连结绳")
        appendLine("- 遗器套装分两种：遗器（4件套）和位面饰品（2件套）— 共42套")
        appendLine("- 光锥适配特定命途，精炼1~5阶 — 共40个光锥（18五星+22四星）")
        appendLine("- 星魂等级：0~6魂，关键星魂通常为 1、2、4、6")
        appendLine()
        appendLine("## 可用的工具（重要）")
        appendLine("你只能调用以下列出的工具，不要编造不存在的工具。")
        appendLine("当用户询问游戏数据时，使用对应工具查询。")
        appendLine("工具会返回结构化的游戏数据，你基于这些数据生成自然语言回答。")
        appendLine()
        appendLine("可用工具列表：")
        appendLine("- simulate_battle: 战斗模拟")
        appendLine("- calculate_damage: 伤害计算")
        appendLine("- score_relics: 遗器评分")
        appendLine("- recommend_relic_set: 遗器套装推荐")
        appendLine("- suggest_relic_upgrade: 遗器升级建议")
        appendLine("- analyze_team: 队伍分析")
        appendLine("- compare_teams: 队伍对比")
        appendLine("- suggest_team: 配队推荐")
        appendLine("- analyze_eidolon: 星魂分析")
        appendLine("- analyze_lightcone: 光锥精炼分析")
        appendLine("- compare_upgrade_path: 升级方案对比")
        appendLine("- get_character_info: 角色信息查询")
        appendLine("- list_characters: 列出所有角色")
        appendLine("- get_light_cone_info: 光锥信息查询（按名称搜索）")
        appendLine("- get_relic_set_info: 遗器套装信息查询")
        appendLine("- get_enemy_info: 敌人信息查询")
        appendLine("- get_recommended_build: 角色推荐配装（含遗器套装、光锥、主词条）")
        appendLine()
        appendLine("注意：没有名为 search_light_cones 的工具。查询光锥请使用 get_light_cone_info 或 get_recommended_build。")
        appendLine()
        appendLine("## 回复风格")
        appendLine("- 专业但友好，使用中文")
        appendLine("- 回答结构清晰，适当使用分段和要点")
        appendLine("- 给出具体数字和分析，不只是泛泛而谈")
        appendLine("- 如果不确定具体数据，诚实告知用户")
    }

    /** 保存对话到持久化仓库（同步写入） */
    private fun saveConversation(convId: String) {
        conversationRepo?.let { repo ->
            llmConversations[convId]?.let { msgs ->
                repo.saveConversation(convId, msgs)
            }
        }
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
            val reply = if (response.toolCalls.isNotEmpty()) {
                handleToolCalls(convId, history, response.toolCalls, messages, response.reasoningContent)
            } else {
                // 纯文本回复
                val text = response.content ?: "抱歉，我没有理解您的问题。"
                history.add(LlmMessage(
                    role = LlmRole.ASSISTANT,
                    content = text,
                    reasoningContent = response.reasoningContent
                ))
                text
            }
            // 自动持久化
            saveConversation(convId)
            reply
        } catch (e: Exception) {
            // LLM 不可用时回退到规则引擎
            val fallback = process(userInput, convId)
            val text = fallback.report?.toFormattedText() ?: "处理失败"
            history.add(LlmMessage(LlmRole.ASSISTANT, text))
            saveConversation(convId)
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

        // 将工具结果发给 LLM 生成最终回复（仍然携带工具定义，支持多轮调用）
        val finalMessages = listOf(history.first()) + history.drop(1).takeLast(MAX_MESSAGES_PER_CONVERSATION)
        val finalResponse = withContext(Dispatchers.IO) {
            llmService?.chat(finalMessages, getToolDefinitions()) ?: throw IllegalStateException("LLM 不可用")
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
        if (json.isBlank()) return emptyMap()
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
    fun clearConversation(conversationId: String) { 
        conversations.remove(conversationId)
        llmConversations.remove(conversationId)
        conversationRepo?.deleteConversation(conversationId)
    }
    
    // ============================================================
    // 对话管理 API
    // ============================================================
    
    /** 获取对话列表 [(id, 预览文本)] */
    fun getConversationList(): List<Pair<String, String>> {
        // 从 LLM 对话获取
        val llmEntries = llmConversations.map { (id, msgs) ->
            val preview = msgs.filter { it.role == LlmRole.USER }
                .firstOrNull()?.content?.take(40) ?: "空对话"
            id to preview
        }.toMutableList()
        // 从规则引擎对话获取（补充不在 llmConversations 中的）
        val processEntries = conversations.map { (id, ctx) ->
            val preview = ctx.messages.firstOrNull()?.content?.take(40) ?: "空对话"
            id to preview
        }
        // 从持久化仓库加载未在内存中的
        conversationRepo?.let { repo ->
            val loadedIds = (llmConversations.keys + conversations.keys).toSet()
            for (convId in repo.listConversations()) {
                if (convId !in loadedIds) {
                    val msgs = repo.loadConversation(convId)
                    if (msgs != null && msgs.isNotEmpty()) {
                        llmConversations[convId] = msgs.toMutableList()
                        llmEntries.add(convId to (msgs.filter { it.role == LlmRole.USER }
                            .firstOrNull()?.content?.take(40) ?: "空对话"))
                    }
                }
            }
        }
        return (llmEntries + processEntries).distinctBy { it.first }
    }
    
    /** 获取对话的完整消息列表 */
    fun getConversationMessages(convId: String): List<LlmMessage> {
        // 先查 LLM 对话，再查规则引擎对话
        llmConversations[convId]?.let { return it }
        conversations[convId]?.let { ctx ->
            return ctx.messages.map { 
                LlmMessage(LlmRole.USER, it.content ?: "")
            }
        }
        return emptyList()
    }
    
    /** 搜索对话内容 */
    fun searchConversations(query: String): List<Pair<String, String>> {
        val q = query.lowercase()
        val results = mutableListOf<Pair<String, String>>()
        
        // 搜索 LLM 对话
        for ((id, msgs) in llmConversations) {
            for (msg in msgs) {
                val content = msg.content ?: continue
                if (content.lowercase().contains(q)) {
                    val preview = content.take(60).replace("\n", " ")
                    results.add(id to preview)
                    break
                }
            }
        }
        
        // 搜索规则引擎对话
        for ((id, ctx) in conversations) {
            for (msg in ctx.messages) {
                val content = msg.content ?: continue
                if (content.lowercase().contains(q)) {
                    val preview = content.take(60).replace("\n", " ")
                    results.add(id to preview)
                    break
                }
            }
        }
        
        return results.distinctBy { it.first }
    }
    
    /** 删除指定对话 */
    fun deleteConversation(convId: String) {
        llmConversations.remove(convId)
        conversations.remove(convId)
        conversationRepo?.deleteConversation(convId)
    }
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