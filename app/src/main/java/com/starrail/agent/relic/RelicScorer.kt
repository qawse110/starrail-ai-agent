package com.starrail.agent.relic

import com.starrail.agent.core.model.*
import com.starrail.agent.player.PlayerRelic

/**
 * 遗器评分系统
 * 基于角色定位计算遗器评分、有效词条识别、最优套装推荐
 */

/** 遗器评分等级 */
enum class RelicGrade(val displayName: String, val minScore: Double) {
    SSS("SSS", 90.0),
    SS("SS", 80.0),
    S("S", 70.0),
    A("A", 60.0),
    B("B", 50.0),
    C("C", 30.0),
    D("D", 0.0);
    
    companion object {
        fun fromScore(score: Double): RelicGrade {
            return entries.find { score >= it.minScore } ?: D
        }
    }
}

/** 副词条权重 */
data class SubStatWeight(
    val statType: StatType,
    val weight: Double,              // 权重 0.0 - 1.0
    val isEffective: Boolean         // 是否为有效词条
)

/** 遗器评分结果 */
data class RelicScore(
    val overallScore: Double,        // 综合评分 (0-100)
    val mainStatScore: Double,       // 主词条评分
    val subStatScore: Double,        // 副词条评分
    val effectiveSubStats: Int,      // 有效副词条数量
    val totalRolls: Int,             // 总强化次数
    val effectiveRolls: Int,        // 有效强化次数
    val grade: RelicGrade,           // 等级
    val scoringDetails: ScoringDetails
)

/** 评分详情 */
data class ScoringDetails(
    val mainStatMatch: Boolean,       // 主词条是否匹配
    val effectiveSubStatTypes: List<StatType>,  // 有效副词条类型
    val wastedSubStatTypes: List<StatType>,     // 无效副词条类型
    val rollDistribution: Map<StatType, Int>,    // 各词条强化次数
    val recommendation: String      // 改进建议
)

/** 强化潜力 */
enum class UpgradePotential(val displayName: String) {
    HIGH("高潜力"),
    MEDIUM("中等潜力"),
    LOW("低潜力"),
    DONE("已毕业")
}

/** 有效词条分析结果 */
data class EffectiveSubStatAnalysis(
    val totalSubStats: Int,          // 总词条数
    val effectiveCount: Int,         // 有效词条数
    val deadCount: Int,              // 无效词条数
    val effectiveRolls: Int,         // 有效强化次数
    val wastedRolls: Int,            // 歪掉的强化次数
    val effectiveValue: Double,      // 有效词条价值总和
    val potential: UpgradePotential  // 强化潜力
)

/** 遗器评分器 */
class RelicScorer {
    
    /**
     * 获取角色推荐词条权重
     * @param characterId 角色ID
     * @param buildType 构建类型（主C/辅助/生存）
     */
    fun getCharacterWeights(characterId: String, buildType: BuildType): List<SubStatWeight> {
        // 根据角色特性返回对应权重
        // 简化版：实际需要根据角色模板读取详细配置
        return when (buildType) {
            BuildType.DPS -> getDpsWeights()
            BuildType.SUPPORT -> getSupportWeights()
            BuildType.SUSTAIN -> getSustainWeights()
            BuildType.HYBRID -> getHybridWeights()
        }
    }
    
    /** DPS角色权重（主C） */
    private fun getDpsWeights(): List<SubStatWeight> = listOf(
        SubStatWeight(StatType.CRIT_RATE, 1.0, true),
        SubStatWeight(StatType.CRIT_DMG, 1.0, true),
        SubStatWeight(StatType.ATK_PERCENT, 0.85, true),
        SubStatWeight(StatType.SPD, 0.7, true),
        SubStatWeight(StatType.BREAK_DMG, 0.6, true),
        SubStatWeight(StatType.ATK, 0.3, false),
        SubStatWeight(StatType.HP_PERCENT, 0.2, false),
        SubStatWeight(StatType.DEF_PERCENT, 0.1, false),
        SubStatWeight(StatType.EFFECT_HIT, 0.15, false),
        SubStatWeight(StatType.EFFECT_RES, 0.1, false)
    )
    
