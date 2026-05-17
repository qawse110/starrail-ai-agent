package com.starrail.agent.core.datasource

import com.starrail.agent.core.model.*
import org.json.JSONObject

/**
 * 基于内存的游戏数据源实现
 * 优先使用 Wiki 数据，回退到硬编码数据
 *
 * @param wikiJson 已解析的 Wiki JSON 对象（由 MainActivity 从 assets 加载）
 */
class InMemoryGameDataSource(private val wikiJson: JSONObject? = null) {
    
    private val characters: List<Character> by lazy { loadCharacters() }
    private val lightCones: List<LightCone> by lazy { loadLightCones() }
    private val relicSets: List<RelicSet> by lazy { createRelicSets() }
    private val enemies: List<Enemy> by lazy { createEnemies() }
    
    private fun loadCharacters(): List<Character> {
        val wiki = wikiJson
        if (wiki == null) return createCharacters()
        
        val wikiChars = wiki.optJSONObject("characters") ?: return createCharacters()
        if (wikiChars.length() == 0) return createCharacters()
        
        val result = mutableListOf<Character>()
        val hardcoded = createCharacters().associateBy { it.name }
        val hardcodedNames = hardcoded.keys
        
        for (title in wikiChars.keys()) {
            try {
                val page = wikiChars.getJSONObject(title)
                val name = page.optString("名称", "").trim().ifEmpty { title }
                val rarityStr = page.optString("稀有度", "").trim()
                val pathStr = page.optString("命途", "").trim()
                val elemStr = page.optString("元素属性", "").trim()
                val faction = page.optString("阵营", "").trim()
                
                val rarity = if (rarityStr.contains("5")) 5 else 4
                val path = parsePath(pathStr) ?: continue
                val element = parseElement(elemStr) ?: continue
                
                val hc = hardcoded[name]
                if (hc != null) {
                    // 用 wiki 元数据覆盖硬编码，保留战斗数据
                    result.add(hc.copy(
                        rarity = rarity, path = path, element = element,
                        resonance = if (faction.isNotBlank()) faction else hc.resonance
                    ))
                } else {
                    // Wiki独有角色
                    result.add(Character(
                        id = "wiki_${name}",
                        name = name, rarity = rarity, path = path, element = element,
                        baseStats = BaseStats(0.0, 0.0, 0.0, 0.0),
                        ascensionStats = emptyMap(),
                        skills = emptyList(), traces = emptyList(), eidolons = emptyList(),
                        resonance = faction
                    ))
                }
            } catch (_: Exception) { /* skip malformed */ }
        }
        
        // 追加硬编码有但wiki缺的角色
        for ((name, hc) in hardcoded) {
            if (result.none { it.name == name }) result.add(hc)
        }
        
        return result
    }
    
    private fun loadLightCones(): List<LightCone> {
        val wiki = wikiJson
        if (wiki == null) return createLightCones()
        
        val wikiCones = wiki.optJSONObject("light_cones") ?: return createLightCones()
        if (wikiCones.length() == 0) return createLightCones()
        
        val result = mutableListOf<LightCone>()
        val hardcoded = createLightCones().associateBy { it.name }
        val hardcodedNames = hardcoded.keys
        
        for (title in wikiCones.keys()) {
            try {
                val page = wikiCones.getJSONObject(title)
                val name = page.optString("名称", "").trim().ifEmpty { title }
                val rarityStr = page.optString("稀有度", "").trim()
                val pathStr = page.optString("命途", "").trim()
                
                val rarity = when {
                    rarityStr.contains("5") -> 5
                    rarityStr.contains("4") -> 4
                    else -> 3
                }
                val path = parsePath(pathStr) ?: continue
                
                val hc = hardcoded[name]
                if (hc != null) {
                    result.add(hc.copy(rarity = rarity, path = path))
                } else {
                    result.add(LightCone(
                        id = "wiki_lc_${name}",
                        name = name, rarity = rarity, path = path,
                        baseStats = BaseStats(0.0, 0.0, 0.0, 0.0),
                        ascensionStats = emptyMap(),
                        skill = LightConeSkill("", "", emptyList()),
                        superimposeLevels = emptyList()
                    ))
                }
            } catch (_: Exception) { /* skip */ }
        }
        
        for ((name, hc) in hardcoded) {
            if (result.none { it.name == name }) result.add(hc)
        }
        
        return result
    }
    
    // ===== 解析辅助 =====
    private fun parsePath(str: String): PathType? = when {
        str.contains("毁灭") -> PathType.毁灭
        str.contains("巡猎") -> PathType.巡猎
        str.contains("智识") -> PathType.智识
        str.contains("同谐") -> PathType.同谐
        str.contains("虚无") -> PathType.虚无
        str.contains("存护") -> PathType.存护
        str.contains("丰饶") -> PathType.丰饶
        str.contains("记忆") -> PathType.记忆
        str.contains("欢愉") -> PathType.欢愉
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
        str.contains("幻想") -> ElementType.幻想
        else -> null
    }
    
    // === Character API ===
    fun getAllCharacters(): List<Character> = characters
    fun getCharacterById(id: String): Character? = characters.find { it.id == id }
    fun getCharactersByPath(path: PathType): List<Character> = characters.filter { it.path == path }
    fun getCharactersByElement(element: ElementType): List<Character> = characters.filter { it.element == element }
    fun searchCharacters(name: String): List<Character> = characters.filter { 
        it.name.contains(name, ignoreCase = true) 
    }
    fun getCharactersByIds(ids: List<String>): List<Character> = characters.filter { it.id in ids }
    
    // === LightCone API ===
    fun getAllLightCones(): List<LightCone> = lightCones
    fun getLightConeById(id: String): LightCone? = lightCones.find { it.id == id }
    fun getLightConesByPath(path: PathType): List<LightCone> = lightCones.filter { it.path == path }
    fun searchLightCones(name: String): List<LightCone> = lightCones.filter { 
        it.name.contains(name, ignoreCase = true) 
    }
    fun getLightConesByIds(ids: List<String>): List<LightCone> = lightCones.filter { it.id in ids }
    
