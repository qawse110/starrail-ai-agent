# 玩家数据模块设计 — Player Data Module

> **模块定位**：管理玩家拥有的角色、光锥、遗器数据，提供数据导入、同步与查询能力。

---

## 1. 职责与边界

### 单一职责 (SRP)
- 仅负责玩家自有数据的持有、导入与持久化
- 不进行任何分析计算
- 不依赖业务分析模块

### 依赖关系
- 依赖 Core Data Layer 的领域模型
- 被业务模块（战斗模拟/遗器分析/配队对比）依赖

---

## 2. 数据类型与模型

### 2.1 玩家持有角色

```kotlin
data class PlayerCharacter(
    val characterId: String,       // 关联 Character.id
    val level: Int,                // 80/80
    val ascension: Int,            // 突破等级 0-6
    val eidolon: Int,              // 星魂 0-6
    val skillLevels: Map<String, Int>,  // 技能ID → 等级
    val equippedLightConeId: String?,   // 装备的光锥ID
    val equippedRelics: Map<RelicSlot, String>,  // 装备的遗器ID
    val traces: Set<String>,       // 已激活的行迹ID集合
    val isOwned: Boolean           // 是否已拥有
)
```

### 2.2 玩家持有光锥

```kotlin
data class PlayerLightCone(
    val instanceId: String,        // 实例唯一ID（可多条相同光锥）
    val lightConeId: String,       // 关联 LightCone.id
    val level: Int,
    val ascension: Int,
    val superimpose: Int,          // 精炼 1-5
    val isEquipped: Boolean,
    val equippedByCharacterId: String?
)
```

### 2.3 玩家遗器库存

```kotlin
data class PlayerRelic(
    val instanceId: String,
    val relicId: String,           // 关联 Relic.id（模板ID）
    val setId: String,
    val slot: RelicSlot,
    val rarity: Int,
    val level: Int,
    val mainStat: StatEntry,
    val subStats: List<StatEntry>,
    val subStatIncrementCount: Int,
    val isEquipped: Boolean,
    val equippedByCharacterId: String?,
    val locked: Boolean,
    val mark: RelicMark?           // 用户标记（保留/狗粮/待定）
)

enum class RelicMark { KEEP, FODDER, UNDECIDED }
```

### 2.4 玩家数据快照

```kotlin
data class PlayerSnapshot(
    val id: String,
    val timestamp: Long,
    val characters: List<PlayerCharacter>,
    val lightCones: List<PlayerLightCone>,
    val relics: List<PlayerRelic>,
    val metadata: Map<String, String>
)
```

---

## 3. 数据导入途径

### 3.1 Hoyolab 自动导入（推荐）

通过 Hoyolab API 获取账号数据：

```
GET https://api.hoyolab.com/.../character/list
→ 返回角色列表（含等级、星魂、装备光锥/遗器）
```

**流程**：
1. 用户输入 Hoyolab UID 或 Cookie
2. 调用 Hoyolab API 获取角色展柜数据
3. 映射到 PlayerCharacter / PlayerLightCone / PlayerRelic
4. 与本地已有数据进行差异合并

### 3.2 手动录入

- 用户逐一添加/编辑角色、光锥、遗器信息
- 通过扫描截图 OCR 辅助录入（未来可扩展）

### 3.3 JSON 导入

- 支持从 Fribbels HSR Optimizer 等第三方工具导入 JSON
- 支持从扫描工具导入

---

## 4. 数据同步与版本管理

### 4.1 版本策略

```kotlin
interface PlayerDataSyncManager {
    /**
     * 从 Hoyolab 拉取最新数据并与本地合并
     * @return 差异报告（新增/修改/删除项）
     */
    suspend fun syncFromHoyolab(): SyncReport
    
    /**
     * 合并外部导入的数据
     */
    suspend fun mergeImport(data: PlayerSnapshot): SyncReport
    
    /**
     * 获取同步历史
     */
    fun getSyncHistory(): List<SyncRecord>
}

data class SyncReport(
    val addedCharacters: List<String>,
    val updatedCharacters: List<String>,
    val addedRelics: List<String>,
    val updatedRelics: List<String>,
    val removedRelics: List<String>
)
```

### 4.2 冲突处理

- 本地修改优先级高于自动同步（用户手动编辑过的条目标记 `isManuallyEdited`）
- 自动同步时保留用户标记（lock / mark 等）
- 导入数据校验：属性值必须在游戏允许范围内

---

## 5. 持久化方案

```
┌─────────────────────────────────────┐
│         PlayerDataRepository        │ ← 对外接口
├─────────────────────────────────────┤
│  PlayerCharacterDao  (Room)         │
│  PlayerLightConeDao (Room)          │
│  PlayerRelicDao      (Room)         │
├─────────────────────────────────────┤
│  HoyolabRemoteDataSource            │
│  JsonFileDataSource                 │
└─────────────────────────────────────┘
```

使用 Room 本地数据库存储玩家数据：

```kotlin
@Dao
interface PlayerRelicDao {
    @Query("SELECT * FROM player_relics WHERE setId = :setId")
    fun getBySet(setId: String): Flow<List<PlayerRelicEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(relics: List<PlayerRelicEntity>)
    
    @Delete
    suspend fun delete(relic: PlayerRelicEntity)
}
```

---

## 6. 设计原则应用

| 原则 | 体现 |
|------|------|
| **SRP** | 玩家数据模块只负责数据持有与导入，不做分析 |
| **DIP** | 通过 PlayerDataRepository 接口对外暴露，实现可替换 |
| **DRY** | 复用 Core 层的模型枚举，不另起炉灶 |
| **KISS** | 数据模型直接映射 Hoyolab API 返回结构，不做过度转义 |
| **OCP** | 新增导入方式只需新增 DataSource 实现 |

---

## 7. 边界条件与约束

- **多账号支持**：PlayerDataRepository 支持 UID 维度的数据隔离
- **数据容量**：单账号遗器上限约 1500 件，查询需分页
- **缓存策略**：自动同步间隔不低于 5 分钟，避免 API 限频
- **数据校验**：
  - 角色等级 ∈ [1, 80]，星魂 ∈ [0, 6]
  - 光锥精炼 ∈ [1, 5]
  - 遗器等级 ∈ [0, 15]，副词条数量 ∈ [0, 4]

---

## 8. 测试策略

- **导入测试**：Mock Hoyolab API 响应，验证映射正确性
- **合并测试**：测试差异合并的各种场景（新增/修改/删除）
- **冲突测试**：本地手动编辑 vs 自动同步的冲突解决
- **边界测试**：空数据、异常数据、超大数量
- **持久化测试**：Room DAO 的所有 CRUD 操作