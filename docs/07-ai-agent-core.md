# AI Agent 核心设计 — AI Agent Core

> **模块定位**：作为整个系统的智能中枢，通过 LLM 理解用户自然语言意图，编排各业务模块工具，生成结构化分析报告。

---

## 1. 职责与边界

### 单一职责 (SRP)
- **意图识别**：将用户自然语言转化为结构化指令
- **工具编排**：调用业务模块完成分析任务
- **报告生成**：整合多模块结果，生成自然语言报告

### 依赖关系
- 依赖所有业务模块（战斗模拟/遗器分析/配队对比/提升分析）
- 依赖 Player Data（玩家数据）
- 依赖 Core Data Layer（游戏数据）
- 不直接依赖 Android 框架层（可独立测试）

---

## 2. 核心架构

```
┌─────────────────────────────────────────────────────┐
│                   AI Agent Core                      │
│                                                      │
│  ┌─────────────────────────────────────────────┐   │
│  │          AgentOrchestrator                  │   │
│  │  - 接收用户输入 → 协调各组件完成处理 → 输出 │   │
│  └─────────────────────────────────────────────┘   │
│            │              │              │          │
│     ┌──────┘    ┌─────────┘    ┌────────┘          │
│     ▼           ▼              ▼                    │
│  ┌────────┐ ┌──────────┐ ┌────────────┐            │
│  │Intent  │ │Tool      │ │Report      │            │
│  │Resolver│ │Executor  │ │Generator   │            │
│  └────────┘ └──────────┘ └────────────┘            │
│       │            │              │                 │
│       ▼            ▼              ▼                 │
│  ┌──────────┐ ┌────────────────────────┐          │
│  │Prompt    │ │  Tool Definitions      │          │
│  │Templates │ │  (各业务模块的接口描述) │          │
│  └──────────┘ └────────────────────────┘          │
└─────────────────────────────────────────────────────┘
```

---

## 3. 意图识别 (IntentResolver)

### 3.1 意图分类

```kotlin
enum class Intent {
    // 战斗模拟
    SIMULATE_BATTLE,            // "模拟我的饮月队打混沌12层"
    CALCULATE_DAMAGE,           // "计算我希儿打可可利亚的伤害"
    
    // 遗器分析
    ANALYZE_RELICS,             // "分析我所有遗器"
    SCORE_CHARACTER_RELICS,     // "给希儿装备评分"
    RECOMMEND_RELIC_SET,        // "饮月应该用什么遗器套"
    SUGGEST_RELIC_UPGRADE,      // "哪些遗器值得强化"
    
    // 配队对比
    ANALYZE_TEAM,               // "分析我这个队伍"
    COMPARE_TEAMS,              // "对比饮月队和卡芙卡队"
    SUGGEST_TEAM,               // "我该用什么队打混沌12层"
    
    // 星魂提升
    ANALYZE_EIDOLON,            // "希儿星魂提升有多大"
    COMPARE_UPGRADE_PATH,       // "先补星魂还是先抽专武"
    ANALYZE_LIGHTCONE,          // "饮月光锥怎么选"
    
    // 通用
    QUERY_INFO,                 // "银狼技能是什么"
    COMPARE_CHARACTERS,         // "饮月和镜流谁强"
    GREETING,                   // 问候
    UNKNOWN
}
```

### 3.2 解析流程

```
用户输入 → 预处理 → 实体提取 → 意图分类 → 参数填充
                    │             │
                    ▼             ▼
               ["希儿",       SIMULATE_BATTLE
                "混沌12层"]    { character: "希儿",
                                 target: "混沌12层" }
```

### 3.3 实体提取

```kotlin
data class ParsedEntities(
    val characters: List<String>,          // 识别出的角色名
    val lightCones: List<String>,          // 识别出的光锥名
    val relicSets: List<String>,           // 识别出的遗器套装名
    val enemies: List<String>,             // 识别出的敌人数值
    val teamSlots: List<Int>,              // 队伍位置引用
    val numericValues: Map<String, Double>, // 数字参数
    val targetContent: String?,            // 混沌/虚构/末日
    val actionType: String?,               // 动词：模拟/对比/推荐
    val qualifiers: List<String>           // 修饰语：我的/最优/极限
)
```

**实体提取策略**：
- **关键词匹配**：角色/光锥/遗器名称库直接匹配
- **模糊匹配**：用户输入不精确时（如"饮月"匹配"丹恒·饮月"）
- **上下文关联**：跨轮对话中保持实体状态

### 3.4 意图分类策略

采用**分层策略**：

**第一层：规则匹配（快速路径）**
- 基于关键词的意图分类
- 命中率约 70%，适用于明确指令

**第二层：LLM 分类（回退路径）**
- 规则未命中时，请求 LLM 进行意图识别
- 提供 Few-shot 示例
- 返回结构化的 Intent + Parameters