    // === Relic API ===
    fun getAllRelicSets(): List<RelicSet> = relicSets
    fun getRelicSetById(id: String): RelicSet? = relicSets.find { it.id == id }
    fun searchRelicSets(name: String): List<RelicSet> = relicSets.filter { 
        it.name.contains(name, ignoreCase = true) 
    }
    fun getRecommendedRelicSets(path: PathType?, element: ElementType?): List<RelicSet> {
        return relicSets.filter { set ->
            path == null || element == null || true  // 简化：返回所有套装
        }.take(10)
    }
    fun getRelicSetsByType(type: RelicSetType): List<RelicSet> = relicSets.filter { it.type == type }
    fun getRelicTemplate(setId: String, slot: RelicSlot): RelicTemplate? = null
    fun getAllRelicTemplates(): List<RelicTemplate> = emptyList()
    
    // === Enemy API ===
    fun getAllEnemies(): List<Enemy> = enemies
    fun getEnemyById(id: String): Enemy? = enemies.find { it.id == id }
    fun getEnemiesByWeakness(element: ElementType): List<Enemy> = enemies.filter { element in it.weakness }
    fun getEnemiesByLocation(location: String): List<Enemy> = enemies.filter { it.location == location }
    fun searchEnemies(name: String): List<Enemy> = enemies.filter { it.name.contains(name, ignoreCase = true) }
    
    /** 创建角色数据 */
    private fun createCharacters(): List<Character> = listOf(
        // 五星角色
        createCharacter(
            id = "1001",
            name = "银狼",
            rarity = 5,
            path = PathType.虚无,
            element = ElementType.雷
        ),
        createCharacter(
            id = "1002",
            name = "希儿",
            rarity = 5,
            path = PathType.巡猎,
            element = ElementType.量子
        ),
        createCharacter(
            id = "1003",
            name = "丹恒·饮月",
            rarity = 5,
            path = PathType.毁灭,
            element = ElementType.虚数
        ),
        createCharacter(
            id = "1004",
            name = "景元",
            rarity = 5,
            path = PathType.智识,
            element = ElementType.雷
        ),
        createCharacter(
            id = "1005",
            name = "刃",
            rarity = 5,
            path = PathType.毁灭,
            element = ElementType.风
        ),
        createCharacter(
            id = "1006",
            name = "卡芙卡",
            rarity = 5,
            path = PathType.虚无,
            element = ElementType.雷
        ),
        createCharacter(
            id = "1007",
            name = "符玄",
            rarity = 5,
            path = PathType.存护,
            element = ElementType.量子
        ),
        createCharacter(
            id = "1008",
            name = "银枝",
            rarity = 5,
            path = PathType.智识,
            element = ElementType.物理
        ),
        createCharacter(
            id = "1009",
            name = "藿藿",
            rarity = 5,
            path = PathType.丰饶,
            element = ElementType.风
        ),
        createCharacter(
            id = "1010",
            name = "驭空",
            rarity = 4,
            path = PathType.同谐,
            element = ElementType.虚数
        ),
        createCharacter(
            id = "1011",
            name = "罗刹",
            rarity = 5,
            path = PathType.丰饶,
            element = ElementType.虚数
        ),
        createCharacter(
            id = "1012",
            name = "白露",
            rarity = 5,
            path = PathType.丰饶,
            element = ElementType.雷
        ),
        createCharacter(
            id = "1013",
            name = "瓦尔特",
            rarity = 5,
            path = PathType.虚无,
            element = ElementType.虚数
        ),
        createCharacter(
            id = "1014",
            name = "姬子",
            rarity = 5,
            path = PathType.智识,
            element = ElementType.火
        ),
        createCharacter(
            id = "1015",
            name = "彦卿",
            rarity = 5,
            path = PathType.巡猎,
            element = ElementType.冰
        ),
        createCharacter(
            id = "1016",
            name = "布洛妮娅",
            rarity = 5,
            path = PathType.同谐,
            element = ElementType.风
        ),
        createCharacter(
            id = "1017",
            name = "杰帕德",
            rarity = 5,
            path = PathType.存护,
            element = ElementType.冰
        ),
        createCharacter(
            id = "1018",
            name = "素裳",
            rarity = 4,
            path = PathType.巡猎,
            element = ElementType.物理
        ),
        createCharacter(
            id = "1019",
            name = "桂子",
            rarity = 4,
            path = PathType.虚无,
            element = ElementType.雷
        ),
        createCharacter(
            id = "1020",
            name = "三月七",
            rarity = 4,
            path = PathType.存护,
            element = ElementType.冰
        ),
        // === 新增强力角色 ===
        createCharacter(
            id = "1021",
            name = "镜流",
            rarity = 5,
            path = PathType.毁灭,
            element = ElementType.冰
        ),
        createCharacter(
            id = "1022",
            name = "托帕",
            rarity = 5,
            path = PathType.巡猎,
            element = ElementType.火
        ),
        createCharacter(
            id = "1023",
            name = "阮梅",
            rarity = 5,
            path = PathType.同谐,
            element = ElementType.冰
        ),
        createCharacter(
            id = "1024",
            name = "黑天鹅",
            rarity = 5,
            path = PathType.虚无,
            element = ElementType.风
        ),
        createCharacter(
            id = "1025",
            name = "花火",
            rarity = 5,
            path = PathType.同谐,
            element = ElementType.量子
        ),
        createCharacter(
            id = "1026",
            name = "知更鸟",
            rarity = 5,
            path = PathType.同谐,
            element = ElementType.物理
        ),
        createCharacter(
            id = "1027",
            name = "黄泉",
            rarity = 5,
            path = PathType.虚无,
            element = ElementType.雷
        ),
        createCharacter(
            id = "1028",
            name = "流萤",
            rarity = 5,
            path = PathType.毁灭,
            element = ElementType.火
        ),
        createCharacter(
            id = "1029",
            name = "波提欧",
            rarity = 5,
            path = PathType.巡猎,
            element = ElementType.物理
        ),
        createCharacter(
            id = "1030",
            name = "翡翠",
            rarity = 5,
            path = PathType.智识,
            element = ElementType.量子
        ),
        // === 第二批补全角色 ===
        createCharacter(
            id = "1031",
            name = "克拉拉",
            rarity = 5,
            path = PathType.毁灭,
            element = ElementType.物理
        ),
        createCharacter(
            id = "1032",
            name = "停云",
            rarity = 4,
            path = PathType.同谐,
            element = ElementType.雷
        ),
        createCharacter(
            id = "1033",
            name = "佩拉",
            rarity = 4,
            path = PathType.虚无,
            element = ElementType.冰
        ),
        createCharacter(
            id = "1034",
            name = "艾丝妲",
            rarity = 4,
            path = PathType.同谐,
            element = ElementType.火
        ),
        createCharacter(
            id = "1035",
            name = "青雀",
            rarity = 4,
            path = PathType.智识,
            element = ElementType.量子
        ),
        createCharacter(
            id = "1036",
            name = "娜塔莎",
            rarity = 4,
            path = PathType.丰饶,
            element = ElementType.物理
        ),
        createCharacter(
            id = "1037",
            name = "真理医生",
            rarity = 5,
            path = PathType.巡猎,
            element = ElementType.虚数
        ),
        createCharacter(
            id = "1038",
            name = "砂金",
            rarity = 5,
            path = PathType.存护,
            element = ElementType.虚数
        ),
        createCharacter(
            id = "1039",
            name = "飞霄",
            rarity = 5,
            path = PathType.巡猎,
            element = ElementType.风
        ),
        createCharacter(
            id = "1040",
            name = "灵砂",
            rarity = 5,
            path = PathType.丰饶,
            element = ElementType.火
        )
    )
    
