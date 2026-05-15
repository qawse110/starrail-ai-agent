package com.starrail.agent.team

import com.starrail.agent.core.model.*
import com.starrail.agent.player.*

/**
 * 配队对比模块
 * 分析队伍协同效应、DPS估算、横向对比
 */

/** 队伍角色定位 */
enum class TeamRole(val displayName: String) {
    MAIN_DPS("主C"),
    SUB_DPS("副C"),
    SUPPORT("辅助"),
    SUSTAIN("生存位"),
    HYBRID("混合位")
}

/** 队伍成员 */
data class TeamMember(
    val characterId: String,
    val playerCharacter: PlayerCharacter,
    val role: TeamRole,
    val lightCone: PlayerLightCone?,
    val relics: List<PlayerRelic>,
    val buildDescription: String = ""
)

/** 队伍 */
data class Team(
    val id: String? = null,
    val name: String,
    val members: List<TeamMember>,
    val teamSynergy: TeamSynergy? = null,
    val estimatedDps: TeamDpsResult? = null
) {
    /** 成员数量 */
    val memberCount: Int get() = members.size
    
    /** 是否有效队伍（至少4人） */
    val isValidTeam: Boolean get() = members.size >= 4
    
    /** 获取主C */
    val mainDps: TeamMember? get() = members.find { it.role == TeamRole.MAIN_DPS }
    
    /** 获取生存位 */
    val sustain: TeamMember? get() = members.find { it.role == TeamRole.SUSTAIN }
    
    /** 获取所有 DPS 成员 */
    val dpsMembers: List<TeamMember> get() = members.filter { 
        it.role == TeamRole.MAIN_DPS || it.role == TeamRole.SUB_DPS 
    }
}

/** 元素协同分析 */
data class ElementSynergy(
    val coverage: Set<ElementType>,       // 队伍覆盖的属性类型
    val weaknessMatch: Int,               // 匹配目标弱点的角色数
    val breakEfficiency: Double,          // 破韧效率评分
    val varietyScore: Double              // 属性多样性评分
)

/** 命途协同分析 */
data class PathSynergy(
    val pathComposition: Map<PathType, Int>,  // 命途分布
    val hasSustain: Boolean,                  // 是否有存护/丰饶
    val harmonyCount: Int,                    // 同谐数量
    val nihilityCount: Int,                   // 虚无数量
    val balancedScore: Double                 // 平衡性评分
)

/** 技能联动类型 */
enum class SynergyType(val displayName: String) {
    BUFF_RECEIVER("Buff提供"),
    SP_GENERATOR("产点"),
    SP_CONSUMER("耗点"),
    ELEMENT_PAIRED("属性配对"),
    FOLLOW_UP("追加攻击联动"),
    BATTERY("充能")
}

/** 技能联动 */
data class SkillInteraction(
    val sourceCharId: String,
    val targetCharId: String,
    val type: SynergyType,
    val description: String,
    val benefitEstimate: Double  // 预估收益
)

/** 技能协同分析 */
data class SkillSynergy(
    val interactions: List<SkillInteraction>,
    val overallScore: Double
)

/** 队伍协同分析 */
data class TeamSynergy(
    val elementSynergy: ElementSynergy,
    val pathSynergy: PathSynergy,
    val skillSynergy: SkillSynergy,
    val overallScore: Double,          // 综合评分 0-100
    val strengths: List<String>,        // 优势总结
    val weaknesses: List<String>,       // 劣势总结
    val suggestions: List<String>       // 改进建议
)

/** 战技点平衡状态 */
enum class SpBalance(val displayName: String) {
    SURPLUS("充足"),
    BALANCED("刚好"),
    STRESSED("紧张"),
    STARVING("严重不足")
}

/** DPS 估算结果 */
data class TeamDpsResult(
    val totalCycleDamage: Double,            // N轮总伤害
    val cycleCount: Double,                   // 消耗轮数
    val avgCycleDamage: Double,               // 平均每轮伤害
    val damageShare: Map<String, Double>,     // 各角色伤害占比
    val avgSkillPointsUsed: Double,           // 每轮耗点
    val avgSkillPointsGenerated: Double,      // 每轮产点
    val spBalance: SpBalance,                  // 战技点平衡状况
    val ultimateFrequency: Map<String, Double>, // 各角色每轮终结技次数
    val buffUptimeAnalysis: Map<String, Double>, // Buff覆盖率
    val damageBreakdown: DamageBreakdown
)

/** 伤害分解 */
data class DamageBreakdown(
    val bySkillType: Map<SkillType, Double>,
    val byElement: Map<ElementType, Double>,
    val toughnessDamage: Double,
    val totalDamage: Double
)

/** 对比行数据 */
data class ComparisonRow(
    val name: String,                      // 指标名
    val values: Map<String, Double>,       // 队伍 → 数值
    val unit: String,                       // 单位
    val higherIsBetter: Boolean
)

