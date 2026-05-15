package com.starrail.agent.player.db

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * FilePlayerDao 单元测试
 * 验证 JSON 文件持久化的 CRUD 正确性
 */
class FilePlayerDaoTest {

    private lateinit var tempDir: File
    private lateinit var dao: FilePlayerDao

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp",
            "starrail_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        // 验证目录可写
        val testFile = File(tempDir, "test_write.txt")
        testFile.writeText("test")
        assertTrue("测试目录应可写", testFile.exists())
        testFile.delete()
        dao = FilePlayerDao(tempDir)
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    // ==================== 角色 CRUD ====================

    @Test
    fun testInsertAndGetCharacter() {
        val char = PlayerCharacterEntity(
            instanceId = "seele_0",
            characterId = "seele",
            level = 80, ascension = 6, eidolon = 0
        )
        dao.insertCharacter(char)

        val loaded = dao.getCharacterById("seele_0")
        assertNotNull("角色应被找到", loaded)
        assertEquals("角色ID正确", "seele", loaded!!.characterId)
        assertEquals("等级正确", 80, loaded.level)
    }

    @Test
    fun testInsertMultipleCharacters() {
        val chars = listOf(
            PlayerCharacterEntity("seele_0", "seele", 80, 6, 0),
            PlayerCharacterEntity("bronya_0", "bronya", 80, 6, 1),
            PlayerCharacterEntity("fuxuan_0", "fuxuan", 70, 5, 0)
        )
        dao.insertCharacters(chars)
        
        val all = dao.getAllCharacters()
        assertEquals("应有3个角色", 3, all.size)
    }

    @Test
    fun testDeleteCharacter() {
        val char = PlayerCharacterEntity("test_1", "test_char", 80, 6, 0)
        dao.insertCharacter(char)
        assertEquals("插入后应有1个", 1, dao.getAllCharacters().size)
        
        dao.deleteCharacter(char)
        assertEquals("删除后应为0个", 0, dao.getAllCharacters().size)
    }

    @Test
    fun testDeleteAllCharacters() {
        dao.insertCharacters(listOf(
            PlayerCharacterEntity("a", "char_a", 80, 6, 0),
            PlayerCharacterEntity("b", "char_b", 80, 6, 0)
        ))
        assertEquals("应有2个", 2, dao.getAllCharacters().size)
        
        dao.deleteAllCharacters()
        assertEquals("清空后应为0", 0, dao.getAllCharacters().size)
    }

    @Test
    fun testGetNonExistentCharacter() {
        val char = dao.getCharacterById("nonexistent")
        assertNull("不存在的角色应返回null", char)
    }

    // ==================== 光锥 CRUD ====================

    @Test
    fun testInsertAndGetLightCone() {
        val lc = PlayerLightConeEntity(
            instanceId = "lc_001",
            lightConeId = "lc_bronya",
            level = 80, ascension = 6, superimpose = 1
        )
        dao.insertLightCone(lc)
        
        val loaded = dao.getLightConeById("lc_001")
        assertNotNull("光锥应被找到", loaded)
        assertEquals("光锥ID正确", "lc_bronya", loaded!!.lightConeId)
    }

    @Test
    fun testInsertMultipleLightCones() {
        dao.insertLightCones(listOf(
            PlayerLightConeEntity("lc1", "lc_a", 80, 6, 1),
            PlayerLightConeEntity("lc2", "lc_b", 70, 5, 2),
            PlayerLightConeEntity("lc3", "lc_c", 80, 6, 5)
        ))
        assertEquals("应有3个光锥", 3, dao.getAllLightCones().size)
        
        val lc3 = dao.getLightConeById("lc3")
        assertEquals("精炼5", 5, lc3!!.superimpose)
    }

    @Test
    fun testDeleteLightCone() {
        val lc = PlayerLightConeEntity("del_lc", "lc_del", 80, 6, 1)
        dao.insertLightCone(lc)
        assertEquals(1, dao.getAllLightCones().size)
        
        dao.deleteLightCone(lc)
        assertEquals(0, dao.getAllLightCones().size)
    }

    @Test
    fun testDeleteAllLightCones() {
        dao.insertLightCones(listOf(
            PlayerLightConeEntity("x", "lc_x", 80, 6, 1),
            PlayerLightConeEntity("y", "lc_y", 80, 6, 3)
        ))
        dao.deleteAllLightCones()
        assertTrue("清空后应为空", dao.getAllLightCones().isEmpty())
    }

    // ==================== 遗器 CRUD ====================

    @Test
    fun testInsertAndGetRelic() {
        val relic = PlayerRelicEntity(
            instanceId = "rel_001",
            relicId = "rl_head_001",
            setId = "rs_musketeer",
            slotName = "头部",
            rarity = 5,
            level = 15,
            mainStatJson = """{"type":"HP","value":705.6}""",
            subStatsJson = """[{"type":"CRIT_RATE","value":0.08},{"type":"ATK_PERCENT","value":0.05}]""",
            subStatIncrementCount = 4
        )
        dao.insertRelic(relic)
        
        val loaded = dao.getRelicById("rel_001")
        assertNotNull("遗器应被找到", loaded)
        assertEquals("稀有度正确", 5, loaded!!.rarity)
        assertEquals("等级正确", 15, loaded.level)
        assertEquals("副词条强化次数", 4, loaded.subStatIncrementCount)
    }

