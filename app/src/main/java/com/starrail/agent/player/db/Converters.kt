package com.starrail.agent.player.db

import com.starrail.agent.core.model.RelicSlot
import com.starrail.agent.core.model.StatEntry
import com.starrail.agent.core.model.StatType
import com.starrail.agent.player.RelicMark
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON 序列化工具类
 * 将复杂类型序列化为 JSON 字符串存储
 */
class Converters {

    fun fromStatEntryList(value: List<StatEntry>): String {
        val arr = JSONArray()
        for (e in value) {
            arr.put(JSONObject().apply {
                put("type", e.type.name)
                put("value", e.value)
            })
        }
        return arr.toString()
    }

    fun toStatEntryList(value: String): List<StatEntry> {
        val arr = JSONArray(value)
        val result = mutableListOf<StatEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val type = StatType.fromString(obj.getString("type")) ?: continue
            result.add(StatEntry(type, obj.getDouble("value")))
        }
        return result
    }

    fun fromRelicSlotMap(value: Map<RelicSlot, String>): String {
        val obj = JSONObject()
        for ((k, v) in value) obj.put(k.name, v)
        return obj.toString()
    }

    fun toRelicSlotMap(value: String): Map<RelicSlot, String> {
        val obj = JSONObject(value)
        val result = mutableMapOf<RelicSlot, String>()
        for (key in obj.keys()) {
            val slot = RelicSlot.fromString(key) ?: continue
            result[slot] = obj.getString(key)
        }
        return result
    }

    fun fromStringSet(value: Set<String>): String = JSONArray(value.toList()).toString()

    fun toStringSet(value: String): Set<String> {
        val arr = JSONArray(value)
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    fun fromStringMap(value: Map<String, String>): String {
        val obj = JSONObject()
        for ((k, v) in value) obj.put(k, v)
        return obj.toString()
    }

    fun toStringMap(value: String): Map<String, String> {
        val obj = JSONObject(value)
        val result = mutableMapOf<String, String>()
        for (key in obj.keys()) result[key] = obj.getString(key)
        return result
    }

    fun fromRelicMark(value: RelicMark): String = value.name

    fun toRelicMark(value: String): RelicMark = try { RelicMark.valueOf(value) } catch (_: Exception) { RelicMark.UNDECIDED }
}