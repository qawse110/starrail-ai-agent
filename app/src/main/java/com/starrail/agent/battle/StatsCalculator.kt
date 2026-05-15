package com.starrail.agent.battle

import com.starrail.agent.core.model.*
import com.starrail.agent.player.PlayerCharacter
import com.starrail.agent.player.PlayerLightCone
import com.starrail.agent.player.PlayerRelic

/**
 * 属性计算器
 * 
 * 将角色基础属性 + 光锥 + 遗器 + 行迹加成合并为最终战斗面板属性
 * 
 * 计算公式:
 *   最终属性 = (基础属性 + 固定值加成) × (1 + 百分比加成总和)
 */
class StatsCalculator {
    
    /**
     * 计算角色的战斗面板属性
     * 
     * @param character 角色模板
     * @param playerData 玩家持有数据（等级/星魂/技能等级）
     * @param lightCone 装备的光锥
     * @param relics 装备的遗器（6件）
     * @param setBonuses 激活的套装效果列表
     * @return 战斗面板属性
     */
    fun computeCombatStats(
        character: Character,
        playerData: PlayerCharacter? = null,
        lightCone: PlayerLightCone? = null,
        relics: List<PlayerRelic> = emptyList(),
        setBonuses: List<Effect> = emptyList()
    ): CombatStats2 {
        val builder = StatsBuilder(character)
        
        // 1. 应用角色基础属性
        builder.addBase(character.baseStats)
        
        // 2. 应用突破等级加成
        if (playerData != null) {
            val ascData = character.ascensionStats[playerData.ascension]
            if (ascData != null) {
                builder.addFlat(ascData)
            }
        }
        
        // 3. 应用光锥属性
        if (lightCone != null) {
            val lcTemplate = lightCone.let { /* 查询光锥模板 - 简化处理 */ null }
            // 简化：光锥基础属性
            builder.addFlat(StatType.ATK, lightCone.ascension * 20.0)
        }
        
        // 4. 应用遗器主词条
        for (relic in relics) {
            builder.addStat(relic.mainStat.type, relic.mainStat.value)
            for (subStat in relic.subStats) {
                builder.addStat(subStat.type, subStat.value)
            }
        }
        
        // 5. 应用套装效果
        for (effect in setBonuses) {
            applyEffect(builder, effect)
        }
        
        // 6. 应用行迹加成
        if (playerData != null) {
            val traceAttack = playerData.traces.size * 0.04  // 简化
            builder.addPercent(StatType.ATK_PERCENT, traceAttack)
        }
        
        return builder.build(playerData?.level ?: 80)
    }
    
    /**
     * 根据星级获取副词条基础值
     * 5星遗器每+3随机增长一次
     */
    fun getSubStatBaseValue(statType: StatType, rarity: Int): Double {
        return when (statType) {
            StatType.HP -> 42.0
            StatType.ATK -> 21.0
            StatType.DEF -> 21.0
            StatType.HP_PERCENT -> 0.04
            StatType.ATK_PERCENT -> 0.04
            StatType.DEF_PERCENT -> 0.04
            StatType.SPD -> 2.3
            StatType.CRIT_RATE -> 0.027
            StatType.CRIT_DMG -> 0.054
            StatType.EFFECT_HIT -> 0.04
            StatType.EFFECT_RES -> 0.04
            StatType.BREAK_DMG -> 0.06
            StatType.HEAL_BONUS -> 0.04
            StatType.ENERGY_RATE -> 0.03
            StatType.ELEMENTAL_DMG_UP -> 0.04
        } * (rarity / 5.0)
    }
    
    /**
     * 应用效果到属性构建器
     */
    private fun applyEffect(builder: StatsBuilder, effect: Effect) {
        val multiplier = if (effect.target == EffectTarget.ENEMY) 0.0 else 1.0
        if (multiplier == 0.0) return  // 不计算对敌人的效果
        
        when (effect.type) {
            EffectType.ATK_UP -> builder.addPercent(StatType.ATK_PERCENT, effect.value)
            EffectType.HP_UP -> builder.addPercent(StatType.HP_PERCENT, effect.value)
            EffectType.DEF_UP -> builder.addPercent(StatType.DEF_PERCENT, effect.value)
            EffectType.SPD_UP -> builder.addStat(StatType.SPD, effect.value)
            EffectType.CRIT_RATE_UP -> builder.addStat(StatType.CRIT_RATE, effect.value)
            EffectType.CRIT_DMG_UP -> builder.addStat(StatType.CRIT_DMG, effect.value)
            EffectType.DMG_UP -> {}  // 伤害加成在伤害计算器处理
            EffectType.BREAK_DMG_UP -> builder.addStat(StatType.BREAK_DMG, effect.value)
            EffectType.ENERGY_REGEN_UP -> builder.addStat(StatType.ENERGY_RATE, effect.value)
            EffectType.EFFECT_HIT_UP -> builder.addStat(StatType.EFFECT_HIT, effect.value)
            EffectType.EFFECT_RES_UP -> builder.addStat(StatType.EFFECT_RES, effect.value)
            else -> {}
        }
    }
}

