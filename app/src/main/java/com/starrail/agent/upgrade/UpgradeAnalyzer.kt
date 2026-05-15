package com.starrail.agent.upgrade

import com.starrail.agent.core.model.*

/**
 * 星魂/精炼提升分析模块
 * 计算星魂与光锥精炼对角色输出/治疗/辅助能力的提升收益
 */

/** 升级评级 */
enum class UpgradeRating(val displayName: String) {
    MUST_HAVE("质变"),
    RECOMMENDED("强烈推荐"),
    GOOD("不错"),
    AVERAGE("一般"),
    SKIP("跳过")
}

/** 星魂收益 */
data class EidolonBenefit(
    val rank: Int,                           // 星魂等级 0→1, 1→2, ...
    val name: String,
    val description: String,
    val damageIncrease: Double?,            // 伤害提升百分比
    val sustainIncrease: Double?,           // 生存提升百分比
    val utilityIncrease: String?,            // 功能性提升描述
    val overallRating: UpgradeRating,        // 综合评级
    val breakdown: EidolonEffectBreakdown
)

/** 星魂效果分解 */
data class EidolonEffectBreakdown(
    val directMultiplier: Double?,          // 直接倍率提升
    val critBuff: Double?,                   // 暴击相关提升
    val defIgnore: Double?,                  // 防御忽略
    val energyBoost: Double?,                // 充能提升
    val newMechanic: String?,                // 新机制描述
    val qualityOfLife: String?               // 手感提升描述
)

/** 星魂收益总览 */
data class EidolonSummary(
    val characterName: String,
    val eidolonBenefits: List<EidolonBenefit>,
    val cumulativeBenefit: Map<Int, Double>,      // 0→N 的累积提升
    val totalBenefitE6: Double,                  // 满星魂总提升
    val keyEidolons: List<Int>,                  // 质变星魂
    val recommendedTarget: Int,                  // 推荐目标星魂
    val reasoning: String
)

/** 精炼收益 */
data class SuperimposeBenefit(
    val level: Int,                           // 精炼等级 1→2, 2→3, ...
    val damageIncrease: Double?,              // 伤害提升
    val utilityDescription: String,           // 效果描述
    val overallRating: UpgradeRating
)

/** 光锥对比 */
data class LightConeComparison(
    val lightConeId: String,
    val sameLcDifferentSuperimpose: List<SuperimposeBenefit>,
    val vsOtherLightCones: Map<String, Double>,  // 对比其他光锥
    val isBestInSlot: Boolean,
    val alternativeOptions: List<AlternativeLc>
)

/** 替代光锥选项 */
data class AlternativeLc(
    val lightConeId: String,
    val superimpose: Int,
    val performancePercent: Double,          // 相对于专武的性能百分比
    val recommendation: String
)

/** 升级曲线 */
data class UpgradeCurve(
    val type: UpgradeType,                   // EIDOLON / SUPERIMPOSE
    val name: String,
    val levels: List<Int>,
    val incrementalBenefits: List<Double>,   // 每级增量收益(%)
    val cumulativeBenefits: List<Double>,     // 累积收益(%)
    val marginalAnalysis: List<String>        // 边际收益分析
)

/** 升级类型 */
enum class UpgradeType { EIDOLON, SUPERIMPOSE }

/** 资源类型 */
enum class ResourceType(val displayName: String) {
    STELLAR_JADE("星琼"),
    UNDYING_STARLIGHT("星辉"),
    SELF_MODELING_RESIN("自塑尘脂"),
    FUEL("燃料"),
    ETERNAL_GLOW("梦之珠泪"),
    REAL_MONEY("人民币")
}

/** 资源消耗 */
data class ResourceCost(
    val type: ResourceType,
    val amount: Int,
    val estimatedTime: String? = null,       // 估算获取时间
    val monetaryValue: Double? = null        // 估算人民币价值
)

