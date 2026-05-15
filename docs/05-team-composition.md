# 配队对比模块设计 — Team Composition Module

> **模块定位**：分析队伍协同效应，估算队伍 DPS，支持多队伍横向对比与优化建议。

---

## 1. 职责与边界

### 单一职责 (SRP)
- 队伍协同分析：评估角色之间的技能联动
- 队伍 DPS 估算：基于战斗模拟计算队伍总输出
- 多队伍对比：横向比较不同队伍配置的优劣

### 依赖关系
- 依赖 Core Data Layer（角色/光锥/遗器数据）
- 依赖 Player Data（玩家拥有的角色和装备）
- 依赖 Battle Simulation（调用模拟器获取伤害数据）

---

## 2. 核心架构

```
┌───────────────────────────────────────┐
│       TeamCompositionFacade           │ ← 对外入口
│  analyzeTeam(team)                    │
│  compareTeams(teams)                  │
│  suggestTeam(target, constraints)     │
├───────────────────────────────────────┤
│  ┌──────────────┐  ┌───────────────┐ │
│  │ SynergyAnalyzer│ │  TeamDPS    │ │
│  │ - 元素协同    │  │   - 队伤计算 │ │
│  │ - 命途协同    │  │   - 轴规划   │ │
│  │ - 技能联动    │  │   - Buff覆盖 │ │
│  └──────────────┘  └───────────────┘ │
│  ┌──────────────┐  ┌───────────────┐ │
│  │ TeamComparator │  │ TeamSuggester │ │
│  │ - 属性对比   │  │   - 约束搜索  │ │
│  │ - 雷达图     │  │   - 阵容推荐  │ │
│  └──────────────┘  └───────────────┘ │
└───────────────────────────────────────┘
```

---

## 3. 队伍模型

```kotlin
data class Team(
    val id: String?,
    val name: String,
    val members: List<TeamMember>,
    val teamSynergy: TeamSynergy?,
    val estimatedDps: TeamDpsResult?
)

data class TeamMember(
    val characterId: String,
    val playerCharacter: PlayerCharacter,
    val role: TeamRole,
    val lightCone: PlayerLightCone?,
    val relics: List<PlayerRelic>,
    val buildDescription: String
)

enum class TeamRole {
    MAIN_DPS,        // 主C
    SUB_DPS,         // 副C
    SUPPORT,         // 辅助
    SUSTAIN,         // 生存位
    HYBRID           // 混合位
}
```

---

## 4. 协同分析 (SynergyAnalyzer)

### 4.1 元素协同

```kotlin
data class ElementSynergy(
    val coverage: Set<ElementType>,       // 队伍覆盖的属性类型
    val weaknessMatch: Int,               // 匹配目标弱点的角色数
    val breakEfficiency: Double,          // 破韧效率评分
    val varietyScore: Double              // 属性多样性评分
)

fun analyzeElementSynergy(team: Team, target: Enemy?): ElementSynergy
```

### 4.2 命途协同

```kotlin
data class PathSynergy(
    val pathComposition: Map<PathType, Int>,  // 命途分布
    val hasSustain: Boolean,                  // 是否有存护/丰饶
    val harmonyCount: Int,                    // 同谐数量
    val nihilityCount: Int,                   // 虚无数量
    val balancedScore: Double                 // 平衡性评分
)
```

### 4.3 技能联动

```kotlin
data class SkillSynergy(
    val interactions: List<SkillInteraction>,
    val overallScore: Double
)

data class SkillInteraction(
    val sourceCharId: String,
    val targetCharId: String,
    val type: SynergyType,
    val description: String,
    val benefitEstimate: Double  // 预估收益
)

enum class SynergyType {
    BUFF_RECEIVER,      // A 给 B 加 Buff
    SP_GENERATOR,       // A 产点供 B 用
    SP_CONSUMER,        // A 耗点，B 是耗点收益者
    ELEMENT_PAIRED,     // 属性配对（如双冰加暴击）
    FOLLOW_UP,          // 追加攻击联动
    BATTERY             // A 给 B 充能
}
```

### 4.4 综合协同评分

```kotlin
data class TeamSynergy(
    val elementSynergy: ElementSynergy,
    val pathSynergy: PathSynergy,
    val skillSynergy: SkillSynergy,
    val overallScore: Double,          // 综合评分 0-100
    val strengths: List<String>,       // 优势总结
    val weaknesses: List<String>,      // 劣势总结
    val suggestions: List<String>      // 改进建议
)
```

---

## 5. 队伍 DPS 估算 (TeamDpsEstimator)

