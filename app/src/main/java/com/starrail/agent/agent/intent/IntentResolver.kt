package com.starrail.agent.agent.intent

import com.starrail.agent.core.model.*

/**
 * AI Agent 意图识别
 * 将用户自然语言转化为结构化指令
 */

/** 意图类型枚举 */
enum class Intent {
    // === 战斗模拟 ===
    /** 执行战斗模拟 */
    SIMULATE_BATTLE,
    /** 计算单次技能伤害 */
    CALCULATE_DAMAGE,
    
    // === 遗器分析 ===
    /** 分析玩家所有遗器 */
    ANALYZE_RELICS,
    /** 对特定角色装备评分 */
    SCORE_CHARACTER_RELICS,
    /** 推荐遗器套装 */
    RECOMMEND_RELIC_SET,
    /** 建议遗器升级 */
    SUGGEST_RELIC_UPGRADE,
    
    // === 配队对比 ===
    /** 分析队伍协同 */
    ANALYZE_TEAM,
    /** 对比多支队伍 */
    COMPARE_TEAMS,
    /** 推荐配队 */
    SUGGEST_TEAM,
    
    // === 星魂提升 ===
    /** 分析星魂收益 */
    ANALYZE_EIDOLON,
    /** 比较升级路径 */
    COMPARE_UPGRADE_PATH,
    /** 分析光锥选择 */
    ANALYZE_LIGHTCONE,
    
    // === 通用 ===
    /** 查询角色信息 */
    QUERY_INFO,
    /** 对比角色强弱 */
    COMPARE_CHARACTERS,
    /** 问候 */
    GREETING,
    /** 未知意图 */
    UNKNOWN;
    
    /** 获取意图描述 */
    fun getDescription(): String {
        return when (this) {
            SIMULATE_BATTLE -> "战斗模拟"
            CALCULATE_DAMAGE -> "伤害计算"
            ANALYZE_RELICS -> "遗器分析"
            SCORE_CHARACTER_RELICS -> "装备评分"
            RECOMMEND_RELIC_SET -> "套装推荐"
            SUGGEST_RELIC_UPGRADE -> "升级建议"
            ANALYZE_TEAM -> "队伍分析"
            COMPARE_TEAMS -> "队伍对比"
            SUGGEST_TEAM -> "配队推荐"
            ANALYZE_EIDOLON -> "星魂分析"
            COMPARE_UPGRADE_PATH -> "升级路径比较"
            ANALYZE_LIGHTCONE -> "光锥分析"
            QUERY_INFO -> "信息查询"
            COMPARE_CHARACTERS -> "角色对比"
            GREETING -> "问候"
            UNKNOWN -> "未知"
        }
    }
}

/** 解析后的实体数据 */
data class ParsedEntities(
    val characters: List<String> = emptyList(),
    val lightCones: List<String> = emptyList(),
    val relicSets: List<String> = emptyList(),
    val enemies: List<String> = emptyList(),
    val teamSlots: List<Int> = emptyList(),
    val numericValues: Map<String, Double> = emptyMap(),
    val targetContent: String? = null,
    val actionType: String? = null,
    val qualifiers: List<String> = emptyList()
) {
    val primaryCharacter: String? get() = characters.firstOrNull()
    val primaryEnemy: String? get() = enemies.firstOrNull()
}

/** 解析后的意图结果 */
data class ResolvedIntent(
    val intent: Intent,
    val confidence: Double,
    val parameters: Map<String, Any> = emptyMap(),
    val entities: ParsedEntities,
    val rawInput: String,
    val clarificationNeeded: Boolean = false,
    val clarificationQuestion: String? = null
) {
    /** 是否为高置信度识别 */
    val isHighConfidence: Boolean get() = confidence >= 0.7
}

/** 关键词模式匹配规则 */
object IntentPatterns {
    /** 战斗模拟关键词 */
    val battleKeywords = listOf("模拟", "战斗", "打", "伤害", "输出", "DPS", "计算")
    
