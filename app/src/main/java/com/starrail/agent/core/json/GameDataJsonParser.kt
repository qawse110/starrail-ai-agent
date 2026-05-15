package com.starrail.agent.core.json

import com.starrail.agent.core.model.*
import org.json.JSONObject
import org.json.JSONArray
import java.io.InputStream

/** 游戏数据 JSON 解析器（简化版，使用 org.json） */
class GameDataJsonParser {
    
    fun parseCharacters(input: InputStream): List<Character> {
        return try {
            val json = JSONObject(input.bufferedReader().readText())
            val items = json.optJSONArray("data") ?: json.optJSONArray("characters") ?: JSONArray()
            val result = mutableListOf<Character>()
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                parseCharacter(obj)?.let { result.add(it) }
            }
            result
        } catch (e: Exception) { emptyList() }
    }
    
    private fun parseCharacter(obj: JSONObject): Character? {
        val id = obj.optString("id", obj.optString("_id", ""))
        if (id.isEmpty()) return null
        val name = obj.optString("name", "") 
        if (name.isEmpty()) return null
        val rarity = obj.optInt("rarity", obj.optInt("rank", 5))
        val pathStr = obj.optString("path", obj.optString("path_name", ""))
        val elementStr = obj.optString("element", obj.optString("element_name", ""))
        val path = PathType.fromString(pathStr) ?: PathType.智识
        val element = ElementType.fromString(elementStr) ?: ElementType.物理
        val stats = obj.optJSONObject("stats") ?: obj.optJSONObject("base_stats")
        val baseStats = if (stats != null) BaseStats(
            stats.optDouble("hp", 0.0),
            stats.optDouble("atk", stats.optDouble("attack", 0.0)),
            stats.optDouble("def", stats.optDouble("defense", 0.0)),
            stats.optDouble("spd", stats.optDouble("speed", 100.0))
        ) else BaseStats(0.0, 0.0, 0.0, 100.0)
        return Character(id, name, rarity, path, element, baseStats, emptyMap(), emptyList(), emptyList(), emptyList())
    }
    
    fun parseLightCones(input: InputStream): List<LightCone> {
        return try {
            val json = JSONObject(input.bufferedReader().readText())
            val items = json.optJSONArray("data") ?: json.optJSONArray("light_cones") ?: JSONArray()
            val result = mutableListOf<LightCone>()
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val pathStr = obj.optString("path", "")
                val path = PathType.fromString(pathStr) ?: continue
                result.add(LightCone(
                    obj.optString("id", ""), obj.optString("name", ""),
                    obj.optInt("rarity", 5), path,
                    BaseStats(800.0, 500.0, 400.0, 100.0),
                    emptyMap(), LightConeSkill("技能", "", emptyList()), emptyList()
                ))
            }
            result
        } catch (e: Exception) { emptyList() }
    }
    
    fun parseRelicSets(input: InputStream): List<RelicSet> {
        return try {
            val json = JSONObject(input.bufferedReader().readText())
            val items = json.optJSONArray("data") ?: json.optJSONArray("relic_sets") ?: JSONArray()
            val result = mutableListOf<RelicSet>()
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val typeStr = obj.optString("type", "relic")
                val type = if (typeStr.lowercase() == "ornament") RelicSetType.ORNAMENT else RelicSetType.RELIC
                result.add(RelicSet(obj.optString("id", ""), obj.optString("name", ""), emptyList(), type))
            }
            result
        } catch (e: Exception) { emptyList() }
    }
}