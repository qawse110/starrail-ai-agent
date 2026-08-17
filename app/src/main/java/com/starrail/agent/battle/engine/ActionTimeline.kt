package com.starrail.agent.battle.engine

import kotlin.math.max

/**
 * 行动轴 / 行动值计算器
 *
 * 星穹铁道核心规则：
 * - 行动值 (Action Value) = 10000 / 速度
 * - 混沌回忆/虚构叙事：首轮 150 行动值，之后每轮 100 行动值
 * - 单位在累计行动值达到自身行动值时出手
 * - 行动提前：剩余行动值 × (1 - 提前%)
 * - 行动延后：剩余行动值 × (1 + 延后%)
 */
object ActionTimeline {

    const val BASE_ACTION_VALUE = 10000.0
    const val FIRST_CYCLE_AV = 150.0
    const val SUBSEQUENT_CYCLE_AV = 100.0

    /** 计算单次行动所需行动值 */
    fun actionValue(speed: Double): Double {
        return BASE_ACTION_VALUE / speed.coerceAtLeast(0.01)
    }

    /** 计算指定总行动值内可行动次数（向下取整） */
    fun actionCountForActionValue(speed: Double, totalActionValue: Double): Int {
        if (totalActionValue <= 0.0 || speed <= 0.0) return 0
        return (totalActionValue * speed / BASE_ACTION_VALUE).toInt()
    }

    /** 计算 N 轮的总行动值（首轮 150，后续每轮 100） */
    fun totalActionValue(
        cycles: Int,
        firstCycleAv: Double = FIRST_CYCLE_AV,
        subsequentCycleAv: Double = SUBSEQUENT_CYCLE_AV
    ): Double {
        if (cycles <= 0) return 0.0
        return if (cycles == 1) firstCycleAv
        else firstCycleAv + (cycles - 1) * subsequentCycleAv
    }

    /** 计算 N 轮内可行动次数 */
    fun actionsInCycles(
        speed: Double,
        cycles: Int,
        firstCycleAv: Double = FIRST_CYCLE_AV,
        subsequentCycleAv: Double = SUBSEQUENT_CYCLE_AV
    ): Int {
        return actionCountForActionValue(speed, totalActionValue(cycles, firstCycleAv, subsequentCycleAv))
    }

    /** 行动提前：百分比作用于剩余行动值 */
    fun applyActionAdvance(remainingActionValue: Double, advancePercent: Double): Double {
        val pct = advancePercent.coerceIn(0.0, 1.0)
        return max(0.0, remainingActionValue * (1.0 - pct))
    }

    /** 行动延后：百分比作用于剩余行动值 */
    fun applyActionDelay(remainingActionValue: Double, delayPercent: Double): Double {
        return remainingActionValue * (1.0 + delayPercent.coerceAtLeast(0.0))
    }
}
