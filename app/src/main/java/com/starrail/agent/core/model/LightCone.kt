package com.starrail.agent.core.model

/**
 * 光锥相关数据模型
 * 定义光锥基础属性、技能、精炼数据
 */

/** 光锥技能效果 */
data class LightConeSkill(
    val name: String,
    val description: String,
    val effects: List<Effect>  // 各精炼等级的数值
)

/** 精炼等级数据 */
data class SuperimposeData(
    val level: Int,           // 精炼等级 1-5
    val effect: Effect,       // 该等级的效果
    val description: String   // 描述文本
)

/** 光锥数据（静态模板） */
data class LightCone(
    val id: String,
    val name: String,
    val rarity: Int,           // 稀有度 3/4/5
    val path: PathType,        // 适配命途
    val baseStats: BaseStats,  // 基础属性
    val ascensionStats: Map<Int, AscensionData>,  // 各突破等级属性
    val skill: LightConeSkill, // 光锥技能
    val superimposeLevels: List<SuperimposeData>  // 1-5精炼数据
) {
    /** 获取指定精炼等级的效果 */
    fun getSuperimposeEffect(level: Int): Effect? {
        return superimposeLevels.getOrNull(level - 1)?.effect
    }
    
    /** 获取精炼加成百分比 */
    fun getSuperimposeBonus(level: Int): Double {
        return superimposeLevels.take(level).sumOf { it.effect.value }
    }
}