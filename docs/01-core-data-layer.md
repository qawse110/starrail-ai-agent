# 核心数据层设计 — Core Data Layer

> **模块定位**：项目数据基础，定义所有游戏领域模型、数据仓库接口与数据源实现策略。

---

## 1. 职责与边界

### 单一职责 (SRP)
- 仅负责游戏静态数据的建模、存储与提供
- 不包含任何业务逻辑（战斗计算、评分等）
- 不依赖任何上层业务模块

### 依赖倒置 (DIP)
- 业务模块依赖本层定义的接口（Repository），而非具体数据源
- 数据源实现可替换（本地 JSON / 远程 API / 本地数据库）

---

## 2. 领域模型

### 2.1 角色 (Character)

```kotlin
data class Character(
    val id: String,              // 唯一ID，如 "char_1001"
    val name: String,            // 名称，如 "丹恒•饮月"
    val rarity: Int,             // 稀有度: 4 / 5
    val path: PathType,          // 命途: 毁灭/巡猎/智识/同谐/虚无/存护/丰饶
    val element: ElementType,    // 属性: 物理/火/冰/雷/风/虚数/量子
    val baseStats: BaseStats,    // 基础属性（生命/攻击/防御/速度等）
    val ascensionStats: Map<Int, AscensionData>,  // 各突破等级属性
    val skills: List<Skill>,     // 技能列表
    val traces: List<Trace>,     // 行迹
    val eidolons: List<Eidolon>, // 星魂
    val resonance: String?       // 额外能力（如适用）
)

data class BaseStats(
    val hp: Double,
    val attack: Double,
    val defense: Double,
    val speed: Double,
    val critRate: Double,
    val critDmg: Double,
    val taunt: Int               // 嘲讽值
)

data class Skill(
    val id: String,
    val type: SkillType,         // 普攻/战技/终结技/天赋/秘技
    val name: String,
    val description: String,
    val scaling: List<ScalingEntry>,  // 倍率表
    val energyGain: Int,         // 回能
    val cooldown: Int?           // 冷却回合（如有）
)

data class ScalingEntry(
    val stat: StatType,          // 基础属性类型
    val multiplier: Double,      // 倍率
    val extraDamage: DamageType? // 附加伤害类型
)

data class Eidolon(
    val rank: Int,               // 星魂等级 1-6
    val name: String,
    val description: String,
    val effects: List<Effect>    // 效果描述（结构化）
)
```

### 2.2 光锥 (LightCone)

```kotlin
data class LightCone(
    val id: String,
    val name: String,
    val rarity: Int,
    val path: PathType,          // 适配命途
    val baseStats: BaseStats,
    val ascensionStats: Map<Int, AscensionData>,
    val skill: LightConeSkill,   // 光锥技能
    val superimposeLevels: List<SuperimposeData>  // 1-5精炼数据
)

data class LightConeSkill(
    val name: String,
    val description: String,
    val effects: List<Effect>    // 各精炼等级的数值
)
```

### 2.3 遗器 (Relic)

```kotlin
data class RelicSet(
    val id: String,
    val name: String,
    val setBonuses: List<SetBonus>,  // 2件套/4件套效果
    val type: RelicSetType           // 位面饰品/遗器
)

data class SetBonus(
    val requiredCount: Int,      // 2 或 4
    val effects: List<Effect>
)

data class Relic(
    val id: String,
    val setId: String,
    val slot: RelicSlot,         // 头部/手部/躯干/脚部/位面球/连结绳
    val rarity: Int,             // 2-5星
    val level: Int,              // 强化等级 0-15
    val mainStat: StatEntry,     // 主词条
    val subStats: List<StatEntry>, // 副词条 0-4条
    val subStatIncrementCount: Int // 强化增量次数
)

data class StatEntry(
    val type: StatType,
    val value: Double            // 数值或百分比
)
```

### 2.4 敌人 (Enemy)

```kotlin
data class Enemy(
    val id: String,
    val name: String,
    val level: Int,
    val toughness: Int,          // 韧性
    val weakness: List<ElementType>,  // 弱点属性
    val resistance: Map<ElementType, Double>,  // 属性抗性
    val stats: BaseStats,
    val debuffResistance: Double // 控制抵抗
)
```

### 2.5 效果 (Effect) — 通用 buff/debuff 模型

