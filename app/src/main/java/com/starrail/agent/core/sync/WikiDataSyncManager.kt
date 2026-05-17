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
 * 支持全量/增量两种模式：
 * - FULL: 重新下载所有页面，替换本地缓存
 * - INCREMENTAL: 只下载有变更的页面（基于 revision ID 检测）
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
            "光锥" to "光锥图鉴",
            "遗器" to "遗器套装"
        )
    }

    private val requestCounter = AtomicInteger(0)
    private val lastRequestTime = mutableListOf<Long>()
    private var syncState = SyncState.load(dataDir)

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
        val mode: SyncMode = SyncMode.FULL,
        val charactersCount: Int = 0,
        val lightConesCount: Int = 0,
        val relicSetsCount: Int = 0,
        val changedCount: Int = 0,       // 增量模式下有变更的页面数
        val errors: List<String> = emptyList()
    )

    /** 执行全量同步：重新下载所有页面 */
    fun syncAll(onProgress: (SyncProgress) -> Unit = {}): SyncResult {
        return sync(SyncMode.FULL, onProgress)
    }

    /** 执行增量同步：只下载有变更的页面 */
    fun syncIncremental(onProgress: (SyncProgress) -> Unit = {}): SyncResult {
        return sync(SyncMode.INCREMENTAL, onProgress)
    }

    /** 核心同步方法 */
    private fun sync(mode: SyncMode, onProgress: (SyncProgress) -> Unit): SyncResult {
        val errors = mutableListOf<String>()
        var changedCount = 0
        val isFull = mode == SyncMode.FULL
        var output: JSONObject? = null

        try {
            // 分类定义：名称 → 模板名 → 输出key
            val categories = listOf(
                Triple("角色", "角色图鉴", "characters"),
                Triple("光锥", "光锥图鉴", "light_cones"),
                Triple("遗器", "遗器套装", "relic_sets")
            )

            val allResults = mutableMapOf<String, JSONObject>()
            val pageVersions = mutableMapOf<String, Long>()

            for ((catName, _, outputKey) in categories) {
                onProgress(SyncProgress("fetch", 0, 1, "获取${catName}列表..."))
                val titles = fetchCategoryMembers(catName)

                val resultMap = JSONObject()
                for ((i, title) in titles.withIndex()) {
                    onProgress(SyncProgress("download", i, titles.size, "$catName: $title"))

                    try {
                        // 增量模式跳过未变更页面
                        if (!isFull) {
                            val pageId = getPageId(title)
                            val revId = getLatestRevisionId(title)
                            if (pageId != null && revId != null && !syncState.isPageChanged(pageId, revId)) {
                                continue
                            }
                        }

                        val pageData = fetchPageContent(title)
                        if (pageData != null) {
                            resultMap.put(title, pageData)
                            val revId = pageData.optLong("rev_id", 0L)
                            val pageId = pageData.optString("page_id", "")
                            if (pageId.isNotBlank() && revId > 0) {
                                pageVersions[pageId] = revId
                            }
                        }
                    } catch (e: Exception) {
                        errors.add("$catName[$title]: ${e.message}")
                    }
                    rateLimit()
                }
                allResults[outputKey] = resultMap
            }

            // 合并增量数据
            onProgress(SyncProgress("save", 0, 1, "保存数据..."))
            dataDir.mkdirs()
            val existingFile = File(dataDir, "wiki_data.json")
            val existing = if (!isFull && existingFile.exists()) {
                try { JSONObject(existingFile.readText()) } catch (e: Exception) { null }
            } else null

            val jsonOutput = JSONObject().also { output = it }
            for ((_, _, outputKey) in categories) {
                val merged = JSONObject()
                if (existing != null) {
                    val old = existing.optJSONObject(outputKey)
                    if (old != null) {
                        for (key in old.keys()) { merged.put(key, old.get(key)) }
                    }
                }
                val newData = allResults[outputKey] ?: JSONObject()
                for (key in newData.keys()) { merged.put(key, newData.get(key)) }
                jsonOutput.put(outputKey, merged)
                jsonOutput.put("${outputKey}_count", merged.length())
            }
            jsonOutput.put("sync_time", System.currentTimeMillis())
            jsonOutput.put("sync_mode", mode.name)

            existingFile.writeText(jsonOutput.toString(2), Charsets.UTF_8)

            // 更新同步状态
            syncState = SyncState(
                lastSyncTime = System.currentTimeMillis(),
                pageVersions = syncState.pageVersions + pageVersions,
                version = syncState.version + 1
            )
            SyncState.save(dataDir, syncState)

        } catch (e: Exception) {
            errors.add("同步异常: ${e.message ?: "未知错误"}")
        }

        return SyncResult(
            success = errors.isEmpty(),
            mode = mode,
            charactersCount = output?.optInt("characters_count") ?: 0,
            lightConesCount = output?.optInt("light_cones_count") ?: 0,
            relicSetsCount = output?.optInt("relic_sets_count") ?: 0,
            changedCount = changedCount,
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
            "rvprop" to "content|ids",  // 添加 ids 获取修订版本号
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
                meta.put("rev_id", rev.optLong("revid", 0L))  // 记录修订版本号
                
                return meta
            }
        }
        return null
    }

    /** 获取页面 ID */
    private fun getPageId(title: String): String? {
        val params = mapOf(
            "action" to "query",
            "prop" to "info",
            "format" to "json",
            "titles" to title
        )
        val json = apiGet(params)
        val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return null
        for (key in pages.keys()) {
            val page = pages.optJSONObject(key) ?: continue
            val pageId = page.optInt("pageid", 0)
            if (pageId > 0) return pageId.toString()
        }
        return null
    }

    /** 获取页面最新修订版本号 */
    private fun getLatestRevisionId(title: String): Long? {
        val params = mapOf(
            "action" to "query",
            "prop" to "revisions",
            "rvprop" to "ids",
            "rvlimit" to "1",
            "format" to "json",
            "titles" to title
        )
        val json = apiGet(params)
        val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return null
        for (key in pages.keys()) {
            val page = pages.optJSONObject(key) ?: continue
            val revisions = page.optJSONArray("revisions")
            if (revisions != null && revisions.length() > 0) {
                return revisions.getJSONObject(0).optLong("revid", 0L)
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