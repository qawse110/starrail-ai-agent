package com.starrail.agent.battle.model

import com.starrail.agent.core.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * 战斗模型量化器单元测试
 */
class BattleModelQuantifierTest {

    private val quantifier = BattleModelQuantifier()

    private val mainDps = Character(
        id = "char_main",
        name = "测试主C",
        rarity = 5,
        path = PathType.巡猎,
        element = ElementType.量子,
        baseStats = BaseStats(hp = 1000.0, attack = 700.0, defense = 400.0, speed = 110.0),
        ascensionStats = emptyMap(),
        skills = listOf(
            Skill("basic", SkillType.BASIC, "普攻", "", listOf(ScalingEntry(StatType.ATK, 1.0))),
            Skill("skill", SkillType.SKILL, "战技", "", listOf(ScalingEntry(StatType.ATK, 2.0))),
            Skill("ult", SkillType.ULTIMATE, "终结技", "", listOf(ScalingEntry(StatType.ATK, 4.0)))
        ),
        traces = emptyList(),
        eidolons = emptyList()
    )

    private val harmonySupport = Character(
        id = "char_support",
        name = "同谐辅助",
        rarity = 5,
        path = PathType.同谐,
        element = ElementType.冰,
        baseStats = BaseStats(hp = 900.0, attack = 500.0, defense = 400.0, speed = 100.0),
        ascensionStats = emptyMap(),
        skills = emptyList(),
        traces = emptyList(),
        eidolons = emptyList()
    )

    private val nihilitySupport = Character(
        id = "char_debuff",
        name = "虚无辅助",
        rarity = 5,
        path = PathType.虚无,
        element = ElementType.风,
        baseStats = BaseStats(hp = 900.0, attack = 500.0, defense = 400.0, speed = 100.0),
        ascensionStats = emptyMap(),
        skills = emptyList(),
        traces = emptyList(),
        eidolons = emptyList()
    )

    private val quantumEnemy = Enemy(
        id = "enemy_test",
        name = "量子弱点敌人",
        level = 90,
        toughness = 10,
        weakness = listOf(ElementType.量子),
        resistance = mapOf(ElementType.量子 to 0.2),
        stats = BaseStats(hp = 40000.0, attack = 600.0, defense = 800.0, speed = 90.0),
        debuffResistance = 0.3,
        location = "测试关卡"
    )

    @Test
    fun testSoloQuantify() {
        val result = quantifier.quantify(mainDps)
        assertTrue(result.cycleDamage > 0)
        assertTrue(result.actionsPerCycle > 0)
        assertEquals(1.0, result.teamBuffMultiplier, 0.001)
        assertTrue(result.dpsScore in 0.0..100.0)
    }

    @Test
    fun testTeamBuffIncreasesDamage() {
        val solo = quantifier.quantify(mainDps)
        val team = quantifier.quantify(mainDps, team = listOf(harmonySupport, nihilitySupport))
        assertTrue("配队伤害应高于单人", team.cycleDamage > solo.cycleDamage)
        assertTrue("配队增益应>1", team.teamBuffMultiplier > 1.0)
    }

    @Test
    fun testWeaknessMatch() {
        val result = quantifier.quantify(mainDps, enemy = quantumEnemy)
        assertTrue("应命中量子弱点", result.weaknessMatch)
    }

    @Test
    fun testScorePositiveAndBounded() {
        val result = quantifier.quantify(
            mainDps,
            team = listOf(harmonySupport, nihilitySupport),
            enemy = quantumEnemy
        )
        assertTrue(result.dpsScore in 0.0..100.0)
        assertNotNull(result.summary)
    }
}