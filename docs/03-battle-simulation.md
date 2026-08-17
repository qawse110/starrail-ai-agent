# 战斗模拟模块设计 — Battle Simulation Module

> **模块定位**：提供回合制战斗推演引擎，支持伤害计算、行动条模拟、增益覆盖分析。

---

## 1. 职责与边界

### 单一职责 (SRP)
- 战斗引擎：执行回合制战斗逻辑
- 伤害计算器：计算技能/普攻/追加攻击等伤害
- 模拟器：运行预设场景并输出统计结果

### 依赖关系
- 依赖 Core Data Layer（角色/光锥/遗器/敌人数据）
- 依赖 Player Data（获取玩家角色当前属性配置）
- 不依赖 AI Agent 核心（被 Agent 调用）

---

## 2. 核心架构

```
┌──────────────────────────────────────────┐
│           BattleSimulator                │ ← 对外入口
│  runScenario(scenario): SimulationResult │
├──────────────────────────────────────────┤
│  ┌────────────┐  ┌──────────────────┐   │
│  │ CombatEngine│  │ DamageCalculator │   │
│  │ - 行动条    │  │ - 技能倍率       │   │
│  │ - 回合管理  │  │ - 增伤/减伤      │   │
│  │ - buff管理  │  │ - 破韧/击破      │   │
│  │ - 目标选择  │  │ - 伤害期望       │   │
│  └────────────┘  └──────────────────┘   │
│  ┌──────────────────────────────────┐   │
│  │      BuffManager                 │   │
│  │  - 增益/减益的添加、移除、叠加    │   │
│  │  - 持续回合计时                  │   │
│  └──────────────────────────────────┘   │
└──────────────────────────────────────────┘
```

---

## 3. 战斗引擎 (CombatEngine)

### 3.1 行动条系统

```kotlin
data class ActionValue(
    val baseSpeed: Double,
    val buffedSpeed: Double,
    val actionAdvance: Double,      // 行动提前百分比
    val actionDelay: Double         // 行动推迟百分比
)

fun calculateActionValue(unit: CombatUnit): Double {
    // AV = 10000 / speed
    // 考虑行动提前/推迟修正
}
```

### 3.2 回合流程

```
[回合开始] → [能量恢复] → [Buff持续减少] → [行动选择]
    → [技能执行] → [伤害计算] → [Buff施加] → [回合结束]
```

### 3.3 战斗单元

```kotlin
data class CombatUnit(
    val id: String,
    val character: Character,          // 角色模板
    val playerData: PlayerCharacter,   // 玩家持有数据
    val equippedLightCone: PlayerLightCone?,
    val equippedRelics: List<PlayerRelic>,
    val computedStats: ComputedStats,  // 最终面板属性
    val buffs: MutableList<ActiveBuff>,
    val currentHp: Double,
    val currentEnergy: Double,
    val isAlive: Boolean,
    val teamPosition: Int
)

data class ComputedStats(
    // 以下均为战斗中的最终值（基础+装备+Buff叠加后）
    val hp: Double,
    val attack: Double,
    val defense: Double,
    val speed: Double,
    val critRate: Double,
    val critDmg: Double,
    val elementalDmgBonus: Double,     // 属性伤害加成
    val dmgBonus: Double,              // 全伤害加成
    val defPenetration: Double,        // 防御穿透
    val resPenetration: Double,        // 抗性穿透
    val energyRegenRate: Double,
    val effectHitRate: Double,
    val effectResistance: Double,
    val breakEffect: Double,
    val healingBonus: Double,
    val energy: Double
)
```

---

## 4. 伤害计算器 (DamageCalculator)

### 4.1 伤害公式

```
最终伤害 = 基础伤害 × 倍率区 × 增伤区 × 防御区 × 抗性区 × 破韧区 × 易伤区 × 暴击区

基础伤害 = Σ(对应属性 × 技能倍率)   // ATK/HP/DEF 可同时存在，按条目分别求和

倍率区 = 1.0                        // 基础伤害已包含倍率

增伤区 = 1 + (元素伤害加成 + 全伤害加成 + 技能类型增伤)

防御区 = (角色等级*10 + 200) / (角色等级*10 + 200 + 敌人防御 × (1 - 防御穿透))

抗性区 = max(0, 1 - (敌人属性抗性 - 抗性穿透))

破韧区 = 1.0（未破） / 1.1（已破韧，敌人受伤害提升约10%）

易伤区 = max(0, 1 + 受到的伤害提升之和)

暴击区 = 1 + (暴击率 < 随机值 ? 0 : 暴击伤害)
```

### 4.2 行动轴（Action Timeline）

```text
行动值 (AV) = 10000 / 速度
首轮行动值 = 150，后续每轮 = 100（混沌回忆/虚构叙事）

N 轮总行动值 = 150 + (N-1) × 100
N 轮内行动次数 = floor(N 轮总行动值 × 速度 / 10000)

行动提前：剩余行动值 × (1 - 提前%)
行动延后：剩余行动值 × (1 + 延后%)
```

实现位于 `battle/engine/ActionTimeline.kt`，提供行动值、轮次内行动次数、提前/延后计算。

### 4.3 期望伤害计算

