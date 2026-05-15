package com.starrail.agent.player.db

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 文件持久化版 PlayerDao
 * 所有数据以 JSON 文件形式存储在设备内部存储中
 * 写入时自动持久化，读取时优先从缓存返回
 */
class FilePlayerDao(private val dataDir: File) : PlayerDao {

    private val charactersFile = File(dataDir, "characters.json")
    private val lightConesFile = File(dataDir, "light_cones.json")
    private val relicsFile = File(dataDir, "relics.json")
    private val snapshotsFile = File(dataDir, "snapshots.json")

    // 内存缓存
    private val characters = mutableMapOf<String, PlayerCharacterEntity>()
    private val lightCones = mutableMapOf<String, PlayerLightConeEntity>()
    private val relics = mutableMapOf<String, PlayerRelicEntity>()
    private val snapshots = mutableListOf<PlayerSnapshotEntity>()

    init {
        dataDir.mkdirs()
        loadAll()
    }

    // ==================== 内部：JSON 序列化/反序列化 ====================

    /** 从 JSON 文件加载到缓存 */
    private fun loadAll() {
        // 加载角色
        val charMap = loadCharacterMap(charactersFile) { obj ->
            PlayerCharacterEntity(
                instanceId = obj.getString("instanceId"),
                characterId = obj.getString("characterId"),
                level = obj.optInt("level", 80),
                ascension = obj.optInt("ascension", 6),
                eidolon = obj.optInt("eidolon", 0),
                skillLevelsJson = obj.optString("skillLevelsJson", "{}"),
                equippedLightConeId = obj.optString("equippedLightConeId", "")?.ifBlank { null },
                equippedRelicsJson = obj.optString("equippedRelicsJson", "{}"),
                tracesJson = obj.optString("tracesJson", "[]"),
                isOwned = obj.optBoolean("isOwned", true)
            )
        }
        characters.putAll(charMap)
        
        // 加载光锥
        val lcMap = loadLightConeMap(lightConesFile) { obj ->
            PlayerLightConeEntity(
                instanceId = obj.getString("instanceId"),
                lightConeId = obj.getString("lightConeId"),
                level = obj.optInt("level", 80),
                ascension = obj.optInt("ascension", 6),
                superimpose = obj.optInt("superimpose", 1),
                isEquipped = obj.optBoolean("isEquipped", false),
                equippedByCharacterId = obj.optString("equippedByCharacterId", "")?.ifBlank { null }
            )
        }
        lightCones.putAll(lcMap)
        
        // 加载遗器
        val relicMap = loadRelicMap(relicsFile) { obj ->
            PlayerRelicEntity(
                instanceId = obj.getString("instanceId"),
                relicId = obj.getString("relicId"),
                setId = obj.getString("setId"),
                slotName = obj.getString("slotName"),
                rarity = obj.optInt("rarity", 5),
                level = obj.optInt("level", 0),
                mainStatJson = obj.optString("mainStatJson", "{}"),
                subStatsJson = obj.optString("subStatsJson", "[]"),
                subStatIncrementCount = obj.optInt("subStatIncrementCount", 0),
                isEquipped = obj.optBoolean("isEquipped", false),
                equippedByCharacterId = obj.optString("equippedByCharacterId", "")?.ifBlank { null },
                locked = obj.optBoolean("locked", false),
                markName = obj.optString("markName", "UNDECIDED")
            )
        }
        relics.putAll(relicMap)
        
        // 加载快照
        snapshots.addAll(loadSnapshotList(snapshotsFile) { obj ->
            PlayerSnapshotEntity(
                id = obj.getString("id"),
                timestamp = obj.getLong("timestamp"),
                characterIdsJson = obj.optString("characterIdsJson", "[]"),
                lightConeIdsJson = obj.optString("lightConeIdsJson", "[]"),
                relicIdsJson = obj.optString("relicIdsJson", "[]"),
                metadataJson = obj.optString("metadataJson", "{}")
            )
        })
    }

    /** 将缓存全部持久化到文件 */
    private fun flushAll() {
        flushCharacterMap(charactersFile, characters)
        flushLightConeMap(lightConesFile, lightCones)
        flushRelicMap(relicsFile, relics)
        flushSnapshotList(snapshotsFile, snapshots)
    }