/** 性价比等级 */
enum class EfficiencyGrade(val displayName: String) {
    S("极高"),
    A("高"),
    B("中"),
    C("低"),
    D("极低")
}

/** 性价比分析 */
data class CostBenefitAnalysis(
    val upgradePath: String,                 // 例如 "希儿 0→1魂"
    val cost: ResourceCost,
    val benefit: Double,                      // 提升百分比
    val benefitPerCost: Double,              // 收益/资源比
    val efficiency: EfficiencyGrade,
    val recommendation: String
)

/** 资源预算 */
data class ResourceBudget(
    val type: ResourceType,
    val amount: Int
)

/** 优先级升级 */
data class PrioritizedUpgrade(
    val characterId: String,
    val upgradeDescription: String,
    val benefit: Double,
    val confidence: Confidence,
    val rank: Int
)

/** 可信度 */
enum class Confidence(val displayName: String) {
    HIGH("高"),
    MEDIUM("中"),
    LOW("低")
}

/** 升级步骤 */
data class UpgradeStep(
    val stepNumber: Int,
    val action: String,                      // "解锁1魂" / "精2专武"
    val benefit: Double,                     // 本步提升
    val cumulativeBenefit: Double,          // 累积提升
    val cost: ResourceCost,
    val efficiencyRatio: Double,
    val recommendation: String
)

/** 升级路径 */
data class UpgradePath(
    val characterId: String,
    val currentState: String,                // 当前0魂+0精
    val recommendedSteps: List<UpgradeStep>,
    val totalBenefit: Double,
    val optimalStoppingPoint: String          // 最优停手点
)

/** 星魂分析器 */
class EidolonAnalyzer {
    
    /**
     * 计算星魂收益
     * @param characterId 角色ID
     * @param fromEidolon 当前星魂
     * @param toEidolon 目标星魂
     */
    fun calculateBenefit(
        characterId: String,
        fromEidolon: Int,
        toEidolon: Int
    ): List<EidolonBenefit> {
        val benefits = mutableListOf<EidolonBenefit>()
        
        // 简化版：基于角色类型估算
        for (rank in (fromEidolon + 1)..toEidolon) {
            val benefit = calculateSingleRankBenefit(characterId, rank)
            benefits.add(benefit)
        }
        
        return benefits
    }
    
    /** 计算单级星魂收益 */
    private fun calculateSingleRankBenefit(
        characterId: String,
        rank: Int
    ): EidolonBenefit {
        // 简化版：基于角色定位估算收益
        val isDpsCharacter = isDpsCharacter(characterId)
        val isSupportCharacter = isSupportCharacter(characterId)
        
        val (damageIncrease, rating) = when {
            // 质变星魂（通常为1、2、4、6魂）
            rank in listOf(1, 2, 4, 6) && isDpsCharacter -> Pair(15.0 + rank * 3, UpgradeRating.RECOMMENDED)
            rank in listOf(1, 2, 4, 6) && isSupportCharacter -> Pair(10.0 + rank * 2, UpgradeRating.GOOD)
            isDpsCharacter -> Pair(8.0 + rank * 1.5, UpgradeRating.AVERAGE)
            else -> Pair(5.0 + rank * 1.0, UpgradeRating.SKIP)
        }
        
        return EidolonBenefit(
            rank = rank,
            name = "星魂 $rank",
            description = getEidolonDescription(characterId, rank),
            damageIncrease = damageIncrease,
            sustainIncrease = if (isSupportCharacter) damageIncrease * 0.5 else null,
            utilityIncrease = getUtilityIncreaseDescription(characterId, rank),
            overallRating = rating,
            breakdown = EidolonEffectBreakdown(
                directMultiplier = damageIncrease * 0.5,
                critBuff = if (rank >= 3) 8.0 else null,
                defIgnore = null,
                energyBoost = if (rank == 2) 10.0 else null,
                newMechanic = if (rank == 6) "解锁满星魂终极效果" else null,
                qualityOfLife = null
            )
        )
    }
    
