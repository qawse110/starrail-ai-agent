package com.starrail.agent.agent.tool

import com.starrail.agent.core.datasource.InMemoryGameDataSource
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ToolExecutorTest {

    private lateinit var dataSource: InMemoryGameDataSource
    private lateinit var executor: ToolExecutor

    @Before
    fun setup() {
        dataSource = InMemoryGameDataSource()
        executor = ToolExecutor(
            dataSource = dataSource
        )
    }

    // ==================== 数据查询工具 ====================

    @Test
    fun testListCharacters() {
        val result = executor.execute("list_characters", emptyMap())
        assertTrue("应成功", result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertEquals(40, data!!["count"])
    }

    @Test
    fun testGetCharacterInfo_found() {
        val result = executor.execute("get_character_info", mapOf("name" to "希儿"))
        assertTrue("应成功", result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertEquals(true, data!!["found"])
        assertEquals("希儿", data["name"])
    }

    @Test
    fun testGetCharacterInfo_notFound() {
        val result = executor.execute("get_character_info", mapOf("name" to "不存在的角色"))
        assertTrue("应成功", result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertEquals(false, data!!["found"])
    }

    @Test
    fun testGetCharacterInfo_byCharacterIdAlias() {
        val result = executor.execute("get_character_info", mapOf("character_id" to "饮月"))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertEquals("丹恒·饮月", data!!["name"])
    }

    @Test
    fun testGetLightConeInfo_found() {
        val result = executor.execute("get_light_cone_info", mapOf("name" to "拂晓"))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertEquals("拂晓之前", data!!["name"])
    }

    @Test
    fun testGetLightConeInfo_listAll() {
        val result = executor.execute("get_light_cone_info", emptyMap())
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        val cones = data!!["light_cones"] as? List<*>
        assertNotNull(cones)
        assertTrue(cones!!.size >= 30)
    }

    @Test
    fun testGetRelicSetInfo_found() {
        val result = executor.execute("get_relic_set_info", mapOf("name" to "天才"))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertEquals(true, data!!["found"])
    }

    @Test
    fun testGetEnemyInfo_found() {
        val result = executor.execute("get_enemy_info", mapOf("name" to "可可利亚"))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertEquals("可可利亚", data!!["name"])
        assertTrue((data["weakness"] as? List<*>)?.isNotEmpty() == true)
    }

    @Test
    fun testGetEnemyInfo_listAll() {
        val result = executor.execute("get_enemy_info", emptyMap())
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        val enemies = data!!["enemies"] as? List<*>
        assertNotNull(enemies)
        assertTrue(enemies!!.size >= 25)
    }

    @Test
    fun testGetRecommendedBuild() {
        val result = executor.execute("get_recommended_build", mapOf("character_name" to "希儿"))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertEquals("希儿", data!!["character"])
        assertNotNull(data["recommended_relics"])
        assertNotNull(data["recommended_main_stats"])
    }

    @Test
    fun testGetRecommendedBuild_notFound() {
        val result = executor.execute("get_recommended_build", mapOf("character_name" to "不存在"))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertNotNull(data!!["error"])
    }

    // ==================== 参数别名解析 ====================

    @Test
    fun testGetCharacterInfo_withDifferentParamNames() {
        // 测试多种参数别名都能正确查询
        val names = listOf("name", "character_id", "character_name")
        for (paramName in names) {
            val result = executor.execute("get_character_info", mapOf(paramName to "银狼"))
            assertTrue("参数名: $paramName 应成功", result.success)
            val data = result.data as? Map<*, *>
            assertEquals("银狼", data!!["name"])
        }
    }

    @Test
    fun testGetEnemyInfo_withDifferentParamNames() {
        val names = listOf("name", "enemy_name", "target")
        for (paramName in names) {
            val result = executor.execute("get_enemy_info", mapOf(paramName to "末日兽"))
            assertTrue("参数名: $paramName 应成功", result.success)
            val data = result.data as? Map<*, *>
            assertEquals("末日兽", data!!["name"])
        }
    }

    // ==================== 可用工具列表 ====================

    @Test
    fun testGetAvailableTools() {
        val tools = executor.getAvailableTools()
        assertTrue(tools.size >= 15)
        assertTrue(tools.any { it.name == "get_character_info" })
        assertTrue(tools.any { it.name == "simulate_battle" })
        assertTrue(tools.any { it.name == "analyze_eidolon" })
    }

    @Test
    fun testGetAvailableTools_byIntent() {
        val queryTools = executor.getAvailableTools(com.starrail.agent.agent.intent.Intent.QUERY_INFO)
        assertTrue(queryTools.isNotEmpty())
        assertTrue(queryTools.all { it.requiredIntent == com.starrail.agent.agent.intent.Intent.QUERY_INFO })
    }

    // ==================== 无效输入 ====================

    @Test
    fun testUnknownTool() {
        val result = executor.execute("nonexistent_tool", emptyMap())
        assertFalse("未知工具应失败", result.success)
    }

    @Test
    fun testEmptyCharacterName() {
        val result = executor.execute("get_character_info", mapOf("name" to ""))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertNotNull(data!!["error"])
    }

    // ==================== 拉表计算工具测试 ====================

    @Test
    fun testGetRecommendedBuild_lightCones() {
        val result = executor.execute("get_recommended_build", mapOf("character_name" to "希儿"))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertNotNull(data!!["recommended_light_cones"])
        val cones = data["recommended_light_cones"] as? List<*>
        assertNotNull(cones)
        assertTrue("应有光锥推荐", cones!!.isNotEmpty())
        assertTrue("应包含伤害数字", cones.any { it.toString().contains("期望") })
    }

    @Test
    fun testCalculateDamage_fullBreakdown() {
        val result = executor.execute("calculate_damage", mapOf("character_id" to "希儿", "target_enemy" to "可可利亚"))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        val results = data!!["damage_results"] as? List<*>
        assertNotNull(results)
        assertTrue("应有≥3个技能", results!!.size >= 3)
        val cycle = data["cycle_damage"] as? Map<*, *>
        assertNotNull(cycle)
        assertTrue((cycle!!["estimated_cycles_to_kill"] as? Int) ?: 0 > 0)
        val first = results!![0] as? Map<*, *>
        assertNotNull(first)
        assertNotNull(first!!["multiplier_zone"])
        assertNotNull(first["defense_zone"])
    }

    @Test
    fun testSimulateBattle_calculated() {
        val result = executor.execute("simulate_battle", mapOf("character_id" to "希儿", "target_enemy" to "可可利亚"))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertTrue((data!!["estimated_cycles"] as? Int) ?: 0 > 0)
        assertTrue((data["estimated_basic_damage"] as? Double) ?: 0.0 > 0)
    }

    @Test
    fun testAnalyzeEidolon_calculated() {
        val result = executor.execute("analyze_eidolon", mapOf("character_id" to "希儿"))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertTrue((data!!["e0_expected_damage"] as? Double) ?: 0.0 > 0)
        assertTrue(data!!["total_e6_increase_pct"] as? Double ?: 0.0 >= 0)
        val benefits = data["benefits"] as? List<*>
        assertNotNull(benefits)
        assertTrue(benefits!!.isNotEmpty())
        val first = benefits!![0] as? Map<*, *>
        assertNotNull(first)
        assertNotNull(first!!["damage_increase_pct"])
    }

    @Test
    fun testAnalyzeEidolon_specificRange() {
        val result = executor.execute("analyze_eidolon", mapOf("character_id" to "希儿", "from_eidolon" to 0, "to_eidolon" to 2))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertEquals(2, (data!!["benefits"] as? List<*>)?.size)
    }

    @Test
    fun testAnalyzeLightCone_calculated() {
        val result = executor.execute("analyze_lightcone", mapOf("light_cone_id" to "于夜色中"))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertTrue((data!!["base_expected_damage"] as? Double) ?: 0.0 > 0)
        assertTrue((data!!["total_increase_pct"] as? Double) ?: 0.0 >= 0)
        val benefits = data["benefits"] as? List<*>
        assertNotNull(benefits)
        assertTrue(benefits!!.isNotEmpty())
        val first = benefits!![0] as? Map<*, *>
        assertNotNull(first)
        assertNotNull(first!!["increase_from_base_pct"])
    }

    @Test
    fun testAnalyzeLightCone_specificRange() {
        val result = executor.execute("analyze_lightcone", mapOf("light_cone_id" to "拂晓之前", "from_level" to 1, "to_level" to 3))
        assertTrue(result.success)
        val data = result.data as? Map<*, *>
        assertNotNull(data)
        assertEquals(2, (data!!["benefits"] as? List<*>)?.size)
    }
}