    // ==================== JSON 文件读写 ====================

    private fun loadCharacterMap(file: File, parser: (JSONObject) -> PlayerCharacterEntity): Map<String, PlayerCharacterEntity> {
        if (!file.exists()) return emptyMap()
        return try {
            val text = file.readText().trim()
            if (text.isEmpty() || text == "{}") return emptyMap()
            val json = JSONObject(text)
            val result = mutableMapOf<String, PlayerCharacterEntity>()
            for (key in json.keys()) result[key] = parser(json.getJSONObject(key))
            result
        } catch (e: Exception) { emptyMap() }
    }

    private fun loadLightConeMap(file: File, parser: (JSONObject) -> PlayerLightConeEntity): Map<String, PlayerLightConeEntity> {
        if (!file.exists()) return emptyMap()
        return try {
            val text = file.readText().trim()
            if (text.isEmpty() || text == "{}") return emptyMap()
            val json = JSONObject(text)
            val result = mutableMapOf<String, PlayerLightConeEntity>()
            for (key in json.keys()) result[key] = parser(json.getJSONObject(key))
            result
        } catch (e: Exception) { emptyMap() }
    }

    private fun loadRelicMap(file: File, parser: (JSONObject) -> PlayerRelicEntity): Map<String, PlayerRelicEntity> {
        if (!file.exists()) return emptyMap()
        return try {
            val text = file.readText().trim()
            if (text.isEmpty() || text == "{}") return emptyMap()
            val json = JSONObject(text)
            val result = mutableMapOf<String, PlayerRelicEntity>()
            for (key in json.keys()) result[key] = parser(json.getJSONObject(key))
            result
        } catch (e: Exception) { emptyMap() }
    }