```kotlin
interface IntentResolver {
    fun resolve(input: String, context: ConversationContext): ResolvedIntent
}

data class ResolvedIntent(
    val intent: Intent,
    val confidence: Double,
    val parameters: Map<String, Any>,
    val entities: ParsedEntities,
    val rawInput: String,
    val clarificationNeeded: Boolean,
    val clarificationQuestion: String?
)
```

---

## 4. 工具定义与执行 (ToolExecutor)

### 4.1 工具模型

每个业务模块的对外方法都注册为 Agent 可调用的**工具**：

```kotlin
data class ToolDefinition(
    val name: String,              // "simulate_battle"
    val description: String,       // "执行战斗模拟"
    val parameters: List<ToolParameter>,
    val module: String,            // "battle"
    val requiredIntent: Intent,    // 关联意图
    val timeout: Duration
)

data class ToolParameter(
    val name: String,
    val type: ParameterType,       // STRING / INT / LIST / OBJECT
    val description: String,
    val required: Boolean,
    val defaultValue: String?,
    val enumValues: List<String>?  // 如果是枚举类型
)

enum class ParameterType { STRING, INT, DOUBLE, BOOLEAN, LIST, OBJECT }
```

### 4.2 预定义工具清单

| 工具名 | 描述 | 对应模块 |
|--------|------|----------|
| `simulate_battle` | 执行战斗模拟，返回伤害统计 | battle |
| `calculate_damage` | 计算单次技能伤害 | battle |
| `score_relics` | 给角色当前遗器评分 | relic |
| `recommend_relic_set` | 推荐最优遗器套装 | relic |
| `suggest_relic_upgrade` | 给出遗器升级建议 | relic |
| `analyze_team` | 分析队伍协同 | team |
| `compare_teams` | 对比多支队伍 | team |
| `suggest_team` | 根据约束推荐队伍 | team |
| `analyze_eidolon` | 分析星魂提升收益 | upgrade |
| `analyze_lightcone` | 分析光锥选择/精炼 | upgrade |
| `compare_upgrade_path` | 对比不同升级方案 | upgrade |
| `query_character_info` | 查询角色基础信息 | core |
| `query_player_inventory` | 查询玩家持有 | player |

### 4.3 工具执行

```kotlin
class ToolExecutor(
    private val tools: Map<String, ToolDefinition>,
    private val battleModule: BattleSimulator,
    private val relicModule: RelicAnalysisFacade,
    private val teamModule: TeamCompositionFacade,
    private val upgradeModule: UpgradeAnalysisFacade,
    private val playerModule: PlayerDataRepository,
    private val coreModule: DataRepository
) {
    suspend fun execute(
        toolName: String,
        parameters: Map<String, Any?>
    ): ToolResult
    
    fun getAvailableTools(intent: Intent? = null): List<ToolDefinition>
}

data class ToolResult(
    val success: Boolean,
    val data: Any?,
    val error: String?,
    val executionTimeMs: Long,
    val metadata: Map<String, String>
)
```

### 4.4 工具编排逻辑

```
用户："我希儿打混沌12层伤害如何？"

Agent:
1. IntentResolver → SIMULATE_BATTLE
2. ToolExecutor:
   a. [player] query_player_inventory("希儿") → 获取玩家希儿配置
   b. [core]   query_enemy_info("混沌12层")  → 获取敌人信息
   c. [battle] simulate_battle(希儿, 装备, 敌人) → 伤害结果
3. ReportGenerator → 生成报告
```

---

## 5. 多轮对话管理

```kotlin
data class ConversationContext(
    val conversationId: String,
    val messages: List<ChatMessage>,
    val currentIntent: ResolvedIntent?,
    val pendingParameters: Map<String, Any?>,
    val extractedEntities: ParsedEntities,
    val history: List<AgentAction>
)

data class ChatMessage(
    val role: MessageRole,        // USER / ASSISTANT / SYSTEM
    val content: String,
    val timestamp: Long
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

data class AgentAction(
    val intent: Intent,
    val toolsCalled: List<String>,
    val result: String,
    val timestamp: Long
)
```

### 对话状态管理

```
[用户] "分析一下我的遗器"
  → 意图: ANALYZE_RELICS
  → Agent: "你想分析哪个角色的遗器？"

[用户] "希儿"
  → 合并上下文: ANALYZE_RELICS + character="希儿"
  → Agent: 调用 score_relics("希儿") → 输出报告

[用户] "换成饮月看看"
  → 继承意图: ANALYZE_RELICS
  → 更新参数: character="饮月"
  → Agent: 调用 score_relics("饮月") → 输出报告
```

---

## 6. 报告生成 (ReportGenerator)

### 6.1 报告结构