/** 队伍对比结果 */
data class TeamComparison(
    val teams: List<Team>,
    val comparisonTable: Map<String, ComparisonRow>,
    val rankings: Map<String, Int>,        // 队伍ID → 排名
    val recommendedTeam: String?,           // 推荐队伍ID
    val radarChartData: RadarChartData      // 雷达图数据
)

/** 雷达图数据 */
data class RadarChartData(
    val labels: List<String>,
    val datasets: List<RadarDataset>
)

/** 雷达图数据集 */
data class RadarDataset(
    val teamName: String,
    val values: List<Double>
)

/** 配队推荐请求 */
data class TeamSuggestionRequest(
    val targetContent: String? = null,      // "混沌12层" / "虚构叙事" / 空=通用
    val availableCharacters: List<String> = emptyList(),  // 可用角色ID列表
    val mainDps: String? = null,            // 指定主C
    val constraints: TeamConstraints = TeamConstraints()
)

/** 配队约束 */
data class TeamConstraints(
    val includeCharacters: List<String> = emptyList(),  // 必须包含的角色
    val excludeCharacters: List<String> = emptyList(),  // 不能包含的角色
    val maxTeamsToGenerate: Int = 10
)

/** 配队推荐结果 */
data class TeamSuggestionResult(
    val suggestions: List<Team>,
    val reasoning: String
)

/** 配队分析器 */
class TeamAnalyzer {
    
    /**
     * 分析队伍协同效应
     */
    fun analyzeSynergy(team: Team): TeamSynergy {
        val elementSynergy = analyzeElementSynergy(team)
        val pathSynergy = analyzePathSynergy(team)
        val skillSynergy = analyzeSkillSynergy(team)
        
        // 综合评分
        val overallScore = (elementSynergy.varietyScore * 0.3 +
                           pathSynergy.balancedScore * 0.3 +
                           skillSynergy.overallScore * 0.4)
        
        return TeamSynergy(
            elementSynergy = elementSynergy,
            pathSynergy = pathSynergy,
            skillSynergy = skillSynergy,
            overallScore = overallScore,
            strengths = generateStrengths(team, elementSynergy, pathSynergy, skillSynergy),
            weaknesses = generateWeaknesses(team, elementSynergy, pathSynergy, skillSynergy),
            suggestions = generateSuggestions(team, elementSynergy, pathSynergy, skillSynergy)
        )
    }
    
    /** 分析元素协同 */
    private fun analyzeElementSynergy(team: Team): ElementSynergy {
        val elements = team.members.mapNotNull { member ->
            // 简化：使用角色ID查找属性，实际需要查询角色模板
            getElementForCharacter(member.characterId)
        }.toSet()
        
        return ElementSynergy(
            coverage = elements,
            weaknessMatch = 0,  // 需要敌人信息才能计算
            breakEfficiency = elements.size * 20.0,
            varietyScore = (elements.size.coerceAtMost(4) * 25.0)
        )
    }
    
    /** 分析命途协同 */
    private fun analyzePathSynergy(team: Team): PathSynergy {
        val paths = team.members.groupBy { member ->
            getPathForCharacter(member.characterId)
        }
        
        val hasSustain = team.members.any { 
            it.role == TeamRole.SUSTAIN || 
            getPathForCharacter(it.characterId) in listOf(PathType.存护, PathType.丰饶)
        }
        
        val harmonyCount = paths.entries.count { it.key == PathType.同谐 }
        val nihilityCount = paths.entries.count { it.key == PathType.虚无 }
        
        // 平衡性评分
        val balancedScore = when {
            hasSustain && harmonyCount >= 1 -> 80.0
            hasSustain -> 70.0
            else -> 50.0
        }
        
        return PathSynergy(
            pathComposition = paths.mapValues { it.value.size },
            hasSustain = hasSustain,
            harmonyCount = harmonyCount,
            nihilityCount = nihilityCount,
            balancedScore = balancedScore
        )
    }
    
    /** 分析技能联动 */
    private fun analyzeSkillSynergy(team: Team): SkillSynergy {
        val interactions = mutableListOf<SkillInteraction>()
        
        // 检测同谐-输出角色联动（简化版）
        val harmonyMembers = team.members.filter { 
            getPathForCharacter(it.characterId) == PathType.同谐 
        }
        val dpsMembers = team.dpsMembers
        
        for (harmony in harmonyMembers) {
            for (dps in dpsMembers) {
                interactions.add(
                    SkillInteraction(
                        sourceCharId = harmony.characterId,
                        targetCharId = dps.characterId,
                        type = SynergyType.BUFF_RECEIVER,
                        description = "${harmony.characterId} 为 ${dps.characterId} 提供 Buff",
                        benefitEstimate = 15.0
                    )
                )
            }
        }
        
        val overallScore = interactions.sumOf { it.benefitEstimate }.coerceAtMost(100.0)
        
        return SkillSynergy(
            interactions = interactions,
            overallScore = overallScore
        )
    }
    
