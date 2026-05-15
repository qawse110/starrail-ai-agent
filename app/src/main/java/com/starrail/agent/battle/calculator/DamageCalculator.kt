package com.starrail.agent.battle.calculator

import com.starrail.agent.core.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 伤害计算器
 * 实现星穹铁道的完整伤害公式
 * 
 * 公式参考：
 * 最终伤害 = 基础伤害 × 伤害倍率区 × 增伤区 × 防御区 × 抗性区 × 易伤区 × 暴击区
 */
class DamageCalculator {
    
    /** 计算伤害结果 */
    fun calculateDamage(
        attacker: CombatStats,
        defender: EnemyDefensiveStats,
        skill: Skill,
        context: DamageContext
    ): DamageResult {
        // 1. 计算基础伤害
        val baseDamage = calculateBaseDamage(attacker, skill)
        
        // 2. 计算各乘区
        val multiplierZone = calculateMultiplierZone(skill)
        val damageBonusZone = calculateDamageBonusZone(attacker, context)
        val defenseZone = calculateDefenseZone(attacker.level, defender.defense, attacker.defPenetration)
        val resistanceZone = calculateResistanceZone(defender.resistance, attacker.resPenetration)
        val weaknessZone = calculateWeaknessZone(context.isWeaknessBroken)
        val critZone = calculateCritZone(attacker.critRate, attacker.critDmg, context.critRoll)
        
        // 3. 计算最终伤害
        val rawDamage = baseDamage * multiplierZone * damageBonusZone * defenseZone * 
                        resistanceZone * weaknessZone
        val finalDamage = rawDamage * critZone
        
        return DamageResult(
            rawDamage = rawDamage,
            critDamage = if (context.critRoll < attacker.critRate) finalDamage else rawDamage,
            expectedDamage = rawDamage * (1 + attacker.critRate * attacker.critDmg),
            isCrit = context.critRoll < attacker.critRate,
            breakdown = DamageBreakdown(
                baseDamage = baseDamage,
                multiplierZone = multiplierZone,
                damageBonusZone = damageBonusZone,
                defenseZone = defenseZone,
                resistanceZone = resistanceZone,
                weaknessZone = weaknessZone,
                critZone = critZone
            )
        )
    }
    
    /** 计算基础伤害（攻击力 × 技能倍率） */
    private fun calculateBaseDamage(attacker: CombatStats, skill: Skill): Double {
        var baseStat = when {
            // 根据技能使用的属性类型选择基础属性
            skill.scaling.any { it.stat == StatType.ATK } -> attacker.attack
            skill.scaling.any { it.stat == StatType.HP } -> attacker.maxHp
            skill.scaling.any { it.stat == StatType.DEF } -> attacker.defense
            else -> attacker.attack
        }
        
        val multiplier = skill.scaling.sumOf { it.multiplier }
        
        return baseStat * multiplier
    }
    
    /** 计算伤害倍率区 */
    private fun calculateMultiplierZone(skill: Skill): Double {
        return skill.scaling.size.coerceAtLeast(1).toDouble()
    }
    
    /** 计算增伤区 */
    private fun calculateDamageBonusZone(attacker: CombatStats, context: DamageContext): Double {
        var bonus = 1.0
        
        // 元素伤害加成
        bonus += attacker.elementalDmgBonus
        
        // 全伤害加成
        bonus += attacker.dmgBonus
        
        // 技能类型增伤（普攻/战技/终结技加成）
        bonus += context.skillTypeBonus
        
        // 额外增伤（如遗器套装效果）
        bonus += attacker.additionalDmgBonus
        
        return bonus.coerceAtLeast(0.0)
    }
    
    /** 计算防御区 */
    private fun calculateDefenseZone(
        attackerLevel: Int,
        defenderDefense: Double,
        defPenetration: Double
    ): Double {
        // 防御公式：(角色等级*2 + 2000) / (角色等级*2 + 2000 + 敌人防御 × (1 - 防御穿透))
        val levelFactor = attackerLevel * 2 + 2000
        val effectiveDefense = defenderDefense * (1 - defPenetration)
        
        return levelFactor / (levelFactor + effectiveDefense)
    }
    
    /** 计算抗性区 */
    private fun calculateResistanceZone(
        defenderResistance: Double,
        resPenetration: Double
    ): Double {
        // 抗性公式：1 - (敌人属性抗性 - 抗性穿透)
        return max(0.0, 1 - (defenderResistance - resPenetration))
    }
    
