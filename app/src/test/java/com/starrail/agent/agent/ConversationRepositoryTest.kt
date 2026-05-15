package com.starrail.agent.agent

import com.starrail.agent.agent.llm.LlmMessage
import com.starrail.agent.agent.llm.LlmRole
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class ConversationRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repo: ConversationRepository

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "conv_test_${System.nanoTime()}")
        tempDir.mkdirs()
        repo = ConversationRepository(tempDir)
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSaveAndLoad() {
        val convId = "test_conv_001"
        val messages = listOf(
            LlmMessage(LlmRole.USER, "你好"),
            LlmMessage(LlmRole.ASSISTANT, "你好！我是AI助手")
        )
        repo.saveConversation(convId, messages)

        val loaded = repo.loadConversation(convId)
        assertNotNull(loaded)
        assertEquals(2, loaded!!.size)
        assertEquals(LlmRole.USER, loaded[0].role)
        assertEquals("你好", loaded[0].content)
        assertEquals(LlmRole.ASSISTANT, loaded[1].role)
    }

    @Test
    fun testListConversations() {
        repo.saveConversation("conv_a", listOf(LlmMessage(LlmRole.USER, "a")))
        repo.saveConversation("conv_b", listOf(LlmMessage(LlmRole.USER, "b")))
        val list = repo.listConversations()
        assertEquals(2, list.size)
        assertTrue(list.contains("conv_a"))
        assertTrue(list.contains("conv_b"))
    }

    @Test
    fun testDeleteConversation() {
        repo.saveConversation("conv_del", listOf(LlmMessage(LlmRole.USER, "test")))
        assertEquals(1, repo.listConversations().size)
        repo.deleteConversation("conv_del")
        assertEquals(0, repo.listConversations().size)
    }

    @Test
    fun testDeleteAll() {
        repo.saveConversation("c1", listOf(LlmMessage(LlmRole.USER, "1")))
        repo.saveConversation("c2", listOf(LlmMessage(LlmRole.USER, "2")))
        repo.deleteAll()
        assertEquals(0, repo.listConversations().size)
    }

    @Test
    fun testLoadNonExistent() {
        val loaded = repo.loadConversation("nonexistent")
        assertNull(loaded)
    }

    @Test
    fun testSaveWithSystemMessage_skipped() {
        val messages = listOf(
            LlmMessage(LlmRole.SYSTEM, "系统提示"),
            LlmMessage(LlmRole.USER, "用户消息")
        )
        repo.saveConversation("skip_sys", messages)
        val loaded = repo.loadConversation("skip_sys")
        assertNotNull(loaded)
        // SYSTEM 消息不应被持久化
        assertEquals(1, loaded!!.size)
        assertEquals(LlmRole.USER, loaded[0].role)
    }

    @Test
    fun testSaveWithToolCalls() {
        val messages = listOf(
            LlmMessage(LlmRole.USER, "查询角色"),
            LlmMessage(
                role = LlmRole.ASSISTANT,
                content = "",
                toolCalls = listOf(
                    com.starrail.agent.agent.llm.LlmToolCall(
                        id = "call_1",
                        functionName = "get_character_info",
                        arguments = "{\"name\":\"希儿\"}"
                    )
                )
            ),
            LlmMessage(LlmRole.TOOL, "角色数据", toolCallId = "call_1")
        )
        repo.saveConversation("tool_conv", messages)
        val loaded = repo.loadConversation("tool_conv")
        assertNotNull(loaded)
        assertEquals(3, loaded!!.size)
        assertEquals(LlmRole.ASSISTANT, loaded[1].role)
        assertNotNull(loaded[1].toolCalls)
        assertEquals("call_1", loaded[1].toolCalls?.first()?.id)
        assertEquals("get_character_info", loaded[1].toolCalls?.first()?.functionName)
        assertEquals(LlmRole.TOOL, loaded[2].role)
        assertEquals("call_1", loaded[2].toolCallId)
    }

    @Test
    fun testEmptyConversation() {
        repo.saveConversation("empty", emptyList())
        val loaded = repo.loadConversation("empty")
        assertNotNull(loaded)
        assertTrue(loaded!!.isEmpty())
    }
}