package com.starrail.agent.core.datasource

import com.starrail.agent.core.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InMemoryGameDataSourceTest {

    private lateinit var dataSource: InMemoryGameDataSource

    @Before
    fun setup() {
        dataSource = InMemoryGameDataSource()
    }

    @Test
    fun testCharacterCount() {
        assertEquals("应有40名角色", 40, dataSource.getAllCharacters().size)
    }

    @Test
    fun testGetCharacterById() {
        val seele = dataSource.getCharacterById("1002")
        assertNotNull("希儿应存在", seele)
        assertEquals("希儿", seele!!.name)
        assertEquals(PathType.巡猎, seele.path)
        assertEquals(ElementType.量子, seele.element)
    }

    @Test
    fun testGetCharacterById_notFound() {
        assertNull(dataSource.getCharacterById("9999"))
    }

    @Test
    fun testAllCharacters_haveBaseStats() {
        for (char in dataSource.getAllCharacters()) {
            assertTrue("${char.name} HP应>0", char.baseStats.hp > 0)
            assertTrue("${char.name} ATK应>0", char.baseStats.attack > 0)
        }
    }

    @Test
    fun testLightConeCount() {
        assertEquals("光锥总数", 40, dataSource.getAllLightCones().size)
    }

    @Test
    fun testGetLightConeById() {
        val lc = dataSource.getLightConeById("lc_1002")
        assertNotNull(lc)
        assertEquals("拂晓之前", lc!!.name)
        assertEquals(5, lc.rarity)
    }

    @Test
    fun testAllLightCones_have5Superimpose() {
        for (lc in dataSource.getAllLightCones()) {
            assertEquals("${lc.name} 应有5级精炼", 5, lc.superimposeLevels.size)
        }
    }

    @Test
    fun testRelicSetCount() {
        val count = dataSource.getAllRelicSets().size
        assertTrue("遗器套数应>0, 实际:$count", count > 0)
    }

    @Test
    fun testRelicSetTypeCounts() {
        val relics = dataSource.getRelicSetsByType(RelicSetType.RELIC).size
        val ornaments = dataSource.getRelicSetsByType(RelicSetType.ORNAMENT).size
        assertTrue("遗器应>0, 实际:$relics", relics > 0)
        assertTrue("饰品应>0, 实际:$ornaments", ornaments > 0)
    }

    @Test
    fun testSearchCharacter() {
        val results = dataSource.searchCharacters("希")
        assertTrue(results.any { it.name == "希儿" })
    }

    @Test
    fun testSearchLightCone() {
        assertTrue(dataSource.searchLightCones("拂晓").isNotEmpty())
    }

    @Test
    fun testGetEnemyById() {
        val enemy = dataSource.getEnemyById("enemy_001")
        assertNotNull(enemy)
        assertEquals("可可利亚", enemy!!.name)
    }

    @Test
    fun testEnemyCount() {
        assertTrue("敌人应>=25, 实际:" + dataSource.getAllEnemies().size, dataSource.getAllEnemies().size >= 25)
    }

    @Test
    fun testEnemies_haveToughness() {
        for (enemy in dataSource.getAllEnemies()) {
            assertTrue("${enemy.name} 韧性值>0", enemy.toughness > 0)
        }
    }

    @Test
    fun testFiveStarLightCones() {
        val count = dataSource.getAllLightCones().count { it.rarity == 5 }
        assertEquals("五星光锥应有18个", 18, count)
    }

    @Test
    fun testFourStarLightCones() {
        val count = dataSource.getAllLightCones().count { it.rarity == 4 }
        assertTrue("四星光锥应>0, 实际:$count", count > 0)
    }
}