package com.starrail.agent.battle

import com.starrail.agent.battle.calculator.DamageCalculator
import com.starrail.agent.battle.calculator.CombatStats
import com.starrail.agent.battle.calculator.EnemyDefensiveStats
import com.starrail.agent.battle.calculator.DamageContext
import com.starrail.agent.core.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * 伤害计算器单元测试
 * 
 * 验证伤害公式的各乘区计算是否正确
 */
class DamageCalculatorTest {
    
    private val calculator = DamageCalculator()
    
    /**
     * 测试：基础攻击属性计算
     * 攻击力3000，倍率1.0，基础伤害应为3000
     */
    @Test
    fun testBaseDamage_fromAttack() {
        val attacker = CombatStats(
            level = 80,
            attack = 3000.0,
            defense = 1000.0,
            maxHp = 10000.0,
            speed = 135.0,
            critRate = 0.05,
            critDmg = 0.50
        )
        
        val defender = EnemyDefensiveStats(
            defense = 800.0,
            resistance = 0.0,
            toughness = 200
        )
        
        val skill = Skill(
            id = "test_basic",
            type = SkillType.BASIC,
            name = "测试普攻",
            description = "",
            scaling = listOf(ScalingEntry(StatType.ATK, 1.0))
        )
        
        val result = calculator.calculateExpectedDamage(
            attacker = attacker,
            defender = defender,
            skill = skill,
            context = DamageContext(isWeaknessBroken = false, skillTypeBonus = 0.0)
        )
        
        // 期望伤害 = 3000 * 1 * 1 * (80*2+2000)/(80*2+2000+800) * 1 * 1 * (1 + 0.05*0.50)
        val expectedDefenseZone = (80 * 2 + 2000.0) / (80 * 2 + 2000.0 + 800.0)
        val expectedRaw = 3000.0 * expectedDefenseZone
        val expectedDamage = expectedRaw * (1 + 0.05 * 0.50)
        
        assertEquals(expectedDamage, result, expectedDamage * 0.01)  // 允许1%误差
    }
    
    /**
     * 测试：暴击伤害计算
     * 暴击率100%时，必定暴击
     */
    @Test
    fun testCritDamage_guaranteedCrit() {
        val attacker = CombatStats(
            level = 80,
            attack = 2000.0,
            defense = 800.0,
            maxHp = 10000.0,
            speed = 100.0,
            critRate = 1.0,
            critDmg = 1.50
        )
        
        val defender = EnemyDefensiveStats(
            defense = 500.0,
            resistance = 0.0,
            toughness = 100
        )
        
        val skill = Skill(
            id = "test_skill",
            type = SkillType.SKILL,
            name = "测试战技",
            description = "",
            scaling = listOf(ScalingEntry(StatType.ATK, 2.0))
        )
        
        val result = calculator.calculateDamage(
            attacker = attacker,
            defender = defender,
            skill = skill,
            context = DamageContext(isWeaknessBroken = false, critRoll = 0.5)
        )
        
        // 暴击率100%时，随机roll 0.5 < 1.0，必定暴击
        assertTrue("应该暴击", result.isCrit)
        assertEquals("暴击伤害 = 非暴击 * (1 + 暴击伤害)", 
            result.rawDamage * (1 + 1.50), 
            result.critDamage, 
            0.01)
    }
    
    /**
     * 测试：零暴击时伤害
     */
    @Test
    fun testNoCrit() {
        val attacker = CombatStats(
            level = 80,
            attack = 1000.0,
            defense = 500.0,
            maxHp = 5000.0,
            speed = 100.0,
            critRate = 0.0,
            critDmg = 0.50
        )
        
        val defender = EnemyDefensiveStats(
            defense = 500.0,
            resistance = 0.0,
            toughness = 100
        )
        
        val skill = Skill(
            id = "test_basic",
            type = SkillType.BASIC,
            name = "测试",
            description = "",
            scaling = listOf(ScalingEntry(StatType.ATK, 1.0))
        )
        
        val result = calculator.calculateDamage(
            attacker = attacker,
            defender = defender,
            skill = skill,
            context = DamageContext(critRoll = 0.5)
        )
        
        // 暴击率0%时，不可能暴击
        assertFalse("不应该暴击", result.isCrit)
        assertEquals("非暴击伤害 = 原始伤害", result.rawDamage, result.critDamage, 0.01)
    }
    
    /**
     * 测试：防御穿透效果
     * 50%防御穿透应有效降低敌人防御
     */
    @Test
    fun testDefPenetration() {
        val attacker = CombatStats(
            level = 80,
            attack = 2000.0,
            defense = 800.0,
            maxHp = 10000.0,
            speed = 100.0,
            critRate = 0.5,
            critDmg = 1.0,
            defPenetration = 0.5
        )
        
        val defender = EnemyDefensiveStats(
            defense = 1000.0,
            resistance = 0.0,
            toughness = 100
        )
        
        val skill = Skill(
            id = "test",
            type = SkillType.SKILL,
            name = "测试",
            description = "",
            scaling = listOf(ScalingEntry(StatType.ATK, 1.0))
        )
        
        val result = calculator.calculateExpectedDamage(
            attacker = attacker,
            defender = defender,
            skill = skill,
            context = DamageContext()
        )
        
        // 有防御穿透的伤害应高于无防御穿透
        val noPenAttacker = attacker.copy(defPenetration = 0.0)
        val resultNoPen = calculator.calculateExpectedDamage(
            attacker = noPenAttacker,
            defender = defender,
            skill = skill,
            context = DamageContext()
        )
        
        assertTrue("防御穿透应提高伤害", result > resultNoPen)
    }
    
    /**
     * 测试：韧性击破状态增伤
     * 击破状态下敌人受到1.5倍伤害
     */
    @Test
    fun testWeaknessBreakBonus() {
        val attacker = CombatStats(
            level = 80,
            attack = 1000.0,
            defense = 500.0,
            maxHp = 5000.0,
            speed = 100.0,
            critRate = 0.0,
            critDmg = 0.0
        )
        
        val defender = EnemyDefensiveStats(
            defense = 500.0,
            resistance = 0.0,
            toughness = 200
        )
        
        val skill = Skill(
            id = "test",
            type = SkillType.BASIC,
            name = "测试",
            description = "",
            scaling = listOf(ScalingEntry(StatType.ATK, 1.0))
        )
        
        val brokenResult = calculator.calculateExpectedDamage(
            attacker = attacker,
            defender = defender,
            skill = skill,
            context = DamageContext(isWeaknessBroken = true)
        )
        
        val normalResult = calculator.calculateExpectedDamage(
            attacker = attacker,
            defender = defender,
            skill = skill,
            context = DamageContext(isWeaknessBroken = false)
        )
        
        assertEquals("击破状态应提升50%伤害", normalResult * 1.5, brokenResult, 0.01)
    }
}