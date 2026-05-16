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
            "光锥" to "光锥图鉴"
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
        var charsCount = 0
        var conesCount = 0
        var changedCount = 0
        val isFull = mode == SyncMode.FULL

        try {
            // Step 1: 获取分类成员列表
            onProgress(SyncProgress("fetch", 0, 1, "获取角色列表..."))
            val characterTitles = fetchCategoryMembers("角色")
            onProgress(SyncProgress("fetch", 0, 1, "获取光锥列表..."))
            val lightConeTitles = fetchCategoryMembers("光锥")

            val total = characterTitles.size + lightConeTitles.size
            var done = 0
            val characters = JSONObject()
            val lightCones = JSONObject()
            val pageVersions = mutableMapOf<String, Long>()

            // Step 2: 下载页面（角色 + 光锥）
            for ((titles, putTarget) in listOf(
                characterTitles to characters,
                lightConeTitles to lightCones
            )) {
                for ((i, title) in titles.withIndex()) {
                    val progressLabel = if (titles === characterTitles) "角色" else "光锥"
                    onProgress(SyncProgress("download", i, titles.size, "$progressLabel: $title"))

                    try {
                        // 增量模式下检查是否需要跳过
                        if (!isFull) {
                            val pageId = getPageId(title)
                            val revId = getLatestRevisionId(title)
                            if (pageId != null && revId != null && !syncState.isPageChanged(pageId, revId)) {
                                done++
                                continue // 页面未变更，跳过
                            }
                        }

                        val pageData = fetchPageContent(title)
                        if (pageData != null) {
                            putTarget.put(title, pageData)
                            changedCount++
                            if (titles === characterTitles) charsCount++
                            else conesCount++

                            // 保存修订 ID
                            val revId = pageData.optLong("rev_id", 0L)
                            val pageId = pageData.optString("page_id", "")
                            if (pageId.isNotBlank() && revId > 0) {
                                pageVersions[pageId] = revId
                            }
                        }
                    } catch (e: Exception) {
                        errors.add("$progressLabel[$title]: ${e.message}")
                    }
                    done++
                    rateLimit()
                }
            }

            onProgress(SyncProgress("save", 0, 1, "保存数据..."))
            dataDir.mkdirs()

            // 读取已有数据，合并增量更新
            val existingFile = File(dataDir, "wiki_data.json")
            val existing = if (!isFull && existingFile.exists()) {
                try { JSONObject(existingFile.readText()) } catch (e: Exception) { null }
            } else null

            // 合并新旧数据
            val mergedChars = JSONObject()
            if (existing != null) {
                val oldChars = existing.optJSONObject("characters")
                if (oldChars != null) {
                    for (key in oldChars.keys()) { mergedChars.put(key, oldChars.get(key)) }
                }
            }
            for (key in characters.keys()) { mergedChars.put(key, characters.get(key)) }

            val mergedCones = JSONObject()
            if (existing != null) {
                val oldCones = existing.optJSONObject("light_cones")
                if (oldCones != null) {
                    for (key in oldCones.keys()) { mergedCones.put(key, oldCones.get(key)) }
                }
            }
            for (key in lightCones.keys()) { mergedCones.put(key, lightCones.get(key)) }

            // 保存
            val output = JSONObject().apply {
                put("sync_time", System.currentTimeMillis())
                put("sync_mode", mode.name)
                put("characters", mergedChars)
                put("light_cones", mergedCones)
                put("character_count", mergedChars.length())
                put("light_cone_count", mergedCones.length())
            }
            existingFile.writeText(output.toString(2), Charsets.UTF_8)

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
            charactersCount = charsCount,
            lightConesCount = conesCount,
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