    @Test
    fun testInsertMultipleRelics() {
        dao.insertRelics(listOf(
            PlayerRelicEntity("r1", "rl_1", "rs_1", "头部", 5, 15, "{}"),
            PlayerRelicEntity("r2", "rl_2", "rs_1", "手部", 5, 12, "{}"),
            PlayerRelicEntity("r3", "rl_3", "rs_2", "躯干", 5, 0, """{"type":"CRIT_RATE","value":0.10}""")
        ))
        assertEquals("应有3个遗器", 3, dao.getAllRelics().size)
    }

    @Test
    fun testDeleteRelic() {
        val r = PlayerRelicEntity("del_r", "rl_del", "rs_del", "脚部", 5, 0, "{}")
        dao.insertRelic(r)
        assertEquals(1, dao.getAllRelics().size)
        
        dao.deleteRelic(r)
        assertEquals(0, dao.getAllRelics().size)
    }

    @Test
    fun testDeleteAllRelics() {
        dao.insertRelics(listOf(
            PlayerRelicEntity("a", "rl_a", "rs_a", "头部", 5, 0, "{}"),
            PlayerRelicEntity("b", "rl_b", "rs_b", "手部", 5, 0, "{}")
        ))
        dao.deleteAllRelics()
        assertTrue(dao.getAllRelics().isEmpty())
    }

    @Test
    fun testRelicWithComplexSubStats() {
        val relic = PlayerRelicEntity(
            instanceId = "complex",
            relicId = "rl_complex",
            setId = "rs_quantum",
            slotName = "位面球",
            rarity = 5,
            level = 15,
            mainStatJson = """{"type":"ELEMENTAL_DMG","value":0.388}""",
            subStatsJson = """[
                {"type":"CRIT_RATE","value":0.12},
                {"type":"CRIT_DMG","value":0.15},
                {"type":"SPD","value":4.5},
                {"type":"ATK_PERCENT","value":0.10}
            ]""",
            subStatIncrementCount = 8,
            isEquipped = true,
            equippedByCharacterId = "seele",
            locked = true,
            markName = "KEEP"
        )
        dao.insertRelic(relic)
        
        val loaded = dao.getRelicById("complex")!!
        assertTrue("应已装备", loaded.isEquipped)
        assertEquals("装备给希儿", "seele", loaded.equippedByCharacterId)
        assertTrue("应锁定", loaded.locked)
        assertEquals("标记为KEEP", "KEEP", loaded.markName)
    }

    // ==================== 快照 CRUD ====================

    @Test
    fun testInsertAndGetSnapshot() {
        val snap = PlayerSnapshotEntity(
            id = "snap_001",
            timestamp = System.currentTimeMillis(),
            characterIdsJson = """["seele","bronya"]""",
            lightConeIdsJson = """["lc_1","lc_2"]""",
            relicIdsJson = """["r1","r2","r3"]""",
            metadataJson = """{"version":"1.0"}"""
        )
        dao.insertSnapshot(snap)
        
        val loaded = dao.getLatestSnapshot()
        assertNotNull("快照应存在", loaded)
        assertEquals("ID正确", "snap_001", loaded!!.id)
    }

    @Test
    fun testMultipleSnapshots_latestReturned() {
        dao.insertSnapshot(PlayerSnapshotEntity("s1", 100L))
        dao.insertSnapshot(PlayerSnapshotEntity("s2", 200L))
        dao.insertSnapshot(PlayerSnapshotEntity("s3", 300L))
        
        val latest = dao.getLatestSnapshot()
        assertEquals("最新快照为s3", "s3", latest!!.id)
    }

    @Test
    fun testDeleteAllSnapshots() {
        dao.insertSnapshot(PlayerSnapshotEntity("s1", 100L))
        dao.insertSnapshot(PlayerSnapshotEntity("s2", 200L))
        dao.deleteAllSnapshots()
        assertNull("清空后应返回null", dao.getLatestSnapshot())
    }

    // ==================== 文件持久化验证 ====================

    @Test
    fun testDataSurvivesNewDaoInstance() {
        // 写入数据到文件
        dao.insertCharacter(PlayerCharacterEntity("seele_0", "seele", 80, 6, 0))
        dao.insertLightCone(PlayerLightConeEntity("lc_seele", "in_the_night", 80, 6, 1))
        dao.insertRelic(PlayerRelicEntity("r_seele", "rl_head", "rs_q", "头部", 5, 15, "{}"))
        dao.insertSnapshot(PlayerSnapshotEntity("snap1", 1000L))
        
        // 验证文件确实已写入
        val charFile = File(tempDir, "characters.json")
        assertTrue("角色文件应存在", charFile.exists())
        assertTrue("角色文件应有内容", charFile.length() > 0)
        
        // 新建 DAO 实例（模拟 App 重启）
        val dao2 = FilePlayerDao(tempDir)
        
        assertEquals("重启后角色数一致", 1, dao2.getAllCharacters().size)
        assertEquals("重启后光锥数一致", 1, dao2.getAllLightCones().size)
        assertEquals("重启后遗器数一致", 1, dao2.getAllRelics().size)
        assertNotNull("重启后快照存在", dao2.getLatestSnapshot())
    }

