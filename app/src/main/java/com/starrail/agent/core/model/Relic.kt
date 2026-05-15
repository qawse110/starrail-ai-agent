package com.starrail.agent.core.model

/**
 * 遗器相关数据模型
 * 定义遗器套装效果、词条等数据
 */

/** 遗器词条 */
data class StatEntry(
    val type: StatType,
    val value: Double  // 数值或百分比
) {
    /** 获取格式化显示值 */
    fun getDisplayValue(): String {
        return when (type) {
            StatType.HP, StatType.ATK, StatType.DEF -> 
                "${value.toInt()}"
            else -> 
                "${(value * 100).toInt()}%"
        }
    }
}

/** 套装效果 */
data class SetBonus(
    val requiredCount: Int,      // 2 或 4
    val effects: List<Effect>
) {
    val displayName: String get() = "${requiredCount}件套"
}

/** 遗器套装（静态模板） */
data class RelicSet(
    val id: String,
    val name: String,
    val setBonuses: List<SetBonus>,   // 2件套/4件套效果
    val type: RelicSetType,           // 遗器/位面饰品
    val recommendedStats: Map<RelicSlot, List<StatType>> = emptyMap()  // 推荐主词条
) {
    /** 获取指定件数的效果 */
    fun getSetBonus(count: Int): SetBonus? {
        return setBonuses.find { it.requiredCount == count }
    }
    
    /** 检查是否激活指定件数效果 */
    fun isSetBonusActive(equippedCount: Int, requiredCount: Int): Boolean {
        return equippedCount >= requiredCount
    }
}

/** 遗器模板（静态） */
data class RelicTemplate(
    val id: String,
    val setId: String,
    val slot: RelicSlot,
    val rarity: Int,               // 2-5星
    val possibleMainStats: List<StatType>,  // 可选主词条
    val possibleSubStats: List<StatType>    // 可选副词条
)

/** 遗器强化增量（每+3一档） */
data class StatIncrement(
    val level: Int,
    val increments: Map<StatType, Double>
)

/** 遗器副词条增量表 */
object RelicSubStatIncrements {
    /** 每级增量基础值（简化版，实际游戏中每+3才有主档） */
    val increments: Map<StatType, List<Double>> = mapOf(
        StatType.HP to listOf(24.0, 32.0, 40.0, 48.0, 56.0, 64.0, 72.0, 80.0, 88.0, 96.0, 104.0, 112.0, 120.0, 128.0, 136.0, 144.0),
        StatType.HP_PERCENT to listOf(1.6, 2.1, 2.6, 3.2, 3.8, 4.3, 4.8, 5.4, 6.0, 6.5, 7.1, 7.6, 8.2, 8.7, 9.3, 9.9),
        StatType.ATK to listOf(15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0, 50.0, 55.0, 60.0, 65.0, 70.0, 75.0, 80.0, 85.0, 90.0),
        StatType.ATK_PERCENT to listOf(1.6, 2.1, 2.6, 3.2, 3.8, 4.3, 4.8, 5.4, 6.0, 6.5, 7.1, 7.6, 8.2, 8.7, 9.3, 9.9),
        StatType.DEF to listOf(12.0, 16.0, 20.0, 24.0, 28.0, 32.0, 36.0, 40.0, 44.0, 48.0, 52.0, 56.0, 60.0, 64.0, 68.0, 72.0),
        StatType.DEF_PERCENT to listOf(1.6, 2.1, 2.6, 3.2, 3.8, 4.3, 4.8, 5.4, 6.0, 6.5, 7.1, 7.6, 8.2, 8.7, 9.3, 9.9),
        StatType.SPD to listOf(1.0, 1.3, 1.6, 2.0, 2.3, 2.6, 3.0, 3.3, 3.6, 4.0, 4.3, 4.6, 5.0, 5.3, 5.6, 6.0),
        StatType.CRIT_RATE to listOf(0.8, 1.0, 1.3, 1.6, 1.9, 2.1, 2.4, 2.7, 3.0, 3.2, 3.5, 3.8, 4.1, 4.3, 4.6, 4.9),
        StatType.CRIT_DMG to listOf(1.6, 2.1, 2.6, 3.2, 3.8, 4.3, 4.8, 5.4, 6.0, 6.5, 7.1, 7.6, 8.2, 8.7, 9.3, 9.9),
        StatType.EFFECT_HIT to listOf(1.3, 1.6, 2.0, 2.4, 2.8, 3.2, 3.7, 4.1, 4.5, 4.9, 5.3, 5.8, 6.2, 6.6, 7.0, 7.5),
        StatType.EFFECT_RES to listOf(1.3, 1.6, 2.0, 2.4, 2.8, 3.2, 3.7, 4.1, 4.5, 4.9, 5.3, 5.8, 6.2, 6.6, 7.0, 7.5),
        StatType.BREAK_DMG to listOf(2.4, 3.1, 3.9, 4.8, 5.6, 6.5, 7.3, 8.1, 9.0, 9.8, 10.6, 11.5, 12.3, 13.1, 14.0, 14.8),
        StatType.HEAL_BONUS to listOf(1.6, 2.1, 2.6, 3.2, 3.8, 4.3, 4.8, 5.4, 6.0, 6.5, 7.1, 7.6, 8.2, 8.7, 9.3, 9.9),
        StatType.ENERGY_RATE to listOf(1.3, 1.6, 2.0, 2.4, 2.8, 3.2, 3.7, 4.1, 4.5, 4.9, 5.3, 5.8, 6.2, 6.6, 7.0, 7.5)
    )
    
    /** 获取指定词条在指定等级的值 */
    fun getValue(statType: StatType, level: Int): Double {
        return increments[statType]?.getOrNull(level) ?: 0.0
    }
}