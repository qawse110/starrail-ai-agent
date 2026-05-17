package com.starrail.agent.core.datasource

import com.starrail.agent.core.model.PathType
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

/**
 * Wiki 数据加载验证测试
 * 使用实际 assets 中的 wiki_data.json 验证解析逻辑
 */
class WikiDataLoadingTest {

    /** 模拟 assets 加载的 JSON 内容 */
    private val wikiJsonString: String by lazy {
        // 从 classpath 读取 (Gradle 测试时 resources 目录可访问)
        val stream = this::class.java.classLoader?.getResourceAsStream("wiki_data.json")
            ?: throw AssertionError("wiki_data.json not found in test resources!" +
                " Run: python3 tools/sync_wiki_data.py app/src/main/assets/wiki_data.json")
        stream.bufferedReader().readText()
    }

    @Test
    fun `parse wiki JSON characters section`() {
        val json = JSONObject(wikiJsonString)
        val chars = json.optJSONObject("characters")
        assertNotNull("Missing 'characters' key in wiki_data.json", chars)
        
        val count = chars!!.length()
        println("Characters in wiki data: $count")
        assertTrue("Should have at least 40 characters, got $count", count >= 40)
        
        // Verify first few have required fields
        var validCount = 0
        for (key in chars.keys()) {
            val c = chars.getJSONObject(key)
            val name = c.optString("名称", "").trim().ifEmpty { key }
            val path = c.optString("命途", "")
            val element = c.optString("元素属性", "")
            if (name.isNotBlank() && path.isNotBlank() && element.isNotBlank()) {
                validCount++
            }
        }
        println("Characters with name+path+element: $validCount")
        assertTrue("Should have at least 40 valid characters, got $validCount", validCount >= 40)
    }

    @Test
    fun `parse wiki JSON light cones section`() {
        val json = JSONObject(wikiJsonString)
        val cones = json.optJSONObject("light_cones")
        assertNotNull("Missing 'light_cones' key in wiki_data.json", cones)
        
        val count = cones!!.length()
        println("Light cones in wiki data: $count")
        assertTrue("Should have at least 40 light cones, got $count", count >= 40)
        
        // Check no empty name issues
        var validCount = 0
        for (key in cones.keys()) {
            val c = cones.getJSONObject(key)
            val name = c.optString("名称", "").trim().ifEmpty { key }
            val path = c.optString("命途", "")
            if (name.isNotBlank() && path.isNotBlank()) {
                validCount++
            }
        }
        println("Light cones with name+path: $validCount")
        assertTrue("Should have at least 40 valid light cones, got $validCount", validCount >= 40)
    }

    @Test
    fun `InMemoryGameDataSource loads wiki characters`() {
        val json = JSONObject(wikiJsonString)
        val ds = InMemoryGameDataSource(json)
        val allChars = ds.getAllCharacters()
        
        println("Total characters loaded: ${allChars.size}")
        assertTrue("Should have more than 40 characters with wiki data, got ${allChars.size}", 
            allChars.size > 40)
        
        // Verify specific known characters exist
        val names = allChars.map { it.name }
        assertTrue("希儿 should be in character list", "希儿" in names)
        
        // Check that some wiki-only characters exist
        val wikiOnlyCount = allChars.count { it.id.startsWith("wiki_") }
        println("Wiki-only characters (not in hardcoded): $wikiOnlyCount")
        assertTrue("Should have some wiki-only characters, got $wikiOnlyCount", wikiOnlyCount > 0)
    }

    @Test
    fun `InMemoryGameDataSource loads wiki light cones`() {
        val json = JSONObject(wikiJsonString)
        val ds = InMemoryGameDataSource(json)
        val allCones = ds.getAllLightCones()
        
        println("Total light cones loaded: ${allCones.size}")
        assertTrue("Should have more than 40 light cones with wiki data, got ${allCones.size}", 
            allCones.size > 40)
        
        // Verify no duplicate IDs
        val ids = allCones.map { it.id }
        val duplicateIds = ids.groupBy { it }.filter { it.value.size > 1 }
        assertTrue("Should have no duplicate light cone IDs, found: $duplicateIds", 
            duplicateIds.isEmpty())
    }
}