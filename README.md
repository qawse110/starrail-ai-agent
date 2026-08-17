# StarRail AI-Agent — 崩坏：星穹铁道智能分析助手

基于 LLM 的 Android 智能 Agent 应用，提供战斗模拟、遗器分析、配队对比、星魂提升评估等功能。
支持 DeepSeek / OpenAI / Gemini 等多种 LLM 提供商。

## 📱 功能特性

| 模块 | 能力 | 状态 |
|------|------|------|
| 🤖 **LLM 智能对话** | 自然语言理解 + Function Calling 调用17个游戏工具 | ✅ |
| ⚔️ **战斗模拟** | 8乘区伤害公式 + 行动轴/行动值估算 + 角色战斗模型量化（同谐/虚无/同属性/生存位配队增益） | ✅ |
| 💎 **遗器分析** | 遗器评分（3大维度）、套装推荐（按命途匹配）、升级建议 | ✅ |
| 👥 **配队对比** | 队伍协同分析（元素/命途/技能）+ 战斗模型量化DPS | ✅ |
| ⬆️ **星魂提升** | 星魂收益曲线、精炼性价比、升级路径优化 | ✅ |
| 📊 **Markdown 渲染** | AI 回复支持加粗/斜体/代码块/标题/列表/分割线 | ✅ |
| 📋 **一键复制** | AI 回复内容复制到剪贴板 | ✅ |
| 📜 **对话历史** | 侧边栏查看/切换/删除历史对话 | ✅ |
| 🎯 **智能滚动** | 阅读历史时不自动滚动，底部 FAB 一键到底 | ✅ |
| 💾 **数据持久化** | 玩家数据以 JSON 文件实时写入设备存储 | ✅ |
| 🔧 **LLM 配置** | 应用内配置 API Key / 端点 / 模型 / 温度参数 + 测试连接 | ✅ |

## 🏗️ 项目架构

```
app/src/main/java/com/starrail/agent/
├── core/
│   ├── model/              # 领域模型（角色/光锥/遗器/敌人等）
│   ├── datasource/         # 96名角色 + 161光锥 + 56遗器套 + 25敌人（Wiki 数据）
│   ├── util/               # MarkdownParser（独立可测试的解析器）
│   └── json/               # JSON 解析/序列化
├── player/
│   ├── PlayerData.kt       # 玩家数据模型
│   └── db/                 # 持久化层（FilePlayerDao + JSON序列化）
├── battle/calculator/      # 战斗计算器（CombatStats / 防御区 / 抗性区 / 易伤区）
├── battle/engine/          # 行动轴/行动值计算器（ActionTimeline）
├── battle/model/           # 战斗模型量化器（配队增益/DPS评分）
├── relic/                  # 遗器评分器（4种Build类型权重）
├── team/                   # 配队分析器（协同评分 + DPS估算）
├── upgrade/                # 升级分析器（星魂/精炼/性价比）
├── agent/
│   ├── intent/             # 规则引擎（16种意图识别）
│   ├── llm/                # LLM 服务（OpenAI API + DSML解析器）
│   │   ├── LlmService.kt   # LLM 接口抽象
│   │   └── OpenAiLlmService.kt  # OpenAI/DeepSeek 实现
│   ├── tool/               # 18个工具执行器（含按命途推荐的遗器配装）
│   ├── report/             # 报告生成器（6种报告模板）
│   └── StarRailAgent.kt    # Agent 编排器（LLM + 规则双模式）
├── settings/               # LLM 配置持久化（SharedPreferences）
└── MainActivity.kt         # Compose UI 聊天界面 + 设置界面
```

### 双模式运行

```
LLM 模式（默认）                   规则模式（回退）
  用户输入                           用户输入
    ↓                                  ↓
  LLM (DeepSeek/OpenAI)             规则引擎匹配意图
    ↓                                  ↓
  Function Calling → 执行工具       执行匹配的工具
    ↓                                  ↓
  LLM 生成自然语言回复              生成结构化报告
```

## 🚀 快速开始

### 编译运行

```bash
# 编译 APK
./gradlew :app:assembleDebug

# 运行单元测试（176项）
./gradlew :app:testDebugUnitTest
```

### 配置 LLM

1. 打开应用，点击左上角 ☰ → ⚙️ 进入设置
2. 开启「启用 LLM」
3. 选择提供商（DeepSeek / OpenAI / Gemini / 自定义）
4. 填入 API Key
5. 点击「测试连接」验证
6. 返回聊天界面即可使用

