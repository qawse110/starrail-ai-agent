package com.starrail.agent.player.db

import com.starrail.agent.player.PlayerCharacter
import com.starrail.agent.player.PlayerLightCone
import com.starrail.agent.player.PlayerRelic

/**
 * 玩家角色持久化实体
 */
data class PlayerCharacterEntity(
    val instanceId: String,
    val characterId: String,
    val level: Int,
    val ascension: Int,
    val eidolon: Int,
    val skillLevelsJson: String = "{}",
    val equippedLightConeId: String? = null,
    val equippedRelicsJson: String = "{}",
    val tracesJson: String = "[]",
    val isOwned: Boolean = true
) {
    fun toDomain(): PlayerCharacter = PlayerCharacter(
        characterId = characterId, level = level, ascension = ascension,
        eidolon = eidolon, skillLevels = emptyMap(),
        equippedLightConeId = equippedLightConeId,
        equippedRelics = emptyMap(), traces = emptySet(), isOwned = isOwned
    )

    companion object {
        fun fromDomain(pc: PlayerCharacter): PlayerCharacterEntity {
            val data = Converters()
            return PlayerCharacterEntity(
                instanceId = "${pc.characterId}_${pc.eidolon}",
                characterId = pc.characterId,
                level = pc.level, ascension = pc.ascension, eidolon = pc.eidolon,
                skillLevelsJson = data.fromStringMap(pc.skillLevels.mapValues { it.value.toString() }),
                equippedLightConeId = pc.equippedLightConeId,
                equippedRelicsJson = data.fromRelicSlotMap(pc.equippedRelics),
                tracesJson = data.fromStringSet(pc.traces),
                isOwned = pc.isOwned
            )
        }
    }
}

/**
 * 玩家光锥持久化实体
 */
data class PlayerLightConeEntity(
    val instanceId: String,
    val lightConeId: String,
    val level: Int,
    val ascension: Int,
    val superimpose: Int,
    val isEquipped: Boolean = false,
    val equippedByCharacterId: String? = null
)

/**
 * 玩家遗器持久化实体
 */
data class PlayerRelicEntity(
    val instanceId: String,
    val relicId: String,
    val setId: String,
    val slotName: String,
    val rarity: Int,
    val level: Int,
    val mainStatJson: String,
    val subStatsJson: String = "[]",
    val subStatIncrementCount: Int = 0,
    val isEquipped: Boolean = false,
    val equippedByCharacterId: String? = null,
    val locked: Boolean = false,
    val markName: String = "UNDECIDED"
)

/**
 * 玩家数据快照持久化实体
 */
data class PlayerSnapshotEntity(
    val id: String,
    val timestamp: Long,
    val characterIdsJson: String = "[]",
    val lightConeIdsJson: String = "[]",
    val relicIdsJson: String = "[]",
    val metadataJson: String = "{}"
)