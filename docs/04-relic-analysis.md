# 遗器分析模块设计 — Relic Analysis Module

> **模块定位**：对玩家遗器进行评分、有效词条分析、套装搭配推荐与优化。

---

## 1. 职责与边界

### 单一职责 (SRP)
- 遗器评分：基于角色给出单件遗器评分
- 套装推荐：基于角色和已有遗器推荐最优套装组合
- 优化建议：识别可替换的弱词条遗器，提供强化方向建议

### 依赖关系
- 依赖 Core Data Layer（遗器套装定义、StatType 枚举）
- 依赖 Player Data（玩家遗器库存）
- 被 AI Agent 核心调用

---

## 2. 核心架构

```
┌───────────────────────────────────────────┐
│          RelicAnalysisFacade              │ ← 对外入口
│  analyzeCharacterRelics()                 │
│  recommendSets()                          │
│  getUpgradeSuggestions()                  │
├───────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────────┐  │
│  │ RelicScorer  │  │ SetRecommender   │  │
│  │ - 评分算法    │  │ - 套装组合枚举   │  │
│  │ - 词条权重    │  │ - 2+2/4件套选择  │  │
│  │ - 有效词条识别│  │ - 分数加权       │  │
│  └──────────────┘  └──────────────────┘  │
│  ┌──────────────────────────────────┐    │
│  │      RelicOptimizer              │    │
│  │  - 遗器替换建议                   │    │
│  │  - 强化方向预测                   │    │
│  │  - 狗粮/保留分类                  │    │
│  └──────────────────────────────────┘    │
└───────────────────────────────────────────┘
```

---

## 3. 遗器评分系统 (RelicScorer)

### 3.1 词条价值系统

```kotlin
data class SubStatWeight(
    val statType: StatType,
    val weight: Double,              // 该角色对该词条的权重 0.0 - 1.0
    val isEffective: Boolean         // 是否为有效词条
)
```

每个角色有其独立的词条权重表：

```kotlin
fun getCharacterWeights(characterId: String, buildType: BuildType): List<SubStatWeight>
```

**权重确定方式**：
- **主C**：双暴 > 攻击% > 速度 > 增伤 > 其他
- **辅助**：速度 > 效果命中 > 能量回复 > 生命% > 防御%
- **存护**：防御% > 速度 > 生命% > 效果抵抗
- 具体数值通过伤害计算器拟合得到（权重 = 该词条对期望伤害的提升比例）

### 3.2 评分算法

```kotlin
data class RelicScore(
    val overallScore: Double,        // 综合评分 (0-100)
    val mainStatScore: Double,       // 主词条评分
    val subStatScore: Double,        // 副词条评分
    val effectiveSubStats: Int,      // 有效副词条数量
    val totalRolls: Int,             // 总强化次数
    val effectiveRolls: Int,         // 有效强化次数
    val grade: RelicGrade            // S/A/B/C/D 等级
)

enum class RelicGrade { SSS, SS, S, A, B, C, D }

fun scoreRelic(
    relic: PlayerRelic,
    characterId: String,
    weights: List<SubStatWeight>
): RelicScore {
    // 1. 主词条评分：匹配推荐主词条得满分，否则按匹配度折减
    // 2. 副词条评分：每条词条按权重×数值计算，考虑强化次数
    // 3. 综合评分：主词条评分×40% + 副词条评分×60%
    // 4. 等级划分：≥90=SSS, ≥80=SS, ≥70=S, ≥60=A, ≥50=B, ≥30=C, <30=D
}
```

### 3.3 有效词条识别

```kotlin
data class EffectiveSubStatAnalysis(
    val totalSubStats: Int,          // 总词条数
    val effectiveCount: Int,         // 有效词条数
    val deadCount: Int,              // 无效词条数
    val effectiveRolls: Int,         // 有效强化次数
    val wastedRolls: Int,            // 歪掉的强化次数
    val effectiveValue: Double,      // 有效词条价值总和
    val potential: UpgradePotential  // 强化潜力
)

enum class UpgradePotential {
    HIGH,       // 3-4条有效且还有提升空间
    MEDIUM,     // 2条有效
    LOW,        // 0-1条有效
    DONE        // 已完美
}
```

---

## 4. 套装推荐 (SetRecommender)

### 4.1 推荐算法

