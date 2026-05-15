package com.starrail.agent.team

import com.starrail.agent.core.model.*
import com.starrail.agent.player.PlayerCharacter
import org.junit.Assert.*
import org.junit.Test

/**
 * 配队分析器单元测试
 */
class TeamAnalyzerTest {

    private val analyzer = TeamAnalyzer()

    private fun makePlayerCharacter(id: String, level: Int = 80): PlayerCharacter {
        return PlayerCharacter(
            characterId = id,
            level = level,
            ascension = 6,
            eidolon = 0,
            skillLevels = emptyMap(),
            equippedLightConeId = null,
            equippedRelics = emptyMap(),
            traces = emptySet()
        )
    }

    private fun makeMember(id: String, role: TeamRole, level: Int = 80): TeamMember {
        return TeamMember(
            characterId = id,
            playerCharacter = makePlayerCharacter(id, level),
            role = role,
            lightCone = null,
            relics = emptyList()
        )
    }

    @Test
    fun testTeamCreate_withFourMembers_valid() {
        val team = Team(
            name = "测试队",
            members = listOf(
                makeMember("希儿", TeamRole.MAIN_DPS),
                makeMember("银狼", TeamRole.SUPPORT),
                makeMember("符玄", TeamRole.SUSTAIN),
                makeMember("驭空", TeamRole.SUPPORT)
            )
        )
        
        assertEquals(4, team.memberCount)
        assertTrue("4人队伍应有效", team.isValidTeam)
        assertNotNull("应有主C", team.mainDps)
        assertNotNull("应有生存位", team.sustain)
    }

    @Test
    fun testTeamCreate_withThreeMembers_invalid() {
        val team = Team(
            name = "不全队",
            members = listOf(
                makeMember("希儿", TeamRole.MAIN_DPS),
                makeMember("银狼", TeamRole.SUPPORT),
                makeMember("符玄", TeamRole.SUSTAIN)
            )
        )
        assertFalse("3人队伍应无效", team.isValidTeam)
    }

    @Test
    fun testMainDps_selection() {
        val team = Team(
            name = "双C队",
            members = listOf(
                makeMember("希儿", TeamRole.MAIN_DPS),
                makeMember("景元", TeamRole.SUB_DPS),
                makeMember("符玄", TeamRole.SUSTAIN),
                makeMember("驭空", TeamRole.SUPPORT)
            )
        )
        assertEquals("主C应为希儿", "希儿", team.mainDps?.characterId)
        assertEquals("副C应为景元", 1, team.dpsMembers.size - 1)
    }

    @Test
    fun testElementSynergy_variety() {
        val team = Team(
            name = "多样队",
            members = listOf(
                makeMember("希儿", TeamRole.MAIN_DPS),
                makeMember("银狼", TeamRole.SUPPORT),
                makeMember("景元", TeamRole.SUB_DPS),
                makeMember("罗刹", TeamRole.SUSTAIN)
            )
        )
        val synergy = analyzer.analyzeSynergy(team)
        assertEquals("3种属性多样性", 75.0, synergy.elementSynergy.varietyScore, 0.01)
        assertEquals("破韧效率", 60.0, synergy.elementSynergy.breakEfficiency, 0.01)
    }

    @Test
    fun testPathSynergy_withSustain() {
        val team = Team(
            name = "稳定队",
            members = listOf(
                makeMember("希儿", TeamRole.MAIN_DPS),
                makeMember("银狼", TeamRole.SUPPORT),
                makeMember("符玄", TeamRole.SUSTAIN),
                makeMember("罗刹", TeamRole.SUSTAIN)
            )
        )
        val synergy = analyzer.analyzeSynergy(team)
        assertTrue("应有生存位", synergy.pathSynergy.hasSustain)
        // 有2个生存位但无同谐 → 70分
        assertEquals("有生存位 → 70分", 70.0, synergy.pathSynergy.balancedScore, 0.01)
    }

