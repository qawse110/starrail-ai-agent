package com.starrail.agent.core.model

/**
 * 敌人数据
 */
data class Enemy(
    val id: String,
    val name: String,
    val level: Int,
    val toughness: Int,                        // 韧性
    val weakness: List<ElementType>,             // 弱点属性
    val resistance: Map<ElementType, Double>,   // 属性抗性
    val stats: BaseStats,
    val debuffResistance: Double,               // 控制抵抗
    val location: String? = null                // 所属副本
)