    /** 获取星魂描述（简化版） */
    private fun getEidolonDescription(characterId: String, rank: Int): String {
        return when {
            rank == 1 -> "战技伤害提升"
            rank == 2 -> "终结技伤害提升"
            rank == 4 -> "追加攻击触发"
            rank == 6 -> "解锁特殊机制"
            else -> "基础属性提升"
        }
    }
    
    /** 获取功能性提升描述 */
    private fun getUtilityIncreaseDescription(characterId: String, rank: Int): String? {
        return when (rank) {
            1 -> "战技使用更加灵活"
            2 -> "终结技频率提升"
            4 -> "输出循环改善"
            6 -> "机制完整度提升"
            else -> null
        }
    }
    
    /** 检查是否为输出角色 */
    private fun isDpsCharacter(characterId: String): Boolean {
        return characterId.contains("希儿") || 
               characterId.contains("饮月") ||
               characterId.contains("景元") ||
               characterId.contains("刃") ||
               characterId.contains("镜流") ||
               characterId.contains("托帕") ||
               characterId.contains("黄泉") ||
               characterId.contains("流萤") ||
               characterId.contains("波提欧") ||
               characterId.contains("翡翠") ||
               characterId.contains("银枝") ||
               characterId.contains("姬子") ||
               characterId.contains("彦卿") ||
               characterId.contains("素裳") ||
               characterId.contains("桂子") ||
               characterId.contains("卡芙卡") ||
               characterId.contains("克拉拉") ||
               characterId.contains("真理医生") ||
               characterId.contains("飞霄") ||
               characterId.contains("青雀")
    }
    
    /** 检查是否为辅助角色 */
    private fun isSupportCharacter(characterId: String): Boolean {
        return characterId.contains("银狼") || 
               characterId.contains("符玄") ||
               characterId.contains("驭空") ||
               characterId.contains("布洛妮娅") ||
               characterId.contains("阮梅") ||
               characterId.contains("花火") ||
               characterId.contains("知更鸟") ||
               characterId.contains("罗刹") ||
               characterId.contains("藿藿") ||
               characterId.contains("白露") ||
               characterId.contains("杰帕德") ||
               characterId.contains("三月七") ||
               characterId.contains("瓦尔特") ||
               characterId.contains("停云") ||
               characterId.contains("佩拉") ||
               characterId.contains("艾丝妲") ||
               characterId.contains("娜塔莎") ||
               characterId.contains("砂金") ||
               characterId.contains("灵砂")
    }
    
    /** 获取关键星魂 */
    fun getKeyEidolons(characterId: String): List<Int> {
        // 输出角色通常1、2、4、6魂质变
        return if (isDpsCharacter(characterId)) {
            listOf(1, 2, 4, 6)
        } else {
            listOf(2, 4, 6)
        }
    }
    
    /** 获取完整星魂收益总览 */
    fun getFullEidolonSummary(characterId: String): EidolonSummary {
        val benefits = calculateBenefit(characterId, 0, 6)
        val cumulative = mutableMapOf<Int, Double>()
        var total = 0.0
        
        for (i in 1..6) {
            total += benefits.getOrNull(i - 1)?.damageIncrease ?: 0.0
            cumulative[i] = total
        }
        
        val keyEidolons = getKeyEidolons(characterId)
        val recommended = when {
            cumulative[2] ?: 0.0 > 25 -> 2
            cumulative[4] ?: 0.0 > 45 -> 4
            else -> 6
        }
        
        return EidolonSummary(
            characterName = characterId,
            eidolonBenefits = benefits,
            cumulativeBenefit = cumulative,
            totalBenefitE6 = total,
            keyEidolons = keyEidolons,
            recommendedTarget = recommended,
            reasoning = "推荐优先解锁关键星魂 ${keyEidolons.take(2).joinToString("、")}"
        )
    }
}

