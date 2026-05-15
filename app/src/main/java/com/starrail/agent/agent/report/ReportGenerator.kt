package com.starrail.agent.agent.report

import com.starrail.agent.agent.intent.*
import com.starrail.agent.agent.tool.ToolResult

/**
 * 报告生成模块
 * 将分析结果转换为结构化自然语言报告
 */

/** 报告章节类型 */
enum class SectionType {
    TEXT,   // 文本
    TABLE,  // 表格
    CHART,  // 图表
    LIST    // 列表
}

/** 报告章节 */
data class ReportSection(
    val title: String,
    val content: String,
    val type: SectionType,
    val importance: Int = 5,  // 1-5，越高越重要
    val data: Any? = null     // 原始数据（用于UI渲染）
)

/** 可视化数据 */
data class VisualData(
    val chartType: ChartType,
    val labels: List<String>,
    val datasets: List<Dataset>
)

/** 图表类型 */
enum class ChartType { BAR, RADAR, LINE, PIE }

/** 数据集 */
data class Dataset(
    val label: String,
    val values: List<Double>,
    val color: String? = null
)

/** 分析报告 */
data class AnalysisReport(
    val title: String,
    val summary: String,               // 一句话总结
    val sections: List<ReportSection>,
    val keyFindings: List<String>,    // 关键发现
    val recommendations: List<String>, // 建议
    val visualData: VisualData? = null,
    val rawData: Map<String, Any>? = null
) {
    /** 转换为格式化文本 */
    fun toFormattedText(): String {
        return buildString {
            appendLine("=" .repeat(50))
            appendLine(title)
            appendLine("=".repeat(50))
            appendLine()
            appendLine("📌 总结")
            appendLine(summary)
            appendLine()
            
            if (keyFindings.isNotEmpty()) {
                appendLine("💡 关键发现")
                keyFindings.forEachIndexed { index, finding ->
                    appendLine("  ${index + 1}. $finding")
                }
                appendLine()
            }
            
            appendLine("📊 详细信息")
            for (section in sections.sortedByDescending { it.importance }) {
                appendLine("-".repeat(40))
                appendLine("【${section.title}】")
                appendLine(section.content)
                appendLine()
            }
            
            if (recommendations.isNotEmpty()) {
                appendLine("✅ 建议")
                recommendations.forEachIndexed { index, rec ->
                    appendLine("  ${index + 1}. $rec")
                }
            }
            
            appendLine()
            appendLine("=".repeat(50))
        }
    }
}

/** 报告生成器 */
class ReportGenerator {
    
    /**
     * 生成报告
     */
    fun generate(
        intent: Intent,
        toolResults: List<ToolResult>,
        entities: ParsedEntities
    ): AnalysisReport {
        return when (intent) {
            Intent.SIMULATE_BATTLE -> generateBattleReport(toolResults, entities)
            Intent.SCORE_CHARACTER_RELICS -> generateRelicScoreReport(toolResults, entities)
            Intent.ANALYZE_TEAM -> generateTeamAnalysisReport(toolResults, entities)
            Intent.ANALYZE_EIDOLON -> generateEidolonReport(toolResults, entities)
            Intent.COMPARE_UPGRADE_PATH -> generateUpgradeComparisonReport(toolResults, entities)
            Intent.GREETING -> generateGreetingReport()
            else -> generateGenericReport(intent, toolResults, entities)
        }
    }
    
    /** 生成战斗模拟报告 */
    private fun generateBattleReport(
        toolResults: List<ToolResult>,
        entities: ParsedEntities
    ): AnalysisReport {
        val data = toolResults.firstOrNull()?.data as? Map<String, Any> ?: emptyMap()
        val characterId = entities.primaryCharacter ?: "角色"
        val estimatedDamage = data["estimated_damage"] as? Double ?: 0.0
        val cycles = data["cycles"] as? Int ?: 0
        val summary = data["summary"] as? String ?: "${characterId}预计需要${cycles}轮"
        
        return AnalysisReport(
            title = "⚔️ 战斗模拟报告",
            summary = summary,
            sections = listOf(
                ReportSection(
                    title = "伤害估算",
                    content = "预计总伤害: ${formatNumber(estimatedDamage)}\n" +
                             "预计回合数: $cycles 轮",
                    type = SectionType.TEXT,
                    importance = 5
                ),
                ReportSection(
                    title = "战技点使用",
                    content = "预计使用战技点: ${data["sp_used"] as? Int ?: 0} 点",
                    type = SectionType.TEXT,
                    importance = 3
                )
            ),
            keyFindings = listOf(
                "${characterId}的总伤害表现",
                "完成战斗预计需要 $cycles 轮"
            ),
            recommendations = listOf(
                "建议保持当前配置",
                "如有需要可调整遗器配装"
            )
        )
    }
    