```kotlin
data class SetRecommendation(
    val planName: String,             // "4件套×猛攻" 或 "2+2组合"
    val relicSlots: Map<RelicSlot, String>,  // 每个槽位选定的遗器ID
    val setBonuses: List<String>,     // 激活的套装效果
    val totalScore: Double,           // 综合评分
    val damageEstimate: Double,       // 估算伤害
    val scoreBreakdown: SetScoreBreakdown
)

data class SetScoreBreakdown(
    val setBonusScore: Double,
    val mainStatScore: Double,
    val subStatScore: Double
)
```

**推荐策略**：

1. **贪婪搜索**：遍历所有可能的套装组合（4件套 / 2+2 / 2+散件）
2. 对每种组合，从玩家库存中选最优的单件遗器填槽
3. 计算综合得分 = 套装效果评分 + 单件评分总和
4. 返回 Top-N 推荐

### 4.2 套装组合枚举

```kotlin
enum class SetCombinationType {
    FOUR_PIECE,          // 4件套（含2+2混搭）
    TWO_PLUS_TWO,        // 2件套×2
    TWO_PLUS_OFFSET,     // 2件套 + 散件
    RAINBOW              // 全散件
}
```

由于遗器有 6 个槽位（4 个遗器槽 + 2 个位面饰品槽），组合数有限：
- 遗器部分：2+2 或 4 件套（通常位面饰品独立计算）
- 位面饰品：2 件套效果
- 总组合数 ≈ 玩家持有遗器数 × 可选套装种类，通过剪枝优化

---

## 5. 遗器优化器 (RelicOptimizer)

### 5.1 单件替换建议

```kotlin
data class UpgradeSuggestion(
    val slot: RelicSlot,
    val currentRelic: PlayerRelic,
    val suggestion: String,
    val targetMainStat: StatType?,    // 推荐主词条
    val targetSubStats: List<StatType>, // 推荐副词条
    val priority: Int,                // 替换优先级 1(最优先)-5
    val expectedImprovement: Double,  // 预期提升百分比
    val reason: String                // 理由
)
```

### 5.2 强化决策支持

```kotlin
data class EnhancementAdvice(
    val relic: PlayerRelic,
    val currentLevel: Int,
    val recommendation: EnhancementAction,
    val confidence: Double,
    val reasoning: String
)

enum class EnhancementAction {
    STRONG_RECOMMEND,    // 值得+15
    WORTH_TRYING,        // 可以考虑+9/12看看
    STOP,                // 不值得继续强化
    FODDER               // 建议作为狗粮
}
```

**决策逻辑**：
1. 0/1 有效词条 + 已歪 → FODDER
2. 2 有效词条 + 0歪 → WORTH_TRYING
3. 3+ 有效词条 → STRONG_RECOMMEND
4. 4 有效词条且完美 → STOP（已毕业）

---

## 6. 批量分析

```kotlin
data class RelicInventoryOverview(
    val totalRelics: Int,
    val byGrade: Map<RelicGrade, Int>,
    val bySet: Map<String, Int>,
    val bySlot: Map<RelicSlot, Int>,
    val keepCount: Int,
    val fodderCount: Int,
    val undecidedCount: Int,
    val summary: String
)

fun analyzeInventory(playerRelics: List<PlayerRelic>): RelicInventoryOverview
```

---

## 7. 设计原则应用

| 原则 | 体现 |
|------|------|
| **SRP** | 评分/推荐/优化三个子模块互不重叠 |
| **OCP** | 新增角色评分权重通过添加权重配置实现，不改评分算法 |
| **DIP** | SetRecommender 依赖 RelicRepository 接口，不依赖具体遗器数据 |
| **DRY** | 词条权重要求复用 DamageCalculator 的计算结果，而非手动枚举 |
| **KISS** | 贪婪搜索而非全局最优搜索，结果足够好且速度快 |
| **YAGNI** | 不实现自动强化模拟，仅提供决策建议 |

---

## 8. 边界条件

- **新角色/新套装**：评分权重需要随版本更新
- **特殊角色**：某些角色防御转攻击、生命转伤害等需要特殊权重（如刃、克拉拉）
- **玩家库存为空**：返回无数据提示而非异常
- **过度优化警告**：建议替换遗器的预期收益低于 2% 时不推荐

---

## 9. 测试策略

- **评分一致性测试**：相同遗器+相同角色 → 评分一致
- **权重敏感度测试**：修改权重向量验证评分变化方向正确
- **套装组合穷举测试**：小规模库存下验证推荐结果正确
- **边界值测试**：0级遗器、+15满级遗器、4有效词条完美遗器
- **角色特异测试**：刃（生命转攻击）等特殊角色的权重验证