    private fun createCharacter(
        id: String,
        name: String,
        rarity: Int,
        path: PathType,
        element: ElementType
    ): Character {
        val baseStats = getBaseStatsForCharacter(name)
        return Character(
            id = id,
            name = name,
            rarity = rarity,
            path = path,
            element = element,
            baseStats = baseStats,
            ascensionStats = createAscensionStats(),
            skills = createSkillsForCharacter(name),
            traces = createTraces(),
            eidolons = createEidolons(),
            resonance = null
        )
    }
    
    private fun getBaseStatsForCharacter(name: String): BaseStats {
        // 根据角色定位设定基础属性
        return when {
            name.contains("刃") -> BaseStats(hp = 1076.8, attack = 446.4, defense = 364.8, speed = 105.0, critRate = 0.05, critDmg = 0.50)
            name.contains("希儿") -> BaseStats(hp = 874.2, attack = 776.4, defense = 437.8, speed = 109.0, critRate = 0.05, critDmg = 0.50)
            name.contains("银狼") -> BaseStats(hp = 986.8, attack = 564.6, defense = 485.6, speed = 103.0, critRate = 0.05, critDmg = 0.50)
            name.contains("饮月") -> BaseStats(hp = 921.4, attack = 699.6, defense = 443.4, speed = 107.0, critRate = 0.05, critDmg = 0.50)
            name.contains("景元") -> BaseStats(hp = 948.6, attack = 635.2, defense = 462.2, speed = 102.0, critRate = 0.05, critDmg = 0.50)
            name.contains("罗刹") -> BaseStats(hp = 1105.2, attack = 423.2, defense = 520.8, speed = 99.0, critRate = 0.05, critDmg = 0.50)
            name.contains("符玄") -> BaseStats(hp = 1299.8, attack = 384.8, defense = 582.4, speed = 98.0, critRate = 0.05, critDmg = 0.50)
            name.contains("布洛妮娅") -> BaseStats(hp = 907.2, attack = 499.8, defense = 408.6, speed = 101.0, critRate = 0.05, critDmg = 0.50)
            name.contains("姬子") -> BaseStats(hp = 867.6, attack = 756.6, defense = 396.4, speed = 96.0, critRate = 0.05, critDmg = 0.50)
            name.contains("彦卿") -> BaseStats(hp = 844.6, attack = 799.8, defense = 381.4, speed = 112.0, critRate = 0.05, critDmg = 0.50)
            name.contains("镜流") -> BaseStats(hp = 1045.2, attack = 762.8, defense = 418.6, speed = 102.0, critRate = 0.12, critDmg = 0.50)
            name.contains("托帕") -> BaseStats(hp = 893.4, attack = 720.6, defense = 412.4, speed = 110.0, critRate = 0.05, critDmg = 0.50)
            name.contains("阮梅") -> BaseStats(hp = 912.6, attack = 523.4, defense = 456.8, speed = 106.0, critRate = 0.05, critDmg = 0.50)
            name.contains("黑天鹅") -> BaseStats(hp = 956.8, attack = 591.2, defense = 478.6, speed = 102.0, critRate = 0.05, critDmg = 0.50)
            name.contains("花火") -> BaseStats(hp = 878.4, attack = 512.6, defense = 423.8, speed = 101.0, critRate = 0.05, critDmg = 0.50)
            name.contains("知更鸟") -> BaseStats(hp = 924.6, attack = 542.8, defense = 446.2, speed = 104.0, critRate = 0.05, critDmg = 0.50)
            name.contains("黄泉") -> BaseStats(hp = 1087.2, attack = 698.4, defense = 472.6, speed = 101.0, critRate = 0.05, critDmg = 0.72)
            name.contains("流萤") -> BaseStats(hp = 1123.4, attack = 478.2, defense = 524.8, speed = 104.0, critRate = 0.05, critDmg = 0.50)
            name.contains("波提欧") -> BaseStats(hp = 867.8, attack = 734.2, defense = 398.4, speed = 108.0, critRate = 0.05, critDmg = 0.50)
            name.contains("翡翠") -> BaseStats(hp = 902.4, attack = 689.6, defense = 434.2, speed = 97.0, critRate = 0.05, critDmg = 0.50)
            name.contains("克拉拉") -> BaseStats(hp = 1034.5, attack = 654.2, defense = 485.6, speed = 99.0, critRate = 0.05, critDmg = 0.50)
            name.contains("停云") -> BaseStats(hp = 845.6, attack = 476.8, defense = 392.4, speed = 112.0, critRate = 0.05, critDmg = 0.50)
            name.contains("佩拉") -> BaseStats(hp = 876.4, attack = 456.2, defense = 412.8, speed = 105.0, critRate = 0.05, critDmg = 0.50)
            name.contains("艾丝妲") -> BaseStats(hp = 834.6, attack = 523.4, defense = 386.2, speed = 106.0, critRate = 0.05, critDmg = 0.50)
            name.contains("青雀") -> BaseStats(hp = 845.8, attack = 612.4, defense = 398.6, speed = 98.0, critRate = 0.05, critDmg = 0.50)
            name.contains("娜塔莎") -> BaseStats(hp = 1002.4, attack = 356.8, defense = 468.2, speed = 99.0, critRate = 0.05, critDmg = 0.50)
            name.contains("真理医生") -> BaseStats(hp = 892.6, attack = 743.8, defense = 416.4, speed = 103.0, critRate = 0.12, critDmg = 0.50)
            name.contains("砂金") -> BaseStats(hp = 1246.8, attack = 342.6, defense = 564.2, speed = 94.0, critRate = 0.05, critDmg = 0.50)
            name.contains("飞霄") -> BaseStats(hp = 903.4, attack = 756.6, defense = 428.4, speed = 115.0, critRate = 0.05, critDmg = 0.50)
            name.contains("灵砂") -> BaseStats(hp = 1089.2, attack = 412.4, defense = 502.8, speed = 98.0, critRate = 0.05, critDmg = 0.50)
            else -> BaseStats(hp = 900.0, attack = 600.0, defense = 400.0, speed = 100.0, critRate = 0.05, critDmg = 0.50)
        }
    }
    
