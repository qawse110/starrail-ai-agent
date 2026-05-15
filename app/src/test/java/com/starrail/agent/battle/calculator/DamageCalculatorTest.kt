package com.starrail.agent.battle.calculator

import com.starrail.agent.core.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * 伤害计算器单元测试
 * 验证7乘区伤害公式的正确性
 */
class DamageCalculatorTest {

    private val calculator = DamageCalculator()

    // 标准80级输出角色属性（希儿模板）
    private val standardAttacker = CombatStats(
        level = 80,
        attack = 2600.0,
        defense = 800.0,
        maxHp = 4000.0,
        speed = 140.0,
        critRate = 0.75,
        critDmg = 1.50,
        elementalDmgBonus = 0.388,   // 量子伤害球38.8%
        dmgBonus = 0.0,
        breakEffect = 0.3
    )

    // 标准敌人防御（90级混沌敌人）
    private val standardDefender = EnemyDefensiveStats(
        defense = 1200.0,
        resistance = 0.20,
        toughness = 10
    )

    // 普通攻击技能（倍率100%）
    private val basicSkill = Skill(
        id = "basic_atk",
        type = SkillType.BASIC,
        name = "普攻",
        description = "普通攻击",
        scaling = listOf(ScalingEntry(StatType.ATK, 1.0)),
        energyGain = 20
    )

    @Test
    fun testBaseDamage_correctCalculation() {
        // 基础伤害 = 攻击力 × 倍率
        val skill = Skill("test", SkillType.SKILL, "测试", "测试技能",
            scaling = listOf(ScalingEntry(StatType.ATK, 2.0)), energyGain = 30)
        
        val result = calculator.calculateDamage(
            standardAttacker, standardDefender, skill,
            DamageContext(isWeaknessBroken = false)
        )
        
        // 基础伤害 = 2600 × 2.0 = 5200
        assertEquals("基础伤害", 5200.0, result.breakdown.baseDamage, 1.0)
    }

    @Test
    fun testDefenseZone_knownValue() {
        // 防御区 = (80*2 + 2000) / (80*2 + 2000 + 1200)
        //       = 2160 / (2160 + 1200) = 2160 / 3360 ≈ 0.6429
        val result = calculator.calculateDamage(
            standardAttacker, standardDefender, basicSkill,
            DamageContext()
        )
        
        assertEquals("防御区", 2160.0 / 3360.0, result.breakdown.defenseZone, 0.001)
    }

    @Test
    fun testResistanceZone_withResPen() {
        // 抗性区 = 1 - (0.20 - 0) = 0.80
        val result = calculator.calculateDamage(
            standardAttacker, standardDefender, basicSkill,
            DamageContext()
        )
        assertEquals("抗性区", 0.80, result.breakdown.resistanceZone, 0.001)
        
        // 带抗性穿透：1 - (0.20 - 0.20) = 1.0
        val attackerWithPen = standardAttacker.copy(resPenetration = 0.20)
        val result2 = calculator.calculateDamage(
            attackerWithPen, standardDefender, basicSkill,
            DamageContext()
        )
        assertEquals("抗性穿透后", 1.0, result2.breakdown.resistanceZone, 0.001)
    }

    @Test
    fun testWeaknessZone_brokenVsUnbroken() {
        // 未破韧
        val resultUnbroken = calculator.calculateDamage(
            standardAttacker, standardDefender, basicSkill,
            DamageContext(isWeaknessBroken = false)
        )
        assertEquals("未破韧", 1.0, resultUnbroken.breakdown.weaknessZone, 0.001)
        
        // 已破韧
        val resultBroken = calculator.calculateDamage(
            standardAttacker, standardDefender, basicSkill,
            DamageContext(isWeaknessBroken = true)
        )
        assertEquals("已破韧", 1.5, resultBroken.breakdown.weaknessZone, 0.001)
    }

    @Test
    fun testCritZone_critVsNonCrit() {
        // 必然暴击 (critRoll=0.0 < critRate=0.75)
        val resultCrit = calculator.calculateDamage(
            standardAttacker, standardDefender, basicSkill,
            DamageContext(critRoll = 0.0)
        )
        assertTrue("应该暴击", resultCrit.isCrit)
        assertEquals("暴击区", 1 + 1.50, resultCrit.breakdown.critZone, 0.001)
        
        // 必然不暴击 (critRoll=0.99 > critRate=0.75)
        val resultNonCrit = calculator.calculateDamage(
            standardAttacker, standardDefender, basicSkill,
            DamageContext(critRoll = 0.99)
        )
        assertFalse("不应暴击", resultNonCrit.isCrit)
        assertEquals("非暴击区", 1.0, resultNonCrit.breakdown.critZone, 0.001)
    }

