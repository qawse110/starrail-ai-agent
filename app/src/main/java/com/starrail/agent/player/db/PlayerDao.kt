package com.starrail.agent.player.db

/**
 * 玩家数据访问接口
 * 提供 CRUD 操作规范，当前为内存实现，可替换为 Room 版本
 */
interface PlayerDao {
    // === 角色 ===
    fun getAllCharacters(): List<PlayerCharacterEntity>
    fun getCharacterById(charId: String): PlayerCharacterEntity?
    fun insertCharacter(character: PlayerCharacterEntity)
    fun insertCharacters(characters: List<PlayerCharacterEntity>)
    fun deleteCharacter(character: PlayerCharacterEntity)
    fun deleteAllCharacters()

    // === 光锥 ===
    fun getAllLightCones(): List<PlayerLightConeEntity>
    fun getLightConeById(lcId: String): PlayerLightConeEntity?
    fun insertLightCone(cone: PlayerLightConeEntity)
    fun insertLightCones(cones: List<PlayerLightConeEntity>)
    fun deleteLightCone(cone: PlayerLightConeEntity)
    fun deleteAllLightCones()

    // === 遗器 ===
    fun getAllRelics(): List<PlayerRelicEntity>
    fun getRelicById(id: String): PlayerRelicEntity?
    fun insertRelic(relic: PlayerRelicEntity)
    fun insertRelics(relics: List<PlayerRelicEntity>)
    fun deleteRelic(relic: PlayerRelicEntity)
    fun deleteAllRelics()

    // === 快照 ===
    fun getLatestSnapshot(): PlayerSnapshotEntity?
    fun insertSnapshot(snapshot: PlayerSnapshotEntity)
    fun deleteAllSnapshots()
}

/**
 * 内存版 DAO 实现
 */
class InMemoryPlayerDao : PlayerDao {
    private val characters = mutableMapOf<String, PlayerCharacterEntity>()
    private val lightCones = mutableMapOf<String, PlayerLightConeEntity>()
    private val relics = mutableMapOf<String, PlayerRelicEntity>()
    private val snapshots = mutableListOf<PlayerSnapshotEntity>()

    override fun getAllCharacters(): List<PlayerCharacterEntity> = characters.values.toList()
    override fun getCharacterById(charId: String): PlayerCharacterEntity? = characters[charId]
    override fun insertCharacter(character: PlayerCharacterEntity) { characters[character.instanceId] = character }
    override fun insertCharacters(chars: List<PlayerCharacterEntity>) { chars.forEach { insertCharacter(it) } }
    override fun deleteCharacter(character: PlayerCharacterEntity) { characters.remove(character.instanceId) }
    override fun deleteAllCharacters() { characters.clear() }

    override fun getAllLightCones(): List<PlayerLightConeEntity> = lightCones.values.toList()
    override fun getLightConeById(lcId: String): PlayerLightConeEntity? = lightCones[lcId]
    override fun insertLightCone(cone: PlayerLightConeEntity) { lightCones[cone.instanceId] = cone }
    override fun insertLightCones(cones: List<PlayerLightConeEntity>) { cones.forEach { insertLightCone(it) } }
    override fun deleteLightCone(cone: PlayerLightConeEntity) { lightCones.remove(cone.instanceId) }
    override fun deleteAllLightCones() { lightCones.clear() }

    override fun getAllRelics(): List<PlayerRelicEntity> = relics.values.toList()
    override fun getRelicById(id: String): PlayerRelicEntity? = relics[id]
    override fun insertRelic(relic: PlayerRelicEntity) { relics[relic.instanceId] = relic }
    override fun insertRelics(rels: List<PlayerRelicEntity>) { rels.forEach { insertRelic(it) } }
    override fun deleteRelic(relic: PlayerRelicEntity) { relics.remove(relic.instanceId) }
    override fun deleteAllRelics() { relics.clear() }

    override fun getLatestSnapshot(): PlayerSnapshotEntity? = snapshots.lastOrNull()
    override fun insertSnapshot(snapshot: PlayerSnapshotEntity) { snapshots.add(snapshot) }
    override fun deleteAllSnapshots() { snapshots.clear() }
}