    private fun createAscensionStats(): Map<Int, AscensionData> {
        return (0..6).associateWith { level ->
            AscensionData(
                level = level * 10,
                hp = level * 50.0,
                attack = level * 20.0,
                defense = level * 15.0,
                speed = level * 2.0
            )
        }
    }
    
    private fun createSkillsForCharacter(name: String): List<Skill> {
        return listOf(
            Skill(
                id = "${name.lowercase()}_basic",
                type = SkillType.BASIC,
                name = "普攻",
                description = "造成物理伤害",
                scaling = listOf(ScalingEntry(StatType.ATK, 1.0)),
                energyGain = 20
            ),
            Skill(
                id = "${name.lowercase()}_skill",
                type = SkillType.SKILL,
                name = "战技",
                description = "造成伤害",
                scaling = listOf(ScalingEntry(StatType.ATK, 2.0)),
                energyGain = 30
            ),
            Skill(
                id = "${name.lowercase()}_ultimate",
                type = SkillType.ULTIMATE,
                name = "终结技",
                description = "造成大量伤害",
                scaling = listOf(ScalingEntry(StatType.ATK, 4.0)),
                energyCost = 100
            ),
            Skill(
                id = "${name.lowercase()}_talent",
                type = SkillType.TALENT,
                name = "天赋",
                description = "被动效果",
                scaling = emptyList(),
                energyGain = 0
            ),
            Skill(
                id = "${name.lowercase()}_technique",
                type = SkillType.TECHNIQUE,
                name = "秘技",
                description = "战斗开始前生效",
                scaling = emptyList(),
                energyGain = 0
            )
        )
    }
    
    private fun createTraces(): List<Trace> {
        return listOf(
            Trace("trace_1", "行迹A", "攻击力提升", 1, false),
            Trace("trace_2", "行迹B", "防御力提升", 2, false),
            Trace("trace_3", "行迹C", "生命值提升", 3, false)
        )
    }
    
    private fun createEidolons(): List<Eidolon> {
        return (1..6).map { rank ->
            Eidolon(
                rank = rank,
                name = "星魂 $rank",
                description = "解锁星魂 $rank",
                effects = listOf(
                    Effect(
                        type = EffectType.ATK_UP,
                        source = "星魂$rank",
                        value = rank * 0.05,
                        valueType = ValueType.PERCENT
                    )
                )
            )
        }
    }
    
    /** 创建光锥数据（32个真实光锥） */
    private fun createLightCones(): List<LightCone> = listOf(
        // ===== 五星限定光锥 =====
        createLightCone("lc_1001", "于夜色中", 5, PathType.巡猎, EffectType.CRIT_RATE_UP, 0.18),
        createLightCone("lc_1002", "拂晓之前", 5, PathType.智识, EffectType.CRIT_DMG_UP, 0.36),
        createLightCone("lc_1003", "但战斗还未结束", 5, PathType.同谐, EffectType.ENERGY_REGEN_UP, 0.10),
        createLightCone("lc_1004", "银河铁道之夜", 5, PathType.智识, EffectType.ATK_UP, 0.24),
        createLightCone("lc_1005", "记一位星神的陨落", 5, PathType.毁灭, EffectType.ATK_UP, 0.24),
        createLightCone("lc_1006", "无可取代的东西", 5, PathType.毁灭, EffectType.CRIT_DMG_UP, 0.48),
        createLightCone("lc_1007", "如泥酣眠", 5, PathType.巡猎, EffectType.CRIT_DMG_UP, 0.36),
        createLightCone("lc_1008", "此身为剑", 5, PathType.毁灭, EffectType.CRIT_DMG_UP, 0.42),
        createLightCone("lc_1009", "制胜的瞬间", 5, PathType.存护, EffectType.DEF_UP, 0.36),
        createLightCone("lc_1010", "时节不居", 5, PathType.丰饶, EffectType.HP_UP, 0.36),
        createLightCone("lc_1011", "棺的回响", 5, PathType.丰饶, EffectType.ATK_UP, 0.24),
        createLightCone("lc_1012", "到不了的彼岸", 5, PathType.毁灭, EffectType.CRIT_RATE_UP, 0.18),
        createLightCone("lc_1013", "此间得自在", 5, PathType.巡猎, EffectType.CRIT_RATE_UP, 0.18),
        createLightCone("lc_1014", "阳光永照之处", 5, PathType.同谐, EffectType.DMG_UP, 0.24),
        createLightCone("lc_1015", "雨一直下", 5, PathType.虚无, EffectType.EFFECT_HIT_UP, 0.24),
        createLightCone("lc_1016", "最后的赢家", 5, PathType.巡猎, EffectType.CRIT_RATE_UP, 0.18),
        createLightCone("lc_1017", "天才们的休憩", 5, PathType.智识, EffectType.ATK_UP, 0.24),
        createLightCone("lc_1018", "片刻，留在眼底", 5, PathType.智识, EffectType.CRIT_DMG_UP, 0.36),
        // ===== 四星光锥 =====
        createLightCone("lc_1019", "晚安与睡颜", 4, PathType.虚无, EffectType.DMG_UP, 0.24),
        createLightCone("lc_1020", "舞！舞！舞！", 4, PathType.同谐, EffectType.SPD_UP, 0.08),
        createLightCone("lc_1021", "镂月裁云之意", 4, PathType.同谐, EffectType.ATK_UP, 0.16),
        createLightCone("lc_1022", "行星相会", 4, PathType.同谐, EffectType.DMG_UP, 0.24),
        createLightCone("lc_1023", "别让世界静下来", 4, PathType.智识, EffectType.ENERGY_REGEN_UP, 0.08),
        createLightCone("lc_1024", "今日亦是和平的一日", 4, PathType.智识, EffectType.ATK_UP, 0.16),
        createLightCone("lc_1025", "秘密誓心", 4, PathType.毁灭, EffectType.ATK_UP, 0.20),
        createLightCone("lc_1026", "鼹鼠党欢迎你", 4, PathType.毁灭, EffectType.ATK_UP, 0.24),
        createLightCone("lc_1027", "论剑", 4, PathType.巡猎, EffectType.DMG_UP, 0.32),
        createLightCone("lc_1028", "唯有沉默", 4, PathType.巡猎, EffectType.ATK_UP, 0.24),
        createLightCone("lc_1029", "春水初生", 4, PathType.巡猎, EffectType.SPD_UP, 0.08),
        createLightCone("lc_1030", "点个关注吧！", 4, PathType.巡猎, EffectType.CRIT_RATE_UP, 0.12),
        createLightCone("lc_1031", "此时恰好", 4, PathType.丰饶, EffectType.HP_UP, 0.24),
        createLightCone("lc_1032", "同一种心情", 4, PathType.丰饶, EffectType.HP_UP, 0.20),
        createLightCone("lc_1033", "我是站长！", 4, PathType.存护, EffectType.DEF_UP, 0.24),
        createLightCone("lc_1034", "余烬", 4, PathType.存护, EffectType.DEF_UP, 0.20),
        createLightCone("lc_1035", "猎物的视线", 4, PathType.虚无, EffectType.EFFECT_HIT_UP, 0.20),
        createLightCone("lc_1036", "决心如汗珠般闪耀", 4, PathType.虚无, EffectType.DEF_PENETRATION, 0.16),
        createLightCone("lc_1037", "延长记号", 4, PathType.虚无, EffectType.BREAK_DMG_UP, 0.28),
        createLightCone("lc_1038", "记忆中的模样", 4, PathType.同谐, EffectType.ENERGY_REGEN_UP, 0.08),
        createLightCone("lc_1039", "鸣弦初闻", 4, PathType.同谐, EffectType.DMG_UP, 0.24),
        createLightCone("lc_1040", "芳华待灼", 4, PathType.毁灭, EffectType.CRIT_DMG_UP, 0.32),
    )
    