    private fun loadSnapshotList(file: File, parser: (JSONObject) -> PlayerSnapshotEntity): List<PlayerSnapshotEntity> {
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText().trim()
            if (text.isEmpty() || text == "[]") return emptyList()
            val arr = JSONArray(text)
            (0 until arr.length()).map { parser(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    private fun flushCharacterMap(file: File, map: Map<String, PlayerCharacterEntity>) {
        try {
            val json = JSONObject()
            for ((key, value) in map) {
                json.put(key, JSONObject().apply {
                    put("instanceId", value.instanceId)
                    put("characterId", value.characterId)
                    put("level", value.level)
                    put("ascension", value.ascension)
                    put("eidolon", value.eidolon)
                    put("skillLevelsJson", value.skillLevelsJson)
                    put("equippedLightConeId", value.equippedLightConeId ?: "")
                    put("equippedRelicsJson", value.equippedRelicsJson)
                    put("tracesJson", value.tracesJson)
                    put("isOwned", value.isOwned)
                })
            }
            file.writeText(json.toString(2))
        } catch (e: Exception) { /* quiet */ }
    }

    private fun flushLightConeMap(file: File, map: Map<String, PlayerLightConeEntity>) {
        try {
            val json = JSONObject()
            for ((key, value) in map) {
                json.put(key, JSONObject().apply {
                    put("instanceId", value.instanceId)
                    put("lightConeId", value.lightConeId)
                    put("level", value.level)
                    put("ascension", value.ascension)
                    put("superimpose", value.superimpose)
                    put("isEquipped", value.isEquipped)
                    put("equippedByCharacterId", value.equippedByCharacterId ?: "")
                })
            }
            file.writeText(json.toString(2))
        } catch (e: Exception) { /* quiet */ }
    }

    private fun flushRelicMap(file: File, map: Map<String, PlayerRelicEntity>) {
        try {
            val json = JSONObject()
            for ((key, value) in map) {
                json.put(key, JSONObject().apply {
                    put("instanceId", value.instanceId)
                    put("relicId", value.relicId)
                    put("setId", value.setId)
                    put("slotName", value.slotName)
                    put("rarity", value.rarity)
                    put("level", value.level)
                    put("mainStatJson", value.mainStatJson)
                    put("subStatsJson", value.subStatsJson)
                    put("subStatIncrementCount", value.subStatIncrementCount)
                    put("isEquipped", value.isEquipped)
                    put("equippedByCharacterId", value.equippedByCharacterId ?: "")
                    put("locked", value.locked)
                    put("markName", value.markName)
                })
            }
            file.writeText(json.toString(2))
        } catch (e: Exception) { /* quiet */ }
    }

    private fun flushSnapshotList(file: File, list: List<PlayerSnapshotEntity>) {
        try {
            val arr = JSONArray()
            for (value in list) {
                arr.put(JSONObject().apply {
                    put("id", value.id)
                    put("timestamp", value.timestamp)
                    put("characterIdsJson", value.characterIdsJson)
                    put("lightConeIdsJson", value.lightConeIdsJson)
                    put("relicIdsJson", value.relicIdsJson)
                    put("metadataJson", value.metadataJson)
                })
            }
            file.writeText(arr.toString(2))
        } catch (e: Exception) { /* quiet */ }
    }

    // ==================== PlayerDao 实现 ====================

    // ------ 角色 ------
    override fun getAllCharacters(): List<PlayerCharacterEntity> = synchronized(characters) {
        characters.values.toList()
    }

    override fun getCharacterById(charId: String): PlayerCharacterEntity? = synchronized(characters) {
        characters[charId]
    }

    override fun insertCharacter(character: PlayerCharacterEntity) = synchronized(characters) {
        characters[character.instanceId] = character
        flushAll()
    }

    override fun insertCharacters(chars: List<PlayerCharacterEntity>) = synchronized(characters) {
        chars.forEach { characters[it.instanceId] = it }
        flushAll()
    }

    override fun deleteCharacter(character: PlayerCharacterEntity) = synchronized(characters) {
        characters.remove(character.instanceId)
        flushAll()
    }

    override fun deleteAllCharacters() = synchronized(characters) {
        characters.clear()
        flushAll()
    }

    // ------ 光锥 ------
    override fun getAllLightCones(): List<PlayerLightConeEntity> = synchronized(lightCones) {
        lightCones.values.toList()
    }

    override fun getLightConeById(lcId: String): PlayerLightConeEntity? = synchronized(lightCones) {
        lightCones[lcId]
    }

    override fun insertLightCone(cone: PlayerLightConeEntity) = synchronized(lightCones) {
        lightCones[cone.instanceId] = cone
        flushAll()
    }

    override fun insertLightCones(cones: List<PlayerLightConeEntity>) = synchronized(lightCones) {
        cones.forEach { lightCones[it.instanceId] = it }
        flushAll()
    }

    override fun deleteLightCone(cone: PlayerLightConeEntity) = synchronized(lightCones) {
        lightCones.remove(cone.instanceId)
        flushAll()
    }

    override fun deleteAllLightCones() = synchronized(lightCones) {
        lightCones.clear()
        flushAll()
    }

    // ------ 遗器 ------
    override fun getAllRelics(): List<PlayerRelicEntity> = synchronized(relics) {
        relics.values.toList()
    }

    override fun getRelicById(id: String): PlayerRelicEntity? = synchronized(relics) {
        relics[id]
    }

    override fun insertRelic(relic: PlayerRelicEntity) = synchronized(relics) {
        relics[relic.instanceId] = relic
        flushAll()
    }

    override fun insertRelics(rels: List<PlayerRelicEntity>) = synchronized(relics) {
        rels.forEach { relics[it.instanceId] = it }
        flushAll()
    }

    override fun deleteRelic(relic: PlayerRelicEntity) = synchronized(relics) {
        relics.remove(relic.instanceId)
        flushAll()
    }

    override fun deleteAllRelics() = synchronized(relics) {
        relics.clear()
        flushAll()
    }

    // ------ 快照 ------
    override fun getLatestSnapshot(): PlayerSnapshotEntity? = synchronized(snapshots) {
        snapshots.lastOrNull()
    }

    override fun insertSnapshot(snapshot: PlayerSnapshotEntity) = synchronized(snapshots) {
        snapshots.add(snapshot)
        flushAll()
    }

    override fun deleteAllSnapshots() = synchronized(snapshots) {
        snapshots.clear()
        flushAll()
    }
}
