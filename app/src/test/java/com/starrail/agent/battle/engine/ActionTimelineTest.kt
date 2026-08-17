package com.starrail.agent.battle.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * 行动轴 / 行动值计算器单元测试
 */
class ActionTimelineTest {

    @Test
    fun testActionValue_100Speed() {
        assertEquals(100.0, ActionTimeline.actionValue(100.0), 0.001)
    }

    @Test
    fun testActionValue_134Speed() {
        assertEquals(10000.0 / 134.0, ActionTimeline.actionValue(134.0), 0.001)
    }

    @Test
    fun testActionsInFirstCycle() {
        assertEquals("100速首轮1动", 1, ActionTimeline.actionsInCycles(100.0, 1))
        assertEquals("134速首轮2动", 2, ActionTimeline.actionsInCycles(134.0, 1))
        assertEquals("200速首轮3动", 3, ActionTimeline.actionsInCycles(200.0, 1))
    }

    @Test
    fun testActionsInTwoCycles() {
        assertEquals("100速2轮2动", 2, ActionTimeline.actionsInCycles(100.0, 2))
        assertEquals("134速2轮3动", 3, ActionTimeline.actionsInCycles(134.0, 2))
        assertEquals("200速2轮5动", 5, ActionTimeline.actionsInCycles(200.0, 2))
    }

    @Test
    fun testTotalActionValue() {
        assertEquals(150.0, ActionTimeline.totalActionValue(1), 0.001)
        assertEquals(250.0, ActionTimeline.totalActionValue(2), 0.001)
        assertEquals(350.0, ActionTimeline.totalActionValue(3), 0.001)
        assertEquals(0.0, ActionTimeline.totalActionValue(0), 0.001)
    }

    @Test
    fun testActionAdvance() {
        assertEquals("提前30%", 70.0, ActionTimeline.applyActionAdvance(100.0, 0.30), 0.001)
        assertEquals("提前100%立即行动", 0.0, ActionTimeline.applyActionAdvance(100.0, 1.0), 0.001)
        assertEquals("提前超过100%截断为0", 0.0, ActionTimeline.applyActionAdvance(100.0, 1.5), 0.001)
    }

    @Test
    fun testActionDelay() {
        assertEquals("延后20%", 120.0, ActionTimeline.applyActionDelay(100.0, 0.20), 0.001)
        assertEquals("延后0%不变", 100.0, ActionTimeline.applyActionDelay(100.0, 0.0), 0.001)
    }
}