```kotlin
data class AnalysisReport(
    val title: String,
    val summary: String,              // 一句话总结
    val sections: List<ReportSection>,
    val keyFindings: List<String>,     // 关键发现
    val recommendations: List<String>, // 建议
    val dataVisualization: VisualData?, // 可视化数据
    val rawData: Map<String, Any>?     // 原始数据（供UI使用）
)

data class ReportSection(
    val title: String,
    val content: String,
    val type: SectionType,            // TEXT / TABLE / CHART / LIST
    val importance: Int               // 1-5，用于折叠展示
)

enum class SectionType { TEXT, TABLE, CHART, LIST }

data class VisualData(
    val chartType: ChartType,         // BAR / RADAR / LINE / PIE
    val labels: List<String>,
    val datasets: List<Dataset>
)

enum class ChartType { BAR, RADAR, LINE, PIE }
```

### 6.2 报告模板

```kotlin
interface ReportTemplate {
    fun generateTitle(data: AnalysisData): String
    fun generateSummary(data: AnalysisData): String
    fun generateSections(data: AnalysisData): List<ReportSection>
    fun generateRecommendations(data: AnalysisData): List<String>
}

// 模板示例：战斗模拟报告
class BattleReportTemplate : ReportTemplate {
    override fun generateSections(data: AnalysisData): List<ReportSection> {
        return listOf(
            ReportSection("伤害总览", data.totalDamage, SectionType.TEXT, 5),
            ReportSection("角色伤害分布", data.damageShare, SectionType.TABLE, 4),
            ReportSection("行动记录", data.actionLog, SectionType.LIST, 2),
            ReportSection("Buff覆盖率", data.buffUptime, SectionType.CHART, 3)
        )
    }
}
```

---

## 7. LLM 集成策略

### 7.1 架构选择

```
┌──────────────┐
│  LLM Service │ ← 抽象接口，可替换
├──────────────┤
│  OpenAI      │
│  Claude      │
│  Local Model │
└──────────────┘
```

```kotlin
interface LlmService {
    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?,
        temperature: Double = 0.3
    ): LlmResponse
    
    suspend fun classify(
        input: String,
        categories: List<String>
    ): ClassificationResult
}

data class LlmResponse(
    val content: String,
    val toolCalls: List<ToolCall>?,
    val usage: TokenUsage
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any?>
)
```

### 7.2 Function Calling 模式

Agent 核心通过 LLM Function Calling 实现：

1. 向 LLM 发送用户输入 + 可用工具定义
2. LLM 决定调用哪个工具（或直接回复）
3. 执行工具，将结果返回给 LLM
4. LLM 生成最终回复

```
User: "希儿打混沌12层伤害如何？"

→ LLM: tool_call(simulate_battle, {character: "希儿", target: "混沌12层"})
→ execute: 计算结果
→ LLM: 生成自然语言报告
```

### 7.3 Prompt 模板

```kotlin
object PromptTemplates {
    val SYSTEM_PROMPT = """
你是一个崩坏：星穹铁道分析助手，擅长战斗模拟、遗器分析、配队建议和星魂提升评估。

可使用的工具：
{tools}

分析时遵循：
1. 先获取玩家数据，再进行分析
2. 数值结果用表格呈现
3. 给出明确的结论和建议
4. 不确定时主动询问用户
""".trimIndent()

    val CLASSIFICATION_PROMPT = """
将用户输入分类到以下意图之一：
{intents}

用户输入：{input}

输出格式：{intent_name} | confidence_score
""".trimIndent()
}
```

---

## 8. 设计原则应用

| 原则 | 体现 |
|------|------|
| **SRP** | 意图识别/工具执行/报告生成职责完全分离 |
| **OCP** | 新增意图只需添加 Intent 枚举 + 对应工具定义 |
| **DIP** | LlmService 抽象接口，可切换不同 LLM 实现 |
| **DRY** | 工具参数模型与业务模块接口共享同一数据契约 |
| **KISS** | 优先规则匹配意图，LLM 作为补充而非主线 |
| **YAGNI** | 不实现自动学习/反馈闭环，保持 Agent 行为可预测 |

---

## 9. 边界与异常处理

### 9.1 异常场景

| 场景 | 处理方式 |
|------|----------|
| 意图不明确 | 反问用户澄清（clarificationNeeded=true） |
| 工具执行超时 | 返回部分结果，提示用户 |
| 数据不存在 | 提示用户先导入数据 |
| 多角色歧义 | "你说的'希儿'是哪一个？" |
| LLM 调用失败 | 降级到规则匹配模式 |

### 9.2 安全约束

- 工具执行参数校验（防止注入）
- 耗时长工具默认异步执行
- 敏感操作（如删除数据）需要用户确认

---

## 10. 测试策略

- **意图分类测试**：覆盖所有 Intent 类型，验证分类准确率 ≥ 90%
- **实体提取测试**：验证模糊匹配、多实体场景
- **工具编排测试**：验证多工具调用链路的正确性
- **多轮对话测试**：验证上下文保持和参数继承
- **报告格式测试**：验证生成的报告格式正确、内容完整
- **LLM Mock 测试**：Mock LLM 响应，验证 Agent 逻辑路径
- **降级测试**：LLM 不可用时，规则匹配可正常工作