    /** 计算弱点区（韧性已破） */
    private fun calculateWeaknessZone(isWeaknessBroken: Boolean): Double {
        return if (isWeaknessBroken) 1.5 else 1.0
    }
    
    /** 计算暴击区 */
    private fun calculateCritZone(
        critRate: Double,
        critDmg: Double,
        critRoll: Double
    ): Double {
        return if (critRoll < critRate) {
            1 + critDmg
        } else {
            1.0
        }
    }
    
    /** 计算期望伤害（用于评分和比较） */
    fun calculateExpectedDamage(
        attacker: CombatStats,
        defender: EnemyDefensiveStats,
        skill: Skill,
        context: DamageContext
    ): Double {
        val baseDamage = calculateBaseDamage(attacker, skill)
        val multiplierZone = calculateMultiplierZone(skill)
        val damageBonusZone = calculateDamageBonusZone(attacker, context)
        val defenseZone = calculateDefenseZone(attacker.level, defender.defense, attacker.defPenetration)
        val resistanceZone = calculateResistanceZone(defender.resistance, attacker.resPenetration)
        val weaknessZone = calculateWeaknessZone(context.isWeaknessBroken)
        
        val rawDamage = baseDamage * multiplierZone * damageBonusZone * defenseZone * 
                        resistanceZone * weaknessZone
        
        // 期望伤害 = 未暴击伤害 × (1 + 暴击率 × 暴击伤害)
        return rawDamage * (1 + attacker.critRate * attacker.critDmg)
    }
    
    /** 计算击破伤害 */
    fun calculateBreakDamage(
        attacker: CombatStats,
        defender: EnemyDefensiveStats,
        element: ElementType,
        context: DamageContext
    ): Double {
        // 击破伤害 = 基础击破伤害 × (1 + 击破特攻)
        val baseBreakDamage = 1000.0  // 简化，实际根据敌人等级和韧性计算
        val breakDmgMultiplier = 1 + attacker.breakEffect
        
        return baseBreakDamage * breakDmgMultiplier
    }
    
    /** 计算持续伤害 (DoT) */
    fun calculateDotDamage(
        attacker: CombatStats,
        defender: EnemyDefensiveStats,
        dotType: DotType,
        context: DamageContext
    ): Double {
        val baseDamage = attacker.attack * 0.5  // 简化
        val dotMultiplier = when (dotType) {
            DotType.BURN -> 1.0
            DotType.BLEED -> 0.8
            DotType.SHOCK -> 1.2
            DotType.WIND_SHEAR -> 1.0
            DotType.QUANTUM -> 1.1
        }
        
        return baseDamage * dotMultiplier * (1 + attacker.breakEffect * 0.5)
    }
}

/** 战斗中的属性（已计算装备和Buff加成） */
data class CombatStats(
    val level: Int,
    val attack: Double,
    val defense: Double,
    val maxHp: Double,
    val speed: Double,
    val critRate: Double,
    val critDmg: Double,
    val elementalDmgBonus: Double = 0.0,
    val dmgBonus: Double = 0.0,
    val defPenetration: Double = 0.0,
    val resPenetration: Double = 0.0,
    val breakEffect: Double = 0.0,
    val additionalDmgBonus: Double = 0.0
)

/** 敌人防御属性 */
data class EnemyDefensiveStats(
    val defense: Double,
    val resistance: Double,
    val toughness: Int
)

/** 伤害计算上下文 */
data class DamageContext(
    val isWeaknessBroken: Boolean = false,
    val skillTypeBonus: Double = 0.0,  // 普攻/战技/终结技各自加成
    val critRoll: Double = Random.nextDouble()  // 暴击随机数
)

/** 伤害结果 */
data class DamageResult(
    val rawDamage: Double,
    val critDamage: Double,
    val expectedDamage: Double,
    val isCrit: Boolean,
    val breakdown: DamageBreakdown
)

/** 伤害公式各乘区明细 */
data class DamageBreakdown(
    val baseDamage: Double,
    val multiplierZone: Double,
    val damageBonusZone: Double,
    val defenseZone: Double,
    val resistanceZone: Double,
    val weaknessZone: Double,
    val critZone: Double
) {
    fun getTotalMultiplier(): Double {
        return multiplierZone * damageBonusZone * defenseZone * 
               resistanceZone * weaknessZone * critZone
    }
}

/** 持续伤害类型 */
enum class DotType {
    BURN,       // 灼烧
    BLEED,      // 流血
    SHOCK,      // 触电
    WIND_SHEAR, // 风蚀
    QUANTUM     // 量子
}