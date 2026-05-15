package com.starrail.agent.player

import com.starrail.agent.core.model.*

/**
 * 玩家持有的角色数据
 * 包含玩家拥有的角色的装备配置
 */
data class PlayerCharacter(
    val characterId: String,              // 关联 Character.id
    val level: Int,                       // 等级 1-80
    val ascension: Int,                    // 突破等级 0-6
    val eidolon: Int,                     // 星魂 0-6
    val skillLevels: Map<String, Int>,     // 技能ID → 等级
    val equippedLightConeId: String?,     // 装备的光锥实例ID
    val equippedRelics: Map<RelicSlot, String>,  // 装备的遗器实例ID
    val traces: Set<String>,              // 已激活的行迹ID集合
    val isOwned: Boolean = true           // 是否已拥有
) {
    /** 验证数据合法性 */
    fun isValid(): Boolean {
        return level in 1..80 &&
               ascension in 0..6 &&
               eidolon in 0..6 &&
               traces.isNotEmpty()
    }
}

/**
 * 玩家持有的光锥数据
 * 同一光锥可能有多个实例
 */
data class PlayerLightCone(
    val instanceId: String,                // 实例唯一ID
    val lightConeId: String,               // 关联 LightCone.id
    val level: Int,                        // 等级
    val ascension: Int,                    // 突破等级
    val superimpose: Int,                  // 精炼 1-5
    val isEquipped: Boolean = false,       // 是否已装备
    val equippedByCharacterId: String? = null  // 装备给哪个角色
) {
    /** 获取有效精炼等级 */
    val effectiveSuperimpose: Int get() = superimpose.coerceIn(1, 5)
    
    /** 验证数据合法性 */
    fun isValid(): Boolean {
        return level in 1..80 && 
               ascension in 0..6 && 
               superimpose in 1..5
    }
}

/**
 * 玩家遗器标记
 */
enum class RelicMark {
    KEEP,       // 保留
    FODDER,     // 狗粮
    UNDECIDED   // 待定
}

/**
 * 玩家持有的遗器数据
 */
data class PlayerRelic(
    val instanceId: String,               // 实例唯一ID
    val relicId: String,                  // 遗器模板ID（关联 RelicTemplate.id）
    val setId: String,                    // 套装ID
    val slot: RelicSlot,                  // 槽位
    val rarity: Int,                      // 稀有度 2-5星
    val level: Int,                       // 强化等级 0-15
    val mainStat: StatEntry,              // 主词条
    val subStats: List<StatEntry>,        // 副词条 0-4条
    val subStatIncrementCount: Int,       // 副词条强化次数（用于计算有效词条）
    val isEquipped: Boolean = false,       // 是否已装备
    val equippedByCharacterId: String? = null,  // 装备给哪个角色
    val locked: Boolean = false,          // 是否锁定
    val mark: RelicMark = RelicMark.UNDECIDED  // 用户标记
) {
    /** 有效副词条数量 */
    val effectiveSubStatCount: Int get() = subStats.size.coerceAtMost(4)
    
    /** 获取副词条强化增量总数 */
    val totalSubStatRolls: Int get() = subStatIncrementCount
    
    /** 计算有效强化次数（简化版） */
    val effectiveRolls: Int get() = (subStatIncrementCount * 0.7).toInt()
    
    /** 验证数据合法性 */
    fun isValid(): Boolean {
        return rarity in 2..5 &&
               level in 0..15 &&
               subStats.size in 0..4 &&
               mainStat.type in getValidMainStatsForSlot(slot)
    }
    
    companion object {
        /** 获取槽位对应的有效主词条 */
        fun getValidMainStatsForSlot(slot: RelicSlot): Set<StatType> {
            return when (slot) {
                RelicSlot.头部 -> setOf(StatType.HP)
                RelicSlot.手部 -> setOf(StatType.ATK)
                RelicSlot.躯干 -> setOf(StatType.CRIT_RATE, StatType.CRIT_DMG, 
                                       StatType.ATK_PERCENT, StatType.HP_PERCENT, 
                                       StatType.DEF_PERCENT, StatType.HEAL_BONUS)
                RelicSlot.脚部 -> setOf(StatType.SPD, StatType.ATK_PERCENT, 
                                        StatType.HP_PERCENT, StatType.DEF_PERCENT)
                RelicSlot.位面球 -> setOf(StatType.ATK_PERCENT, StatType.HP_PERCENT,
                                           StatType.DEF_PERCENT, StatType.CRIT_RATE,
                                           StatType.CRIT_DMG, StatType.EFFECT_HIT,
                                           StatType.EFFECT_RES, StatType.BREAK_DMG)
                RelicSlot.连结绳 -> setOf(StatType.ENERGY_RATE, StatType.ATK_PERCENT,
                                           StatType.HP_PERCENT, StatType.DEF_PERCENT)
            }
        }
    }
}

