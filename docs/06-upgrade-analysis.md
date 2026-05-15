# 星魂/精炼提升分析模块 — Upgrade Analysis Module

> **模块定位**：计算星魂与光锥精炼对角色输出/治疗/辅助能力的提升收益，提供性价比评估与升级路径推荐。

---

## 1. 职责与边界

### 单一职责 (SRP)
- 星魂收益计算：每个星魂等级对角色能力的提升百分比
- 光锥精炼收益：光锥各精炼等级的提升幅度
- 性价比分析：综合考虑资源消耗与收益
- 升级路径推荐：最优的资源投入顺序

### 依赖关系
- 依赖 Core Data Layer（角色星魂数据、光锥精炼数据）
- 依赖 Player Data（玩家当前配置）
- 依赖 Battle Simulation（调用伤害计算器评估收益）

---

## 2. 核心架构

```
┌──────────────────────────────────────────────┐
│          UpgradeAnalysisFacade               │ ← 对外入口
│  analyzeEidolonUpgrade(charId)               │
│  analyzeSuperimposeUpgrade(lcId)             │
│  compareUpgradePaths(charId, resources)      │
├──────────────────────────────────────────────┤
│  ┌────────────────┐  ┌──────────────────┐   │
│  │ EidolonAnalyzer │  │ LightConeAnalyzer│   │
│  │ - 逐级收益      │  │ - 精炼收益       │   │
│  │ - 关键星魂识别  │  │ - 精炼收益曲线   │   │
│  │ - 完全体估算    │  │ - 对比光锥       │   │
│  └────────────────┘  └──────────────────┘   │
│  ┌────────────────┐  ┌──────────────────┐   │
│  │ CostBenefit     │  │ PathRecommender  │   │
│  │ - 资源量化      │  │ - 升级路径排序   │   │
│  │ - 收益/资源比   │  │ - 优先级建议     │   │
│  │ - 性价比曲线    │  │ - 长线规划       │   │
│  └────────────────┘  └──────────────────┘   │
└──────────────────────────────────────────────┘
```

---

## 3. 星魂收益分析 (EidolonAnalyzer)

### 3.1 逐级收益计算

```kotlin
data class EidolonBenefit(
    val rank: Int,                           // 星魂等级 0→1, 1→2, ...
    val name: String,
    val description: String,
    val damageIncrease: Double?,             // 伤害提升百分比
    val sustainIncrease: Double?,            // 生存提升百分比
    val utilityIncrease: Double?,            // 功能性提升描述
    val overallRating: UpgradeRating,        // 综合评级
    val breakdown: EidolonEffectBreakdown
)

enum class UpgradeRating {
    MUST_HAVE,        // 质变星魂（如改变机制）
    RECOMMENDED,      // 强烈推荐，提升显著
    GOOD,             // 不错的提升
    AVERAGE,          // 平均提升
    SKIP              // 提升很小，建议跳过
}

data class EidolonEffectBreakdown(
    val directMultiplier: Double?,           // 直接倍率提升
    val critBuff: Double?,                   // 暴击相关提升
    val defIgnore: Double?,                  // 防御忽略
    val energyBoost: Double?,                // 充能提升
    val newMechanic: String?,                // 新机制描述（如追加攻击）
    val qualityOfLife: String?               // 手感提升描述
)
```

### 3.2 收益计算方法

```kotlin
interface EidolonAnalyzer {
    /**
     * 计算从当前星魂到目标星魂的累积收益
     */
    fun calculateBenefit(
        characterId: String,
        fromEidolon: Int,           // 当前星魂
        toEidolon: Int,             // 目标星魂
        context: AnalysisContext     // 包含装备信息
    ): List<EidolonBenefit>
    
    /**
     * 获取关键星魂标识
     */
    fun getKeyEidolons(characterId: String): List<Int>
    
    /**
     * 获取完整星魂提升摘要
     */
    fun getFullEidolonSummary(characterId: String): EidolonSummary
}
```

**计算策略**：
1. 对每个星魂等级，创建该等级下角色的完整配置副本
2. 调用 DamageCalculator 对比升阶前后的期望伤害
3. 对治疗/护盾角色，计算治疗量/护盾量的变化
4. 对功能性质变（如额外回合、新机制），使用定性评级而非百分比

### 3.3 星魂提升总览

```kotlin
data class EidolonSummary(
    val characterName: String,
    val eidolonBenefits: List<EidolonBenefit>,
    val cumulativeBenefit: Map<Int, Double>,      // 0→N 的累积提升
    val totalBenefitE6: Double,                    // 满星魂总提升
    val keyEidolons: List<Int>,                    // 质变星魂
    val recommendedTarget: Int,                    // 推荐目标星魂
    val reasoning: String
)
```

---

## 4. 光锥精炼分析 (LightConeAnalyzer)

### 4.1 精炼收益计算