    /** 辅助角色权重 */
    private fun getSupportWeights(): List<SubStatWeight> = listOf(
        SubStatWeight(StatType.SPD, 0.9, true),
        SubStatWeight(StatType.EFFECT_HIT, 0.8, true),
        SubStatWeight(StatType.ENERGY_RATE, 0.75, true),
        SubStatWeight(StatType.HP_PERCENT, 0.6, true),
        SubStatWeight(StatType.DEF_PERCENT, 0.5, true),
        SubStatWeight(StatType.CRIT_RATE, 0.4, false),
        SubStatWeight(StatType.ATK_PERCENT, 0.3, false),
        SubStatWeight(StatType.EFFECT_RES, 0.3, false),
        SubStatWeight(StatType.ATK, 0.1, false),
        SubStatWeight(StatType.HP, 0.1, false)
    )
    
    /** 生存角色权重 */
    private fun getSustainWeights(): List<SubStatWeight> = listOf(
        SubStatWeight(StatType.HP_PERCENT, 0.9, true),
        SubStatWeight(StatType.DEF_PERCENT, 0.85, true),
        SubStatWeight(StatType.SPD, 0.7, true),
        SubStatWeight(StatType.EFFECT_RES, 0.5, true),
        SubStatWeight(StatType.HEAL_BONUS, 0.5, true),
        SubStatWeight(StatType.ENERGY_RATE, 0.4, false),
        SubStatWeight(StatType.CRIT_RATE, 0.2, false),
        SubStatWeight(StatType.ATK_PERCENT, 0.15, false),
        SubStatWeight(StatType.ATK, 0.05, false),
        SubStatWeight(StatType.HP, 0.1, false)
    )
    
    /** 混合角色权重 */
    private fun getHybridWeights(): List<SubStatWeight> = listOf(
        SubStatWeight(StatType.CRIT_RATE, 0.8, true),
        SubStatWeight(StatType.CRIT_DMG, 0.8, true),
        SubStatWeight(StatType.ATK_PERCENT, 0.75, true),
        SubStatWeight(StatType.SPD, 0.65, true),
        SubStatWeight(StatType.HP_PERCENT, 0.5, true),
        SubStatWeight(StatType.DEF_PERCENT, 0.4, true),
        SubStatWeight(StatType.BREAK_DMG, 0.4, true),
        SubStatWeight(StatType.ENERGY_RATE, 0.3, false),
        SubStatWeight(StatType.ATK, 0.2, false),
        SubStatWeight(StatType.HP, 0.1, false)
    )
    
    /**
     * 评分单件遗器
     * @param relic 玩家遗器
     * @param characterId 角色ID
     * @param weights 词条权重
     */
    fun scoreRelic(
        relic: PlayerRelic,
        characterId: String,
        weights: List<SubStatWeight>
    ): RelicScore {
        // 1. 计算主词条评分
        val mainStatScore = calculateMainStatScore(relic, weights)
        
        // 2. 计算副词条评分
        val (subStatScore, effectiveCount, effectiveRolls, details) = 
            calculateSubStatScore(relic, weights)
        
        // 3. 计算综合评分
        val overallScore = mainStatScore * 0.4 + subStatScore * 0.6
        
        return RelicScore(
            overallScore = overallScore.coerceIn(0.0, 100.0),
            mainStatScore = mainStatScore,
            subStatScore = subStatScore,
            effectiveSubStats = effectiveCount,
            totalRolls = relic.totalSubStatRolls,
            effectiveRolls = effectiveRolls,
            grade = RelicGrade.fromScore(overallScore),
            scoringDetails = details
        )
    }
    
    /** 计算主词条评分 */
    private fun calculateMainStatScore(
        relic: PlayerRelic,
        weights: List<SubStatWeight>
    ): Double {
        val recommendedMainStats = getRecommendedMainStats(relic.slot)
        val mainWeight = weights.find { it.statType == relic.mainStat.type }
        
        return if (relic.mainStat.type in recommendedMainStats && mainWeight != null) {
            // 主词条匹配：满分 × 权重
            100.0 * mainWeight.weight
        } else {
            // 主词条不匹配：低分
            20.0
        }
    }
    