/** 光锥分析器 */
class LightConeAnalyzer {
    
    /**
     * 计算精炼收益
     */
    fun calculateSuperimposeBenefit(
        lightConeId: String,
        fromLevel: Int,
        toLevel: Int
    ): List<SuperimposeBenefit> {
        val benefits = mutableListOf<SuperimposeBenefit>()
        
        for (level in (fromLevel + 1)..toLevel) {
            val benefit = calculateSingleLevelBenefit(lightConeId, level)
            benefits.add(benefit)
        }
        
        return benefits
    }
    
    /** 计算单级精炼收益 */
    private fun calculateSingleLevelBenefit(
        lightConeId: String,
        level: Int
    ): SuperimposeBenefit {
        // 简化版：精炼收益通常是递增的
        val baseIncrease = when (level) {
            2 -> 10.0
            3 -> 8.0
            4 -> 6.0
            5 -> 5.0
            else -> 0.0
        }
        
        val is5StarLc = lightConeId.contains("5")  // 简化判断
        val increase = if (is5StarLc) baseIncrease * 1.2 else baseIncrease
        
        val rating = when (level) {
            2 -> UpgradeRating.RECOMMENDED
            3 -> UpgradeRating.GOOD
            4 -> UpgradeRating.AVERAGE
            5 -> UpgradeRating.SKIP
            else -> UpgradeRating.SKIP
        }
        
        return SuperimposeBenefit(
            level = level,
            damageIncrease = increase,
            utilityDescription = "精炼 $level 效果",
            overallRating = rating
        )
    }
    
    /** 生成升级曲线 */
    fun generateUpgradeCurve(
        type: UpgradeType,
        name: String,
        maxLevel: Int
    ): UpgradeCurve {
        val levels = (1..maxLevel).toList()
        val incremental = mutableListOf<Double>()
        val cumulative = mutableListOf<Double>()
        var total = 0.0
        
        for (i in levels) {
            val inc = when (type) {
                UpgradeType.EIDOLON -> 10.0 + i * 2.0
                UpgradeType.SUPERIMPOSE -> 8.0 + (maxLevel - i) * 1.5
            }
            total += inc
            incremental.add(inc)
            cumulative.add(total)
        }
        
        val marginalAnalysis = levels.map { level ->
            when {
                level <= 2 -> "收益较高，值得提升"
                level <= 4 -> "收益适中，可以考虑"
                else -> "收益递减，优先级降低"
            }
        }
        
        return UpgradeCurve(
            type = type,
            name = name,
            levels = levels,
            incrementalBenefits = incremental,
            cumulativeBenefits = cumulative,
            marginalAnalysis = marginalAnalysis
        )
    }
}

/** 性价比分析器 */
class CostBenefitAnalyzer {
    
    /**
     * 分析性价比
     */
    fun analyze(
        characterId: String,
        upgradeType: UpgradeType,
        currentLevel: Int,
        targetLevel: Int
    ): CostBenefitAnalysis {
        // 计算收益
        val benefit = when (upgradeType) {
            UpgradeType.EIDOLON -> {
                val analyzer = EidolonAnalyzer()
                analyzer.calculateBenefit(characterId, currentLevel, targetLevel)
                    .sumOf { it.damageIncrease ?: 0.0 }
            }
            UpgradeType.SUPERIMPOSE -> {
                val analyzer = LightConeAnalyzer()
                analyzer.calculateSuperimposeBenefit(characterId, currentLevel, targetLevel)
                    .sumOf { it.damageIncrease ?: 0.0 }
            }
        }
        
        // 计算成本
        val cost = calculateCost(upgradeType, currentLevel, targetLevel)
        
        // 计算收益/成本比
        val benefitPerCost = benefit / cost.amount
        
        val efficiency = when {
            benefitPerCost > 0.5 -> EfficiencyGrade.S
            benefitPerCost > 0.3 -> EfficiencyGrade.A
            benefitPerCost > 0.2 -> EfficiencyGrade.B
            benefitPerCost > 0.1 -> EfficiencyGrade.C
            else -> EfficiencyGrade.D
        }
        
        return CostBenefitAnalysis(
            upgradePath = "$characterId ${upgradeType.name} $currentLevel→$targetLevel",
            cost = cost,
            benefit = benefit,
            benefitPerCost = benefitPerCost,
            efficiency = efficiency,
            recommendation = generateRecommendation(upgradeType, benefit, efficiency)
        )
    }
    
