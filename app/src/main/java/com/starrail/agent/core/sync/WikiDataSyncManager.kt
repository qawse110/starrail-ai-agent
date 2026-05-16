package com.starrail.agent.core.sync

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * 星穹铁道 Wiki 数据同步管理器
 * 从 bilibili Wiki MediaWiki API 获取真实游戏数据
 * 数据源: https://wiki.biligame.com/sr/api.php
 *
 * 参考 AstralArchives 项目思路：
 * https://github.com/XCreeperPa/AstralArchives
 */
class WikiDataSyncManager(private val dataDir: File) {

    companion object {
        private const val API_BASE = "https://wiki.biligame.com/sr/api.php"
        private val REQUEST_INTERVAL_MS = 500L  // 避免触发限流
        private val CATEGORIES = mapOf(
            "角色" to "角色图鉴",
            "光锥" to "光锥图鉴"
        )
    }

    private val requestCounter = AtomicInteger(0)
    private val lastRequestTime = mutableListOf<Long>()

    /** 同步状态回调 */
    class SyncProgress(
        val stage: String,
        val current: Int,
        val total: Int,
        val message: String
    )

    /** 同步结果 */
    data class SyncResult(
        val success: Boolean,
        val charactersCount: Int = 0,
        val lightConesCount: Int = 0,
        val errors: List<String> = emptyList()
    )

    /** 执行完整同步 */
    fun syncAll(onProgress: (SyncProgress) -> Unit = {}): SyncResult {
        val errors = mutableListOf<String>()
        var charsCount = 0
        var conesCount = 0

        try {
            // Step 1: 获取分类成员
            onProgress(SyncProgress("fetch", 0, 1, "获取角色列表..."))
            val characterTitles = fetchCategoryMembers("角色")
            onProgress(SyncProgress("fetch", 0, 1, "获取光锥列表..."))
            val lightConeTitles = fetchCategoryMembers("光锥")

            val total = characterTitles.size + lightConeTitles.size
            var done = 0

            // Step 2: 拉取角色详情
            val characters = JSONObject()
            for ((i, title) in characterTitles.withIndex()) {
                onProgress(SyncProgress("download", i, characterTitles.size, "下载: $title"))
                try {
                    val pageData = fetchPageContent(title)
                    if (pageData != null) {
                        characters.put(title, pageData)
                        charsCount++
                    }
                } catch (e: Exception) {
                    errors.add("角色[$title]: ${e.message}")
                }
                done++
                rateLimit()
            }

            // Step 3: 拉取光锥详情
            val lightCones = JSONObject()
            for ((i, title) in lightConeTitles.withIndex()) {
                onProgress(SyncProgress("download", i, lightConeTitles.size, "下载: $title"))
                try {
                    val pageData = fetchPageContent(title)
                    if (pageData != null) {
                        lightCones.put(title, pageData)
                        conesCount++
                    }
                } catch (e: Exception) {
                    errors.add("光锥[$title]: ${e.message}")
                }
                done++
                rateLimit()
            }

            // Step 4: 保存到本地
            onProgress(SyncProgress("save", 0, 1, "保存数据..."))
            dataDir.mkdirs()

            val output = JSONObject().apply {
                put("sync_time", System.currentTimeMillis())
                put("characters", characters)
                put("light_cones", lightCones)
                put("character_count", charsCount)
                put("light_cone_count", conesCount)
            }
            File(dataDir, "wiki_data.json").writeText(output.toString(2), Charsets.UTF_8)

        } catch (e: Exception) {
            errors.add("同步异常: ${e.message ?: "未知错误"}")
        }

        return SyncResult(
            success = errors.isEmpty(),
            charactersCount = charsCount,
            lightConesCount = conesCount,
            errors = errors
        )
    }

    /** 获取分类下的所有页面标题 */
    private fun fetchCategoryMembers(category: String): List<String> {
        val members = mutableListOf<String>()
        var cmcontinue: String? = null

        while (true) {
            val params = mutableMapOf(
                "action" to "query",
                "list" to "categorymembers",
                "cmtitle" to "Category:$category",
                "cmlimit" to "max",
                "format" to "json"
            )
            cmcontinue?.let { params["cmcontinue"] = it }

            val json = apiGet(params)
            val query = json.optJSONObject("query")
            val items = query?.optJSONArray("categorymembers")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val title = item.optString("title", "")
                    if (title.isNotBlank() && item.optInt("ns", 0) == 0) {
                        members.add(title)
                    }
                }
            }

            val cont = json.optJSONObject("continue")
            cmcontinue = cont?.optString("cmcontinue", null)
            if (cmcontinue == null) break
            rateLimit()
        }

        return members
    }

    /** 获取页面源码内容 */
    private fun fetchPageContent(title: String): JSONObject? {
        val params = mapOf(
            "action" to "query",
            "prop" to "revisions",
            "rvprop" to "content",
            "format" to "json",
            "titles" to title
        )

        val json = apiGet(params)
        val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return null

        for (key in pages.keys()) {
            val page = pages.optJSONObject(key) ?: continue
            val revisions = page.optJSONArray("revisions")
            if (revisions != null && revisions.length() > 0) {
                val rev = revisions.getJSONObject(0)
                val wikitext = rev.optString("*", "") ?: ""
                
                // 提取结构化元数据
                val meta = extractTemplateFields(wikitext)
                meta.put("title", title)
                meta.put("page_id", page.optInt("pageid"))
                
                return meta
            }
        }
        return null
    }

    /** 从 Wiki 模板文本中提取字段 */
    private fun extractTemplateFields(wikitext: String): JSONObject {
        val meta = JSONObject()
        // 匹配模板中的 |key=value 行
        val lineRegex = Regex("""\|([^=]+)=([^|]*)""")
        for (match in lineRegex.findAll(wikitext)) {
            val key = match.groupValues[1].trim()
            val value = match.groupValues[2].trim()
            if (key.isNotBlank()) {
                meta.put(key, cleanWikiText(value))
            }
        }
        return meta
    }

    /** 清理 Wiki 标记文本 */
    private fun cleanWikiText(text: String): String {
        var result = text
        result = result.replace(Regex("""\{\{.*?}}"""), "")  // 移除 {{模板}}
        result = result.replace(Regex("""<.*?>"""), "")       // 移除 HTML 标签
        result = result.replace(Regex("""'''"""), "")          // 移除粗体
        result = result.replace("''", "")                      // 移除斜体
        // 简化 Wiki 链接 [[目标|显示文本]] → 显示文本
        result = Regex("""\[\[([^|]+)\|([^\]]+)\]\]""").replace(result) { it.groupValues[2] }
        result = Regex("""\[\[([^\]]+)\]\]""").replace(result) { it.groupValues[1] }
        return result.trim().take(200)
    }

    /** 调用 MediaWiki API */
    private fun apiGet(params: Map<String, String>): JSONObject {
        val queryString = params.entries.joinToString("&") { 
            "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
        }
        val url = URL("$API_BASE?$queryString")
        
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        
        return try {
            val body = conn.inputStream.bufferedReader().readText()
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    /** 限流控制 */
    private fun rateLimit() {
        val now = System.currentTimeMillis()
        // 清理旧记录
        lastRequestTime.removeAll { now - it > 5000 }
        
        if (lastRequestTime.size >= 10) {
            // 每10个请求后等待更久
            Thread.sleep(REQUEST_INTERVAL_MS * 2)
        } else {
            Thread.sleep(REQUEST_INTERVAL_MS)
        }
        lastRequestTime.add(now)
        requestCounter.incrementAndGet()
    }
}