```kotlin
data class TeamDpsResult(
    val totalCycleDamage: Double,            // N轮总伤害
    val cycleCount: Double,                  // 消耗轮数
    val avgCycleDamage: Double,              // 平均每轮伤害
    val damageShare: Map<String, Double>,    // 各角色伤害占比
    val avgSkillPointsUsed: Double,          // 每轮耗点
    val avgSkillPointsGenerated: Double,     // 每轮产点
    val spBalance: SpBalance,                // 战技点平衡状况
    val ultimateFrequency: Map<String, Double>, // 各角色每轮终结技次数
    val buffUptimeAnalysis: Map<String, Double>, // Buff覆盖率
    val damageBreakdown: DamageBreakdown
)

enum class SpBalance {
    SURPLUS,        // 战技点充足
    BALANCED,       // 刚好够用
    STRESSED,       // 稍显紧张
    STARVING        // 严重缺战技点
}

data class DamageBreakdown(
    val bySkillType: Map<ActionType, Double>,
    val byElement: Map<ElementType, Double>,
    val toughnessDamage: Double,
    val totalDamage: Double
)
```

### 估算策略

由于完整战斗模拟计算量较大，采用**分层估算**策略：

1. **快速估算** (Fast Path)
   - 仅计算期望伤害，不考虑行动条排序
   - 使用简单的循环轴（预设的固定行动序列）
   - 适用于多队伍快速对比

2. **精确模拟** (Full Simulation)
   - 调用 BattleSimulator 进行完整战斗模拟
   - 考虑行动条、Buff 覆盖动态变化
   - 适用于最终确认分析

```kotlin
interface TeamDpsEstimator {
    fun estimateFast(team: Team, context: SimulationContext): TeamDpsResult
    fun simulateFull(team: Team, context: SimulationContext): TeamDpsResult
}
```

---

## 6. 多队伍对比 (TeamComparator)

### 6.1 对比维度

```kotlin
data class TeamComparison(
    val teams: List<Team>,
    val comparisonTable: Map<String, ComparisonRow>,
    val rankings: Map<String, Int>,
    val recommendedTeam: Team,
    val radarChartData: RadarData         // 雷达图数据
)

data class ComparisonRow(
    val name: String,                      // 指标名
    val values: Map<String, Double>,       // 队伍 → 数值
    val unit: String,                      // 单位
    val higherIsBetter: Boolean
)
```

### 6.2 对比指标

| 指标 | 说明 | 单位 |
|------|------|------|
| 总伤害 | 4轮总伤害 | 数字 |
| 平均每轮伤害 | DPS | 数字/轮 |
| 轮数 | 完成击杀所需轮数 | 轮 |
| 协同评分 | 1-100 | 分 |
| 战技点平衡 | 1-100 | 分 |
| 生存能力 | 1-100 | 分 |
| AOE能力 | 1-100 | 分 |
| 单体能-力 | 1-100 | 分 |
| 泛用性 | 适配多环境的评分 | 分 |

---

## 7. 配队推荐 (TeamSuggester)

```kotlin
data class TeamSuggestionRequest(
    val targetContent: String?,         // "混沌12层" / "虚构叙事" / 空=通用
    val availableCharacters: List<String>,  // 可用角色ID列表
    val mainDps: String?,               // 指定主C（可选）
    val constraints: TeamConstraints
)

data class TeamConstraints(
    val includeCharacters: List<String> = emptyList(),
    val excludeCharacters: List<String> = emptyList(),
    val maxTeamsToGenerate: Int = 10
)

data class TeamSuggestionResult(
    val suggestions: List<Team>,
    val reasoning: String
)
```

**推荐逻辑**：
1. 根据目标内容确定需求特征（对单/对群/生存压力）
2. 从可用角色中枚举可行组合（剪枝：必须有 C + 辅助 + 生存）
3. 计算每个组合的协同评分 + 快速 DPS 估算
4. 按综合分数排序，返回 Top-N

---

## 8. 设计原则应用

| 原则 | 体现 |
|------|------|
| **SRP** | 协同分析/DPS估算/对比/推荐各为独立子模块 |
| **OCP** | 新增协同类型通过扩展 SynergyType 枚举实现 |
| **DIP** | TeamDpsEstimator 依赖 BattleSimulator 接口，而非具体实现 |
| **DRY** | 复用 Core Data Layer 的角色模型，不独立维护 |
| **KISS** | 快速估算和精确模拟双层策略，默认走快速路径 |
| **YAGNI** | 不实现"全自动配队优化"，仅提供建议和对比 |

---

## 9. 边界条件

- **4人不满**：允许不足 4 人的队伍，协同评分降低
- **重复角色**：不允许同一角色出现两次
- **装备冲突**：同一件光锥/遗器不能分配给多人
- **无可用角色**：返回错误而非崩溃
- **版本新角色**：即使尚无准确数据，也应在模型中占位

---

## 10. 测试策略

- **协同评分测试**：已知强协同组合评分应高于随机组合
- **DPS 估算一致性**：快速估算与精确模拟的排序应基本一致
- **对比排序测试**：已知强队应排弱队前面
- **推荐覆盖测试**：不同约束条件应返回合理结果
- **边界测试**：单角色队伍、全辅助队伍等极端配置