    /** 计算资源消耗 */
    private fun calculateCost(
        upgradeType: UpgradeType,
        fromLevel: Int,
        toLevel: Int
    ): ResourceCost {
        return when (upgradeType) {
            UpgradeType.EIDOLON -> {
                // 星魂消耗（简化估算）
                val starLight = (toLevel - fromLevel) * 50  // 简化
                ResourceCost(
                    type = ResourceType.UNDYING_STARLIGHT,
                    amount = starLight,
                    estimatedTime = "${starLight / 10}天"
                )
            }
            UpgradeType.SUPERIMPOSE -> {
                // 光锥精炼消耗（简化估算）
                val jade = (toLevel - fromLevel) * 120  // 简化
                ResourceCost(
                    type = ResourceType.STELLAR_JADE,
                    amount = jade,
                    estimatedTime = "${jade / 160}天"
                )
            }
        }
    }
    
    /** 生成建议 */
    private fun generateRecommendation(
        upgradeType: UpgradeType,
        benefit: Double,
        efficiency: EfficiencyGrade
    ): String {
        return when (efficiency) {
            EfficiencyGrade.S -> "强烈推荐：收益极高，性价比优秀"
            EfficiencyGrade.A -> "推荐：收益不错，可以考虑"
            EfficiencyGrade.B -> "一般：根据资源情况决定"
            EfficiencyGrade.C -> "不推荐：收益一般，建议等待"
            EfficiencyGrade.D -> "跳过：性价比过低，建议放弃"
        }
    }
    
    /** 生成升级路径 */
    fun generateUpgradePath(
        characterId: String,
        currentEidolon: Int,
        currentSuperimpose: Int,
        budget: ResourceBudget
    ): UpgradePath {
        val steps = mutableListOf<UpgradeStep>()
        var totalBenefit = 0.0
        
        // 简化版：先检查星魂
        val eidolonAnalyzer = EidolonAnalyzer()
        val eidolonBenefits = eidolonAnalyzer.calculateBenefit(characterId, currentEidolon, 6)
        
        for ((index, benefit) in eidolonBenefits.withIndex()) {
            val stepNumber = index + 1
            totalBenefit += benefit.damageIncrease ?: 0.0
            
            steps.add(UpgradeStep(
                stepNumber = stepNumber,
                action = "解锁 ${benefit.rank} 魂",
                benefit = benefit.damageIncrease ?: 0.0,
                cumulativeBenefit = totalBenefit,
                cost = ResourceCost(ResourceType.UNDYING_STARLIGHT, 50),
                efficiencyRatio = (benefit.damageIncrease ?: 0.0) / 50.0,
                recommendation = when (benefit.overallRating) {
                    UpgradeRating.MUST_HAVE -> "质变星魂，优先解锁"
                    UpgradeRating.RECOMMENDED -> "强烈推荐提升"
                    UpgradeRating.GOOD -> "可以考虑"
                    else -> "根据资源决定"
                }
            ))
        }
        
        return UpgradePath(
            characterId = characterId,
            currentState = "${currentEidolon}魂 + ${currentSuperimpose}精",
            recommendedSteps = steps,
            totalBenefit = totalBenefit,
            optimalStoppingPoint = if (totalBenefit > 40) "4魂" else "6魂"
        )
    }
}