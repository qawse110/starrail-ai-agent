package com.starrail.agent.agent

import com.starrail.agent.agent.intent.Intent
import com.starrail.agent.agent.intent.IntentResolver
import org.junit.Assert.*
import org.junit.Test

/**
 * 意图识别器单元测试
 * 
 * 验证规则匹配的正确性
 */
class IntentResolverTest {
    
    private val resolver = IntentResolver()
    
    @Test
    fun testGreeting_intent() {
        val result = resolver.resolve("你好")
        assertEquals(Intent.GREETING, result.intent)
        assertTrue("问候置信度应较高", result.confidence >= 0.7)
    }
    
    @Test
    fun testSimulateBattle_withCharacter() {
        val result = resolver.resolve("模拟希儿打混沌12层")
        assertEquals(Intent.SIMULATE_BATTLE, result.intent)
        assertTrue("应识别出角色名", result.entities.characters.contains("希儿"))
        assertTrue("应识别出混沌", result.entities.targetContent == "混沌回忆")
    }
    
    @Test
    fun testSimulateBattle_history() {
        val result = resolver.resolve("模拟我的饮月队打混沌12层")
        assertEquals(Intent.SIMULATE_BATTLE, result.intent)
        assertTrue(result.entities.characters.contains("饮月"))
    }
    
    @Test
    fun testScoreRelics() {
        val result = resolver.resolve("给希儿装备评分")
        assertEquals(Intent.SCORE_CHARACTER_RELICS, result.intent)
        assertTrue(result.entities.characters.contains("希儿"))
    }
    
    @Test
    fun testRecommendRelicSet() {
        val result = resolver.resolve("饮月应该用什么遗器套")
        assertEquals(Intent.RECOMMEND_RELIC_SET, result.intent)
        assertTrue(result.entities.characters.contains("饮月"))
    }
    
    @Test
    fun testAnalyzeTeam() {
        val result = resolver.resolve("分析我这个队伍")
        assertEquals(Intent.ANALYZE_TEAM, result.intent)
    }
    
    @Test
    fun testCompareTeams() {
        val result = resolver.resolve("对比希儿队和卡芙卡队")
        assertEquals(Intent.COMPARE_TEAMS, result.intent)
    }
    
    @Test
    fun testSuggestTeam() {
        val result = resolver.resolve("推荐配队")
        assertEquals(Intent.SUGGEST_TEAM, result.intent)
    }
    
    @Test
    fun testEidolonAnalysis() {
        val result = resolver.resolve("希儿星魂提升有多大")
        assertEquals(Intent.ANALYZE_EIDOLON, result.intent)
        assertTrue(result.entities.characters.contains("希儿"))
    }
    
    @Test
    fun testCompareUpgradePath() {
        val result = resolver.resolve("先补星魂还是先抽专武")
        assertEquals(Intent.COMPARE_UPGRADE_PATH, result.intent)
    }
    
    @Test
    fun testLightConeAnalysis() {
        val result = resolver.resolve("饮月光锥怎么选")
        assertEquals(Intent.ANALYZE_LIGHTCONE, result.intent)
        assertTrue(result.entities.characters.contains("饮月"))
    }
    
    @Test
    fun testDamageCalculation() {
        val result = resolver.resolve("计算希儿伤害")
        assertEquals(Intent.SIMULATE_BATTLE, result.intent)
    }
    
    @Test
    fun testRelicUpgrade() {
        val result = resolver.resolve("哪些遗器值得强化")
        assertEquals(Intent.SUGGEST_RELIC_UPGRADE, result.intent)
    }
    
    @Test
    fun testUnknown_intent() {
        val result = resolver.resolve("今天的天气怎么样")
        assertEquals(Intent.UNKNOWN, result.intent)
    }
    
    @Test
    fun test_clarificationNeeded_noCharacter() {
        // 战斗模拟但没有指定角色
        val result = resolver.resolve("模拟战斗")
        assertTrue("缺少角色时应要求澄清", result.clarificationNeeded)
        assertNotNull(result.clarificationQuestion)
    }
    
    @Test
    fun testExtract_qualifiers() {
        val result = resolver.resolve("分析我的希儿遗器")
        assertEquals(Intent.SCORE_CHARACTER_RELICS, result.intent)
        assertTrue("应识别出'我的'修饰语", result.entities.qualifiers.contains("我的"))
    }
}