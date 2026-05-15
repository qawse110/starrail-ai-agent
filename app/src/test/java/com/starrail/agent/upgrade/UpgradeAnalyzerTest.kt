package com.starrail.agent.upgrade

import org.junit.Assert.*
import org.junit.Test

/**
 * 升级分析器单元测试
 * 验证星魂/精炼/性价比分析的正确性
 */
class UpgradeAnalyzerTest {

    private val eidolonAnalyzer = EidolonAnalyzer()
    private val lcAnalyzer = LightConeAnalyzer()
    private val costBenefitAnalyzer = CostBenefitAnalyzer()

    @Test
    fun testEidolonBenefit_dpsCharacter_rank1() {
        val benefits = eidolonAnalyzer.calculateBenefit("希儿", 0, 1)
        assertEquals("应返回1个星魂收益", 1, benefits.size)
        
        val benefit = benefits[0]
        assertEquals("星魂1", 1, benefit.rank)
        assertNotNull("DPS角色应有伤害提升", benefit.damageIncrease)
        assertTrue("伤害提升应>0", (benefit.damageIncrease ?: 0.0) > 0)
    }

    @Test
    fun testEidolonBenefit_dpsCharacter_0to6() {
        val benefits = eidolonAnalyzer.calculateBenefit("希儿", 0, 6)
        assertEquals("0→6应返回6个", 6, benefits.size)
        
        // 关键星魂（1,2,4,6）评级应更高
        val keyRanks = setOf(1, 2, 4, 6)
        for (b in benefits) {
            assertNotNull("星魂${b.rank}应有评级", b.overallRating)
            if (b.rank in keyRanks) {
                assertTrue(
                    "关键星魂${b.rank}应为推荐以上",
                    b.overallRating in listOf(UpgradeRating.RECOMMENDED, UpgradeRating.GOOD, UpgradeRating.MUST_HAVE)
                )
            }
        }
    }

    @Test
    fun testEidolonBenefit_supportCharacter() {
        val benefits = eidolonAnalyzer.calculateBenefit("银狼", 0, 2)
        assertEquals("应返回2个星魂收益", 2, benefits.size)
        
        // 辅助星的魂应包含生存提升
        for (b in benefits) {
            assertNotNull("辅助应有生存提升描述", b.sustainIncrease)
        }
    }

    @Test
    fun testKeyEidolons_dpsCharacter() {
        val keyEidolons = eidolonAnalyzer.getKeyEidolons("希儿")
        assertEquals("DPS角色应有4个关键星魂", 4, keyEidolons.size)
        assertTrue("应包含1魂", keyEidolons.contains(1))
        assertTrue("应包含2魂", keyEidolons.contains(2))
        assertTrue("应包含4魂", keyEidolons.contains(4))
        assertTrue("应包含6魂", keyEidolons.contains(6))
    }

    @Test
    fun testKeyEidolons_supportCharacter() {
        val keyEidolons = eidolonAnalyzer.getKeyEidolons("银狼")
        assertEquals("辅助角色应有3个关键星魂", 3, keyEidolons.size)
        assertFalse("不应包含1魂", keyEidolons.contains(1))
    }

    @Test
    fun testFullEidolonSummary_cumulative() {
        val summary = eidolonAnalyzer.getFullEidolonSummary("希儿")
        
        assertEquals("角色名正确", "希儿", summary.characterName)
        assertEquals("应有6个星魂收益", 6, summary.eidolonBenefits.size)
        assertEquals("累积收益应有6级", 6, summary.cumulativeBenefit.size)
        
        // 累积收益应递增
        for (i in 1..5) {
            assertTrue(
                "星魂${i+1}累积收益 > 星魂${i}",
                (summary.cumulativeBenefit[i + 1] ?: 0.0) >= (summary.cumulativeBenefit[i] ?: 0.0)
            )
        }
    }

    @Test
    fun testSuperimposeBenefit_1to5() {
        val benefits = lcAnalyzer.calculateSuperimposeBenefit("lc_5star_1", 1, 5)
        assertEquals("1→5精应返回4个收益", 4, benefits.size)
        
        // 精炼收益应递减
        for (i in 0 until benefits.size - 1) {
            assertTrue(
                "精${i+2}收益应高于精${i+3}",
                (benefits[i].damageIncrease ?: 0.0) >= (benefits[i + 1].damageIncrease ?: 0.0)
            )
        }
    }