```kotlin
data class SuperimposeBenefit(
    val level: Int,                              // 精炼等级 1→2, 2→3, ...
    val damageIncrease: Double?,                 // 伤害提升
    val utilityDescription: String,              // 效果描述
    val overallRating: UpgradeRating
)

data class LightConeComparison(
    val lightConeId: String,
    val sameLcDifferentSuperimpose: List<SuperimposeBenefit>,
    val vsOtherLightCones: Map<String, Double>,  // 对比其他光锥
    val isBestInSlot: Boolean,
    val alternativeOptions: List<AlternativeLc>
)

data class AlternativeLc(
    val lightConeId: String,
    val superimpose: Int,
    val performancePercent: Double,              // 相对于专武的性能百分比
    val recommendation: String
)
```

### 4.2 收益曲线

```kotlin
data class UpgradeCurve(
    val type: UpgradeType,                       // EIDOLON / SUPERIMPOSE
    val name: String,
    val levels: List<Int>,
    val incrementalBenefits: List<Double>,       // 每级增量收益(%)
    val cumulativeBenefits: List<Double>,        // 累积收益(%)
    val marginalAnalysis: List<String>           // 边际收益分析
)

enum class UpgradeType { EIDOLON, SUPERIMPOSE }
```

---

## 5. 性价比分析 (CostBenefitAnalyzer)

### 5.1 资源模型

```kotlin
data class ResourceCost(
    val type: ResourceType,
    val amount: Int,
    val estimatedTime: String?,                  // 估算获取时间
    val monetaryValue: Double?                   // 估算人民币价值（可选）
)

enum class ResourceType {
    STELLAR_JADE,       // 星琼
    UNDYING_STARLIGHT, // 星辉/星芒（换星魂）
    SELF_MODELING_RESIN, // 自塑尘脂
    FUEL,               // 燃料
    ETERNAL_GLOW,       // 梦之珠泪（光锥精炼材料）
    REAL_MONEY          // 实际充值
}

data class CostBenefitAnalysis(
    val upgradePath: String,                     // 例如 "希儿 0→1魂"
    val cost: ResourceCost,
    val benefit: Double,                         // 提升百分比
    val benefitPerCost: Double,                  // 收益/资源比
    val efficiency: EfficiencyGrade,
    val recommendation: String
)

enum class EfficiencyGrade { S, A, B, C, D }     // S=性价比极高, D=极低
```

### 5.2 性价比计算

```kotlin
interface CostBenefitAnalyzer {
    /**
     * 计算从当前配置升级到目标配置的性价比
     */
    fun analyze(
        current: PlayerCharacter,
        target: PlayerCharacter,
        resourceType: ResourceType
    ): CostBenefitAnalysis
    
    /**
     * 在有限资源约束下，寻找最优升级路径
     */
    fun optimizePath(
        characters: List<PlayerCharacter>,
        budget: ResourceBudget
    ): List<PrioritizedUpgrade>
}

data class ResourceBudget(
    val type: ResourceType,
    val amount: Int
)

data class PrioritizedUpgrade(
    val characterId: String,
    val upgradeDescription: String,
    val benefit: Double,
    val confidence: Confidence,
    val rank: Int
)

enum class Confidence { HIGH, MEDIUM, LOW }
```

---

## 6. 升级路径推荐 (PathRecommender)

```kotlin
data class UpgradePath(
    val characterId: String,
    val currentState: String,                 // 当前0魂+0精
    val recommendedSteps: List<UpgradeStep>,
    val totalBenefit: Double,
    val optimalStoppingPoint: String          // 最优停手点
)

data class UpgradeStep(
    val stepNumber: Int,
    val action: String,                       // "解锁1魂" / "精2专武"
    val benefit: Double,                      // 本步提升
    val cumulativeBenefit: Double,            // 累积提升
    val cost: ResourceCost,
    const efficiencyRatio: Double,
    val recommendation: String
)
```

**推荐逻辑**：
1. 枚举所有可能的升级操作（各角色星魂×各光锥精炼）
2. 计算每步的收益/成本比
3. 按性价比排序，输出 Top-N 建议
4. 标记"质变"节点（机制变化）
5. 提供"最优停手点"建议（收益边际递减明显时）

---

## 7. 设计原则应用

| 原则 | 体现 |
|------|------|
| **SRP** | 星魂分析/光锥分析/性价比/推荐各为独立模块 |
| **OCP** | 新增角色时只需添加星魂效果数据，分析算法通用 |
| **DIP** | 收益计算依赖 DamageCalculator 接口，可 Mock |
| **DRY** | ResourceCost 模型复用，不对每种资源单独建模 |
| **KISS** | 收益计算以当前配置为基线，不做多变量耦合 |
| **YAGNI** | 不实现抽卡模拟、期望抽数计算等随机性分析 |

---

## 8. 边界条件

- **零基础角色**：1级角色无装备时，给出数据和提升建议
- **满星魂/满精炼**：提示已满级，无需分析
- **收益极低**：低于 2% 的提升标记为 SKIP
- **机制质变**：即使提升比例不大（如银狼1魂充能优化），也高亮提示
- **多角色优先级**：当资源有限时，跨角色排序需考虑队伍整体提升

---

## 9. 测试策略

- **收益一致性测试**：相同配置下收益计算结果可复现
- **边际递减验证**：确认部分星魂确实存在边际递减
- **质变星魂识别**：验证关键机制变化的星魂被正确标记
- **性价比排序测试**：高收益/低成本的操作应排在前面
- **边界测试**：满星魂角色、无星魂角色、不同精炼等级光锥