    /** 生成优势列表 */
    private fun generateStrengths(
        team: Team,
        element: ElementSynergy,
        path: PathSynergy,
        skill: SkillSynergy
    ): List<String> {
        val strengths = mutableListOf<String>()
        
        if (path.hasSustain) {
            strengths.add("有生存位保障")
        }
        if (element.varietyScore >= 75) {
            strengths.add("属性覆盖全面")
        }
        if (skill.interactions.isNotEmpty()) {
            strengths.add("角色之间有良好协同")
        }
        if (path.harmonyCount >= 1) {
            strengths.add("同谐提供强力辅助")
        }
        
        return strengths.ifEmpty { listOf("队伍配置完整") }
    }
    
    /** 生成劣势列表 */
    private fun generateWeaknesses(
        team: Team,
        element: ElementSynergy,
        path: PathSynergy,
        skill: SkillSynergy
    ): List<String> {
        val weaknesses = mutableListOf<String>()
        
        if (!path.hasSustain) {
            weaknesses.add("缺少生存位，可能不够稳定")
        }
        if (team.dpsMembers.size < 2) {
            weaknesses.add("输出角色数量偏少")
        }
        if (skill.interactions.isEmpty()) {
            weaknesses.add("角色协同较少")
        }
        
        return weaknesses.ifEmpty { listOf("无明显劣势") }
    }
    
    /** 生成改进建议 */
    private fun generateSuggestions(
        team: Team,
        element: ElementSynergy,
        path: PathSynergy,
        skill: SkillSynergy
    ): List<String> {
        val suggestions = mutableListOf<String>()
        
        if (!path.hasSustain) {
            suggestions.add("建议添加存护或丰饶角色提升生存能力")
        }
        if (element.varietyScore < 50) {
            suggestions.add("建议调整属性覆盖以应对更多敌人")
        }
        if (team.dpsMembers.size > 2) {
            suggestions.add("DPS角色过多可能导致战技点不足")
        }
        
        return suggestions.ifEmpty { listOf("当前配置已经不错") }
    }
    
    /** 估算队伍 DPS（简化版） */
    fun estimateDps(team: Team): TeamDpsResult {
        // 简化估算：基于角色等级和装备
        var totalDamage = 0.0
        val damageShare = mutableMapOf<String, Double>()
        
        for (member in team.members) {
            val damage = estimateMemberDamage(member)
            totalDamage += damage
            damageShare[member.characterId] = damage
        }
        
        return TeamDpsResult(
            totalCycleDamage = totalDamage,
            cycleCount = 4.0,  // 假设4轮战斗
            avgCycleDamage = totalDamage / 4,
            damageShare = damageShare,
            avgSkillPointsUsed = 3.0,
            avgSkillPointsGenerated = 4.0,
            spBalance = SpBalance.BALANCED,
            ultimateFrequency = team.members.associate { it.characterId to 0.7 },
            buffUptimeAnalysis = team.members.associate { it.characterId to 0.8 },
            damageBreakdown = DamageBreakdown(
                bySkillType = mapOf(SkillType.BASIC to totalDamage * 0.3),
                byElement = emptyMap(),
                toughnessDamage = totalDamage * 0.1,
                totalDamage = totalDamage
            )
        )
    }
    
    /** 估算单个成员伤害（简化版） */
    private fun estimateMemberDamage(member: TeamMember): Double {
        // 简化估算
        val baseDamage = member.playerCharacter.level * 100.0
        val roleMultiplier = when (member.role) {
            TeamRole.MAIN_DPS -> 1.5
            TeamRole.SUB_DPS -> 1.0
            TeamRole.SUPPORT -> 0.3
            TeamRole.SUSTAIN -> 0.1
            TeamRole.HYBRID -> 0.8
        }
        return baseDamage * roleMultiplier
    }
    
