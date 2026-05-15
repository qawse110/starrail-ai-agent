package com.starrail.agent.agent.tool

import com.starrail.agent.agent.intent.*
import com.starrail.agent.battle.calculator.*
import com.starrail.agent.relic.*
import com.starrail.agent.team.*
import com.starrail.agent.upgrade.*
import com.starrail.agent.player.*
import com.starrail.agent.core.model.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * 工具定义
 */
data class ToolDefinition(
    val name: String,              // "simulate_battle"
    val description: String,       // "执行战斗模拟"
    val parameters: List<ToolParameter>,
    val module: String,            // "battle"
    val requiredIntent: Intent,   // 关联意图
    val timeout: Long = 30000      // 超时时间（毫秒）
)

/** 工具参数 */
data class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean,
    val defaultValue: String? = null,
    val enumValues: List<String>? = null
)

/** 参数类型 */
enum class ParameterType { 
    STRING, 
    INT, 
    DOUBLE, 
    BOOLEAN, 
    LIST, 
    OBJECT 
}

/** 工具结果 */
data class ToolResult(
    val success: Boolean,
    val data: Any?,
    val error: String?,
    val executionTimeMs: Long,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 工具执行器
 * 负责编排各业务模块工具
 */
class ToolExecutor(
    private val damageCalculator: DamageCalculator = DamageCalculator(),
    private val relicScorer: RelicScorer = RelicScorer(),
    private val teamAnalyzer: TeamAnalyzer = TeamAnalyzer(),
    private val eidolonAnalyzer: EidolonAnalyzer = EidolonAnalyzer(),
    private val lightConeAnalyzer: LightConeAnalyzer = LightConeAnalyzer(),
    private val costBenefitAnalyzer: CostBenefitAnalyzer = CostBenefitAnalyzer(),
    private val dataSource: com.starrail.agent.core.datasource.InMemoryGameDataSource? = null
) {
    /** 所有可用工具 */
    private val tools: Map<String, ToolDefinition> = buildToolDefinitions()
    
    /**
     * 执行工具
     */
    fun execute(
        toolName: String,
        parameters: Map<String, Any?>
    ): ToolResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = when (toolName) {
                "simulate_battle" -> executeSimulateBattle(parameters)
                "calculate_damage" -> executeCalculateDamage(parameters)
                "score_relics" -> executeScoreRelics(parameters)
                "recommend_relic_set" -> executeRecommendRelicSet(parameters)
                "suggest_relic_upgrade" -> executeSuggestRelicUpgrade(parameters)
                "analyze_team" -> executeAnalyzeTeam(parameters)
                "compare_teams" -> executeCompareTeams(parameters)
                "suggest_team" -> executeSuggestTeam(parameters)
                "analyze_eidolon" -> executeAnalyzeEidolon(parameters)
                "analyze_lightcone" -> executeAnalyzeLightCone(parameters)
                "compare_upgrade_path" -> executeCompareUpgradePath(parameters)
                // 数据查询工具
                "get_character_info" -> executeGetCharacterInfo(parameters)
                "list_characters" -> executeListCharacters(parameters)
                "get_light_cone_info" -> executeGetLightConeInfo(parameters)
                "get_relic_set_info" -> executeGetRelicSetInfo(parameters)
                "get_enemy_info" -> executeGetEnemyInfo(parameters)
                "get_recommended_build" -> executeGetRecommendedBuild(parameters)
                else -> throw IllegalArgumentException("Unknown tool: $toolName")
            }
            
            ToolResult(
                success = true,
                data = result,
                error = null,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolResult(
                success = false,
                data = null,
                error = e.message ?: "Unknown error",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /** 获取可用工具列表 */
    fun getAvailableTools(intent: Intent? = null): List<ToolDefinition> {
        return if (intent != null) {
            tools.values.filter { it.requiredIntent == intent }
        } else {
            tools.values.toList()
        }
    }
    
    // === 战斗模拟工具 ===
    
    private fun executeSimulateBattle(parameters: Map<String, Any?>): Map<String, Any> {
        val characterId = getParam(parameters, "character_id", "name", "character_name") ?: ""
        val targetEnemy = getParam(parameters, "target_enemy", "enemy_name", "target") ?: ""
        val ds = dataSource

        // 查找角色和敌人
        val char = ds?.searchCharacters(characterId)?.firstOrNull()
        val enemy = ds?.searchEnemies(targetEnemy)?.firstOrNull()
        
        // 构建攻击者属性（基础属性 + 装备加成）
        val baseAtk = char?.baseStats?.attack ?: 700.0
        val baseHp = char?.baseStats?.hp ?: 900.0
        val baseSpd = char?.baseStats?.speed ?: 100.0
        
        val attackerStats = CombatStats(
            level = 80,
            attack = baseAtk * 3.5,  // 假设装备加成约250%
            defense = char?.baseStats?.defense ?: 400.0 * 2.0,
            maxHp = baseHp * 3.0,
            speed = baseSpd + 20,  // 副词条补正
            critRate = 0.65,
            critDmg = 1.50
        )
        
        // 构建敌人防御属性
        val defenderStats = EnemyDefensiveStats(
            defense = enemy?.stats?.defense ?: 800.0,
            resistance = enemy?.resistance?.values?.average()?.takeIf { it > 0 } ?: 0.2,
            toughness = enemy?.toughness ?: 200
        )
        
        // 使用 DamageCalculator 计算
        val basicSkill = char?.skills?.firstOrNull { it.type == SkillType.BASIC } 
            ?: Skill("basic", SkillType.BASIC, "普攻", "", listOf(ScalingEntry(StatType.ATK, 1.0)))
        val ultSkill = char?.skills?.firstOrNull { it.type == SkillType.ULTIMATE }
            ?: Skill("ult", SkillType.ULTIMATE, "终结技", "", listOf(ScalingEntry(StatType.ATK, 4.0)))
        
        val context = DamageContext(critRoll = 0.3) // 70%暴击率下期望
        val expectedDamage = damageCalculator.calculateExpectedDamage(
            attackerStats, defenderStats, basicSkill, context
        )
        val ultDamage = damageCalculator.calculateExpectedDamage(
            attackerStats, defenderStats, ultSkill, context
        )
        
        // 估算击杀轮数
        val enemyHp = enemy?.stats?.hp ?: 40000.0
        val totalCycleDmg = expectedDamage * 2 + ultDamage * 1  // 2普攻+1终结技每轮
        val cycles = if (totalCycleDmg > 0) kotlin.math.ceil(enemyHp / totalCycleDmg).toInt().coerceAtLeast(1) else 3
        
        return mapOf(
            "character" to (char?.name ?: characterId),
            "target" to (enemy?.name ?: targetEnemy),
            "character_base_atk" to baseAtk,
            "estimated_basic_damage" to expectedDamage,
            "estimated_ult_damage" to ultDamage,
            "estimated_cycles" to cycles,
            "enemy_hp" to enemyHp,
            "enemy_toughness" to (enemy?.toughness ?: 200),
            "enemy_location" to (enemy?.location ?: "未知"),
            "summary" to "${char?.name ?: characterId} 对 ${enemy?.name ?: targetEnemy} 的普攻期望伤害约 ${"%.0f".format(expectedDamage)}，终结技约 ${"%.0f".format(ultDamage)}，预计 ${cycles} 轮击杀"
        )
    }
    
    private fun executeCalculateDamage(parameters: Map<String, Any?>): Map<String, Any> {
        val characterId = getParam(parameters, "character_id", "name") ?: ""
        val enemyName = getParam(parameters, "target_enemy", "enemy_name") ?: ""
        val ds = dataSource
        
        val char = ds?.searchCharacters(characterId)?.firstOrNull()
        val enemy = ds?.searchEnemies(enemyName)?.firstOrNull()
        
        // 使用角色真实数据构建战斗属性
        val baseAtk = char?.baseStats?.attack ?: 700.0
        val baseHp = char?.baseStats?.hp ?: 900.0
        val baseSpd = char?.baseStats?.speed ?: 100.0
        
        val attackerStats = CombatStats(
            level = 80,
            attack = baseAtk * 3.5,
            defense = char?.baseStats?.defense ?: 400.0 * 2.0,
            maxHp = baseHp * 3.0,
            speed = baseSpd + 15,
            critRate = (char?.baseStats?.critRate ?: 0.05) + 0.60,  // 装备补正
            critDmg = (char?.baseStats?.critDmg ?: 0.50) + 1.0
        )
        
        val defenderStats = EnemyDefensiveStats(
            defense = enemy?.stats?.defense ?: 800.0,
            resistance = enemy?.resistance?.values?.average()?.takeIf { it > 0 } ?: 0.2,
            toughness = enemy?.toughness ?: 200
        )
        
        val context = DamageContext(critRoll = 0.5)
        val skills = char?.skills?.take(2) ?: listOf(
            Skill("basic", SkillType.BASIC, "普攻", "", listOf(ScalingEntry(StatType.ATK, 1.0)))
        )
        
        val results = skills.map { skill ->
            val dmg = damageCalculator.calculateExpectedDamage(attackerStats, defenderStats, skill, context)
            mapOf("skill" to skill.name, "expected_damage" to dmg)
        }
        
        return mapOf(
            "character" to (char?.name ?: characterId),
            "target" to (enemy?.name ?: enemyName),
            "attacker_stats" to mapOf(
                "attack" to attackerStats.attack,
                "crit_rate" to attackerStats.critRate,
                "crit_dmg" to attackerStats.critDmg,
                "speed" to attackerStats.speed
            ),
            "defender_stats" to mapOf(
                "defense" to defenderStats.defense,
                "resistance" to defenderStats.resistance,
                "toughness" to defenderStats.toughness
            ),
            "damage_results" to results,
            "summary" to "${char?.name ?: characterId} 对 ${enemy?.name ?: enemyName} 的各技能期望伤害: ${results.joinToString(" | ") { "${it["skill"]}: ${"%.0f".format(it["expected_damage"])}" }}"
        )
    }
    
    // === 遗器分析工具 ===
    
    private fun executeScoreRelics(parameters: Map<String, Any?>): Map<String, Any> {
        val characterId = parameters["character_id"] as? String ?: ""
        val buildType = parameters.getOrDefault("build_type", "DPS") as String
        
        val type = when (buildType) {
            "SUPPORT" -> BuildType.SUPPORT
            "SUSTAIN" -> BuildType.SUSTAIN
            "HYBRID" -> BuildType.HYBRID
            else -> BuildType.DPS
        }
        
        val weights = relicScorer.getCharacterWeights(characterId, type)
        
        // 简化评分结果
        return mapOf(
            "character_id" to characterId,
            "build_type" to buildType,
            "weights" to weights.map { mapOf("stat" to it.statType.name, "weight" to it.weight, "effective" to it.isEffective) },
            "summary" to "获取到${characterId}的${buildType}权重配置"
        )
    }
    
    private fun executeRecommendRelicSet(parameters: Map<String, Any?>): Map<String, Any> {
        val characterId = parameters["character_id"] as? String ?: ""
        val path = parameters.getOrDefault("path", "DPS") as String
        
        // 简化推荐
        val recommendedSets = when (path) {
            "DPS" -> listOf("猛攻", "天才", "浮空")
            "SUPPORT" -> listOf("流放", "老者", "圣骑士")
            else -> listOf("猛攻", "天才")
        }
        
        return mapOf(
            "character_id" to characterId,
            "recommended_sets" to recommendedSets,
            "reasoning" to "根据角色定位推荐以上套装"
        )
    }
    
    private fun executeSuggestRelicUpgrade(parameters: Map<String, Any?>): Map<String, Any> {
        // 简化升级建议
        return mapOf(
            "slot" to "躯干",
            "current_main_stat" to "生命%",
            "recommended_main_stat" to "暴击率",
            "priority" to 1,
            "reasoning" to "输出角色建议使用暴击率躯干"
        )
    }
    
    // === 配队对比工具 ===
    
    private fun executeAnalyzeTeam(parameters: Map<String, Any?>): Map<String, Any> {
        val teamId = parameters["team_id"] as? String ?: ""
        
        // 简化分析结果
        return mapOf(
            "team_id" to teamId,
            "synergy_score" to 75.0,
            "strengths" to listOf("有生存位", "属性覆盖全面"),
            "weaknesses" to listOf("战技点可能紧张"),
            "suggestions" to listOf("建议添加同谐角色")
        )
    }
    
    private fun executeCompareTeams(parameters: Map<String, Any?>): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val teams = parameters["teams"] as? List<String> ?: emptyList()
        
        // 简化对比结果
        val rankingsMap = mutableMapOf<String, Int>()
        teams.getOrNull(0)?.let { rankingsMap[it] = 1 }
        teams.getOrNull(1)?.let { rankingsMap[it] = 2 }
        return mapOf(
            "teams" to teams,
            "rankings" to rankingsMap,
            "recommended_team" to (teams.firstOrNull() ?: ""),
            "comparison" to mapOf("协同评分" to 75.0, "DPS" to 50000.0)
        )
    }
    
    private fun executeSuggestTeam(parameters: Map<String, Any?>): Map<String, Any> {
        val targetContent = parameters["target_content"] as? String ?: "混沌"
        val mainDps = parameters["main_dps"] as? String ?: ""
        
        // 简化推荐
        return mapOf(
            "target_content" to targetContent,
            "main_dps" to mainDps,
            "suggested_team" to listOf(mainDps, "银狼", "驭空", "白露"),
            "reasoning" to "推荐该队伍进行${targetContent}"
        )
    }
    
    // === 数据查询工具（为 LLM 提供的真实数据接口）===
    
    /** 带别名解析的参数获取 */
    private fun getParam(parameters: Map<String, Any?>, vararg names: String): String? {
        for (name in names) {
            val value = parameters[name] as? String
            if (!value.isNullOrBlank()) return value.trim()
        }
        return null
    }

    private fun executeGetCharacterInfo(parameters: Map<String, Any?>): Map<String, Any> {
        val name = getParam(parameters, "name", "character_id", "character_name")?.lowercase() ?: ""
        if (name.isEmpty()) return mapOf("error" to "请提供角色名称，参数名支持: name/character_id")
        
        val ds = dataSource ?: return mapOf("error" to "数据源不可用")
        val chars = ds.searchCharacters(name)
        
        if (chars.isEmpty()) {
            val allNames = ds.getAllCharacters().map { it.name }
            return mapOf(
                "found" to false,
                "query" to name,
                "available_characters" to allNames,
                "message" to "未找到角色，当前数据中的角色: ${allNames.joinToString("、")}"
            )
        }
        
        val c = chars.first()
        return mapOf(
            "found" to true,
            "id" to c.id,
            "name" to c.name,
            "rarity" to c.rarity,
            "path" to c.path.displayName,
            "element" to c.element.name,
            "base_stats" to mapOf(
                "hp" to c.baseStats.hp,
                "attack" to c.baseStats.attack,
                "defense" to c.baseStats.defense,
                "speed" to c.baseStats.speed
            ),
            "skills" to c.skills.map { s -> mapOf("name" to s.name, "type" to s.type.name, "description" to s.description) },
            "eidolons" to c.eidolons.size
        )
    }
    
    private fun executeListCharacters(parameters: Map<String, Any?>): Map<String, Any> {
        val ds = dataSource ?: return mapOf("error" to "数据源不可用")
        val all = ds.getAllCharacters()
        return mapOf(
            "count" to all.size,
            "characters" to all.map { c ->
                mapOf("id" to c.id, "name" to c.name, "rarity" to c.rarity, 
                      "path" to c.path.displayName, "element" to c.element.name)
            }
        )
    }
    
    private fun executeGetLightConeInfo(parameters: Map<String, Any?>): Map<String, Any> {
        val name = getParam(parameters, "name", "light_cone_id", "light_cone_name")?.lowercase() ?: ""
        val ds = dataSource ?: return mapOf("error" to "数据源不可用")
        if (name.isEmpty()) {
            return mapOf("light_cones" to ds.getAllLightCones().map { it.name })
        }
        val lc = ds.searchLightCones(name).firstOrNull()
            ?: return mapOf("found" to false, "available" to ds.getAllLightCones().map { it.name })
        return mapOf(
            "found" to true, "name" to lc.name, "rarity" to lc.rarity,
            "path" to lc.path.displayName,
            "skill" to lc.skill.name,
            "skill_description" to lc.skill.description
        )
    }
    
    private fun executeGetRelicSetInfo(parameters: Map<String, Any?>): Map<String, Any> {
        val name = getParam(parameters, "name", "set_name", "relic_set_name")?.lowercase() ?: ""
        val ds = dataSource ?: return mapOf("error" to "数据源不可用")
        if (name.isEmpty()) {
            return mapOf("relic_sets" to ds.getAllRelicSets().map { it.name })
        }
        val set = ds.searchRelicSets(name).firstOrNull()
            ?: return mapOf("found" to false, "available" to ds.getAllRelicSets().map { it.name })
        return mapOf(
            "found" to true, "name" to set.name, "type" to set.type.name,
            "bonuses" to set.setBonuses.map { b ->
                mapOf("required" to b.requiredCount, "effects" to b.effects.map { e ->
                    mapOf("type" to e.type.name, "value" to e.value)
                })
            }
        )
    }
    
    private fun executeGetEnemyInfo(parameters: Map<String, Any?>): Map<String, Any> {
        val name = getParam(parameters, "name", "enemy_name", "target")?.lowercase() ?: ""
        val ds = dataSource ?: return mapOf("error" to "数据源不可用")
        if (name.isEmpty()) {
            return mapOf("enemies" to ds.getAllEnemies().map { it.name })
        }
        val e = ds.searchEnemies(name).firstOrNull()
            ?: return mapOf("found" to false, "available" to ds.getAllEnemies().map { it.name })
        return mapOf(
            "found" to true, "name" to e.name, "level" to e.level,
            "toughness" to e.toughness, "weakness" to e.weakness.map { it.name },
            "location" to (e.location ?: "未知")
        )
    }
    
    private fun executeGetRecommendedBuild(parameters: Map<String, Any?>): Map<String, Any> {
        val name = getParam(parameters, "character_name", "name", "character_id") ?: ""
        val ds = dataSource ?: return mapOf("error" to "数据源不可用")
        val char = ds.searchCharacters(name).firstOrNull()
            ?: return mapOf("error" to "未找到角色: $name")
        
        // 根据命途和属性推荐实际遗器套装
        val allSets = ds.getAllRelicSets()
        val path = char.path
        val element = char.element
        
        // 通用输出套装（2件套+4件套）
        val generalDps2pc = allSets.filter { it.type == RelicSetType.ORNAMENT }
            .filter { s -> s.setBonuses.any { it.effects.any { e ->
                e.type == EffectType.CRIT_RATE_UP || e.type == EffectType.CRIT_DMG_UP || e.type == EffectType.ATK_UP
            }}}
        val generalDps4pc = allSets.filter { it.type == RelicSetType.RELIC }
            .filter { s -> s.setBonuses.any { it.effects.any { e ->
                e.type == EffectType.CRIT_RATE_UP || e.type == EffectType.CRIT_DMG_UP || e.type == EffectType.DMG_UP
            }}}
        
        // 根据命途选择推荐
        val (recommended4pc, recommended2pc) = when (path) {
            PathType.巡猎, PathType.毁灭, PathType.智识 -> {
                val four = generalDps4pc.take(3).map { it.name }
                val two = generalDps2pc.take(3).map { it.name }
                four to two
            }
            PathType.同谐, PathType.虚无 -> {
                val four = allSets.filter { it.type == RelicSetType.RELIC }
                    .take(3).map { it.name }
                val two = generalDps2pc.filter { s ->
                    s.setBonuses.any { it.effects.any { e ->
                        e.type == EffectType.ENERGY_REGEN_UP
                    }}
                }.take(3).map { it.name }
                four to two
            }
            PathType.存护 -> {
                val four = allSets.filter { it.type == RelicSetType.RELIC }
                    .filter { s -> s.setBonuses.any { it.effects.any { e ->
                        e.type == EffectType.DEF_UP || e.type == EffectType.HP_UP
                    }}}
                    .take(3).map { it.name }
                val two = allSets.filter { it.type == RelicSetType.ORNAMENT }
                    .filter { s -> s.setBonuses.any { it.effects.any { e ->
                        e.type == EffectType.DEF_UP || e.type == EffectType.EFFECT_RES_UP
                    }}}
                    .take(3).map { it.name }
                four to two
            }
            PathType.丰饶 -> {
                val four = allSets.filter { it.type == RelicSetType.RELIC }
                    .filter { s -> s.setBonuses.any { it.effects.any { e ->
                        e.type == EffectType.HP_UP
                    }}}
                    .take(3).map { it.name }
                val two = allSets.filter { it.type == RelicSetType.ORNAMENT }
                    .filter { s -> s.setBonuses.any { it.effects.any { e ->
                        e.type == EffectType.HP_UP || e.type == EffectType.ENERGY_REGEN_UP
                    }}}
                    .take(3).map { it.name }
                four to two
            }
            else -> generalDps4pc.take(3).map { it.name } to generalDps2pc.take(3).map { it.name }
        }
        
        val recommendedMainStats = when (path) {
            PathType.毁灭, PathType.巡猎 -> 
                listOf("躯干: 暴击率/暴击伤害", "脚部: 速度/攻击%", "位面球: ${element.name}伤害加成", "连结绳: 攻击%/充能")
            PathType.智识 -> 
                listOf("躯干: 暴击率/暴击伤害", "脚部: 速度/攻击%", "位面球: ${element.name}伤害加成", "连结绳: 攻击%/充能")
            PathType.同谐 -> 
                listOf("躯干: 暴击率/生命%", "脚部: 速度", "位面球: 生命%/攻击%", "连结绳: 充能")
            PathType.虚无 -> 
                listOf("躯干: 效果命中/暴击率", "脚部: 速度", "位面球: 攻击%/${element.name}伤害", "连结绳: 充能/攻击%")
            PathType.存护 -> 
                listOf("躯干: 防御%/生命%", "脚部: 速度/防御%", "位面球: 防御%/生命%", "连结绳: 充能/防御%")
            PathType.丰饶 -> 
                listOf("躯干: 治疗加成/生命%", "脚部: 速度", "位面球: 生命%", "连结绳: 充能/生命%")
            else -> listOf("躯干: 暴击率", "脚部: 速度", "位面球: 攻击%", "连结绳: 攻击%")
        }
        
        return mapOf(
            "character" to char.name,
            "path" to path.displayName,
            "element" to element.name,
            "rarity" to "${char.rarity}星",
            "recommended_relics" to mapOf(
                "四件套推荐" to recommended4pc,
                "二件套推荐" to recommended2pc
            ),
            "recommended_main_stats" to recommendedMainStats,
            "summary" to "${char.name}（${path.displayName}·${element.name}）推荐使用 ${recommended4pc.firstOrNull() ?: "输出"} 四件套 + ${recommended2pc.firstOrNull() ?: "通用"} 二件套"
        )
    }
// === 升级分析工具 ===
    
    /** 安全获取 Int 参数（支持 String→Int 自动转换） */
    private fun safeInt(parameters: Map<String, Any?>, key: String, default: Int = 0): Int {
        val value = parameters[key]
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }

    /** 安全获取 Double 参数（支持 String→Double 自动转换） */
    private fun safeDouble(parameters: Map<String, Any?>, key: String, default: Double = 0.0): Double {
        val value = parameters[key]
        return when (value) {
            is Double -> value
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    private fun executeAnalyzeEidolon(parameters: Map<String, Any?>): Map<String, Any> {
        val characterId = parameters["character_id"] as? String ?: ""
        val fromEidolon = safeInt(parameters, "from_eidolon", 0)
        val toEidolon = safeInt(parameters, "to_eidolon", 6)
        val benefits = eidolonAnalyzer.calculateBenefit(characterId, fromEidolon, toEidolon)
        val summary = eidolonAnalyzer.getFullEidolonSummary(characterId)
        
        return mapOf(
            "character_id" to characterId,
            "from_eidolon" to fromEidolon,
            "to_eidolon" to toEidolon,
            "benefits" to benefits.map { 
                mapOf(
                    "rank" to it.rank,
                    "rating" to it.overallRating.displayName,
                    "damage_increase" to it.damageIncrease
                )
            },
            "total_benefit" to summary.totalBenefitE6,
            "key_eidolons" to summary.keyEidolons,
            "recommended_target" to summary.recommendedTarget
        )
    }
    
    private fun executeAnalyzeLightCone(parameters: Map<String, Any?>): Map<String, Any> {
        val lightConeId = parameters["light_cone_id"] as? String ?: ""
        val fromLevel = safeInt(parameters, "from_level", 1)
        val toLevel = safeInt(parameters, "to_level", 5)
        
        val benefits = lightConeAnalyzer.calculateSuperimposeBenefit(lightConeId, fromLevel, toLevel)
        
        return mapOf(
            "light_cone_id" to lightConeId,
            "from_level" to fromLevel,
            "to_level" to toLevel,
            "benefits" to benefits.map {
                mapOf(
                    "level" to it.level,
                    "rating" to it.overallRating.displayName,
                    "damage_increase" to it.damageIncrease
                )
            },
            "total_benefit" to benefits.sumOf { it.damageIncrease ?: 0.0 }
        )
    }
    
    private fun executeCompareUpgradePath(parameters: Map<String, Any?>): Map<String, Any> {
        val characterId = parameters["character_id"] as? String ?: ""
        
        // 简化的路径对比
        return mapOf(
            "character_id" to characterId,
            "option_1" to mapOf(
                "type" to "星魂",
                "target" to 2,
                "benefit" to 25.0,
                "cost" to 100,
                "efficiency" to "A"
            ),
            "option_2" to mapOf(
                "type" to "精炼",
                "target" to 3,
                "benefit" to 20.0,
                "cost" to 240,
                "efficiency" to "B"
            ),
            "recommendation" to "星魂提升性价比更高，建议优先补星魂"
        )
    }
    
    /** 构建工具定义 */
    private fun buildToolDefinitions(): Map<String, ToolDefinition> {
        return mapOf(
            "simulate_battle" to ToolDefinition(
                name = "simulate_battle",
                description = "执行战斗模拟，返回伤害统计",
                parameters = listOf(
                    ToolParameter("character_id", ParameterType.STRING, "角色ID", true),
                    ToolParameter("target_enemy", ParameterType.STRING, "目标敌人", false, "default")
                ),
                module = "battle",
                requiredIntent = Intent.SIMULATE_BATTLE
            ),
            "calculate_damage" to ToolDefinition(
                name = "calculate_damage",
                description = "计算单次技能伤害",
                parameters = listOf(
                    ToolParameter("attacker_stats", ParameterType.OBJECT, "攻击者属性", true),
                    ToolParameter("skill_id", ParameterType.STRING, "技能ID", true)
                ),
                module = "battle",
                requiredIntent = Intent.CALCULATE_DAMAGE
            ),
            "score_relics" to ToolDefinition(
                name = "score_relics",
                description = "给角色当前遗器评分",
                parameters = listOf(
                    ToolParameter("character_id", ParameterType.STRING, "角色ID", true),
                    ToolParameter("build_type", ParameterType.STRING, "构建类型", false, "DPS")
                ),
                module = "relic",
                requiredIntent = Intent.SCORE_CHARACTER_RELICS
            ),
            "recommend_relic_set" to ToolDefinition(
                name = "recommend_relic_set",
                description = "推荐最优遗器套装",
                parameters = listOf(
                    ToolParameter("character_id", ParameterType.STRING, "角色ID", true),
                    ToolParameter("path", ParameterType.STRING, "角色定位", false, "DPS")
                ),
                module = "relic",
                requiredIntent = Intent.RECOMMEND_RELIC_SET
            ),
            "suggest_relic_upgrade" to ToolDefinition(
                name = "suggest_relic_upgrade",
                description = "给出遗器升级建议",
                parameters = listOf(
                    ToolParameter("character_id", ParameterType.STRING, "角色ID", true)
                ),
                module = "relic",
                requiredIntent = Intent.SUGGEST_RELIC_UPGRADE
            ),
            "analyze_team" to ToolDefinition(
                name = "analyze_team",
                description = "分析队伍协同",
                parameters = listOf(
                    ToolParameter("team_id", ParameterType.STRING, "队伍ID", true)
                ),
                module = "team",
                requiredIntent = Intent.ANALYZE_TEAM
            ),
            "compare_teams" to ToolDefinition(
                name = "compare_teams",
                description = "对比多支队伍",
                parameters = listOf(
                    ToolParameter("teams", ParameterType.LIST, "队伍ID列表", true)
                ),
                module = "team",
                requiredIntent = Intent.COMPARE_TEAMS
            ),
            "suggest_team" to ToolDefinition(
                name = "suggest_team",
                description = "根据约束推荐队伍",
                parameters = listOf(
                    ToolParameter("target_content", ParameterType.STRING, "目标副本", false),
                    ToolParameter("main_dps", ParameterType.STRING, "主C", true)
                ),
                module = "team",
                requiredIntent = Intent.SUGGEST_TEAM
            ),
            "analyze_eidolon" to ToolDefinition(
                name = "analyze_eidolon",
                description = "分析星魂提升收益",
                parameters = listOf(
                    ToolParameter("character_id", ParameterType.STRING, "角色ID", true),
                    ToolParameter("from_eidolon", ParameterType.INT, "当前星魂", false, "0"),
                    ToolParameter("to_eidolon", ParameterType.INT, "目标星魂", false, "6")
                ),
                module = "upgrade",
                requiredIntent = Intent.ANALYZE_EIDOLON
            ),
            "analyze_lightcone" to ToolDefinition(
                name = "analyze_lightcone",
                description = "分析光锥选择/精炼",
                parameters = listOf(
                    ToolParameter("light_cone_id", ParameterType.STRING, "光锥ID", true),
                    ToolParameter("from_level", ParameterType.INT, "当前精炼", false, "1"),
                    ToolParameter("to_level", ParameterType.INT, "目标精炼", false, "5")
                ),
                module = "upgrade",
                requiredIntent = Intent.ANALYZE_LIGHTCONE
            ),
            "compare_upgrade_path" to ToolDefinition(
                name = "compare_upgrade_path",
                description = "对比不同升级方案",
                parameters = listOf(
                    ToolParameter("character_id", ParameterType.STRING, "角色ID", true)
                ),
                module = "upgrade",
                requiredIntent = Intent.COMPARE_UPGRADE_PATH
            ),
            // === 数据查询工具（LLM 用） ===
            "get_character_info" to ToolDefinition(
                name = "get_character_info",
                description = "获取角色详细信息（基础属性、技能、星魂等）",
                parameters = listOf(
                    ToolParameter("name", ParameterType.STRING, "角色名称（支持模糊搜索）", true)
                ),
                module = "data",
                requiredIntent = Intent.QUERY_INFO
            ),
            "list_characters" to ToolDefinition(
                name = "list_characters",
                description = "列出所有可用角色",
                parameters = emptyList(),
                module = "data",
                requiredIntent = Intent.QUERY_INFO
            ),
            "get_light_cone_info" to ToolDefinition(
                name = "get_light_cone_info",
                description = "获取光锥详细信息",
                parameters = listOf(
                    ToolParameter("name", ParameterType.STRING, "光锥名称（支持模糊搜索，留空列出所有）", false)
                ),
                module = "data",
                requiredIntent = Intent.QUERY_INFO
            ),
            "get_relic_set_info" to ToolDefinition(
                name = "get_relic_set_info",
                description = "获取遗器套装详细信息",
                parameters = listOf(
                    ToolParameter("name", ParameterType.STRING, "套装名称（支持模糊搜索，留空列出所有）", false)
                ),
                module = "data",
                requiredIntent = Intent.QUERY_INFO
            ),
            "get_enemy_info" to ToolDefinition(
                name = "get_enemy_info",
                description = "获取敌人详细信息（弱点、韧性等）",
                parameters = listOf(
                    ToolParameter("name", ParameterType.STRING, "敌人名称（支持模糊搜索，留空列出所有）", false)
                ),
                module = "data",
                requiredIntent = Intent.QUERY_INFO
            ),
            "get_recommended_build" to ToolDefinition(
                name = "get_recommended_build",
                description = "获取角色推荐遗器配装方案",
                parameters = listOf(
                    ToolParameter("character_name", ParameterType.STRING, "角色名称", true)
                ),
                module = "data",
                requiredIntent = Intent.RECOMMEND_RELIC_SET
            )
        )
    }
}