```kotlin
data class DamageResult(
    val rawDamage: Double,           // 未暴击伤害
    val critDamage: Double,          // 暴击时伤害
    val expectedDamage: Double,      // 期望伤害 = raw × (1 + critRate × critDmg)
    val minDamage: Double,           // 最小值
    val maxDamage: Double,           // 最大值（暴击）
    val breakdown: DamageBreakdown   // 分段明细
)

fun calculateSkillDamage(
    attacker: CombatUnit,
    target: Enemy,
    skill: Skill,
    context: BattleContext
): DamageResult
```

### 4.4 特殊伤害机制

- **追加攻击**：独立触发条件与倍率
- **持续伤害 (DoT)**：回合开始触发，受元素精通/效果命中等影响
- **击破伤害**：基于击破特攻和敌人韧性
- **扩散伤害 (Blast)**：主目标全额，相邻目标百分比
- **弹射伤害 (Bounce)**：随机目标，每次递减或固定倍率

### 4.5 战斗模型量化

- 对角色估算标准循环伤害（A + E + Q）。
- 根据队友命途施加简化配队增益：
  - 同谐：每名提供 `+18% 攻击` 与 `+12% 全增伤`
  - 虚无：每名提供 `+8% 防御穿透` 与 `+10% 抗性穿透`
  - 同属性队友：每名提供 `+3% 属性伤害加成`
  - 存护/丰饶：提供生存位评分
- 结合行动轴输出每轮行动次数、弱点匹配、抗性、配队描述与 0-100 分量化评分。
- 评分拆分为：伤害 `65` + 行动 `15` + 弱点 `10` + 配队增益 `5` + 生存位 `5`。
- 实现位于 `battle/model/BattleModelQuantifier.kt`。

---

## 5. Buff 管理器 (BuffManager)

```kotlin
data class ActiveBuff(
    val effect: Effect,
    val sourceId: String,
    val remainingTurns: Int?,
    val currentStack: Int,
    val appliedTime: Int             // 作用回合序号
)

class BuffManager {
    fun applyBuff(target: CombatUnit, buff: Effect, source: String)
    fun removeBuff(target: CombatUnit, buffId: String)
    fun tickBuffs(units: List<CombatUnit>)  // 减少持续时间
    fun getActiveBuffs(unit: CombatUnit): List<ActiveBuff>
    fun calculateBuffModifier(unit: CombatUnit, statType: StatType): Double
}
```

---

## 6. 模拟场景 (Scenario)

```kotlin
data class BattleScenario(
    val name: String,
    val team: List<TeamMember>,
    val enemies: List<Enemy>,
    val environment: BattleEnvironment,
    val simulationConfig: SimulationConfig
)

data class BattleEnvironment(
    val iterationCount: Int,         // 模拟轮次（取平均）
    val maxCycles: Int,              // 最大轮数
    val memoryTurbulence: List<String>?  // 混沌回忆/虚构叙事 特殊条件
)

data class SimulationConfig(
    val strategy: Strategy,          // AUTO / MANUAL / OPTIMIZED
    val targetCycleCount: Int,       // 目标轮数（如 0T / 1T）
    val detailedLog: Boolean
)

enum class Strategy { AUTO, MANUAL, OPTIMIZED }
```

### 模拟结果

```kotlin
data class SimulationResult(
    val scenarioName: String,
    val totalDamage: Double,
    val cycleCount: Double,          // 消耗轮数
    val damageBreakdown: Map<String, Double>,  // 角色 → 伤害
    val actionLog: List<ActionRecord>,
    val buffUptime: Map<String, Double>,       // Buff覆盖率
    val summary: String
)

data class ActionRecord(
    val cycle: Int,
    val unitName: String,
    val actionType: ActionType,      // SKILL / ULTIMATE / BASIC / FUA
    val target: String,
    val damage: Double,
    val energyBefore: Double,
    val energyAfter: Double
)

enum class ActionType { BASIC, SKILL, ULTIMATE, FUA, TECHNIQUE, WAIT }
```

---

## 7. 设计原则应用

| 原则 | 体现 |
|------|------|
| **SRP** | 引擎/计算器/Buff管理 严格分离各自职责 |
| **OCP** | 新增伤害类型或Buff类型通过扩展 Effect 子系统完成，不改核心引擎 |
| **DIP** | DamageCalculator 依赖 ComputedStats 等抽象，不依赖具体单元实现 |
| **DRY** | 公用伤害公式固化在 DamageCalculator 中，各技能调用其方法 |
| **KISS** | 不追求完美AI模拟，默认使用 AUTO 策略简化复杂度 |
| **YAGNI** | 不实现"所有可能的战斗场景"，仅覆盖主流的 DPS 检查场景 |

---

## 8. 边界场景

- **能量溢出**：超过上限部分正确截断
- **Buff刷新**：同类型 Buff 刷新持续时间 vs 叠加层数
- **击杀/复活**：单位死亡后移除所有 Buff
- **速度变化**：行动条重新排序
- **控制状态**：冻结/禁锢等跳过回合

---

## 9. 测试策略

- **公式验证**：输入已知属性，验证伤害是否符合游戏内公式
- **回归测试**：每个版本更新后验证核心角色伤害不变
- **Buff覆盖测试**：验证 Buff 持续回合数与叠加逻辑
- **边界测试**：0攻击、满暴击、极限增伤等极端配置
- **快照测试**：典型场景的模拟结果与预期快照比对
- **性能测试**：1000 轮 Monte Carlo 模拟的执行时间不超过 5s