    @Test
    fun testInsertThenLoadPreservesAllFields() {
        // 先验证文件写入
        dao.insertCharacter(PlayerCharacterEntity("seele_0", "seele", 80, 6, 0))
        val fileSize = File(tempDir, "characters.json").length()
        assertTrue("文件应有内容", fileSize > 0)
        
        // 直接验证当前实例能正确读取
        val loaded = dao.getCharacterById("seele_0")!!
        assertEquals("seele", loaded.characterId)
        assertEquals(80, loaded.level)
        
        // 完整字段测试
        dao.insertCharacter(PlayerCharacterEntity(
            instanceId = "seele_0",
            characterId = "seele",
            level = 75,
            ascension = 5,
            eidolon = 2,
            skillLevelsJson = """{"basic":6,"skill":8,"ultimate":8}""",
            equippedLightConeId = "lc_seele",
            equippedRelicsJson = """{"头部":"r1","手部":"r2"}""",
            tracesJson = """["t1","t2","t3"]""",
            isOwned = true
        ))
        
        // 新建 DAO 读取文件验证
        val dao2 = FilePlayerDao(tempDir)
        val reloaded = dao2.getCharacterById("seele_0")
        assertNotNull("新实例应读到数据", reloaded)
        assertEquals("技能数据保留", """{"basic":6,"skill":8,"ultimate":8}""", reloaded!!.skillLevelsJson)
    }

    @Test
    fun testUpdateCharacter_overwritesExisting() {
        val char = PlayerCharacterEntity("seele_0", "seele", 70, 4, 0)
        dao.insertCharacter(char)
        
        // 更新为不同数据
        val updated = char.copy(level = 80, ascension = 6, eidolon = 2)
        dao.insertCharacter(updated) // 同 instanceId 覆盖
        
        val loaded = dao.getCharacterById("seele_0")!!
        assertEquals("等级已更新", 80, loaded.level)
        assertEquals("星魂已更新", 2, loaded.eidolon)
        assertEquals("仍为1个角色", 1, dao.getAllCharacters().size)
    }

    @Test
    fun testEmptyDao_noFilesCreated() {
        // 未写入任何数据就创建 DAO
        val dao2 = FilePlayerDao(tempDir)
        
        assertTrue("角色为空", dao2.getAllCharacters().isEmpty())
        assertTrue("光锥为空", dao2.getAllLightCones().isEmpty())
        assertTrue("遗器为空", dao2.getAllRelics().isEmpty())
        assertNull("快照为null", dao2.getLatestSnapshot())
    }

    @Test
    fun testCorruptedJsonFile_gracefulHandling() {
        // 写入错误格式的 JSON 文件
        File(tempDir, "characters.json").writeText("{{{corrupted json")
        
        // 新建 DAO 应静默处理，返回空集合
        val dao2 = FilePlayerDao(tempDir)
        assertTrue("损坏文件应返回空", dao2.getAllCharacters().isEmpty())
        
        // 后续写入应正常工作
        dao2.insertCharacter(PlayerCharacterEntity("new", "new_char", 80, 6, 0))
        assertEquals("写入仍正常", 1, dao2.getAllCharacters().size)
    }

    @Test
    fun testMixedCrud_operations() {
        // 混合 CRUD：插入角色 → 删除光锥 → 插入遗器 → 验证
        dao.insertCharacter(PlayerCharacterEntity("c1", "char1", 80, 6, 0))
        dao.insertCharacter(PlayerCharacterEntity("c2", "char2", 80, 6, 0))
        dao.insertLightCone(PlayerLightConeEntity("l1", "lc1", 80, 6, 1))
        
        assertEquals("2角色", 2, dao.getAllCharacters().size)
        assertEquals("1光锥", 1, dao.getAllLightCones().size)
        
        dao.deleteLightCone(PlayerLightConeEntity("l1", "lc1", 80, 6, 1))
        dao.deleteCharacter(PlayerCharacterEntity("c1", "char1", 80, 6, 0))
        
        assertEquals("1角色剩余", 1, dao.getAllCharacters().size)
        assertEquals("0光锥剩余", 0, dao.getAllLightCones().size)
        assertEquals("剩余角色ID", "char2", dao.getAllCharacters()[0].characterId)
        
        dao.insertRelic(PlayerRelicEntity("r1", "rl_1", "rs_1", "头部", 5, 0, "{}"))
        assertEquals("1遗器", 1, dao.getAllRelics().size)
    }
}