    /** 生成遗器评分报告 */
    private fun generateRelicScoreReport(
        toolResults: List<ToolResult>,
        entities: ParsedEntities
    ): AnalysisReport {
        val characterId = entities.primaryCharacter ?: "角色"
        
        @Suppress("UNCHECKED_CAST")
        val weights = (toolResults.firstOrNull()?.data as? Map<String, Any>)
            ?.get("weights") as? List<Map<String, Any>> ?: emptyList()
        
        val effectiveStats = weights.filter { 
            it["effective"] == true 
        }.mapNotNull { 
            it["stat"] as? String 
        }
        
        val summary = "${characterId}的有效词条：${effectiveStats.joinToString("、")}"
        
        return AnalysisReport(
            title = "💎 遗器评分报告",
            summary = summary,
            sections = listOf(
                ReportSection(
                    title = "有效词条",
                    content = effectiveStats.joinToString("\n") { "- $it" },
                    type = SectionType.LIST,
                    importance = 5
                ),
                ReportSection(
                    title = "权重配置",
                    content = weights.joinToString("\n") { 
                        val stat = it["stat"] as? String ?: ""
                        val weight = it["weight"] as? Double ?: 0.0
                        val effective = if (it["effective"] == true) "✅" else "❌"
                        "$effective $stat: ${(weight * 100).toInt()}%"
                    },
                    type = SectionType.TABLE,
                    importance = 4
                )
            ),
            keyFindings = listOf(
                "该角色的核心属性为：${effectiveStats.take(3).joinToString("、")}",
                "共识别 ${effectiveStats.size} 个有效词条"
            ),
            recommendations = listOf(
                "遗器副词条优先追求以上有效词条",
                "主词条根据槽位选择对应属性"
            )
        )
    }
    
    /** 生成队伍分析报告 */
    private fun generateTeamAnalysisReport(
        toolResults: List<ToolResult>,
        entities: ParsedEntities
    ): AnalysisReport {
        val data = toolResults.firstOrNull()?.data as? Map<String, Any> ?: emptyMap()
        val synergyScore = data["synergy_score"] as? Double ?: 0.0
        
        @Suppress("UNCHECKED_CAST")
        val strengths = data["strengths"] as? List<String> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val weaknesses = data["weaknesses"] as? List<String> ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val suggestions = data["suggestions"] as? List<String> ?: emptyList()
        
        return AnalysisReport(
            title = "👥 队伍分析报告",
            summary = "队伍协同评分：${synergyScore.toInt()}分",
            sections = listOf(
                ReportSection(
                    title = "协同评分",
                    content = "综合评分: ${synergyScore.toInt()}/100",
                    type = SectionType.TEXT,
                    importance = 5
                ),
                ReportSection(
                    title = "队伍优势",
                    content = strengths.joinToString("\n") { "✅ $it" },
                    type = SectionType.LIST,
                    importance = 4
                ),
                ReportSection(
                    title = "队伍劣势",
                    content = weaknesses.joinToString("\n") { "⚠️ $it" },
                    type = SectionType.LIST,
                    importance = 3
                ),
                ReportSection(
                    title = "改进建议",
                    content = suggestions.joinToString("\n") { "💡 $it" },
                    type = SectionType.LIST,
                    importance = 4
                )
            ),
            keyFindings = listOf(
                "队伍协同评分达到 ${synergyScore.toInt()} 分",
                "队伍优势: ${strengths.firstOrNull() ?: "配置完整"}"
            ),
            recommendations = suggestions
        )
    }
    
    /** 生成星魂分析报告 */
    private fun generateEidolonReport(
        toolResults: List<ToolResult>,
        entities: ParsedEntities
    ): AnalysisReport {
        val data = toolResults.firstOrNull()?.data as? Map<String, Any> ?: emptyMap()
        val characterId = entities.primaryCharacter ?: "角色"
        val totalBenefit = data["total_benefit"] as? Double ?: 0.0
        val recommendedTarget = data["recommended_target"] as? Int ?: 0
        
        @Suppress("UNCHECKED_CAST")
        val benefits = data["benefits"] as? List<Map<String, Any>> ?: emptyList()
        
        @Suppress("UNCHECKED_CAST")
        val keyEidolons = data["key_eidolons"] as? List<Int> ?: emptyList()
        
        val summary = "${characterId}满星魂总提升约 ${totalBenefit.toInt()}%"
        
        return AnalysisReport(
            title = "⬆️ 星魂提升分析报告",
            summary = summary,
            sections = listOf(
                ReportSection(
                    title = "逐级收益",
                    content = benefits.joinToString("\n") {
                        val rank = it["rank"] as? Int ?: 0
                        val rating = it["rating"] as? String ?: ""
                        val increase = it["damage_increase"] as? Double ?: 0.0
                        "星魂 $rank: ${increase.toInt()}% (${rating})"
                    },
                    type = SectionType.TABLE,
                    importance = 5
                ),
                ReportSection(
                    title = "关键星魂",
                    content = "质变星魂: ${keyEidolons.joinToString("、")}",
                    type = SectionType.TEXT,
                    importance = 5
                ),
                ReportSection(
                    title = "推荐目标",
                    content = "建议优先提升至 ${recommendedTarget} 魂",
                    type = SectionType.TEXT,
                    importance = 4
                )
            ),
            keyFindings = listOf(
                "满星魂总提升 ${totalBenefit.toInt()}%",
                "关键质变星魂为 ${keyEidolons.take(2).joinToString("、")} 魂"
            ),
            recommendations = listOf(
                "优先解锁关键质变星魂 (${keyEidolons.joinToString("、")})",
                "根据资源情况决定是否继续提升"
            )
        )
    }
    