/**
 * 战斗属性（扩展版）
 */
data class CombatStats2(
    val level: Int,
    val maxHp: Double,
    val attack: Double,
    val defense: Double,
    val speed: Double,
    val critRate: Double,
    val critDmg: Double,
    val energyRegen: Double,
    val effectHitRate: Double,
    val effectResistance: Double,
    val breakEffect: Double,
    val healingBonus: Double,
    // 伤害加成（按属性）
    val elementalDmgBonus: Double = 0.0,
    val dmgBonus: Double = 0.0,
    val defPenetration: Double = 0.0,
    val resPenetration: Double = 0.0
) {
    /** 转换为伤害计算器可用的格式 */
    fun toCombatStats(): com.starrail.agent.battle.calculator.CombatStats {
        return com.starrail.agent.battle.calculator.CombatStats(
            level = level,
            attack = attack,
            defense = defense,
            maxHp = maxHp,
            speed = speed,
            critRate = critRate,
            critDmg = critDmg,
            elementalDmgBonus = elementalDmgBonus,
            dmgBonus = dmgBonus,
            defPenetration = defPenetration,
            resPenetration = resPenetration,
            breakEffect = breakEffect
        )
    }
}

/**
 * 属性构建器（内部类）
 */
private class StatsBuilder(private val character: Character) {
    private var level = 80
    
    // 固定值（基础 + 突破 + 固定词条）
    private var baseHp = 0.0
    private var baseAtk = 0.0
    private var baseDef = 0.0
    private var baseSpd = 0.0
    
    // 百分比加成
    private var hpPercent = 1.0
    private var atkPercent = 1.0
    private var defPercent = 1.0
    private var spdPercent = 1.0
    
    // 固定追加
    private var flatHp = 0.0
    private var flatAtk = 0.0
    private var flatDef = 0.0
    private var flatSpd = 0.0
    
    // 其他属性
    private var critRate = 0.05
    private var critDmg = 0.50
    private var energyRegen = 1.0
    private var effectHit = 0.0
    private var effectRes = 0.0
    private var breakEffect = 1.0
    private var healingBonus = 0.0
    
    /** 添加基础属性 */
    fun addBase(stats: BaseStats) {
        baseHp += stats.hp
        baseAtk += stats.attack
        baseDef += stats.defense
        baseSpd += stats.speed
        critRate = stats.critRate
        critDmg = stats.critDmg
    }
    
    /** 添加突破属性 */
    fun addFlat(asc: AscensionData) {
        baseHp += asc.hp
        baseAtk += asc.attack
        baseDef += asc.defense
        baseSpd += asc.speed
    }
    
    /** 添加指定类型的固定值 */
    fun addFlat(type: StatType, value: Double) {
        when (type) {
            StatType.HP -> flatHp += value
            StatType.ATK -> flatAtk += value
            StatType.DEF -> flatDef += value
            StatType.SPD -> flatSpd += value
            else -> {}
        }
    }
    
    /** 添加百分比加成 */
    fun addPercent(type: StatType, value: Double) {
        when (type) {
            StatType.HP_PERCENT -> hpPercent += value
            StatType.ATK_PERCENT -> atkPercent += value
            StatType.DEF_PERCENT -> defPercent += value
            else -> {}
        }
    }
    
    /** 添加词条属性（自动识别固定/百分比） */
    fun addStat(type: StatType, value: Double) {
        when (type) {
            StatType.HP -> flatHp += value
            StatType.ATK -> flatAtk += value
            StatType.DEF -> flatDef += value
            StatType.SPD -> flatSpd += value
            StatType.HP_PERCENT -> hpPercent += value
            StatType.ATK_PERCENT -> atkPercent += value
            StatType.DEF_PERCENT -> defPercent += value
            StatType.CRIT_RATE -> critRate += value
            StatType.CRIT_DMG -> critDmg += value
            StatType.EFFECT_HIT -> effectHit += value
            StatType.EFFECT_RES -> effectRes += value
            StatType.BREAK_DMG -> breakEffect += value
            StatType.HEAL_BONUS -> healingBonus += value
            StatType.ENERGY_RATE -> energyRegen += value
            StatType.ELEMENTAL_DMG_UP -> {}  // 属性伤害加成在伤害计算器处理
        }
    }
    
    /** 构建最终属性 */
    fun build(charLevel: Int): CombatStats2 {
        level = charLevel
        
        return CombatStats2(
            level = level,
            maxHp = (baseHp + flatHp) * hpPercent,
            attack = (baseAtk + flatAtk) * atkPercent,
            defense = (baseDef + flatDef) * defPercent,
            speed = (baseSpd + flatSpd) * spdPercent,
            critRate = critRate.coerceIn(0.0, 1.0),
            critDmg = critDmg,
            energyRegen = energyRegen,
            effectHitRate = effectHit,
            effectResistance = effectRes,
            breakEffect = breakEffect,
            healingBonus = healingBonus
        )
    }
}

/**
 * 辅助数据类
 */
private data class Attack(val value: Double)