    /** 遗器分析关键词 */
    val relicKeywords = listOf("遗器", "装备", "圣遗物", "评分", "套装", "强化", "升级")
    
    /** 配队关键词 */
    val teamKeywords = listOf("配队", "队伍", "阵容", "组队", "组合", "队")
    
    /** 星魂提升关键词 */
    val upgradeKeywords = listOf("星魂", "命座", "专武", "精炼", "提升", "抽", "补", "光锥")
    
    /** 查询关键词 */
    val queryKeywords = listOf("查询", "查看", "看看", "什么", "哪个")
    
    /** 对比关键词 */
    val compareKeywords = listOf("对比", "比较", "哪个好", "谁强", "选哪个")
    
    /** 问候关键词 */
    val greetingKeywords = listOf("你好", "hi", "hello", "在吗", "帮忙")
}

/** 意图解析器 */
class IntentResolver {
    
    /**
     * 解析用户输入
     * 采用规则匹配（快速路径）+ 可选的 LLM 辅助（精确路径）
     */
    fun resolve(input: String, context: ConversationContext? = null): ResolvedIntent {
        val normalizedInput = input.trim().lowercase()
        val entities = extractEntities(input)
        
        // 规则匹配确定意图
        val (intent, confidence, params) = matchIntent(normalizedInput, entities)
        
        // 检查是否需要澄清
        val (clarificationNeeded, clarificationQuestion) = checkClarification(intent, entities)
        
        return ResolvedIntent(
            intent = intent,
            confidence = confidence,
            parameters = params,
            entities = entities,
            rawInput = input,
            clarificationNeeded = clarificationNeeded,
            clarificationQuestion = clarificationQuestion
        )
    }
    
    /** 提取实体 */
    private fun extractEntities(input: String): ParsedEntities {
        val characters = extractCharacterNames(input)
        val enemies = extractEnemyNames(input)
        val qualifiers = extractQualifiers(input)
        
        return ParsedEntities(
            characters = characters,
            enemies = enemies,
            qualifiers = qualifiers,
            targetContent = extractTargetContent(input)
        )
    }
    
    /** 提取角色名（简化版，需要对接角色名称库） */
    private fun extractCharacterNames(input: String): List<String> {
        val knownCharacters = listOf(
            "希儿", "银狼", "丹恒", "饮月", "景元", "刃", "卡芙卡",
            "符玄", "藿藿", "银枝", "素裳", "桂子", "驭空",
            "罗刹", "白露", "娜塔莎", "三月七", "丹恒·饮月"
        )
        
        return knownCharacters.filter { input.contains(it) }
    }
    
    /** 提取敌人名称 */
    private fun extractEnemyNames(input: String): List<String> {
        val knownEnemies = listOf(
            "混沌", "12层", "虚构", "末日", "Boss", "精英"
        )
        
        return knownEnemies.filter { input.contains(it) }
    }
    
    /** 提取修饰语 */
    private fun extractQualifiers(input: String): List<String> {
        val qualifiers = mutableListOf<String>()
        if (input.contains("我的") || input.contains("当前")) qualifiers.add("我的")
        if (input.contains("最优") || input.contains("最强")) qualifiers.add("最优")
        if (input.contains("极限") || input.contains("最高")) qualifiers.add("极限")
        if (input.contains("推荐") || input.contains("建议")) qualifiers.add("推荐")
        return qualifiers
    }
    
    /** 提取目标内容 */
    private fun extractTargetContent(input: String): String? {
        return when {
            input.contains("混沌") -> "混沌回忆"
            input.contains("虚构") -> "虚构叙事"
            input.contains("末日") -> "末日乐土"
            input.contains("忘却") -> "忘却之庭"
            else -> null
        }
    }
    