    @Test
    fun testSuperimposeBenefit_5starVs4star() {
        val lc5Benefits = lcAnalyzer.calculateSuperimposeBenefit("lc_5star_1", 1, 2)
        val lc4Benefits = lcAnalyzer.calculateSuperimposeBenefit("lc_4star_1", 1, 2)
        
        // 5星光锥精炼收益应更高
        assertTrue(
            "5星光锥精炼收益更高",
            (lc5Benefits[0].damageIncrease ?: 0.0) > (lc4Benefits[0].damageIncrease ?: 0.0)
        )
    }

    @Test
    fun testUpgradeCurve_eidolon() {
        val curve = lcAnalyzer.generateUpgradeCurve(UpgradeType.EIDOLON, "希儿", 6)
        
        assertEquals("升级类型正确", UpgradeType.EIDOLON, curve.type)
        assertEquals("6个等级", 6, curve.levels.size)
        assertEquals("增量收益6个", 6, curve.incrementalBenefits.size)
        assertEquals("累积收益6个", 6, curve.cumulativeBenefits.size)
        assertEquals("边际分析6个", 6, curve.marginalAnalysis.size)
    }

    @Test
    fun testUpgradeCurve_superimpose() {
        val curve = lcAnalyzer.generateUpgradeCurve(UpgradeType.SUPERIMPOSE, "如泥酣眠", 5)
        
        assertEquals("5个等级", 5, curve.levels.size)
        // 精炼边际收益递减
        assertTrue("精1收益 > 精5", curve.incrementalBenefits[0] > curve.incrementalBenefits[4])
    }

    @Test
    fun testCostBenefitAnalysis_eidolon_efficiency() {
        val analysis = costBenefitAnalyzer.analyze(
            characterId = "希儿",
            upgradeType = UpgradeType.EIDOLON,
            currentLevel = 0,
            targetLevel = 1
        )
        
        assertTrue("升级路径应包含角色名", analysis.upgradePath.contains("希儿"))
        assertTrue("升级路径应包含EIDOLON", analysis.upgradePath.contains("EIDOLON"))
        assertNotNull("应有推荐建议", analysis.recommendation)
        assertTrue("收益/成本比应为正", analysis.benefitPerCost > 0)
    }

    @Test
    fun testCostBenefitAnalysis_superimpose() {
        val analysis = costBenefitAnalyzer.analyze(
            characterId = "lc_5star_1",
            upgradeType = UpgradeType.SUPERIMPOSE,
            currentLevel = 1,
            targetLevel = 3
        )
        
        assertTrue("收益应为正", analysis.benefit >= 0)
        assertTrue("成本应为正", analysis.cost.amount > 0)
    }

    @Test
    fun testGenerateUpgradePath() {
        val path = costBenefitAnalyzer.generateUpgradePath(
            characterId = "希儿",
            currentEidolon = 0,
            currentSuperimpose = 1,
            budget = com.starrail.agent.upgrade.ResourceBudget(
                type = ResourceType.UNDYING_STARLIGHT, amount = 200
            )
        )
        
        assertNotNull("应有升级路径", path)
        assertEquals("角色名正确", "希儿", path.characterId)
        assertTrue("应有推荐步骤", path.recommendedSteps.isNotEmpty())
        assertTrue("总收益应为正", path.totalBenefit > 0)
        
        // 步骤号应递增
        for (i in 0 until path.recommendedSteps.size - 1) {
            assertTrue(
                "步骤号递增",
                path.recommendedSteps[i].stepNumber < path.recommendedSteps[i + 1].stepNumber
            )
        }
    }

    @Test
    fun testEidolonBenefit_invalidRange() {
        // 0到0不产生收益
        val benefits = eidolonAnalyzer.calculateBenefit("希儿", 0, 0)
        assertTrue("相同星魂不应有收益", benefits.isEmpty())
    }

    @Test
    fun testSuperimposeBenefit_highLevelHasLowerRating() {
        val benefits = lcAnalyzer.calculateSuperimposeBenefit("lc_5star_1", 4, 5)
        assertEquals("应返回1个收益", 1, benefits.size)
        
        // 精5评级较低（边际收益递减）
        assertTrue(
            "精5评级应为SKIP或AVERAGE",
            benefits[0].overallRating in listOf(UpgradeRating.SKIP, UpgradeRating.AVERAGE)
        )
    }

    @Test
    fun testRecommendedTarget_forDps() {
        val summary = eidolonAnalyzer.getFullEidolonSummary("希儿")
        assertTrue("推荐目标应为2/4/6魂", summary.recommendedTarget in listOf(2, 4, 6))
    }
}