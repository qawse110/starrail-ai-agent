package com.starrail.agent.agent

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StarRailAgentTest {

    private lateinit var agent: StarRailAgent

    @Before
    fun setup() {
        // 不使用 LLM 模式（null llmService），测试规则引擎和数据查询
        agent = StarRailAgent()
    }

    @Test
    fun testGetCharacters_returns40() {
        val chars = agent.getCharacters()
        assertTrue(chars.size >= 40)
        assertTrue(chars.any { it.contains("希儿") })
    }

    @Test
    fun testSearchCharacters() {
        val results = agent.searchCharacters("镜流")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.contains("镜流") })
    }

    @Test
    fun testGetLightCones() {
        val cones = agent.getLightCones()
        assertTrue(cones.size >= 30)
    }

    @Test
    fun testGetRelicSets() {
        val sets = agent.getRelicSets()
        assertTrue(sets.size >= 40)
    }

    @Test
    fun testGetEnemies() {
        val enemies = agent.getEnemies()
        assertTrue(enemies.size >= 25)
    }

    @Test
    fun testClearConversation() {
        // 先创建一条对话记录（通过 process 触发）
        agent.process("希儿", "conv_clear_test")
        agent.clearConversation("conv_clear_test")
        val list = agent.getConversationList()
        assertFalse(list.any { it.first == "conv_clear_test" })
    }

    @Test
    fun testProcessRuleEngine() {
        val response = agent.process("列出所有角色")
        assertFalse(response.needsClarification)
        assertNotNull(response.report)
        assertTrue(response.conversationId.isNotBlank())
    }

    @Test
    fun testProcessWithConversationId() {
        val convId = "test_conv_process"
        val response = agent.process("希儿星魂提升", convId)
        assertEquals(convId, response.conversationId)
    }

    @Test
    fun testGetConversationList() {
        agent.process("测试对话1", "list_test_1")
        agent.process("测试对话2", "list_test_2")
        val list = agent.getConversationList()
        assertTrue(list.size >= 2)
        assertTrue(list.any { it.first == "list_test_1" })
        assertTrue(list.any { it.first == "list_test_2" })
    }

    @Test
    fun testDeleteConversation() {
        agent.process("删除测试", "del_test")
        assertTrue(agent.getConversationList().any { it.first == "del_test" })
        agent.deleteConversation("del_test")
        assertFalse(agent.getConversationList().any { it.first == "del_test" })
    }

    @Test
    fun testGetConversationMessages() {
        agent.process("消息测试", "msg_test")
        val msgs = agent.getConversationMessages("msg_test")
        assertTrue(msgs.isNotEmpty())
    }

    @Test
    fun testProcessUnknownInput() {
        val response = agent.process("@#$%^&")
        assertNotNull(response)
        // 规则引擎应该能处理乱码输入而不崩溃
        assertNotNull(response.report)
    }

    @Test
    fun testProcessEmptyInput() {
        val response = agent.process("")
        assertNotNull(response)
    }

    @Test
    fun testGetToolDefinitions() {
        val defs = agent.getToolDefinitions()
        assertTrue(defs.size >= 15)
        assertTrue(defs.any { it.name == "get_character_info" })
    }

    @Test
    fun testMultipleConversations() {
        agent.process("对话A", "multi_a")
        agent.process("对话B", "multi_b")
        agent.process("对话C", "multi_c")
        val list = agent.getConversationList()
        val ids = list.map { it.first }
        assertTrue(ids.contains("multi_a"))
        assertTrue(ids.contains("multi_b"))
        assertTrue(ids.contains("multi_c"))
    }
}