    /** 对比多个队伍 */
    fun compareTeams(teams: List<Team>): TeamComparison {
        val comparisonTable = mutableMapOf<String, ComparisonRow>()
        
        // 计算各项指标
        val synergyScores = teams.associate { (it.id ?: "") to (it.teamSynergy?.overallScore ?: 0.0) }
        val dpsValues = teams.associate { (it.id ?: "") to (it.estimatedDps?.avgCycleDamage ?: 0.0) }
        val memberCounts = teams.associate { (it.id ?: "") to it.memberCount.toDouble() }
        
        comparisonTable["协同评分"] = ComparisonRow(
            name = "协同评分",
            values = synergyScores,
            unit = "分",
            higherIsBetter = true
        )
        
        comparisonTable["平均轮伤"] = ComparisonRow(
            name = "平均轮伤",
            values = dpsValues,
            unit = "",
            higherIsBetter = true
        )
        
        comparisonTable["队伍人数"] = ComparisonRow(
            name = "队伍人数",
            values = memberCounts,
            unit = "人",
            higherIsBetter = true
        )
        
        // 计算排名
        val rankings = teams.associate { team ->
            (team.id ?: "") to teams.count { other ->
                (other.teamSynergy?.overallScore ?: 0.0) > (team.teamSynergy?.overallScore ?: 0.0)
            } + 1
        }
        
        // 推荐队伍（综合评分最高的）
        val recommendedTeam = rankings.minByOrNull { it.value }?.key
        
        return TeamComparison(
            teams = teams,
            comparisonTable = comparisonTable,
            rankings = rankings,
            recommendedTeam = recommendedTeam,
            radarChartData = RadarChartData(
                labels = listOf("协同", "DPS", "生存", "泛用"),
                datasets = teams.map { team ->
                    RadarDataset(
                        teamName = team.name,
                        values = listOf(
                            team.teamSynergy?.overallScore ?: 50.0,
                            team.estimatedDps?.avgCycleDamage?.div(1000) ?: 50.0,
                            50.0,  // 简化
                            50.0   // 简化
                        )
                    )
                }
            )
        )
    }
    
    // === 辅助方法 ===
    
    /** 获取角色的元素属性（需要数据源支持） */
    private fun getElementForCharacter(characterId: String): ElementType? {
        return when {
            characterId.contains("希儿") -> ElementType.量子
            characterId.contains("银狼") -> ElementType.雷
            characterId.contains("丹恒") -> ElementType.风
            characterId.contains("景元") -> ElementType.雷
            characterId.contains("卡芙卡") -> ElementType.雷
            characterId.contains("符玄") -> ElementType.量子
            characterId.contains("罗刹") -> ElementType.虚数
            characterId.contains("驭空") -> ElementType.虚数
            characterId.contains("布洛妮娅") -> ElementType.冰
            characterId.contains("镜流") -> ElementType.冰
            characterId.contains("托帕") -> ElementType.火
            characterId.contains("阮梅") -> ElementType.冰
            characterId.contains("黑天鹅") -> ElementType.风
            characterId.contains("花火") -> ElementType.量子
            characterId.contains("知更鸟") -> ElementType.物理
            characterId.contains("黄泉") -> ElementType.雷
            characterId.contains("流萤") -> ElementType.火
            characterId.contains("波提欧") -> ElementType.物理
            characterId.contains("翡翠") -> ElementType.量子
            characterId.contains("克拉拉") -> ElementType.物理
            characterId.contains("停云") -> ElementType.雷
            characterId.contains("艾丝妲") -> ElementType.火
            characterId.contains("佩拉") -> ElementType.冰
            characterId.contains("青雀") -> ElementType.量子
            characterId.contains("娜塔莎") -> ElementType.物理
            characterId.contains("真理医生") -> ElementType.虚数
            characterId.contains("砂金") -> ElementType.虚数
            characterId.contains("飞霄") -> ElementType.风
            characterId.contains("灵砂") -> ElementType.火
            else -> ElementType.物理
        }
    }
    
    /** 获取角色的命途（需要数据源支持） */
    private fun getPathForCharacter(characterId: String): PathType {
        return when {
            characterId.contains("希儿") -> PathType.巡猎
            characterId.contains("银狼") -> PathType.虚无
            characterId.contains("符玄") -> PathType.存护
            characterId.contains("罗刹") -> PathType.丰饶
            characterId.contains("镜流") -> PathType.毁灭
            characterId.contains("托帕") -> PathType.巡猎
            characterId.contains("阮梅") -> PathType.同谐
            characterId.contains("黑天鹅") -> PathType.虚无
            characterId.contains("花火") -> PathType.同谐
            characterId.contains("知更鸟") -> PathType.同谐
            characterId.contains("黄泉") -> PathType.毁灭
            characterId.contains("流萤") -> PathType.毁灭
            characterId.contains("波提欧") -> PathType.巡猎
            characterId.contains("翡翠") -> PathType.智识
            characterId.contains("克拉拉") -> PathType.毁灭
            characterId.contains("停云") -> PathType.同谐
            characterId.contains("艾丝妲") -> PathType.同谐
            characterId.contains("佩拉") -> PathType.虚无
            characterId.contains("青雀") -> PathType.智识
            characterId.contains("娜塔莎") -> PathType.丰饶
            characterId.contains("真理医生") -> PathType.巡猎
            characterId.contains("砂金") -> PathType.存护
            characterId.contains("飞霄") -> PathType.巡猎
            characterId.contains("灵砂") -> PathType.丰饶
            else -> PathType.智识
        }
    }
}