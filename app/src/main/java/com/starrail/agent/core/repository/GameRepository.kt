package com.starrail.agent.core.repository

import com.starrail.agent.core.model.*

/**
 * 角色数据仓库接口
 * 遵循依赖倒置原则，业务层依赖此接口而非具体实现
 */
interface CharacterRepository {
    /** 获取所有角色 */
    fun getAll(): List<Character>
    
    /** 根据ID获取角色 */
    fun getById(id: String): Character?
    
    /** 根据命途获取角色 */
    fun getByPath(path: PathType): List<Character>
    
    /** 根据属性获取角色 */
    fun getByElement(element: ElementType): List<Character>
    
    /** 搜索角色（支持模糊匹配） */
    fun search(name: String): List<Character>
    
    /** 根据ID列表批量获取角色 */
    fun getByIds(ids: List<String>): List<Character>
}

/**
 * 光锥数据仓库接口
 */
interface LightConeRepository {
    /** 获取所有光锥 */
    fun getAll(): List<LightCone>
    
    /** 根据ID获取光锥 */
    fun getById(id: String): LightCone?
    
    /** 根据命途获取光锥 */
    fun getByPath(path: PathType): List<LightCone>
    
    /** 搜索光锥 */
    fun search(name: String): List<LightCone>
    
    /** 根据ID列表批量获取光锥 */
    fun getByIds(ids: List<String>): List<LightCone>
}

/**
 * 遗器数据仓库接口
 */
interface RelicRepository {
    /** 获取所有遗器套装 */
    fun getAllSets(): List<RelicSet>
    
    /** 根据ID获取遗器套装 */
    fun getSetById(id: String): RelicSet?
    
    /** 根据套装名称搜索 */
    fun searchSets(name: String): List<RelicSet>
    
    /** 获取指定命途/属性的推荐套装 */
    fun getRecommendedSets(path: PathType?, element: ElementType?): List<RelicSet>
    
    /** 获取指定类型的套装（遗器/位面饰品） */
    fun getSetsByType(type: RelicSetType): List<RelicSet>
    
    /** 获取遗器模板 */
    fun getRelicTemplate(setId: String, slot: RelicSlot): RelicTemplate?
    
    /** 获取所有遗器模板 */
    fun getAllRelicTemplates(): List<RelicTemplate>
}

/**
 * 敌人数据仓库接口
 */
interface EnemyRepository {
    /** 获取所有敌人 */
    fun getAll(): List<Enemy>
    
    /** 根据ID获取敌人 */
    fun getById(id: String): Enemy?
    
    /** 根据弱点属性获取敌人 */
    fun getByWeakness(element: ElementType): List<Enemy>
    
    /** 获取指定副本的敌人 */
    fun getByLocation(location: String): List<Enemy>
    
    /** 搜索敌人 */
    fun search(name: String): List<Enemy>
}