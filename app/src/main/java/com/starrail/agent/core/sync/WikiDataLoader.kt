package com.starrail.agent.core.sync

import org.json.JSONObject
import java.io.File

/**
 * Wiki 数据加载器
 * 从 assets 或 files 目录加载 wiki_data.json，提供角色/光锥/遗器查询接口
 * 供 ToolExecutor/LLM 获取真实 Wiki 数据
 */
class WikiDataLoader(private val dataDir: File? = null) {

    private var cachedJson: JSONObject? = null
    private var lastLoadTime = 0L
    private val cacheTtlMs = 300_000L  // 5分钟缓存
    private var assetJson: String? = null  // assets注入

    /** 用于从 assets 加载的构造 */
    constructor(assetContent: String) : this(null) {
        this.assetJson = assetContent
    }

    /** 检查是否有可用数据 */
    fun hasData(): Boolean {
        if (assetJson != null) return true
        if (dataDir == null) return false
        val file = File(dataDir, "wiki_data.json")
        return file.exists() && file.length() > 100
    }

    /** 获取同步时间戳 */
    fun getSyncTime(): Long? {
        return try {
            load()?.optLong("sync_time", 0)?.takeIf { it > 0 }
        } catch (_: Exception) { null }
    }

    /** 获取同步数据大小 */
    fun getDataSize(): String {
        if (assetJson != null) return "${assetJson!!.length / 1024}KB"
        if (dataDir == null) return "无"
        val file = File(dataDir, "wiki_data.json")
        return if (file.exists()) "${file.length() / 1024}KB" else "无"
    }

    /** 计数统计 */
    fun getStats(): Map<String, Int> {
        val data = load() ?: return emptyMap()
        return mapOf(
            "characters" to data.optInt("character_count", 0),
            "light_cones" to data.optInt("light_cone_count", 0)
        )
    }

    /** 按名称搜索角色 */
    fun searchCharacter(query: String): List<Map<String, String>> {
        val data = load() ?: return emptyList()
        val chars = data.optJSONObject("characters") ?: return emptyList()
        val results = mutableListOf<Map<String, String>>()
        val lowerQuery = query.lowercase()

        for (key in chars.keys()) {
            val c = chars.optJSONObject(key) ?: continue
            val name = c.optString("名称", "")
            val title = c.optString("title", "")

            // 模糊匹配
            if (name.lowercase().contains(lowerQuery) ||
                title.lowercase().contains(lowerQuery) ||
                c.optString("外文名", "").lowercase().contains(lowerQuery)
            ) {
                results.add(buildCharacterEntry(c))
            }
        }
        return results
    }

    /** 按命途筛选角色 */
    fun listCharactersByPath(path: String): List<Map<String, String>> {
        val data = load() ?: return emptyList()
        val chars = data.optJSONObject("characters") ?: return emptyList()
        val results = mutableListOf<Map<String, String>>()
        val lowerPath = path.lowercase()

        for (key in chars.keys()) {
            val c = chars.optJSONObject(key) ?: continue
            val charPath = c.optString("命途", "").lowercase()
            if (charPath.contains(lowerPath)) {
                results.add(buildCharacterEntry(c))
            }
        }
        return results
    }

    /** 列出所有角色 */
    fun listAllCharacters(): List<Map<String, String>> {
        val data = load() ?: return emptyList()
        val chars = data.optJSONObject("characters") ?: return emptyList()
        val results = mutableListOf<Map<String, String>>()
        for (key in chars.keys()) {
            val c = chars.optJSONObject(key) ?: continue
            results.add(buildCharacterEntry(c))
        }
        return results.sortedBy { it["名称"] }
    }

    /** 搜索光锥 */
    fun searchLightCone(query: String): List<Map<String, String>> {
        val data = load() ?: return emptyList()
        val cones = data.optJSONObject("light_cones") ?: return emptyList()
        val results = mutableListOf<Map<String, String>>()
        val lowerQuery = query.lowercase()

        for (key in cones.keys()) {
            val c = cones.optJSONObject(key) ?: continue
            val name = c.optString("名称", "")
            val title = c.optString("title", "")

            if (name.lowercase().contains(lowerQuery) ||
                title.lowercase().contains(lowerQuery)
            ) {
                results.add(buildLightConeEntry(c))
            }
        }
        return results
    }

    /** 按命途筛选光锥 */
    fun listLightConesByPath(path: String): List<Map<String, String>> {
        val data = load() ?: return emptyList()
        val cones = data.optJSONObject("light_cones") ?: return emptyList()
        val results = mutableListOf<Map<String, String>>()
        val lowerPath = path.lowercase()

        for (key in cones.keys()) {
            val c = cones.optJSONObject(key) ?: continue
            val conePath = c.optString("命途", "").lowercase()
            if (conePath.contains(lowerPath)) {
                results.add(buildLightConeEntry(c))
            }
        }
        return results
    }

    /** 列出所有光锥 */
    fun listAllLightCones(): List<Map<String, String>> {
        val data = load() ?: return emptyList()
        val cones = data.optJSONObject("light_cones") ?: return emptyList()
        val results = mutableListOf<Map<String, String>>()
        for (key in cones.keys()) {
            val c = cones.optJSONObject(key) ?: continue
            results.add(buildLightConeEntry(c))
        }
        return results.sortedBy { it["名称"] }
    }

    /** 构建角色条目 */
    private fun buildCharacterEntry(c: JSONObject): Map<String, String> {
        return mapOf(
            "名称" to c.optString("名称", ""),
            "稀有度" to c.optString("稀有度", ""),
            "命途" to c.optString("命途", ""),
            "元素属性" to c.optString("元素属性", ""),
            "外文名" to c.optString("外文名", ""),
            "阵营" to c.optString("阵营", ""),
            "性别" to c.optString("性别", ""),
            "限定" to c.optString("限定", ""),
            "实装版本" to c.optString("实装版本", ""),
            "介绍" to c.optString("角色详细", "").take(100),
            "定位" to c.optString("角色定位", "")
        )
    }

    /** 构建光锥条目 */
    private fun buildLightConeEntry(c: JSONObject): Map<String, String> {
        return mapOf(
            "名称" to c.optString("名称", ""),
            "稀有度" to c.optString("稀有度", ""),
            "命途" to c.optString("命途", ""),
            "相关角色" to c.optString("相关角色", ""),
            "光锥故事" to c.optString("光锥故事", "").take(150)
        )
    }

    /** 懒加载 + 缓存 */
    private fun load(): JSONObject? {
        val now = System.currentTimeMillis()
        if (cachedJson != null && now - lastLoadTime < cacheTtlMs) {
            return cachedJson
        }
        // 优先从 assetJson 加载
        if (assetJson != null) {
            return try {
                val json = JSONObject(assetJson)
                cachedJson = json
                lastLoadTime = now
                json
            } catch (_: Exception) { null }
        }
        if (dataDir == null) return null
        val file = File(dataDir, "wiki_data.json")
        if (!file.exists()) return null
        return try {
            val text = file.readText(Charsets.UTF_8)
            val json = JSONObject(text)
            cachedJson = json
            lastLoadTime = now
            json
        } catch (e: Exception) {
            null
        }
    }
}