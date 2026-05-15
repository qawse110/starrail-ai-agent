package com.starrail.agent.core.model

/** 游戏内枚举类型统一定义 */
enum class PathType(val displayName: String) {
    毁灭("毁灭"), 巡猎("巡猎"), 智识("智识"),
    同谐("同谐"), 虚无("虚无"), 存护("存护"), 丰饶("丰饶");
    companion object {
        fun fromString(v: String): PathType? = entries.find { it.name == v || it.displayName == v }
    }
}

enum class ElementType(val displayName: String) {
    物理("物理"), 火("火"), 冰("冰"), 雷("雷"), 风("风"), 虚数("虚数"), 量子("量子");
    companion object {
        fun fromString(v: String): ElementType? = entries.find { it.name == v }
    }
}

enum class RelicSlot(val displayName: String) {
    头部("头部"), 手部("手部"), 躯干("躯干"), 脚部("脚部"), 位面球("位面球"), 连结绳("连结绳");
    companion object {
        fun fromString(v: String): RelicSlot? = entries.find { it.name == v }
    }
}

enum class StatType {
    HP, HP_PERCENT, ATK, ATK_PERCENT, DEF, DEF_PERCENT, SPD, CRIT_RATE, CRIT_DMG,
    EFFECT_HIT, EFFECT_RES, BREAK_DMG, HEAL_BONUS, ENERGY_RATE, ELEMENTAL_DMG_UP;
    companion object {
        fun fromString(v: String): StatType? = entries.find { it.name == v }
    }
}

enum class SkillType { BASIC, SKILL, ULTIMATE, TALENT, TECHNIQUE }
enum class DamageType { SINGLE, BLAST, AOE, BOUNCE, DOT }
enum class ValueType { PERCENT, FLAT }
enum class EffectTarget { SELF, ALLY, ENEMY, ALL }
enum class RelicSetType { RELIC, ORNAMENT }
enum class EffectType {
    ATK_UP, HP_UP, DEF_UP, SPD_UP, CRIT_RATE_UP, CRIT_DMG_UP,
    DMG_UP, ELEMENTAL_DMG_UP, DEF_DOWN, DEF_PENETRATION, RES_PENETRATION,
    EFFECT_HIT_UP, EFFECT_RES_UP, BREAK_DMG_UP, HEAL_BONUS_UP, ENERGY_REGEN_UP
}