    private fun createLightCone(
        id: String,
        name: String,
        rarity: Int,
        path: PathType,
        effectType: EffectType,
        value: Double
    ): LightCone {
        return LightCone(
            id = id,
            name = name,
            rarity = rarity,
            path = path,
            baseStats = BaseStats(hp = 800.0, attack = 500.0, defense = 400.0, speed = 100.0),
            ascensionStats = createAscensionStats(),
            skill = LightConeSkill(
                name = "光锥技能",
                description = "增加 $effectType ${(value * 100).toInt()}%",
                effects = (1..5).map { level ->
                    Effect(
                        type = effectType,
                        source = name,
                        value = value * (1 + level * 0.2),
                        valueType = ValueType.PERCENT
                    )
                }
            ),
            superimposeLevels = (1..5).map { level ->
                SuperimposeData(
                    level = level,
                    effect = Effect(effectType, name, value * level * 0.2, ValueType.PERCENT),
                    description = "精炼$level: ${effectType.name} +${(value * level * 20).toInt()}%"
                )
            }
        )
    }
    
    /** 创建遗器套装数据（使用真实官方名称） */
    private fun createRelicSets(): List<RelicSet> = listOf(
        // ===== 遗器（4件套） =====
        createRelicSet("rs_1001", "野穗伴行的快枪手", RelicSetType.RELIC,
            EffectType.ATK_UP, 0.12, EffectType.SPD_UP, 0.06),
        createRelicSet("rs_1002", "繁星璀璨的天才", RelicSetType.RELIC,
            EffectType.DMG_UP, 0.08, EffectType.ATK_UP, 0.10),
        createRelicSet("rs_1003", "净庭教宗的圣骑士", RelicSetType.RELIC,
            EffectType.DEF_UP, 0.12, EffectType.DEF_UP, 0.12),
        createRelicSet("rs_1004", "密林卧雪的猎人", RelicSetType.RELIC,
            EffectType.DMG_UP, 0.10, EffectType.CRIT_DMG_UP, 0.10),
        createRelicSet("rs_1005", "街头出身的拳王", RelicSetType.RELIC,
            EffectType.DMG_UP, 0.10, EffectType.ATK_UP, 0.10),
        createRelicSet("rs_1006", "戍卫风雪的铁卫", RelicSetType.RELIC,
            EffectType.HP_UP, 0.12, EffectType.DEF_UP, 0.12),
        createRelicSet("rs_1007", "熔岩锻铸的火匠", RelicSetType.RELIC,
            EffectType.DMG_UP, 0.10, EffectType.ATK_UP, 0.10),
        createRelicSet("rs_1008", "激奏雷电的乐队", RelicSetType.RELIC,
            EffectType.DMG_UP, 0.10, EffectType.ATK_UP, 0.10),
        createRelicSet("rs_1009", "翔鹰高飞的塔尖", RelicSetType.RELIC,
            EffectType.DMG_UP, 0.10, EffectType.SPD_UP, 0.06),
        createRelicSet("rs_1010", "流星追迹的怪盗", RelicSetType.RELIC,
            EffectType.BREAK_DMG_UP, 0.16, EffectType.SPD_UP, 0.06),
        createRelicSet("rs_1011", "盗匪荒漠的废土客", RelicSetType.RELIC,
            EffectType.DMG_UP, 0.10, EffectType.ATK_UP, 0.10),
        createRelicSet("rs_1012", "宝命长存的莳者", RelicSetType.RELIC,
            EffectType.HP_UP, 0.12, EffectType.CRIT_RATE_UP, 0.08),
        createRelicSet("rs_1013", "骇域漫游的信使", RelicSetType.RELIC,
            EffectType.SPD_UP, 0.06, EffectType.SPD_UP, 0.06),
        createRelicSet("rs_1014", "毁烬焚骨的大公", RelicSetType.RELIC,
            EffectType.DMG_UP, 0.10, EffectType.CRIT_DMG_UP, 0.10),
        createRelicSet("rs_1015", "幽锁深牢的系囚", RelicSetType.RELIC,
            EffectType.ATK_UP, 0.12, EffectType.DEF_PENETRATION, 0.08),
        createRelicSet("rs_1016", "死水深潜的先驱", RelicSetType.RELIC,
            EffectType.DMG_UP, 0.10, EffectType.CRIT_DMG_UP, 0.10),
        createRelicSet("rs_1017", "机梦筑梦的钟表匠", RelicSetType.RELIC,
            EffectType.BREAK_DMG_UP, 0.16, EffectType.SPD_UP, 0.06),
        createRelicSet("rs_1018", "荡除蠹灾的铁骑", RelicSetType.RELIC,
            EffectType.BREAK_DMG_UP, 0.16, EffectType.DEF_PENETRATION, 0.10),
        createRelicSet("rs_1019", "风举云飞的勇烈", RelicSetType.RELIC,
            EffectType.CRIT_RATE_UP, 0.08, EffectType.DMG_UP, 0.10),
        createRelicSet("rs_1020", "重负试炼的祭司", RelicSetType.RELIC,
            EffectType.SPD_UP, 0.06, EffectType.DMG_UP, 0.10),
        createRelicSet("rs_1021", "识海迷坠的学者", RelicSetType.RELIC,
            EffectType.CRIT_RATE_UP, 0.08, EffectType.DMG_UP, 0.10),
        createRelicSet("rs_1022", "凯歌祝捷的豪侠", RelicSetType.RELIC,
            EffectType.ATK_UP, 0.12, EffectType.CRIT_DMG_UP, 0.10),
        createRelicSet("rs_1023", "哀悼残灰的诗人", RelicSetType.RELIC,
            EffectType.DMG_UP, 0.10, EffectType.DMG_UP, 0.10),
        // ===== 位面饰品（2件套） =====
        createRelicSet("rs_2001", "太空封印站", RelicSetType.ORNAMENT,
            EffectType.ATK_UP, 0.12, EffectType.SPD_UP, 0.06),
        createRelicSet("rs_2002", "不老者的仙舟", RelicSetType.ORNAMENT,
            EffectType.HP_UP, 0.12, EffectType.ATK_UP, 0.08),
        createRelicSet("rs_2003", "泛银河商业公司", RelicSetType.ORNAMENT,
            EffectType.EFFECT_HIT_UP, 0.10, EffectType.ATK_UP, 0.10),
        createRelicSet("rs_2004", "筑城者的贝洛伯格", RelicSetType.ORNAMENT,
            EffectType.DEF_UP, 0.15, EffectType.EFFECT_RES_UP, 0.10),
        createRelicSet("rs_2005", "星体差分机", RelicSetType.ORNAMENT,
            EffectType.CRIT_DMG_UP, 0.16, EffectType.ATK_UP, 0.08),
        createRelicSet("rs_2006", "停转的萨尔索图", RelicSetType.ORNAMENT,
            EffectType.CRIT_RATE_UP, 0.08, EffectType.DMG_UP, 0.12),
        createRelicSet("rs_2007", "盗贼公国塔利亚", RelicSetType.ORNAMENT,
            EffectType.BREAK_DMG_UP, 0.16, EffectType.SPD_UP, 0.06),
        createRelicSet("rs_2008", "生命的翁瓦克", RelicSetType.ORNAMENT,
            EffectType.ENERGY_REGEN_UP, 0.05, EffectType.SPD_UP, 0.06),
        createRelicSet("rs_2009", "繁星竞技场", RelicSetType.ORNAMENT,
            EffectType.CRIT_RATE_UP, 0.08, EffectType.DMG_UP, 0.12),
        createRelicSet("rs_2010", "折断的龙骨", RelicSetType.ORNAMENT,
            EffectType.EFFECT_RES_UP, 0.10, EffectType.CRIT_DMG_UP, 0.10),
        createRelicSet("rs_2011", "苍穹战线格拉默", RelicSetType.ORNAMENT,
            EffectType.ATK_UP, 0.12, EffectType.DMG_UP, 0.10),
        createRelicSet("rs_2012", "梦想之地匹诺康尼", RelicSetType.ORNAMENT,
            EffectType.ENERGY_REGEN_UP, 0.05, EffectType.DMG_UP, 0.10),
        createRelicSet("rs_2013", "无主荒星茨冈尼亚", RelicSetType.ORNAMENT,
            EffectType.CRIT_RATE_UP, 0.08, EffectType.CRIT_DMG_UP, 0.10),
        createRelicSet("rs_2014", "出云显世与高天神国", RelicSetType.ORNAMENT,
            EffectType.ATK_UP, 0.12, EffectType.CRIT_RATE_UP, 0.08),
        createRelicSet("rs_2015", "奔狼的都蓝王朝", RelicSetType.ORNAMENT,
            EffectType.DMG_UP, 0.10, EffectType.DMG_UP, 0.10),
        createRelicSet("rs_2016", "劫火莲灯铸造的炼狱", RelicSetType.ORNAMENT,
            EffectType.BREAK_DMG_UP, 0.16, EffectType.DMG_UP, 0.10),
        createRelicSet("rs_2017", "沉入海域的露莎卡", RelicSetType.ORNAMENT,
            EffectType.ENERGY_REGEN_UP, 0.05, EffectType.ATK_UP, 0.08),
        createRelicSet("rs_2018", "奇想蕉乐园", RelicSetType.ORNAMENT,
            EffectType.CRIT_DMG_UP, 0.16, EffectType.DMG_UP, 0.10)
    )
    