**推荐配置：**
- **DeepSeek**: `https://api.deepseek.com` / `deepseek-chat`
- **OpenAI**: `https://api.openai.com` / `gpt-4o-mini`
- **Gemini**: `https://generativelanguage.googleapis.com` / `gemini-2.0-flash`

### 使用示例

```
"列出所有角色"                                              → 返回全部96名角色
"希儿的技能是什么"                                          → 角色详情查询
"模拟希儿打可可利亚"                                        → 战斗模拟
"给希儿推荐遗器配装"                                        → 按命途推荐遗器套装
"银狼星魂提升有多大"                                        → 星魂收益分析
"先补星魂还是先抽专武"                                      → 升级路径对比
"推荐混沌12层配队"                                          → 配队推荐
```

## 🧪 测试覆盖

| 模块 | 测试数 | 覆盖内容 |
|------|-------|---------|
| IntentResolver | 17 | 16种意图精确匹配 + 量化识别 |
| RelicScorer | 6 | 权重/评分/词条分析 |
| DamageCalculator | 20 | 8乘区/混合倍率/防御/抗性/破韧/易伤/暴击公式 |
| ActionTimeline | 7 | 行动值/轮次行动数/提前/延后 |
| BattleModelQuantifier | 5 | 单人/配队增益/同属性/弱点匹配/评分 |
| TeamAnalyzer | 11 | 协同/命途/元素/DPS估算 |
| UpgradeAnalyzer | 16 | 星魂/精炼/性价比曲线 |
| FilePlayerDao | 23 | JSON 持久化完整 CRUD |
| InMemoryGameDataSource | 16 | 全部数据查询 API 完整性 |
| MarkdownParser | 19 | 标题/列表/粗体/斜体/代码块解析 |
| StarRailAgent | 15 | Agent 编排/对话/回退逻辑 |
| ToolExecutor | 28 | 工具定义与执行 |
| ConversationRepository | 8 | 对话持久化 CRUD |
| WikiDataLoading | 4 | Wiki JSON 数据加载验证 |
| **总计** | **195** | 全部通过 ✅ |

## 📊 数据源规模

| 数据 | 数量 | 覆盖范围 |
|------|------|---------|
| 角色 | **96 条** | Wiki 图鉴条目（5星61 / 4星17，其余待补全） |
| 光锥 | **161 条** | Wiki 图鉴条目（部分条目仅有标题，待补全详情） |
| 遗器套装 | **56 套** | 隧洞遗器 + 位面饰品 |
| 敌人 | **25 个** | 周本Boss/混沌精英/模拟宇宙/活动 |
| LLM 提供商 | **4 种** | DeepSeek / OpenAI / Gemini / 自定义 |

## 🛠️ 数据同步

```bash
# 从 bilibili Wiki 全量同步角色/光锥/遗器数据
python3 tools/sync_wiki_data.py

# 检查当前 wiki_data.json 的完整度
python3 tools/check_data_completeness.py
```

> 注意：Wiki 接口有频率限制，同步脚本已做限流和重试；如果遇到 `HTTP 567`，请稍后重试。

## 📊 代码统计

| 指标 | 值 |
|------|-----|
| 源码文件 | **33+** |
| 单元测试 | **176 项** |
| 编译 | 0 errors ✅ |
| APK | 构建成功 ✅ |
| SDK | minSdk 24 / targetSdk 35 |
| UI 框架 | Jetpack Compose + Material3 |
| 构建系统 | Gradle + Kotlin DSL |

## 📚 设计文档

详见 [docs/](./docs/) 目录：

| 文档 | 内容 |
|------|------|
| 01-core-data-layer.md | 领域模型、数据源架构 |
| 02-player-data.md | 玩家数据模型与持久化 |
| 03-battle-simulation.md | 伤害公式与战斗模拟 |
| 04-relic-analysis.md | 遗器评分算法 |
| 05-team-composition.md | 配队协同分析 |
| 06-upgrade-analysis.md | 星魂/精炼升级分析 |
| 07-ai-agent-core.md | AI Agent 编排器设计 |

## 📄 许可证

本项目为粉丝自制工具，与 HoYoverse / COGNOSPHERE PTE. LTD. 无关。

## 🔗 参考资源

- [崩坏星穹铁道官网](https://sr.mihoyo.com/)
- [Huroka Database](https://www.huroka.com/)
- [Project Yatta](https://sr.yatta.moe/)
- [DeepSeek API 文档](https://platform.deepseek.com/)
- [OpenAI API 文档](https://platform.openai.com/)
