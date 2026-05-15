package com.starrail.agent.relic

import com.starrail.agent.core.model.*
import com.starrail.agent.player.PlayerRelic
import org.junit.Assert.*
import org.junit.Test

/**
 * 遗器评分器单元测试
 */
class RelicScorerTest {
    
    private val scorer = RelicScorer()
    
    @Test
    fun testDpsWeights_critStatsTop() {
        val weights = scorer.getCharacterWeights("希儿", BuildType.DPS)
        
        // 双暴权重应为最高
        val critRate = weights.find { it.statType == StatType.CRIT_RATE }
        val critDmg = weights.find { it.statType == StatType.CRIT_DMG }
        
        assertNotNull(critRate)
        assertNotNull(critDmg)
        assertEquals(1.0, critRate!!.weight, 0.01)
        assertEquals(1.0, critDmg!!.weight, 0.01)
    }
    
    @Test
    fun testSupportWeights_speedHigh() {
        val weights = scorer.getCharacterWeights("银狼", BuildType.SUPPORT)
        
        val speed = weights.find { it.statType == StatType.SPD }
        assertNotNull(speed)
        assertTrue("辅助速度权重应较高", speed!!.weight >= 0.8)
    }
    
    @Test
    fun testScoreRelic_mainStatMatter() {
        // 测试主词条正确的遗器应获得更高评分
        val goodRelic = PlayerRelic(
            instanceId = "test_1",
            relicId = "relic_001",
            setId = "rs_1001",
            slot = RelicSlot.躯干,
            rarity = 5,
            level = 0,
            mainStat = StatEntry(StatType.CRIT_RATE, 0.10),
            subStats = listOf(
                StatEntry(StatType.CRIT_DMG, 0.06),
                StatEntry(StatType.ATK_PERCENT, 0.04)
            ),
            subStatIncrementCount = 0
        )
        
        val weights = scorer.getCharacterWeights("希儿", BuildType.DPS)
        val score = scorer.scoreRelic(goodRelic, "希儿", weights)
        
        assertTrue("合理的躯干遗器评分应大于50", score.overallScore > 50)
        assertTrue("主词条应匹配", score.scoringDetails.mainStatMatch)
    }
    
    @Test
    fun testScoreRelic_wrongMainStat_lowScore() {
        // 主词条错误的遗器应获得低分
        val badRelic = PlayerRelic(
            instanceId = "test_2",
            relicId = "relic_002",
            setId = "rs_1001",
            slot = RelicSlot.躯干,
            rarity = 5,
            level = 0,
            mainStat = StatEntry(StatType.DEF_PERCENT, 0.20),  // 防御%对DPS无用
            subStats = listOf(
                StatEntry(StatType.HP, 30.0),
                StatEntry(StatType.DEF, 20.0)
            ),
            subStatIncrementCount = 0
        )
        
        val weights = scorer.getCharacterWeights("希儿", BuildType.DPS)
        val score = scorer.scoreRelic(badRelic, "希儿", weights)
        
        assertFalse("主词条不应匹-配", score.scoringDetails.mainStatMatch)
        assertEquals("错误主词条只有20分", 20.0, score.mainStatScore, 0.01)
    }
    
    @Test
    fun testAnalyzeEffectiveSubStats_allEffective() {
        val relic = PlayerRelic(
            instanceId = "test_3",
            relicId = "relic_003",
            setId = "rs_1001",
            slot = RelicSlot.躯干,
            rarity = 5,
            level = 12,
            mainStat = StatEntry(StatType.CRIT_RATE, 0.10),
            subStats = listOf(
                StatEntry(StatType.CRIT_DMG, 0.06),
                StatEntry(StatType.ATK_PERCENT, 0.08),
                StatEntry(StatType.SPD, 2.3)
            ),
            subStatIncrementCount = 4
        )
        
        val weights = scorer.getCharacterWeights("希儿", BuildType.DPS)
        val analysis = scorer.analyzeEffectiveSubStats(relic, weights)
        
        assertEquals("3条有效词条", 3, analysis.effectiveCount)
        assertEquals("0条无效词条", 0, analysis.deadCount)
    }
    
    @Test
    fun testRelicGrade_sss() {
        val relic = PlayerRelic(
            instanceId = "test_4",
            relicId = "relic_004",
            setId = "rs_1001",
            slot = RelicSlot.躯干,
            rarity = 5,
            level = 15,
            mainStat = StatEntry(StatType.CRIT_RATE, 0.12),
            subStats = listOf(
                StatEntry(StatType.CRIT_DMG, 0.20),
                StatEntry(StatType.ATK_PERCENT, 0.16),
                StatEntry(StatType.SPD, 5.0),
                StatEntry(StatType.ATK, 40.0)
            ),
            subStatIncrementCount = 12
        )
        
        val weights = scorer.getCharacterWeights("希儿", BuildType.DPS)
        val score = scorer.scoreRelic(relic, "希儿", weights)
        
        assertTrue("极品遗器评分应极高", score.overallScore >= 90)
    }
}