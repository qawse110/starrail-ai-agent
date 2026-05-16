package com.starrail.agent.core.sync

import org.json.JSONObject
import java.io.File

/**
 * 数据版本管理
 * 追踪每个页面最后同步时间，支持增量更新
 */
data class SyncState(
    val lastSyncTime: Long = 0L,
    val pageVersions: Map<String, Long> = emptyMap(), // pageId → lastRevisionId
    val version: Int = 1
) {
    companion object {
        private const val FILE_NAME = "sync_state.json"

        fun load(dataDir: File): SyncState {
            val file = File(dataDir, FILE_NAME)
            if (!file.exists()) return SyncState()
            return try {
                val json = JSONObject(file.readText())
                SyncState(
                    lastSyncTime = json.optLong("last_sync_time", 0L),
                    pageVersions = json.optJSONObject("page_versions")?.let { obj ->
                        obj.keys().asSequence().associateWith { obj.getLong(it) }
                    } ?: emptyMap(),
                    version = json.optInt("version", 1)
                )
            } catch (e: Exception) {
                SyncState()
            }
        }

        fun save(dataDir: File, state: SyncState) {
            val json = JSONObject().apply {
                put("last_sync_time", state.lastSyncTime)
                put("page_versions", JSONObject(state.pageVersions))
                put("version", state.version)
            }
            File(dataDir, FILE_NAME).writeText(json.toString(2), Charsets.UTF_8)
        }
    }

    fun isPageChanged(pageId: String, currentRevision: Long): Boolean {
        val saved = pageVersions[pageId] ?: 0L
        return saved < currentRevision
    }
}

/**
 * 数据同步模式
 */
enum class SyncMode {
    FULL,       // 全量：重新下载所有页面
    INCREMENTAL // 增量：只下载有变更的页面
}