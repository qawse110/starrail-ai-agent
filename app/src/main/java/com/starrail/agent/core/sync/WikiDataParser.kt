package com.starrail.agent.core.sync

import com.starrail.agent.core.model.*
import org.json.JSONObject

/**
 * Wiki 数据解析器
 * 将 bilibili Wiki 下载的 JSON 数据转换为游戏数据模型
 *
 * Wiki 模板格式：
 * {{角色图鉴
 * |名称=希儿
 * |外文名=Seele
 * |稀有度=5星
 * |命途=巡猎
 * |元素属性=量子
 * |...}}
 */
object WikiDataParser {

    /** 解析角色元数据（从 wiki_data.json 提取） */
    fun parseCharacterMetadata(wikiJson: JSONObject?): List<Character> {
        if (wikiJson == null) return emptyList()
        val chars = mutableListOf<Character>()
        val characters = wikiJson.optJSONObject("characters") ?: return emptyList()

        for (title in characters.keys()) {
            try {
                val page = characters.getJSONObject(title)
                val name = page.optString("名称", title).cleanWiki()
                val rarityStr = page.optString("稀有度", "").cleanWiki()
                val pathStr = page.optString("命途", "").cleanWiki()
                val elementStr = page.optString("元素属性", "").cleanWiki()
                val faction = page.optString("阵营", "").cleanWiki()
                val nickname = page.optString("昵称/外号", "").cleanWiki()

                val rarity = if (rarityStr.contains("5")) 5 else 4
                val path = parsePath(pathStr)
                val element = parseElement(elementStr)

                if (path == null || element == null) continue

                val id = "wiki_${name}"
                chars.add(Character(
                    id = id,
                    name = name,
                    rarity = rarity,
                    path = path,
                    element = element,
                    baseStats = BaseStats(hp = 0.0, attack = 0.0, defense = 0.0, speed = 0.0),
                    ascensionStats = emptyMap(),
                    skills = emptyList(),
                    traces = emptyList(),
                    eidolons = emptyList(),
                    resonance = faction
                ))
            } catch (e: Exception) { /* skip malformed entries */ }
        }
        return chars
    }

    /** 解析光锥元数据 */
    fun parseLightConeMetadata(wikiJson: JSONObject?): List<LightCone> {
        if (wikiJson == null) return emptyList()
        val cones = mutableListOf<LightCone>()
        val lightCones = wikiJson.optJSONObject("light_cones") ?: return emptyList()

        for (title in lightCones.keys()) {
            try {
                val page = lightCones.getJSONObject(title)
                val name = page.optString("名称", title).cleanWiki()
                val rarityStr = page.optString("稀有度", "").cleanWiki()
                val pathStr = page.optString("命途", "").cleanWiki()
                val relatedChar = page.optString("相关角色", "").cleanWiki()

                val rarity = when {
                    rarityStr.contains("5") -> 5
                    rarityStr.contains("4") -> 4
                    else -> 3
                }
                val path = parsePath(pathStr) ?: continue

                cones.add(LightCone(
                    id = "wiki_lc_${name}",
                    name = name,
                    rarity = rarity,
                    path = path,
                    baseStats = BaseStats(hp = 0.0, attack = 0.0, defense = 0.0, speed = 0.0),
                    ascensionStats = emptyMap(),
                    skill = LightConeSkill(name = "", description = "", effects = emptyList()),
                    superimposeLevels = emptyList()
                ))
            } catch (e: Exception) { /* skip */ }
        }
        return cones
    }

    /** 合并 wiki 元数据到现有角色数据 */
    fun mergeCharacterData(
        wikiChars: List<Character>,
        hardcodedChars: List<Character>
    ): List<Character> {
        val wikiMap = wikiChars.associateBy { it.name }
        return hardcodedChars.map { hc ->
            val wiki = wikiMap[hc.name]
            if (wiki != null) {
                // 用 wiki 的元数据覆盖硬编码（保留硬编码的战斗数据）
                hc.copy(
                    rarity = wiki.rarity,
                    path = wiki.path,
                    element = wiki.element,
                    resonance = wiki.resonance ?: hc.resonance
                )
            } else hc // wiki 中没有的保留原样
        }
    }

    /** 合并 wiki 光锥元数据 */
    fun mergeLightConeData(
        wikiCones: List<LightCone>,
        hardcodedCones: List<LightCone>
    ): List<LightCone> {
        val wikiMap = wikiCones.associateBy { it.name }
        return hardcodedCones.map { hc ->
            val wiki = wikiMap[hc.name]
            if (wiki != null) {
                hc.copy(rarity = wiki.rarity, path = wiki.path)
            } else hc
        }
    }

    /** 检查 wiki 数据是否包含补充数据（技能/星魂等）的占位 */
    fun needsSupplementaryData(wikiJson: JSONObject?): Boolean {
        if (wikiJson == null) return true
        val chars = wikiJson.optJSONObject("characters")
        if (chars == null || chars.length() == 0) return true
        // 检查第一个角色是否有详细战斗数据
        val firstKey = chars.keys().next()
        val first = chars.getJSONObject(firstKey)
        return !first.has("skills") // 如果没有 skills 字段，需要补充数据
    }

    // ===== 低层级解析辅助 =====

    private fun parsePath(str: String): PathType? = when {
        str.contains("毁灭") -> PathType.毁灭
        str.contains("巡猎") -> PathType.巡猎
        str.contains("智识") -> PathType.智识
        str.contains("同谐") -> PathType.同谐
        str.contains("虚无") -> PathType.虚无
        str.contains("存护") -> PathType.存护
        str.contains("丰饶") -> PathType.丰饶
        else -> null
    }

    private fun parseElement(str: String): ElementType? = when {
        str.contains("火") -> ElementType.火
        str.contains("冰") -> ElementType.冰
        str.contains("雷") -> ElementType.雷
        str.contains("风") -> ElementType.风
        str.contains("量子") -> ElementType.量子
        str.contains("虚数") -> ElementType.虚数
        str.contains("物理") -> ElementType.物理
        else -> null
    }
}

/** 清理 wiki 文本的扩展函数 */
private fun String.cleanWiki(): String {
    return this
        .replace(Regex("""\{\{.*?}}"""), "")
        .replace(Regex("""<.*?>"""), "")
        .replace(Regex("""'''"""), "")
        .replace("''", "")
        .replace(Regex("""\[\[[^|]+\|([^\]]+)\]\]"""), "$1")
        .replace(Regex("""\[\[([^\]]+)\]\]"""), "$1")
        .trim()
}