    /** 生成升级路径对比报告 */
    private fun generateUpgradeComparisonReport(
        toolResults: List<ToolResult>,
        entities: ParsedEntities
    ): AnalysisReport {
        val data = toolResults.firstOrNull()?.data as? Map<String, Any> ?: emptyMap()
        val characterId = entities.primaryCharacter ?: "角色"
        val recommendation = data["recommendation"] as? String ?: "根据性价比选择"
        
        @Suppress("UNCHECKED_CAST")
        val option1 = data["option_1"] as? Map<String, Any> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val option2 = data["option_2"] as? Map<String, Any> ?: emptyMap()
        
        return AnalysisReport(
            title = "⚖️ 升级路径对比报告",
            summary = recommendation,
            sections = listOf(
                ReportSection(
                    title = "方案一：星魂提升",
                    content = buildString {
                        appendLine("目标: ${option1["target"]}魂")
                        appendLine("预计提升: ${option1["benefit"]}%")
                        appendLine("消耗: ${option1["cost"]}星辉")
                        appendLine("性价比: ${option1["efficiency"]}级")
                    },
                    type = SectionType.TEXT,
                    importance = 5
                ),
                ReportSection(
                    title = "方案二：光锥精炼",
                    content = buildString {
                        appendLine("目标: 精${option2["target"]}")
                        appendLine("预计提升: ${option2["benefit"]}%")
                        appendLine("消耗: ${option2["cost"]}星琼")
                        appendLine("性价比: ${option2["efficiency"]}级")
                    },
                    type = SectionType.TEXT,
                    importance = 5
                )
            ),
            keyFindings = listOf(
                "星魂和光锥精炼各有优劣",
                "需要根据资源储备情况决定"
            ),
            recommendations = listOf(
                recommendation,
                "优先选择性价比更高的方案"
            )
        )
    }
    
    /** 生成问候报告 */
    private fun generateGreetingReport(): AnalysisReport {
        return AnalysisReport(
            title = "👋 欢迎使用星穹铁道助手",
            summary = "我可以帮助您分析战斗、遗器、配队和星魂提升",
            sections = listOf(
                ReportSection(
                    title = "可用功能",
                    content = """
                        |⚔️ 战斗模拟 - 模拟战斗伤害和回合
                        |💎 遗器分析 - 评分和套装推荐
                        |👥 配队对比 - 分析队伍协同效应
                        |⬆️ 星魂提升 - 分析星魂/精炼收益
                    """.trimMargin(),
                    type = SectionType.LIST,
                    importance = 5
                ),
                ReportSection(
                    title = "使用示例",
                    content = """
                        |• "分析我的希儿遗器"
                        |• "模拟饮月队打混沌12层"
                        |• "对比星魂和专武哪个更值得"
                    """.trimMargin(),
                    type = SectionType.TEXT,
                    importance = 3
                )
            ),
            keyFindings = listOf(
                "请告诉我您想分析什么"
            ),
            recommendations = listOf(
                "输入您想了解的内容即可开始分析"
            )
        )
    }
    
    /** 生成通用报告 */
    private fun generateGenericReport(
        intent: Intent,
        toolResults: List<ToolResult>,
        entities: ParsedEntities
    ): AnalysisReport {
        val intentName = intent.getDescription()
        
        return AnalysisReport(
            title = "📊 ${intentName}报告",
            summary = "已完成 ${intentName}",
            sections = toolResults.mapIndexed { index, result ->
                ReportSection(
                    title = "结果 ${index + 1}",
                    content = result.data?.toString() ?: result.error ?: "无数据",
                    type = SectionType.TEXT,
                    importance = 5 - index
                )
            },
            keyFindings = listOf(
                "${intentName}已完成"
            ),
            recommendations = listOf(
                "如需进一步分析，请告诉我具体需求"
            )
        )
    }
    
    /** 格式化数字 */
    private fun formatNumber(value: Double): String {
        return when {
            value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000)
            value >= 1_000 -> String.format("%.1fK", value / 1_000)
            else -> value.toInt().toString()
        }
    }
}