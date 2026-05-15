package com.starrail.agent.core.model

/**
 * 通用效果模型
 * 用于描述 Buff、Debuff、套装效果等
 */

/** 效果数据 */
data class Effect(
    val type: EffectType,           // 效果类型
    val source: String,             // 来源描述
    val value: Double,              // 数值
    val valueType: ValueType = ValueType.PERCENT,  // 数值类型
    val duration: Int? = null,      // 持续回合，null=常驻
    val stackable: Boolean = false,  // 是否可叠加
    val maxStack: Int = 1,          // 最大叠加层数
    val target: EffectTarget = EffectTarget.SELF  // 生效目标
) {
    /** 获取显示值（考虑百分比） */
    fun getDisplayValue(): String {
        val displayValue = if (valueType == ValueType.PERCENT) {
            "${(value * 100).toInt()}%"
        } else {
            value.toInt().toString()
        }
        return displayValue
    }
    
    /** 检查是否为常驻效果 */
    val isPermanent: Boolean get() = duration == null
    
    /** 检查是否可刷新持续时间 */
    val canRefresh: Boolean get() = !stackable
}

/** 激活中的 Buff */
data class ActiveBuff(
    val effect: Effect,
    val sourceId: String,
    val remainingTurns: Int?,
    val currentStack: Int = 1,
    val appliedTime: Int  // 作用回合序号
) {
    /** 更新持续时间 */
    fun tick(): ActiveBuff {
        return if (remainingTurns != null) {
            copy(remainingTurns = remainingTurns - 1)
        } else {
            this
        }
    }
    
    /** 是否已过期 */
    val isExpired: Boolean get() = remainingTurns != null && remainingTurns <= 0
}

/** Buff 来源类型 */
enum class BuffSource {
    CHARACTER_TRACE,    // 角色行迹
    EIDOLON,           // 星魂
    LIGHT_CONE,         // 光锥
    RELIC_SET,         // 遗器套装
    TEAMMATE,          // 队友技能
    ENEMY              // 敌人
}