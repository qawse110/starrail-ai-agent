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
        val baseCritRate = char?.baseStats?.critRate ?: 0.05
        val baseCritDmg = char?.baseStats?.critDmg ?: 0.50
        
        val attackerStats = CombatStats(
            level = 80,
            attack = baseAtk * 3.5,
            defense = char?.baseStats?.defense ?: 400.0 * 2.0,
            maxHp = baseHp * 3.0,
            speed = baseSpd + 15,
            critRate = baseCritRate + 0.60,
            critDmg = baseCritDmg + 1.0,
            elementalDmgBonus = 0.389,  // 位面球38.9%
            dmgBonus = 0.0
        )
        
        val defenderStats = EnemyDefensiveStats(
            defense = enemy?.stats?.defense ?: 800.0,
            resistance = enemy?.resistance?.values?.average()?.takeIf { it > 0 } ?: 0.2,
            toughness = enemy?.toughness ?: 200
        )
        
        // 计算全部技能伤害+乘区分解
        val context = DamageContext(critRoll = 0.5)
        val skills = char?.skills ?: listOf(
            Skill("basic", SkillType.BASIC, "普攻", "", listOf(ScalingEntry(StatType.ATK, 1.0)))
        )
        
        val results = skills.map { skill ->
            val result = damageCalculator.calculateDamage(attackerStats, defenderStats, skill, context)
            mapOf(
                "skill" to skill.name,
                "type" to skill.type.name,
                "base_damage" to result.breakdown.baseDamage,
                "multiplier_zone" to result.breakdown.multiplierZone,
                "bonus_zone" to result.breakdown.damageBonusZone,
                "defense_zone" to result.breakdown.defenseZone,
                "resistance_zone" to result.breakdown.resistanceZone,
                "crit_zone" to result.breakdown.critZone,
                "expected_damage" to result.expectedDamage,
                "crit_damage" to result.critDamage
            )
        }
        
        // 计算普攻连携循环伤害（E+Q+E 标准轮）
        val basicSkill = skills.firstOrNull { it.type == SkillType.BASIC }
        val skillSkill = skills.firstOrNull { it.type == SkillType.SKILL }
        val ultSkill = skills.firstOrNull { it.type == SkillType.ULTIMATE }
        
        val basicDmg = if (basicSkill != null)
            damageCalculator.calculateExpectedDamage(attackerStats, defenderStats, basicSkill, context) else 0.0
        val skillDmg = if (skillSkill != null)
            damageCalculator.calculateExpectedDamage(attackerStats, defenderStats, skillSkill, context) else 0.0
        val ultDmg = if (ultSkill != null)
            damageCalculator.calculateExpectedDamage(attackerStats, defenderStats, ultSkill, context) else 0.0
        
        val cycleDmg = skillDmg + ultDmg + basicDmg  // E+Q+A 标准循环
        val enemyHp = enemy?.stats?.hp ?: 40000.0
        val cyclesToKill = if (cycleDmg > 0) kotlin.math.ceil(enemyHp / cycleDmg).toInt() else 99
        
        return mapOf(
            "character" to (char?.name ?: characterId),
            "target" to (enemy?.name ?: enemyName),
            "attacker_stats" to mapOf(
                "attack" to attackerStats.attack,
                "crit_rate" to "${"%.1f".format(attackerStats.critRate * 100)}%",
                "crit_dmg" to "${"%.1f".format(attackerStats.critDmg * 100)}%",
                "speed" to attackerStats.speed
            ),
            "defender_stats" to mapOf(
                "defense" to defenderStats.defense,
                "resistance" to "${"%.0f".format(defenderStats.resistance * 100)}%",
                "toughness" to defenderStats.toughness,
                "estimated_hp" to enemyHp
            ),
            "damage_results" to results,
            "cycle_damage" to mapOf(
                "basic_expected" to basicDmg,
                "skill_expected" to skillDmg,
                "ultimate_expected" to ultDmg,
                "total_per_cycle" to cycleDmg,
                "estimated_cycles_to_kill" to cyclesToKill
            ),
            "summary" to buildString {
                appendLine("${char?.name ?: characterId} 对 ${enemy?.name ?: enemyName} 的技能伤害：")
                for (r in results) {
                    appendLine("${r["type"]}·${r["skill"]}: 期望 ${"%.0f".format(r["expected_damage"])} | 倍率区×${"%.2f".format(r["multiplier_zone"])} 增伤区×${"%.2f".format(r["bonus_zone"])} 防御区×${"%.2f".format(r["defense_zone"])} 抗性区×${"%.2f".format(r["resistance_zone"])}")
                }
                appendLine("标准循环(E+Q+A): 总伤 ${"%.0f".format(cycleDmg)}，约 ${cyclesToKill} 轮击杀")
            }
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
        
        // 光锥推荐：按命途筛选，拉表计算期望伤害
        val allCones = ds.getAllLightCones()
        val pathCones = allCones.filter { it.path == path }
        
        // 构建基础战斗属性（角色基础值 + 标准遗器加成）
        val baseAtk = char.baseStats.attack
        val baseHp = char.baseStats.hp
        val baseDef = char.baseStats.defense
        val baseSpd = char.baseStats.speed
        val baseCritRate = 0.05 + 0.32  // 基础5% + 躯干暴击率主词条32%
        val baseCritDmg = 0.50 + 0.30   // 基础50% + 副词条补正
        
        // 标准敌人（混沌12层精英）
        val standardEnemy = EnemyDefensiveStats(defense = 1100.0, resistance = 0.20, toughness = 200)
        val standardSkill = Skill("basic", SkillType.BASIC, "普攻", "",
            listOf(ScalingEntry(StatType.ATK, 1.0)))
        val calcContext = DamageContext(critRoll = 0.3)
        
        // 对每个光锥计算期望伤害
        val coneWithDamage = pathCones.map { lc ->
            // 光锥精炼1效果
            val lcEffect = lc.superimposeLevels.firstOrNull()?.effect
            var lcAttack = baseAtk + lc.baseStats.attack  // 加光锥白值
            var lcCritRate = baseCritRate
            var lcCritDmg = baseCritDmg
            var lcDmgBonus = 0.0
            var lcDefPen = 0.0
            var lcResPen = 0.0
            
            if (lcEffect != null) {
                when (lcEffect.type) {
                    EffectType.ATK_UP -> lcAttack = baseAtk * (1 + lcEffect.value) + lc.baseStats.attack
                    EffectType.CRIT_RATE_UP -> lcCritRate = baseCritRate + lcEffect.value
                    EffectType.CRIT_DMG_UP -> lcCritDmg = baseCritDmg + lcEffect.value
                    EffectType.DMG_UP -> lcDmgBonus = lcEffect.value
                    EffectType.ELEMENTAL_DMG_UP -> lcDmgBonus = lcEffect.value
                    EffectType.DEF_PENETRATION -> lcDefPen = lcEffect.value
                    EffectType.RES_PENETRATION -> lcResPen = lcEffect.value
                    else -> { /* 不直接影响伤害的效果忽略 */ }
                }
            }
            
            // 装备加成估算
            val lcStats = CombatStats(
                level = 80,
                attack = lcAttack * 2.2,  // 遗器攻击%加成
                defense = baseDef * 1.5,
                maxHp = baseHp * 2.5,
                speed = baseSpd + 20,
                critRate = lcCritRate,
                critDmg = lcCritDmg,
                dmgBonus = lcDmgBonus,
                elementalDmgBonus = 0.389,  // 位面球主词条
                defPenetration = lcDefPen,
                resPenetration = lcResPen
            )
            
            val dmg = damageCalculator.calculateExpectedDamage(lcStats, standardEnemy, standardSkill, calcContext)
            lc to dmg
        }.sortedByDescending { it.second }
        
        val recommendedCones = coneWithDamage.take(5).map { (lc, dmg) ->
            "${lc.name} ★${lc.rarity} — 期望伤害 ${"%.0f".format(dmg)} | ${lc.superimposeLevels.firstOrNull()?.description?.take(40) ?: ""}"
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
            "recommended_light_cones" to recommendedCones,
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
        val ds = dataSource
        val char = ds?.searchCharacters(characterId)?.firstOrNull()
        if (char == null) return mapOf("error" to "未找到角色: $characterId")
        
        // 构建基础战斗属性（0魂基准）
        val baseAtk = char.baseStats.attack
        val baseHp = char.baseStats.hp
        val baseDef = char.baseStats.defense
        val baseSpd = char.baseStats.speed
        val baseCr = char.baseStats.critRate + 0.60  // 遗器补正
        val baseCd = char.baseStats.critDmg + 1.0
        
        // 标准敌人 + 普攻技能
        val enemy = EnemyDefensiveStats(defense = 1100.0, resistance = 0.20, toughness = 200)
        val skill = Skill("basic", SkillType.BASIC, "普攻", "",
            listOf(ScalingEntry(StatType.ATK, 1.0)))
        val ctx = DamageContext(critRoll = 0.3)
        
        // 计算每个星魂等级的累计伤害
        val rankDamages = mutableListOf<Pair<Int, Double>>()
        for (rank in 0..toEidolon) {
            // 应用该星魂及之前所有星魂的效果
            var atkMult = 1.0
            var crAdd = 0.0
            var cdAdd = 0.0
            var dmgAdd = 0.0
            
            val appliedEidolons = char.eidolons.filter { it.rank <= rank }
            for (e in appliedEidolons) {
                for (effect in e.effects) {
                    when (effect.type) {
                        EffectType.ATK_UP -> atkMult += effect.value
                        EffectType.CRIT_RATE_UP -> crAdd += effect.value
                        EffectType.CRIT_DMG_UP -> cdAdd += effect.value
                        EffectType.DMG_UP -> dmgAdd += effect.value
                        else -> { }
                    }
                }
            }
            
            val stats = CombatStats(
                level = 80,
                attack = (baseAtk * 3.5) * (1 + atkMult),
                defense = baseDef * 1.5,
                maxHp = baseHp * 3.0,
                speed = baseSpd + 20,
                critRate = (baseCr + crAdd).coerceAtMost(1.0),
                critDmg = baseCd + cdAdd,
                dmgBonus = dmgAdd,
                elementalDmgBonus = 0.389
            )
            
            val dmg = damageCalculator.calculateExpectedDamage(stats, enemy, skill, ctx)
            rankDamages.add(rank to dmg)
        }
        
        // 生成星魂收益列表
        val benefits = mutableListOf<Map<String, Any>>()
        for (rank in (fromEidolon + 1)..toEidolon) {
            val baseDmg = rankDamages.firstOrNull { it.first == fromEidolon }?.second ?: rankDamages.first().second
            val currentDmg = rankDamages.firstOrNull { it.first == rank }?.second ?: 0.0
            val increasePct = if (baseDmg > 0) ((currentDmg / baseDmg) - 1.0) * 100 else 0.0
            
            val eidolon = char.eidolons.getOrNull(rank - 1)
            val rating = when {
                increasePct >= 25 -> "质变 ★"
                increasePct >= 15 -> "推荐"
                increasePct >= 8 -> "不错"
                else -> "一般"
            }
            
            benefits.add(mapOf(
                "rank" to rank,
                "name" to (eidolon?.name ?: "星魂$rank"),
                "description" to (eidolon?.description ?: ""),
                "base_expected_damage" to baseDmg,
                "new_expected_damage" to currentDmg,
                "damage_increase_pct" to increasePct,
                "rating" to rating
            ))
        }
        
        // 计算E6总提升
        val e0Dmg = rankDamages.firstOrNull { it.first == 0 }?.second ?: 1.0
        val e6Dmg = rankDamages.firstOrNull { it.first == 6 }?.second ?: 1.0
        val totalIncrease = if (e0Dmg > 0) ((e6Dmg / e0Dmg) - 1.0) * 100 else 0.0
        
        // 找最大收益星魂
        val bestRank = benefits.maxByOrNull { it["damage_increase_pct"] as? Double ?: 0.0 }
        
        return mapOf(
            "character" to char.name,
            "from_eidolon" to fromEidolon,
            "to_eidolon" to toEidolon,
            "e0_expected_damage" to e0Dmg,
            "e6_expected_damage" to e6Dmg,
            "total_e6_increase_pct" to totalIncrease,
            "best_rank" to (bestRank?.get("rank") ?: 0),
            "benefits" to benefits,
            "summary" to buildString {
                appendLine("${char.name} 星魂收益分析（基于拉表计算）：")
                appendLine("E0基准伤害: ${"%.0f".format(e0Dmg)}")
                for (b in benefits) {
                    appendLine("E${b["rank"]} ${b["name"]}: +${"%.1f".format(b["damage_increase_pct"])}% [${b["rating"]}]")
                }
                appendLine("E0→E6总提升: ${"%.1f".format(totalIncrease)}%")
                if (bestRank != null) {
                    appendLine("推荐优先解锁: E${bestRank["rank"]}（+${"%.1f".format(bestRank["damage_increase_pct"])}%）")
                }
            }
        )
    }
private fun executeAnalyzeLightCone(parameters: Map<String, Any?>): Map<String, Any> {
        val lightConeId = parameters["light_cone_id"] as? String ?: ""
        val fromLevel = safeInt(parameters, "from_level", 1)
        val toLevel = safeInt(parameters, "to_level", 5)
        val ds = dataSource
        
        val allCones = ds?.getAllLightCones() ?: return mapOf("error" to "数据源不可用")
        val lc = allCones.firstOrNull { it.id == lightConeId || it.name.contains(lightConeId, ignoreCase = true) }
        if (lc == null) return mapOf("error" to "未找到光锥: $lightConeId")
        
        // 标准角色模板 + 标准敌人
        val standardEnemy = EnemyDefensiveStats(defense = 1100.0, resistance = 0.20, toughness = 200)
        val skill = Skill("basic", SkillType.BASIC, "普攻", "",
            listOf(ScalingEntry(StatType.ATK, 1.0)))
        val ctx = DamageContext(critRoll = 0.3)
        
        // 计算每个精炼等级的期望伤害
        val levelDamages = mutableListOf<Pair<Int, Double>>()
        for (level in fromLevel..toLevel) {
            val si = lc.superimposeLevels.getOrNull(level - 1)
            var atkTotal = 700.0 * 3.5 + lc.baseStats.attack  // 基础ATK×遗器倍数 + 光锥白值
            var cr = 0.05 + 0.60
            var cd = 0.50 + 1.0
            var dmgBonus = 0.0
            var defPen = 0.0
            var resPen = 0.0
            
            if (si != null) {
                when (si.effect.type) {
                    EffectType.ATK_UP -> atkTotal = (700.0 * 3.5) * (1 + si.effect.value) + lc.baseStats.attack
                    EffectType.CRIT_RATE_UP -> cr = (0.05 + 0.60) + si.effect.value
                    EffectType.CRIT_DMG_UP -> cd = (0.50 + 1.0) + si.effect.value
                    EffectType.DMG_UP -> dmgBonus = si.effect.value
                    EffectType.ELEMENTAL_DMG_UP -> dmgBonus = si.effect.value
                    EffectType.DEF_PENETRATION -> defPen = si.effect.value
                    EffectType.RES_PENETRATION -> resPen = si.effect.value
                    else -> { }
                }
            }
            
            val stats = CombatStats(
                level = 80,
                attack = atkTotal,
                defense = 400.0 * 1.5,
                maxHp = 900.0 * 3.0,
                speed = 100.0 + 20,
                critRate = cr.coerceAtMost(1.0),
                critDmg = cd,
                dmgBonus = dmgBonus,
                elementalDmgBonus = 0.389,
                defPenetration = defPen,
                resPenetration = resPen
            )
            
            val dmg = damageCalculator.calculateExpectedDamage(stats, standardEnemy, skill, ctx)
            levelDamages.add(level to dmg)
        }
        
        // 生成精炼收益列表
        val benefits = mutableListOf<Map<String, Any>>()
        for (level in (fromLevel + 1)..toLevel) {
            val baseDmg = levelDamages.firstOrNull { it.first == fromLevel }?.second ?: levelDamages.first().second
            val currentDmg = levelDamages.firstOrNull { it.first == level }?.second ?: 0.0
            val increasePct = if (baseDmg > 0) ((currentDmg / baseDmg) - 1.0) * 100 else 0.0
            
            val si = lc.superimposeLevels.getOrNull(level - 1)
            benefits.add(mapOf(
                "level" to level,
                "description" to (si?.description ?: "精炼$level"),
                "expected_damage" to currentDmg,
                "increase_from_base_pct" to increasePct,
                "rating" to when {
                    increasePct >= 12 -> "推荐"
                    increasePct >= 6 -> "不错"
                    else -> "一般"
                }
            ))
        }
        
        val baseDmg = levelDamages.first().second
        val maxDmg = levelDamages.last().second
        val totalIncrease = if (baseDmg > 0) ((maxDmg / baseDmg) - 1.0) * 100 else 0.0
        
        return mapOf(
            "light_cone" to lc.name,
            "rarity" to lc.rarity,
            "path" to lc.path.displayName,
            "from_level" to fromLevel,
            "to_level" to toLevel,
            "base_expected_damage" to baseDmg,
            "max_expected_damage" to maxDmg,
            "total_increase_pct" to totalIncrease,
            "benefits" to benefits,
            "summary" to buildString {
                appendLine("${lc.name} 精炼收益分析（基于拉表计算）：")
                appendLine("精$fromLevel 基准伤害: ${"%.0f".format(baseDmg)}")
                for (b in benefits) {
                    appendLine("精${b["level"]}: +${"%.1f".format(b["increase_from_base_pct"])}% [${b["rating"]}]")
                }
                appendLine("精$fromLevel→精$toLevel 总提升: ${"%.1f".format(totalIncrease)}%")
            }
        )
    }
    
    private fun executeCompareUpgradePath(parameters: Map<String, Any?>): Map<String, Any> {
        val characterId = parameters["character_id"] as? String ?: ""
        val ds = dataSource
        val char = ds?.searchCharacters(characterId)?.firstOrNull()
        if (char == null) return mapOf("error" to "未找到角色: $characterId")
        
        // 标准敌人 + 普攻技能
        val enemy = EnemyDefensiveStats(defense = 1100.0, resistance = 0.20, toughness = 200)
        val skill = Skill("basic", SkillType.BASIC, "普攻", "",
            listOf(ScalingEntry(StatType.ATK, 1.0)))
        val ctx = DamageContext(critRoll = 0.3)
        
        val baseAtk = char.baseStats.attack
        val baseCr = char.baseStats.critRate + 0.60
        val baseCd = char.baseStats.critDmg + 1.0
        
        // E0 基准伤害
        fun makeStats(atkMul: Double, crAdd: Double, cdAdd: Double, dmgAdd: Double): CombatStats {
            return CombatStats(
                level = 80,
                attack = baseAtk * 3.5 * (1 + atkMul),
                defense = char.baseStats.defense * 1.5,
                maxHp = char.baseStats.hp * 3.0,
                speed = char.baseStats.speed + 20,
                critRate = (baseCr + crAdd).coerceAtMost(1.0),
                critDmg = baseCd + cdAdd,
                dmgBonus = dmgAdd,
                elementalDmgBonus = 0.389
            )
        }
        
        val e0Dmg = damageCalculator.calculateExpectedDamage(makeStats(0.0, 0.0, 0.0, 0.0), enemy, skill, ctx)
        
        // 方案A: 星魂提升至E2（取E1+E2效果累计）
        var eAtkMul = 0.0; var eCrAdd = 0.0; var eCdAdd = 0.0; var eDmgAdd = 0.0
        for (e in char.eidolons.filter { it.rank <= 2 }) {
            for (eff in e.effects) {
                when (eff.type) {
                    EffectType.ATK_UP -> eAtkMul += eff.value
                    EffectType.CRIT_RATE_UP -> eCrAdd += eff.value
                    EffectType.CRIT_DMG_UP -> eCdAdd += eff.value
                    EffectType.DMG_UP -> eDmgAdd += eff.value
                    else -> { }
                }
            }
        }
        val e2Dmg = damageCalculator.calculateExpectedDamage(makeStats(eAtkMul, eCrAdd, eCdAdd, eDmgAdd), enemy, skill, ctx)
        val e2Gain = if (e0Dmg > 0) ((e2Dmg / e0Dmg) - 1.0) * 100 else 0.0
        
        // 方案B: 星魂提升至E6
        var e6AtkMul = 0.0; var e6CrAdd = 0.0; var e6CdAdd = 0.0; var e6DmgAdd = 0.0
        for (e in char.eidolons) {
            for (eff in e.effects) {
                when (eff.type) {
                    EffectType.ATK_UP -> e6AtkMul += eff.value
                    EffectType.CRIT_RATE_UP -> e6CrAdd += eff.value
                    EffectType.CRIT_DMG_UP -> e6CdAdd += eff.value
                    EffectType.DMG_UP -> e6DmgAdd += eff.value
                    else -> { }
                }
            }
        }
        val e6Dmg = damageCalculator.calculateExpectedDamage(makeStats(e6AtkMul, e6CrAdd, e6CdAdd, e6DmgAdd), enemy, skill, ctx)
        val e6Gain = if (e0Dmg > 0) ((e6Dmg / e0Dmg) - 1.0) * 100 else 0.0
        
        // 方案C: 精炼从1→5（找适配光锥）
        val pathCones = ds?.getAllLightCones()?.filter { it.path == char.path } ?: emptyList()
        val bestCone = pathCones.maxByOrNull {
            damageCalculator.calculateExpectedDamage(makeStats(0.0, 0.0, 0.0, 0.0), enemy, skill, ctx)
        }
        var s1Gain = 0.0
        var s5Gain = 0.0
        if (bestCone != null) {
            val s1 = bestCone.superimposeLevels.firstOrNull()
            val s5 = bestCone.superimposeLevels.lastOrNull()
            
            fun coneStats(level: Int): CombatStats {
                val si = bestCone.superimposeLevels.getOrNull(level - 1)
                var atk = baseAtk * 3.5 + bestCone.baseStats.attack
                var cr = baseCr; var cd = baseCd; var dmg = 0.0; var defPen = 0.0; var resPen = 0.0
                if (si != null) {
                    when (si.effect.type) {
                        EffectType.ATK_UP -> atk = (baseAtk * 3.5) * (1 + si.effect.value) + bestCone.baseStats.attack
                        EffectType.CRIT_RATE_UP -> cr = baseCr + si.effect.value
                        EffectType.CRIT_DMG_UP -> cd = baseCd + si.effect.value
                        EffectType.DMG_UP -> dmg = si.effect.value
                        EffectType.ELEMENTAL_DMG_UP -> dmg = si.effect.value
                        EffectType.DEF_PENETRATION -> defPen = si.effect.value
                        EffectType.RES_PENETRATION -> resPen = si.effect.value
                        else -> { }
                    }
                }
                return CombatStats(80, atk, char.baseStats.defense * 1.5, char.baseStats.hp * 3.0,
                    char.baseStats.speed + 20, cr.coerceAtMost(1.0), cd, dmg, 0.389, defPen, resPen)
            }
            
            val s1Dmg = damageCalculator.calculateExpectedDamage(coneStats(1), enemy, skill, ctx)
            val s5Dmg = damageCalculator.calculateExpectedDamage(coneStats(5), enemy, skill, ctx)
            s1Gain = if (s1Dmg > e0Dmg) ((s1Dmg / e0Dmg) - 1.0) * 100 else 0.0
            s5Gain = if (s5Dmg > s1Dmg) ((s5Dmg / s1Dmg) - 1.0) * 100 else 0.0
        }
        
        // 排序推荐
        val options = listOf(
            mapOf("path" to "星魂 E0→E2", "damage_increase_pct" to e2Gain, "cost_estimate" to "中等"),
            mapOf("path" to "星魂 E0→E6", "damage_increase_pct" to e6Gain, "cost_estimate" to "高"),
            mapOf("path" to "光锥精炼 1→5", "damage_increase_pct" to s5Gain, "cost_estimate" to "高"),
            mapOf("path" to "光锥获取(精1)", "damage_increase_pct" to s1Gain, "cost_estimate" to "低~中")
        ).sortedByDescending { it["damage_increase_pct"] as? Double ?: 0.0 }
        
        return mapOf(
            "character" to char.name,
            "e0_baseline_damage" to e0Dmg,
            "options" to options,
            "recommendation" to "按伤害提升排序：${options.joinToString(" > ") { it["path"] as? String ?: "" }}"
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
                description = "获取角色推荐配装方案（含遗器套装、光锥、主词条）",
                parameters = listOf(
                    ToolParameter("character_name", ParameterType.STRING, "角色名称", true)
                ),
                module = "data",
                requiredIntent = Intent.RECOMMEND_RELIC_SET
            )
        )
    }
}