    /** 计算副词条评分 */
    private fun calculateSubStatScore(
        relic: PlayerRelic,
        weights: List<SubStatWeight>
    ): Quadruple<Double, Int, Int, ScoringDetails> {
        var totalScore = 0.0
        var effectiveCount = 0
        var effectiveRolls = 0
        val effectiveTypes = mutableListOf<StatType>()
        val wastedTypes = mutableListOf<StatType>()
        val rollDistribution = mutableMapOf<StatType, Int>()
        
        // 每个副词条的基础分（简化版，假设每+3算一次强化）
        val rollPerSubStat = 3
        
        for (subStat in relic.subStats) {
            val weight = weights.find { it.statType == subStat.type }
            rollDistribution[subStat.type] = (rollDistribution[subStat.type] ?: 0) + 1
            
            if (weight != null && weight.isEffective) {
                totalScore += subStat.value * weight.weight * 100 + 10.0  // 百分比值 * 权重 * 100 + 有效词条基础分
                effectiveCount++
                effectiveRolls += rollPerSubStat
                effectiveTypes.add(subStat.type)
            } else {
                totalScore += subStat.value * 2  // 无效词条低分
                wastedTypes.add(subStat.type)
            }
        }
        
        val details = ScoringDetails(
            mainStatMatch = isMainStatMatch(relic),
            effectiveSubStatTypes = effectiveTypes,
            wastedSubStatTypes = wastedTypes,
            rollDistribution = rollDistribution,
            recommendation = generateRecommendation(relic, effectiveCount, wastedTypes)
        )
        
        return Quadruple(totalScore.coerceIn(0.0, 100.0), effectiveCount, effectiveRolls, details)
    }
    
    /** 检查主词条是否匹配 */
    private fun isMainStatMatch(relic: PlayerRelic): Boolean {
        val recommended = getRecommendedMainStats(relic.slot)
        return relic.mainStat.type in recommended
    }
    
    /** 获取推荐主词条 */
    private fun getRecommendedMainStats(slot: RelicSlot): Set<StatType> {
        return when (slot) {
            RelicSlot.头部 -> setOf(StatType.HP)
            RelicSlot.手部 -> setOf(StatType.ATK)
            RelicSlot.躯干 -> setOf(StatType.CRIT_RATE, StatType.CRIT_DMG, StatType.ATK_PERCENT, StatType.HP_PERCENT)
            RelicSlot.脚部 -> setOf(StatType.SPD, StatType.ATK_PERCENT, StatType.HP_PERCENT)
            RelicSlot.位面球 -> setOf(StatType.ATK_PERCENT, StatType.HP_PERCENT, StatType.ELEMENTAL_DMG_UP)
            RelicSlot.连结绳 -> setOf(StatType.ENERGY_RATE, StatType.ATK_PERCENT)
        }
    }
    
    /** 生成改进建议 */
    private fun generateRecommendation(
        relic: PlayerRelic,
        effectiveCount: Int,
        wastedTypes: List<StatType>
    ): String {
        return when {
            effectiveCount >= 3 -> "这件遗器质量不错，建议保留"
            effectiveCount == 2 -> "有提升空间，可作为过渡装备"
            wastedTypes.isNotEmpty() -> "副词条歪了（${wastedTypes.joinToString { it.name }}），建议作为狗粮"
            relic.level == 0 -> "未强化，可以考虑强化试试"
            else -> "建议继续强化寻找更好的替代品"
        }
    }
    
    /** 分析有效词条 */
    fun analyzeEffectiveSubStats(relic: PlayerRelic, weights: List<SubStatWeight>): EffectiveSubStatAnalysis {
        var effectiveCount = 0
        var effectiveRolls = 0
        var wastedRolls = 0
        
        for (subStat in relic.subStats) {
            val weight = weights.find { it.statType == subStat.type }
            if (weight != null && weight.isEffective) {
                effectiveCount++
                effectiveRolls += 3
            } else {
                wastedRolls += 3
            }
        }
        
        val potential = when {
            effectiveCount >= 3 && relic.level < 15 -> UpgradePotential.HIGH
            effectiveCount >= 2 && relic.level < 15 -> UpgradePotential.MEDIUM
            effectiveCount >= 2 -> UpgradePotential.DONE
            else -> UpgradePotential.LOW
        }
        
        return EffectiveSubStatAnalysis(
            totalSubStats = relic.subStats.size,
            effectiveCount = effectiveCount,
            deadCount = relic.subStats.size - effectiveCount,
            effectiveRolls = effectiveRolls,
            wastedRolls = wastedRolls,
            effectiveValue = effectiveCount * 20.0,  // 简化版
            potential = potential
        )
    }
}

/** 四元组数据类 */
data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/** 构建类型 */
enum class BuildType {
    DPS,      // 输出
    SUPPORT,  // 辅助
    SUSTAIN,  // 生存
    HYBRID    // 混合
}