    @Test
    fun testExpectedDamage_formula() {
        // 期望伤害 = rawDamage × (1 + critRate × critDmg)
        val expected = calculator.calculateExpectedDamage(
            standardAttacker, standardDefender, basicSkill,
            DamageContext()
        )
        
        // 手动计算期望
        val baseDmg = 2600.0 * 1.0  // 基础伤害
        val dmgBonus = 1.0 + 0.388  // 增伤区
        val defZone = 2160.0 / 3360.0
        val resZone = 1.0 - 0.20
        val rawDmg = baseDmg * 1.0 * dmgBonus * defZone * resZone * 1.0
        val expectedManual = rawDmg * (1 + 0.75 * 1.50)
        
        assertEquals("期望伤害", expectedManual, expected, 1.0)
    }

    @Test
    fun testHpScaling_damage() {
        // HP倍率角色（如刃）
        val hpAttacker = standardAttacker.copy(attack = 800.0, maxHp = 8000.0)
        val hpSkill = Skill("hp_skill", SkillType.SKILL, "HP倍率技能", "基于HP的技能",
            scaling = listOf(ScalingEntry(StatType.HP, 1.5)), energyGain = 30)
        
        val result = calculator.calculateDamage(
            hpAttacker, standardDefender, hpSkill,
            DamageContext(critRoll = 0.0)
        )
        
        // 基础伤害 = HP × 倍率 = 8000 × 1.5 = 12000
        assertEquals("HP倍率基础伤害", 12000.0, result.breakdown.baseDamage, 1.0)
    }

    @Test
    fun testBreakDamage() {
        val breakDmg = calculator.calculateBreakDamage(
            standardAttacker, standardDefender, ElementType.量子,
            DamageContext()
        )
        
        // 击破伤害 = 1000 × (1 + 0.3) = 1300
        assertEquals("击破伤害", 1300.0, breakDmg, 1.0)
    }

    @Test
    fun testDotDamage() {
        val dotDmg = calculator.calculateDotDamage(
            standardAttacker, standardDefender, DotType.BURN,
            DamageContext()
        )
        
        // DoT = 2600 * 0.5 * 1.0 * (1 + 0.3*0.5) = 1300 * 1.15 = 1495
        val expected = 2600.0 * 0.5 * 1.0 * (1 + 0.3 * 0.5)
        assertEquals("DoT伤害", expected, dotDmg, 1.0)
    }

    @Test
    fun testDefensePenetration_effect() {
        val defender = standardDefender.copy(defense = 1200.0)
        
        // 无防御穿透
        val noPen = calculator.calculateExpectedDamage(
            standardAttacker, defender, basicSkill, DamageContext()
        )
        
        // 30%防御穿透
        val attackerWithPen = standardAttacker.copy(defPenetration = 0.30)
        val withPen = calculator.calculateExpectedDamage(
            attackerWithPen, defender, basicSkill, DamageContext()
        )
        
        // 30%防御穿透应提高伤害
        assertTrue("防御穿透应提高伤害", withPen > noPen)
    }

    @Test
    fun testDamageBreakdown_getTotalMultiplier() {
        val result = calculator.calculateDamage(
            standardAttacker, standardDefender, basicSkill,
            DamageContext(isWeaknessBroken = true, critRoll = 0.0)
        )
        
        val totalMult = result.breakdown.getTotalMultiplier()
        val expectedMult = result.breakdown.multiplierZone * 
                           result.breakdown.damageBonusZone *
                           result.breakdown.defenseZone *
                           result.breakdown.resistanceZone *
                           result.breakdown.weaknessZone *
                           result.breakdown.critZone
        
        assertEquals("总乘区乘积", expectedMult, totalMult, 0.001)
    }

    @Test
    fun testDamageIncreases_withBuffs() {
        // 对比加成前后的伤害
        val noBuff = calculator.calculateExpectedDamage(
            standardAttacker, standardDefender, basicSkill, DamageContext()
        )
        
        // 添加50%全增伤Buff
        val buffed = standardAttacker.copy(dmgBonus = 0.50)
        val withBuff = calculator.calculateExpectedDamage(
            buffed, standardDefender, basicSkill, DamageContext()
        )
        
        // 增伤区从 1.388 → 1.888，伤害应提升约 1.888/1.388 ≈ 1.36倍
        val expectedRatio = (1.0 + 0.388 + 0.50) / (1.0 + 0.388)
        assertEquals("增伤后伤害比", expectedRatio, withBuff / noBuff, 0.01)
    }

    @Test
    fun testDamageIsPositive() {
        // 伤害计算在任何情况下都应为正数
        val zeroAtk = standardAttacker.copy(attack = 0.0)
        val result = calculator.calculateExpectedDamage(
            zeroAtk, standardDefender, basicSkill, DamageContext()
        )
        assertTrue("伤害应为正数", result >= 0.0)
    }
}