    /** 匹配意图 */
    private fun matchIntent(
        input: String,
        entities: ParsedEntities
    ): Triple<Intent, Double, Map<String, Any>> {
        // 优先级检查：问候 → 具体操作 → 通用查询
        
        // 1. 问候检测
        if (IntentPatterns.greetingKeywords.any { input.contains(it) } && 
            input.length < 20) {
            return Triple(Intent.GREETING, 0.9, emptyMap())
        }
        
        // 2. 战斗相关
        if (IntentPatterns.battleKeywords.any { input.contains(it) }) {
            return Triple(Intent.SIMULATE_BATTLE, 0.8, mapOf("target" to (entities.primaryEnemy ?: "default")))
        }
        
        // 3. 遗器相关
        if (IntentPatterns.relicKeywords.any { input.contains(it) }) {
            val intent = when {
                input.contains("评分") || input.contains("评价") -> Intent.SCORE_CHARACTER_RELICS
                input.contains("分析") && entities.characters.isNotEmpty() -> Intent.SCORE_CHARACTER_RELICS
                input.contains("推荐") || input.contains("什么") && input.contains("套") -> Intent.RECOMMEND_RELIC_SET
                input.contains("升级") || input.contains("强化") -> Intent.SUGGEST_RELIC_UPGRADE
                else -> Intent.ANALYZE_RELICS
            }
            return Triple(intent, 0.8, emptyMap())
        }
        
        // 4. 配队相关
        if (IntentPatterns.teamKeywords.any { input.contains(it) }) {
            val intent = when {
                input.contains("对比") || input.contains("比较") -> Intent.COMPARE_TEAMS
                input.contains("推荐") || input.contains("怎么组") -> Intent.SUGGEST_TEAM
                else -> Intent.ANALYZE_TEAM
            }
            return Triple(intent, 0.8, emptyMap())
        }
        
        // 5. 升级相关（注意：先检测比较路径再检测单一分析）
        if (IntentPatterns.upgradeKeywords.any { input.contains(it) }) {
            val intent = when {
                input.contains("先") && input.contains("还是") -> Intent.COMPARE_UPGRADE_PATH
                input.contains("星魂") || input.contains("命座") -> Intent.ANALYZE_EIDOLON
                input.contains("光锥") || input.contains("专武") -> Intent.ANALYZE_LIGHTCONE
                else -> Intent.ANALYZE_EIDOLON
            }
            return Triple(intent, 0.75, emptyMap())
        }
        
        // 6. 对比相关
        if (IntentPatterns.compareKeywords.any { input.contains(it) }) {
            return Triple(Intent.COMPARE_CHARACTERS, 0.7, emptyMap())
        }
        
        // 7. 未知
        return Triple(Intent.UNKNOWN, 0.5, emptyMap())
    }
    
    /** 检查是否需要澄清 */
    private fun checkClarification(
        intent: Intent,
        entities: ParsedEntities
    ): Pair<Boolean, String?> {
        return when {
            // 战斗模拟但没有指定角色
            intent == Intent.SIMULATE_BATTLE && entities.characters.isEmpty() -> 
                Pair(true, "请问您想分析哪位角色的战斗表现？")
            
            // 装备评分但没有指定角色
            intent == Intent.SCORE_CHARACTER_RELICS && entities.characters.isEmpty() -> 
                Pair(true, "请问您想给哪位角色评分？")
            
            // 队伍分析但没有指定成员
            intent == Intent.ANALYZE_TEAM && entities.characters.size < 2 -> 
                Pair(true, "请提供队伍成员信息（至少2人）")
            
            else -> Pair(false, null)
        }
    }
}

/** 对话上下文 */
data class ConversationContext(
    val conversationId: String,
    val messages: List<ChatMessage> = emptyList(),
    val currentIntent: ResolvedIntent? = null,
    val pendingParameters: Map<String, Any?> = emptyMap(),
    val extractedEntities: ParsedEntities = ParsedEntities()
)

/** 聊天消息 */
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/** 消息角色 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}