    @Test
    fun testSkillSynergy_harmonyBuffDps() {
        val team = Team(
            name = "辅助队",
            members = listOf(
                makeMember("希儿", TeamRole.MAIN_DPS),
                makeMember("驭空", TeamRole.SUPPORT),
                makeMember("布洛妮娅", TeamRole.SUPPORT),
                makeMember("符玄", TeamRole.SUSTAIN)
            )
        )
        val synergy = analyzer.analyzeSynergy(team)
        // 驭空/布洛妮娅未被映射为同谐，故无技能联动
        assertEquals("0次技能联动", 0, synergy.skillSynergy.interactions.size)
    }

    @Test
    fun testTeamComparison_rankings() {
        val team1 = Team(id = "team1", name = "强队",
            members = listOf(
                makeMember("希儿", TeamRole.MAIN_DPS),
                makeMember("银狼", TeamRole.SUPPORT),
                makeMember("符玄", TeamRole.SUSTAIN),
                makeMember("驭空", TeamRole.SUPPORT)
            ))
        val team2 = Team(id = "team2", name = "弱队",
            members = listOf(
                makeMember("主角", TeamRole.MAIN_DPS),
                makeMember("艾丝妲", TeamRole.SUB_DPS),
                makeMember("三月七", TeamRole.SUSTAIN),
                makeMember("娜塔莎", TeamRole.SUSTAIN)
            ))
        val t1 = team1.copy(teamSynergy = analyzer.analyzeSynergy(team1))
        val t2 = team2.copy(teamSynergy = analyzer.analyzeSynergy(team2))
        val comparison = analyzer.compareTeams(listOf(t1, t2))
        assertEquals("应有2个队伍排名", 2, comparison.rankings.size)
        assertNotNull("应有推荐队伍", comparison.recommendedTeam)
    }

    @Test
    fun testStrengths_withSustainAndHarmony() {
        val team = Team(
            name = "完备队",
            members = listOf(
                makeMember("希儿", TeamRole.MAIN_DPS),
                makeMember("银狼", TeamRole.SUPPORT),
                makeMember("符玄", TeamRole.SUSTAIN),
                makeMember("罗刹", TeamRole.SUSTAIN)
            )
        )
        val synergy = analyzer.analyzeSynergy(team)
        assertTrue("应有生存位优势", synergy.strengths.any { it.contains("生存") })
        assertTrue("应有优势", synergy.strengths.isNotEmpty())
    }

    @Test
    fun testWeaknesses_noSustain() {
        val team = Team(
            name = "无奶队",
            members = listOf(
                makeMember("希儿", TeamRole.MAIN_DPS),
                makeMember("银狼", TeamRole.SUB_DPS),
                makeMember("驭空", TeamRole.SUPPORT),
                makeMember("停云", TeamRole.SUPPORT)
            )
        )
        val synergy = analyzer.analyzeSynergy(team)
        assertTrue("无生存位应提示", synergy.weaknesses.any { it.contains("生存") })
    }

    @Test
    fun testDpsEstimation_mainDealsMoreDamage() {
        val team = Team(
            name = "估算队",
            members = listOf(
                makeMember("希儿", TeamRole.MAIN_DPS),
                makeMember("银狼", TeamRole.SUPPORT),
                makeMember("符玄", TeamRole.SUSTAIN),
                makeMember("驭空", TeamRole.SUPPORT)
            )
        )
        val dpsResult = analyzer.estimateDps(team)
        val maxChar = dpsResult.damageShare.maxByOrNull { it.value }?.key
        assertEquals("主C伤害最高", "希儿", maxChar)
        assertTrue("总伤害应为正", dpsResult.totalCycleDamage > 0)
        assertEquals("4轮战斗", 4.0, dpsResult.cycleCount, 0.01)
    }

    @Test
    fun testSuggestions_whenNoSustain() {
        val team = Team(
            name = "改进队",
            members = listOf(
                makeMember("希儿", TeamRole.MAIN_DPS),
                makeMember("银狼", TeamRole.SUB_DPS),
                makeMember("驭空", TeamRole.SUPPORT),
                makeMember("停云", TeamRole.SUPPORT)
            )
        )
        val synergy = analyzer.analyzeSynergy(team)
        assertTrue("无生存时应有改进建议",
            synergy.suggestions.any { it.contains("生存") || it.contains("存护") || it.contains("丰饶") })
    }
}