```kotlin
data class Effect(
    val type: EffectType,        // ATK_UP, DMG_UP, CRIT_RATE_UP, DEF_DOWN 等
    val source: String,          // 来源描述
    val value: Double,           // 数值
    val valueType: ValueType,    // PERCENT / FLAT
    val duration: Int?,          // 持续回合，null=常驻
    val stackable: Boolean,
    val maxStack: Int,
    val target: EffectTarget     // SELF / ALLY / ENEMY / ALL
)
```

### 2.6 枚举类型

```kotlin
enum class PathType { 毁灭, 巡猎, 智识, 同谐, 虚无, 存护, 丰饶 }
enum class ElementType { 物理, 火, 冰, 雷, 风, 虚数, 量子 }
enum class RelicSlot { 头部, 手部, 躯干, 脚部, 位面球, 连结绳 }
enum class StatType { HP, HP_PERCENT, ATK, ATK_PERCENT, DEF, DEF_PERCENT, 
                      SPD, CRIT_RATE, CRIT_DMG, EFFECT_HIT, EFFECT_RES,
                      BREAK_DMG, HEAL_BONUS, ENERGY_RATE }
enum class SkillType { BASIC, SKILL, ULTIMATE, TALENT, TECHNIQUE }
enum class DamageType { SINGLE, BLAST, AOE, BOUNCE, DOT }
enum class ValueType { PERCENT, FLAT }
enum class EffectTarget { SELF, ALLY, ENEMY, ALL }
```

---

## 3. 仓库接口 (Repository)

```kotlin
interface CharacterRepository {
    fun getAll(): List<Character>
    fun getById(id: String): Character?
    fun getByPath(path: PathType): List<Character>
    fun getByElement(element: ElementType): List<Character>
    fun search(name: String): List<Character>
}

interface LightConeRepository {
    fun getAll(): List<LightCone>
    fun getById(id: String): LightCone?
    fun getByPath(path: PathType): List<LightCone>
}

interface RelicRepository {
    fun getAllSets(): List<RelicSet>
    fun getSetById(id: String): RelicSet?
    fun getRelicsBySet(setId: String): List<Relic>
}

interface EnemyRepository {
    fun getAll(): List<Enemy>
    fun getById(id: String): Enemy?
    fun getByWeakness(element: ElementType): List<Enemy>
}
```

---

## 4. 数据源策略

| 数据源 | 用途 | 更新频率 |
|--------|------|----------|
| **本地 JSON** | 角色/光锥/遗器静态数据 | 随版本更新 |
| **远程 API** | 实时数据补充（当期深渊 enemies） | 每期更新 |
| **本地数据库 (Room)** | 玩家自有数据 + 缓存 | 实时 |
| **Hoyolab API** | 导入玩家持有的角色/光锥/遗器 | 按需 |

### 数据源选择器

```kotlin
interface DataSourceStrategy {
    fun <T> resolve(
        type: DataType,
        freshness: FreshnessRequirement
    ): DataSource<T>
}

enum class FreshnessRequirement {
    REAL_TIME,    // 实时：走远程 API
    RECENT,       // 近期：走本地 DB
    STATIC        // 静态：走本地 JSON
}
```

---

## 5. KISS / YAGNI / DRY 应用

| 原则 | 体现 |
|------|------|
| **KISS** | 模型字段直接对应游戏实际属性，不做额外抽象层 |
| **YAGNI** | 不预留"未来可能出的"属性类型，仅覆盖当前已存在的 |
| **DRY** | StatType/PathType/ElementType 等枚举全局统一，各模块复用 |
| **DIP** | 业务层依赖 Repository 接口，而非具体数据源实现 |
| **OCP** | 新增数据源只需实现 DataSource 接口，不影响现有代码 |

---

## 6. 数据完整性约束

- 所有模型 ID 全局唯一、版本无关
- 静态数据 JSON 通过自动化脚本从 Wiki 生成（TODO）
- 每次版本更新需运行数据完整性校验测试
- 数值存储以游戏内精确值为准，不做近似

---

## 7. 测试策略

- **模型序列化测试**：确保 JSON ↔ Kotlin 互转无误
- **数据完整性测试**：所有角色/光锥/遗器必填字段非空
- **边界值测试**：基础属性极值处理
- **引用完整性测试**：遗器 set 引用必须存在对应 set 定义
