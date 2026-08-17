package com.starrail.agent.battle.model

import com.starrail.agent.battle.calculator.*
import com.starrail.agent.battle.engine.ActionTimeline
import com.starrail.agent.core.model.*

/**
 * 战斗模型量化器
 *
 * 对不同角色估算标准循环伤害，并叠加配队带来的增益（同谐/虚无/生存位），
 * 结合行动轴和敌人弱点情况输出可对比的量化评分。
 */
data class BattleModelContext(
    val supportAtkBonus: Double = 0.0,
    val supportDmgBonus: Double = 0.0,
    val supportDefPen: Double = 0.0,
    val supportResPen: Double = 0.0,
    val harmonyCount: Int = 0,
    val nihilityCount: Int = 0,
    val sustainPresent: Boolean = false
)

data class BattleModelResult(
    val characterId: String,
    val characterName: String,
    val element: ElementType,
    val path: PathType,
    val baseAttack: Double,
    val combatAttack: Double,
    val cycleDamage: Double,
    val soloCycleDamage: Double,
    val actionsPerCycle: Double,
    val weaknessMatch: Boolean,
    val teamBuffMultiplier: Double,
    val dpsScore: Double,
    val summary: String
)

class BattleModelQuantifier(
    private val damageCalculator: DamageCalculator = DamageCalculator()
) {

    /** 量化单个角色：team 为队友列表（不应包含主角自己） */
    fun quantify(
        character: Character,
        team: List<Character> = emptyList(),
        enemy: Enemy? = null,
        cycles: Int = 4
    ): BattleModelResult {
        val teammates = team.filter { it.id != character.id && it.name != character.name }
        val context = buildTeamContext(teammates)
        val soloCycleDamage = estimateCycleDamage(character, BattleModelContext(), enemy)
        val teamCycleDamage = estimateCycleDamage(character, context, enemy)
        val attackerStats = buildAttackerStats(character, context)
        val speed = attackerStats.speed
        val safeCycles = cycles.coerceAtLeast(1)
        val actionsPerCycle = ActionTimeline.actionsInCycles(speed, safeCycles).toDouble() / safeCycles
        val weaknessMatch = enemy?.weakness?.contains(character.element) == true
        val teamBuffMultiplier = if (soloCycleDamage > 0.0) teamCycleDamage / soloCycleDamage else 1.0
        val score = computeScore(teamCycleDamage, actionsPerCycle, weaknessMatch, teamBuffMultiplier)

        val summary = buildString {
            append("${character.name}（${character.element.displayName}/${character.path.displayName}）")
            append("标准循环期望 ${"%.0f".format(teamCycleDamage)}")
            if (teammates.isNotEmpty()) {
                append("，配队增益×${"%.2f".format(teamBuffMultiplier)}")
            }
            if (enemy != null) {
                append(if (weaknessMatch) "，克制目标弱点" else "，未命中目标弱点")
            }
            append("，${"%.1f".format(actionsPerCycle)} 动/轮，评分 ${"%.1f".format(score)}")
        }

        return BattleModelResult(
            characterId = character.id,
            characterName = character.name,
            element = character.element,
            path = character.path,
            baseAttack = character.baseStats.attack,
            combatAttack = attackerStats.attack,
            cycleDamage = teamCycleDamage,
            soloCycleDamage = soloCycleDamage,
            actionsPerCycle = actionsPerCycle,
            weaknessMatch = weaknessMatch,
            teamBuffMultiplier = teamBuffMultiplier,
            dpsScore = score,
            summary = summary
        )
    }

    private fun estimateCycleDamage(
        character: Character,
        context: BattleModelContext,
        enemy: Enemy? = null
    ): Double {
        val attackerStats = buildAttackerStats(character, context)
        val defenderStats = buildDefenderStats(character.element, enemy)

        val basicSkill = character.skills.firstOrNull { it.type == SkillType.BASIC }
            ?: Skill("basic", SkillType.BASIC, "普攻", "", listOf(ScalingEntry(StatType.ATK, 1.0)))
        val skillSkill = character.skills.firstOrNull { it.type == SkillType.SKILL }
        val ultSkill = character.skills.firstOrNull { it.type == SkillType.ULTIMATE }
            ?: Skill("ult", SkillType.ULTIMATE, "终结技", "", listOf(ScalingEntry(StatType.ATK, 4.0)))

        val basicDamage = damageCalculator.calculateExpectedDamage(
            attackerStats, defenderStats, basicSkill, DamageContext()
        )
        val skillDamage = if (skillSkill != null) {
            damageCalculator.calculateExpectedDamage(attackerStats, defenderStats, skillSkill, DamageContext())
        } else {
            basicDamage * 1.5
        }
        val ultDamage = damageCalculator.calculateExpectedDamage(
            attackerStats, defenderStats, ultSkill, DamageContext()
        )
        return basicDamage + skillDamage + ultDamage
    }

    private fun buildAttackerStats(character: Character, context: BattleModelContext): CombatStats {
        val speed = character.baseStats.speed + 20.0
        return CombatStats(
            level = 80,
            attack = character.baseStats.attack * 3.5 * (1.0 + context.supportAtkBonus),
            defense = character.baseStats.defense * 2.0,
            maxHp = character.baseStats.hp * 3.0,
            speed = speed,
            critRate = (character.baseStats.critRate + 0.60).coerceAtMost(1.0),
            critDmg = character.baseStats.critDmg + 1.0,
            elementalDmgBonus = 0.389,
            dmgBonus = context.supportDmgBonus,
            defPenetration = context.supportDefPen,
            resPenetration = context.supportResPen,
            breakEffect = 0.3
        )
    }

    private fun buildDefenderStats(element: ElementType, enemy: Enemy?): EnemyDefensiveStats {
        val resistance = enemy?.resistance?.get(element) ?: 0.2
        return EnemyDefensiveStats(
            defense = enemy?.stats?.defense ?: 800.0,
            resistance = resistance,
            toughness = enemy?.toughness ?: 200
        )
    }

    private fun buildTeamContext(team: List<Character>): BattleModelContext {
        val harmony = team.count { it.path == PathType.同谐 }
        val nihility = team.count { it.path == PathType.虚无 }
        val sustain = team.any { it.path == PathType.存护 || it.path == PathType.丰饶 }
        return BattleModelContext(
            supportAtkBonus = 0.18 * harmony,
            supportDmgBonus = 0.12 * harmony,
            supportDefPen = 0.08 * nihility,
            supportResPen = 0.10 * nihility,
            harmonyCount = harmony,
            nihilityCount = nihility,
            sustainPresent = sustain
        )
    }

    private fun computeScore(
        teamCycleDamage: Double,
        actionsPerCycle: Double,
        weaknessMatch: Boolean,
        teamBuffMultiplier: Double
    ): Double {
        val damageScore = (teamCycleDamage / 500000.0 * 100.0).coerceIn(0.0, 70.0)
        val actionScore = (actionsPerCycle.coerceAtMost(4.0) / 4.0 * 15.0)
        val weaknessScore = if (weaknessMatch) 10.0 else 0.0
        val teamScore = ((teamBuffMultiplier - 1.0).coerceIn(0.0, 0.5) / 0.5 * 5.0)
        return (damageScore + actionScore + weaknessScore + teamScore).coerceIn(0.0, 100.0)
    }
}