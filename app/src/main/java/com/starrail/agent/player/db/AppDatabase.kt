package com.starrail.agent.player.db

import java.io.File

/**
 * 数据库访问入口
 * 支持内存模式（默认）和文件持久化模式
 */
class AppDatabase private constructor(private val dao: PlayerDao) {
    fun playerDao(): PlayerDao = dao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 获取内存模式实例（数据不持久化） */
        fun getInstance(): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(InMemoryPlayerDao()).also { INSTANCE = it }
            }
        }

        /** 获取文件持久化模式实例 */
        fun getFileInstance(dataDir: File): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val fileDao = FilePlayerDao(dataDir)
                AppDatabase(fileDao).also { INSTANCE = it }
            }
        }

        /** 重置单例（测试用） */
        fun resetForTest() { INSTANCE = null }
    }
}