/**
 * 玩家数据快照
 * 用于数据备份和多账号管理
 */
data class PlayerSnapshot(
    val id: String,
    val timestamp: Long,
    val characters: List<PlayerCharacter>,
    val lightCones: List<PlayerLightCone>,
    val relics: List<PlayerRelic>,
    val metadata: Map<String, String> = emptyMap()
) {
    /** 获取角色总数 */
    val characterCount: Int get() = characters.size
    
    /** 获取光锥总数 */
    val lightConeCount: Int get() = lightCones.size
    
    /** 获取遗器总数 */
    val relicCount: Int get() = relics.size
    
    /** 获取已装备角色数 */
    val equippedCharacterCount: Int get() = 
        characters.count { it.equippedLightConeId != null || it.equippedRelics.isNotEmpty() }
    
    /** 生成摘要信息 */
    fun getSummary(): String {
        return buildString {
            appendLine("玩家数据快照")
            appendLine("创建时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(timestamp))}")
            appendLine("角色数量: $characterCount")
            appendLine("光锥数量: $lightConeCount")
            appendLine("遗器数量: $relicCount")
        }
    }
}

/**
 * 同步报告
 */
data class SyncReport(
    val addedCharacters: List<String> = emptyList(),
    val updatedCharacters: List<String> = emptyList(),
    val removedCharacters: List<String> = emptyList(),
    val addedRelics: List<String> = emptyList(),
    val updatedRelics: List<String> = emptyList(),
    val removedRelics: List<String> = emptyList(),
    val addedLightCones: List<String> = emptyList(),
    val updatedLightCones: List<String> = emptyList(),
    val errors: List<String> = emptyList()
) {
    val hasChanges: Boolean get() = 
        addedCharacters.isNotEmpty() || updatedCharacters.isNotEmpty() ||
        addedRelics.isNotEmpty() || updatedRelics.isNotEmpty() ||
        errors.isNotEmpty()
    
    fun getSummary(): String {
        return buildString {
            if (addedCharacters.isNotEmpty()) appendLine("新增角色: ${addedCharacters.size}")
            if (updatedCharacters.isNotEmpty()) appendLine("更新角色: ${updatedCharacters.size}")
            if (addedRelics.isNotEmpty()) appendLine("新增遗器: ${addedRelics.size}")
            if (updatedRelics.isNotEmpty()) appendLine("更新遗器: ${updatedRelics.size}")
            if (errors.isNotEmpty()) appendLine("错误: ${errors.size}条")
        }
    }
}

/**
 * 同步记录
 */
data class SyncRecord(
    val id: String,
    val timestamp: Long,
    val source: SyncSource,
    val report: SyncReport
)

/**
 * 同步来源
 */
enum class SyncSource {
    HOYOLAB,     // 从 Hoyolab API 同步
    MANUAL,      // 手动导入
    JSON_IMPORT, // JSON 文件导入
    SNAPSHOT     // 快照恢复
}