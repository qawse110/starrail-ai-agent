package com.starrail.agent.core.model

/**
 * 角色相关数据模型
 * 定义角色基础属性、技能、星魂等数据
 */

/** 基础属性 */
data class BaseStats(
    val hp: Double,
    val attack: Double,
    val defense: Double,
    val speed: Double,
    val critRate: Double = 0.05,
    val critDmg: Double = 0.50,
    val taunt: Int = 100  // 嘲讽值
)

/** 突破等级属性数据 */
data class AscensionData(
    val level: Int,
    val hp: Double,
    val attack: Double,
    val defense: Double,
    val speed: Double
)

/** 技能倍率条目 */
data class ScalingEntry(
    val stat: StatType,      // 基础属性类型
    val multiplier: Double,  // 倍率
    val extraDamage: DamageType? = null  // 附加伤害类型
)

/** 技能数据 */
data class Skill(
    val id: String,
    val type: SkillType,           // 技能类型
    val name: String,
    val description: String,
    val scaling: List<ScalingEntry>,  // 倍率表
    val energyGain: Int = 0,        // 回能
    val energyCost: Int = 0,       // 耗能
    val cooldown: Int? = null      // 冷却回合
)

/** 行迹（角色被动） */
data class Trace(
    val id: String,
    val name: String,
    val description: String,
    val level: Int,                // 行迹等级
    val isMinorTrace: Boolean       // 是否为小行迹
)

/** 星魂效果 */
data class Eidolon(
    val rank: Int,                // 星魂等级 1-6
    val name: String,
    val description: String,
    val effects: List<Effect>     // 效果列表
)

/** 角色数据（静态模板） */
data class Character(
    val id: String,                           // 唯一ID，如 "char_1001"
    val name: String,                         // 名称，如 "丹恒·饮月"
    val rarity: Int,                           // 稀有度: 4 / 5
    val path: PathType,                        // 命途
    val element: ElementType,                  // 属性
    val baseStats: BaseStats,                  // 基础属性
    val ascensionStats: Map<Int, AscensionData>, // 各突破等级属性
    val skills: List<Skill>,                   // 技能列表
    val traces: List<Trace>,                   // 行迹
    val eidolons: List<Eidolon>,               // 星魂
    val resonance: String? = null              // 额外能力
) {
    /** 获取推荐技能等级 */
    fun getRecommendedSkillLevels(): Map<String, Int> {
        return skills.associate { it.id to 10 }  // 默认满级
    }
}

/** 推荐主词条映射 */
val RELIC_MAIN_STATS: Map<RelicSlot, List<StatType>> = mapOf(
    RelicSlot.头部 to listOf(StatType.HP),
    RelicSlot.手部 to listOf(StatType.ATK),
    RelicSlot.躯干 to listOf(
        StatType.CRIT_RATE,
        StatType.CRIT_DMG,
        StatType.ATK_PERCENT,
        StatType.HP_PERCENT
    ),
    RelicSlot.脚部 to listOf(
        StatType.SPD,
        StatType.ATK_PERCENT,
        StatType.HP_PERCENT
    ),
    RelicSlot.位面球 to listOf(
        StatType.ATK_PERCENT,
        StatType.HP_PERCENT,
        StatType.DEF_PERCENT,
        StatType.ELEMENTAL_DMG_UP
    ),
    RelicSlot.连结绳 to listOf(
        StatType.ENERGY_RATE,
        StatType.ATK_PERCENT
    )
)