    private fun createRelicSet(
        id: String,
        name: String,
        type: RelicSetType,
        twoSetEffectType: EffectType,
        twoSetValue: Double,
        fourSetEffectType: EffectType,
        fourSetValue: Double
    ): RelicSet {
        return RelicSet(
            id = id,
            name = name,
            setBonuses = listOf(
                SetBonus(
                    requiredCount = 2,
                    effects = listOf(
                        Effect(twoSetEffectType, name, twoSetValue, ValueType.PERCENT)
                    )
                ),
                SetBonus(
                    requiredCount = 4,
                    effects = listOf(
                        Effect(fourSetEffectType, name, fourSetValue, ValueType.PERCENT)
                    )
                )
            ),
            type = type,
            recommendedStats = mapOf(
                RelicSlot.头部 to listOf(StatType.HP),
                RelicSlot.手部 to listOf(StatType.ATK)
            )
        )
    }
    
    /** 创建敌人数据 */
    private fun createEnemies(): List<Enemy> = listOf(
        // ===== 周常Boss =====
        Enemy(
            id = "enemy_001",
            name = "可可利亚",
            level = 85,
            toughness = 300,
            weakness = listOf(ElementType.物理, ElementType.雷, ElementType.虚数),
            resistance = mapOf(
                ElementType.物理 to 0.20, ElementType.火 to 0.20, ElementType.冰 to 0.20,
                ElementType.雷 to 0.10, ElementType.风 to 0.20, ElementType.虚数 to 0.10, ElementType.量子 to 0.20
            ),
            stats = BaseStats(hp = 80000.0, attack = 800.0, defense = 1200.0, speed = 90.0),
            debuffResistance = 0.30, location = "混沌回忆"
        ),
        Enemy(
            id = "enemy_002",
            name = "末日兽",
            level = 80, toughness = 250,
            weakness = listOf(ElementType.物理, ElementType.雷),
            resistance = mapOf(ElementType.物理 to 0.20, ElementType.雷 to 0.10),
            stats = BaseStats(hp = 60000.0, attack = 700.0, defense = 1000.0, speed = 85.0),
            debuffResistance = 0.25, location = "混沌12层"
        ),
        // ===== 混沌精英 =====
        Enemy(
            id = "enemy_003",
            name = "银衣剑士",
            level = 75, toughness = 180,
            weakness = listOf(ElementType.冰, ElementType.火),
            resistance = mapOf(ElementType.冰 to 0.20, ElementType.火 to 0.20),
            stats = BaseStats(hp = 35000.0, attack = 600.0, defense = 800.0, speed = 95.0),
            debuffResistance = 0.20, location = "混沌10层"
        ),
        Enemy(
            id = "enemy_004",
            name = "丰饶玄鹿",
            level = 80, toughness = 240,
            weakness = listOf(ElementType.火, ElementType.雷, ElementType.风),
            resistance = mapOf(ElementType.物理 to 0.20, ElementType.冰 to 0.20),
            stats = BaseStats(hp = 50000.0, attack = 650.0, defense = 900.0, speed = 80.0),
            debuffResistance = 0.25, location = "模拟宇宙"
        ),
        Enemy(
            id = "enemy_005",
            name = "史瓦罗",
            level = 82, toughness = 280,
            weakness = listOf(ElementType.火, ElementType.冰, ElementType.虚数),
            resistance = mapOf(ElementType.物理 to 0.30, ElementType.雷 to 0.20),
            stats = BaseStats(hp = 65000.0, attack = 750.0, defense = 1100.0, speed = 82.0),
            debuffResistance = 0.30, location = "混沌回忆"
        ),
        Enemy(
            id = "enemy_006",
            name = "卡芙卡（周本）",
            level = 85, toughness = 320,
            weakness = listOf(ElementType.物理, ElementType.雷, ElementType.风),
            resistance = mapOf(ElementType.火 to 0.30, ElementType.冰 to 0.30, ElementType.雷 to 0.10),
            stats = BaseStats(hp = 75000.0, attack = 850.0, defense = 1000.0, speed = 95.0),
            debuffResistance = 0.35, location = "混沌12层"
        ),
        Enemy(
            id = "enemy_007",
            name = "布洛妮娅（周本）",
            level = 83, toughness = 260,
            weakness = listOf(ElementType.火, ElementType.虚数, ElementType.风),
            resistance = mapOf(ElementType.冰 to 0.30, ElementType.雷 to 0.20, ElementType.物理 to 0.20),
            stats = BaseStats(hp = 55000.0, attack = 700.0, defense = 850.0, speed = 105.0),
            debuffResistance = 0.30, location = "混沌11层"
        ),
        // ===== 普通精英 =====
        Enemy(
            id = "enemy_008",
            name = "金人司阍",
            level = 78, toughness = 200,
            weakness = listOf(ElementType.冰, ElementType.雷, ElementType.虚数),
            resistance = mapOf(ElementType.物理 to 0.10, ElementType.火 to 0.10),
            stats = BaseStats(hp = 38000.0, attack = 620.0, defense = 950.0, speed = 92.0),
            debuffResistance = 0.20, location = "金人巷"
        ),
        Enemy(
            id = "enemy_009",
            name = "蚕食者之影",
            level = 77, toughness = 190,
            weakness = listOf(ElementType.火, ElementType.风, ElementType.虚数),
            resistance = mapOf(ElementType.冰 to 0.20, ElementType.雷 to 0.20),
            stats = BaseStats(hp = 32000.0, attack = 580.0, defense = 750.0, speed = 88.0),
            debuffResistance = 0.15, location = "模拟宇宙"
        ),
        Enemy(
            id = "enemy_010",
            name = "兴风者",
            level = 79, toughness = 220,
            weakness = listOf(ElementType.物理, ElementType.冰, ElementType.雷),
            resistance = mapOf(ElementType.火 to 0.20, ElementType.风 to 0.20),
            stats = BaseStats(hp = 42000.0, attack = 680.0, defense = 880.0, speed = 96.0),
            debuffResistance = 0.20, location = "混沌10层"
        ),
        // ===== 模拟宇宙Boss =====
        Enemy(
            id = "enemy_011",
            name = "虫群·真蛰虫",
            level = 82, toughness = 300,
            weakness = listOf(ElementType.火, ElementType.雷, ElementType.风),
            resistance = mapOf(ElementType.物理 to 0.30, ElementType.量子 to 0.30),
            stats = BaseStats(hp = 70000.0, attack = 780.0, defense = 1050.0, speed = 75.0),
            debuffResistance = 0.30, location = "模拟宇宙"
        ),
        Enemy(
            id = "enemy_012",
            name = "宇宙冰",
            level = 76, toughness = 170,
            weakness = listOf(ElementType.火, ElementType.雷, ElementType.虚数),
            resistance = mapOf(ElementType.冰 to 0.40, ElementType.风 to 0.20),
            stats = BaseStats(hp = 28000.0, attack = 550.0, defense = 700.0, speed = 85.0),
            debuffResistance = 0.15, location = "模拟宇宙"
        ),
        Enemy(
            id = "enemy_013",
            name = "回忆之刃",
            level = 80, toughness = 200,
            weakness = listOf(ElementType.物理, ElementType.雷, ElementType.虚数),
            resistance = mapOf(ElementType.火 to 0.20, ElementType.风 to 0.20),
            stats = BaseStats(hp = 45000.0, attack = 720.0, defense = 850.0, speed = 100.0),
            debuffResistance = 0.25, location = "混沌12层"
        ),
        Enemy(
            id = "enemy_014",
            name = "深寒徘徊者",
            level = 78, toughness = 180,
            weakness = listOf(ElementType.火, ElementType.雷, ElementType.虚数),
            resistance = mapOf(ElementType.冰 to 0.40),
            stats = BaseStats(hp = 35000.0, attack = 600.0, defense = 800.0, speed = 90.0),
            debuffResistance = 0.20, location = "模拟宇宙"
        ),
        Enemy(
            id = "enemy_015",
            name = "守护者之影",
            level = 80, toughness = 230,
            weakness = listOf(ElementType.火, ElementType.冰, ElementType.风),
            resistance = mapOf(ElementType.雷 to 0.30, ElementType.物理 to 0.20),
            stats = BaseStats(hp = 48000.0, attack = 680.0, defense = 920.0, speed = 86.0),
            debuffResistance = 0.25, location = "混沌11层"
        ),
        // ===== 新增：模拟宇宙精英 =====
        Enemy(
            id = "enemy_016",
            name = "扑满",
            level = 70, toughness = 60,
            weakness = listOf(ElementType.物理, ElementType.雷, ElementType.风, ElementType.虚数),
            resistance = emptyMap(),
            stats = BaseStats(hp = 15000.0, attack = 400.0, defense = 500.0, speed = 120.0),
            debuffResistance = 0.0, location = "模拟宇宙"
        ),
        Enemy(
            id = "enemy_017",
            name = "自动机兵·齿狼",
            level = 78, toughness = 200,
            weakness = listOf(ElementType.冰, ElementType.雷, ElementType.虚数),
            resistance = mapOf(ElementType.物理 to 0.20, ElementType.火 to 0.20),
            stats = BaseStats(hp = 40000.0, attack = 620.0, defense = 880.0, speed = 94.0),
            debuffResistance = 0.20, location = "模拟宇宙"
        ),
        Enemy(
            id = "enemy_018",
            name = "炽燃徘徊者",
            level = 79, toughness = 190,
            weakness = listOf(ElementType.物理, ElementType.冰, ElementType.虚数),
            resistance = mapOf(ElementType.火 to 0.40, ElementType.风 to 0.20),
            stats = BaseStats(hp = 36000.0, attack = 700.0, defense = 800.0, speed = 98.0),
            debuffResistance = 0.15, location = "模拟宇宙"
        ),
        // ===== 新增：混沌精英 =====
        Enemy(
            id = "enemy_019",
            name = "云骑骁卫·彦卿",
            level = 83, toughness = 280,
            weakness = listOf(ElementType.雷, ElementType.风, ElementType.量子),
            resistance = mapOf(ElementType.冰 to 0.40, ElementType.物理 to 0.20),
            stats = BaseStats(hp = 55000.0, attack = 780.0, defense = 1000.0, speed = 108.0),
            debuffResistance = 0.30, location = "混沌12层"
        ),
        Enemy(
            id = "enemy_020",
            name = "药王秘传·炼形者",
            level = 80, toughness = 240,
            weakness = listOf(ElementType.火, ElementType.雷, ElementType.风),
            resistance = mapOf(ElementType.物理 to 0.20, ElementType.虚数 to 0.20),
            stats = BaseStats(hp = 50000.0, attack = 650.0, defense = 950.0, speed = 82.0),
            debuffResistance = 0.25, location = "混沌10层"
        ),
        // ===== 新增：周常Boss =====
        Enemy(
            id = "enemy_021",
            name = "幻胧",
            level = 85, toughness = 350,
            weakness = listOf(ElementType.火, ElementType.雷, ElementType.风, ElementType.虚数),
            resistance = mapOf(ElementType.物理 to 0.40, ElementType.冰 to 0.40, ElementType.量子 to 0.30),
            stats = BaseStats(hp = 90000.0, attack = 900.0, defense = 1300.0, speed = 78.0),
            debuffResistance = 0.40, location = "周本"
        ),
        Enemy(
            id = "enemy_022",
            name = "碎星王虫·斯喀拉卡巴兹",
            level = 84, toughness = 320,
            weakness = listOf(ElementType.火, ElementType.雷, ElementType.风),
            resistance = mapOf(ElementType.物理 to 0.30, ElementType.量子 to 0.30, ElementType.虚数 to 0.20),
            stats = BaseStats(hp = 85000.0, attack = 850.0, defense = 1150.0, speed = 72.0),
            debuffResistance = 0.35, location = "模拟宇宙"
        ),
        // ===== 新增：活动精英 =====
        Enemy(
            id = "enemy_023",
            name = "惊梦剧团的弹簧荷官",
            level = 77, toughness = 180,
            weakness = listOf(ElementType.物理, ElementType.冰, ElementType.虚数),
            resistance = mapOf(ElementType.雷 to 0.20, ElementType.火 to 0.20),
            stats = BaseStats(hp = 34000.0, attack = 580.0, defense = 780.0, speed = 100.0),
            debuffResistance = 0.15, location = "匹诺康尼"
        ),
        Enemy(
            id = "enemy_024",
            name = "忆域迷因·狂怒褪去之壳",
            level = 81, toughness = 260,
            weakness = listOf(ElementType.物理, ElementType.火, ElementType.风),
            resistance = mapOf(ElementType.雷 to 0.30, ElementType.冰 to 0.20, ElementType.虚数 to 0.20),
            stats = BaseStats(hp = 52000.0, attack = 720.0, defense = 1050.0, speed = 88.0),
            debuffResistance = 0.25, location = "匹诺康尼"
        ),
        Enemy(
            id = "enemy_025",
            name = "忆域迷因·何物朝向死亡",
            level = 82, toughness = 300,
            weakness = listOf(ElementType.火, ElementType.雷, ElementType.量子),
            resistance = mapOf(ElementType.物理 to 0.30, ElementType.风 to 0.30, ElementType.虚数 to 0.20),
            stats = BaseStats(hp = 58000.0, attack = 800.0, defense = 1100.0, speed = 92.0),
            debuffResistance = 0.30, location = "匹诺康尼"
        )
    )
}
