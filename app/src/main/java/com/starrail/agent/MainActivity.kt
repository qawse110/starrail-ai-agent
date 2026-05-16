package com.starrail.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import com.starrail.agent.agent.ConversationExporter
import com.starrail.agent.agent.ConversationRepository
import com.starrail.agent.agent.StarRailAgent
import com.starrail.agent.agent.llm.LlmConfig
import com.starrail.agent.core.sync.WikiDataSyncManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.starrail.agent.agent.llm.OpenAiLlmService
import com.starrail.agent.player.db.AppDatabase
import com.starrail.agent.settings.LlmProvider
import com.starrail.agent.settings.LlmSettings
import com.starrail.agent.settings.LlmSettingsData
import com.starrail.agent.core.util.MarkdownParser
import kotlinx.coroutines.launch
import java.io.File

// ============================================================
// 消息类型
// ============================================================
sealed class ChatItem {
    data class Message(val text: String, val isUser: Boolean) : ChatItem()
    data class Report(val title: String, val content: String, val isError: Boolean = false) : ChatItem()
    data class Action(val label: String, val query: String) : ChatItem()
}

/** 页面枚举 */
private enum class Page { CHAT, SETTINGS, GALLERY, REFERENCE }

// ============================================================
// MainActivity
// ============================================================
class MainActivity : ComponentActivity() {
    private lateinit var llmSettings: LlmSettings
    private lateinit var convRepo: ConversationRepository
    private var agent: StarRailAgent = StarRailAgent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        llmSettings = LlmSettings.getInstance(this)
        AppDatabase.getFileInstance(filesDir)
        convRepo = ConversationRepository(filesDir)
        agent = createAgentFromSettings()

        setContent {
            val settingsState = remember { mutableStateOf(llmSettings.load()) }
            val isDarkMode = settingsState.value.darkMode || isSystemInDarkTheme()
            val colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        agent = agent,
                        llmSettings = llmSettings,
                        settingsState = settingsState,
                        onSettingsChanged = ::recreateAgent,
                        playerDao = AppDatabase.getFileInstance(filesDir).playerDao()
                    )
                }
            }
        }
    }

    private fun createAgentFromSettings(): StarRailAgent {
        val settings = llmSettings.load()
        return if (settings.enabled && settings.apiKey.isNotBlank()) {
            val llmService = OpenAiLlmService(
                LlmConfig(
                    apiKey = settings.apiKey,
                    baseUrl = settings.baseUrl.trimEnd('/'),
                    model = settings.model,
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens
                )
            )
            StarRailAgent(llmService, convRepo)
        } else {
            StarRailAgent(null, convRepo)
        }
    }

    private fun recreateAgent() {
        agent = createAgentFromSettings()
    }
}

// ============================================================
// 主屏幕（路由）
// ============================================================
@Composable
private fun MainScreen(
    agent: StarRailAgent,
    llmSettings: LlmSettings,
    settingsState: MutableState<LlmSettingsData>,
    onSettingsChanged: () -> Unit,
    playerDao: com.starrail.agent.player.db.PlayerDao? = null
) {
    var currentPage by remember { mutableStateOf(Page.CHAT) }

    when (currentPage) {
        Page.CHAT -> StarRailChatScreen(
            agent = agent,
            onOpenSettings = { currentPage = Page.SETTINGS },
            onOpenGallery = { currentPage = Page.GALLERY },
            onOpenReference = { currentPage = Page.REFERENCE }
        )
        Page.SETTINGS -> LlmSettingsScreen(
            llmSettings = llmSettings,
            settingsState = settingsState,
            onBack = {
                onSettingsChanged()
                currentPage = Page.CHAT
            }
        )
        Page.GALLERY -> CharacterGalleryScreen(
            dataSource = agent.gameDataSource,
            playerDao = playerDao,
            onBack = { currentPage = Page.CHAT }
        )
        Page.REFERENCE -> ReferenceScreen(
            dataSource = agent.gameDataSource,
            onBack = { currentPage = Page.CHAT }
        )
    }
}

// ============================================================
// 聊天界面（带对话历史侧边栏）
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarRailChatScreen(agent: StarRailAgent, onOpenSettings: () -> Unit, onOpenGallery: () -> Unit, onOpenReference: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val chatItems = remember { mutableStateListOf<ChatItem>() }
    var showClearConfirm by remember { mutableStateOf(false) }
    var currentConvId by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    // 对话列表状态
    var conversationList by remember { mutableStateOf(agent.getConversationList()) }
    
    fun refreshConversationList() {
        conversationList = agent.getConversationList()
    }

    fun resetToWelcome() {
        chatItems.clear()
        currentConvId = null
        chatItems.add(ChatItem.Message("你好！我是星穹铁道 AI 助手 ✨\n\n我可以帮你做这些：", false))
        chatItems.add(ChatItem.Action("📋 所有角色", "请列出所有角色"))
        chatItems.add(ChatItem.Action("⚔️ 战斗模拟", "模拟希儿战斗"))
        chatItems.add(ChatItem.Action("💎 星魂分析", "希儿星魂提升有多大"))
        chatItems.add(ChatItem.Action("🎯 推荐配队", "配队推荐"))
        chatItems.add(ChatItem.Action("🏆 光锥推荐", "希儿用什么光锥"))
        chatItems.add(ChatItem.Action("🛡️ 遗器推荐", "希儿推荐遗器"))
        chatItems.add(ChatItem.Action("📊 队伍对比", "对比镜流和希儿"))
        chatItems.add(ChatItem.Action("🔍 查敌人", "可可利亚弱点"))
        chatItems.add(ChatItem.Action("📈 伤害计算", "希儿打可可利亚伤害"))
        chatItems.add(ChatItem.Action("🔄 升级建议", "希儿优先升级什么"))
        scope.launch { listState.animateScrollToItem(chatItems.size - 1) }
    }

    LaunchedEffect(Unit) { resetToWelcome() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                val context = LocalContext.current
                // 头部 + 搜索
                Text(
                    "对话历史",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                var drawerSearch by remember { mutableStateOf("") }
                val searchResults = remember(drawerSearch, conversationList) {
                    if (drawerSearch.isBlank()) null
                    else agent.searchConversations(drawerSearch)
                }
                OutlinedTextField(
                    value = drawerSearch,
                    onValueChange = { drawerSearch = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp),
                    placeholder = { Text("搜索对话内容...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    textStyle = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider()
                
                val displayList = if (searchResults != null) searchResults else conversationList.toList()
                
                if (displayList.isEmpty()) {
                    Text(
                        "暂无历史对话",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(displayList) { (convId, preview) ->
                        Surface(
                            onClick = {
                                // 加载该对话的历史消息
                                val msgs = agent.getConversationMessages(convId)
                                chatItems.clear()
                                var first = true
                                for (msg in msgs) {
                                    when (msg.role) {
                                        com.starrail.agent.agent.llm.LlmRole.USER -> {
                                            chatItems.add(ChatItem.Message(msg.content ?: "", true))
                                        }
                                        com.starrail.agent.agent.llm.LlmRole.ASSISTANT -> {
                                            if (!msg.content.isNullOrEmpty() && !first) {
                                                chatItems.add(ChatItem.Message(msg.content, false))
                                            }
                                            first = false
                                        }
                                        else -> {}
                                    }
                                }
                                currentConvId = convId
                                scope.launch { drawerState.close() }
                                scope.launch { listState.animateScrollToItem(chatItems.size - 1) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = preview,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(
                                    onClick = {
                                        val msgs = agent.getConversationMessages(convId)
                                        if (msgs.isNotEmpty()) {
                                            val file = ConversationExporter.exportAsText(context, msgs)
                                            ConversationExporter.shareFile(context, file)
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "导出",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        agent.deleteConversation(convId)
                                        refreshConversationList()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("星穹铁道 AI 助手", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = {
                            refreshConversationList()
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "历史对话",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenReference) {
                            Icon(Icons.Default.Book, contentDescription = "游戏资料",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onOpenGallery) {
                            Icon(Icons.Default.Person, contentDescription = "角色图鉴",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "清空对话",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            currentConvId?.let { agent.clearConversation(it) }
                            resetToWelcome()
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "新建对话")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatItems.toList()) { item ->
                        when (item) {
                            is ChatItem.Message -> MessageBubble(item)
                            is ChatItem.Report -> ReportCard(item)
                            is ChatItem.Action -> ActionChip(item) {
                                processQuery(agent, it, chatItems, scope, listState) { isLoading = it }
                            }
                        }
                    }
                }
                
                // 滚动到底部按钮（用户滚动离开底部时显示）
                val canScrollDown = remember {
                    derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val total = listState.layoutInfo.totalItemsCount
                        lastVisible < total - 2
                    }
                }
                if (canScrollDown.value) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(chatItems.size - 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "滚动到底部")
                    }
                }
            }

            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            // 快捷操作栏
            QuickActionBar(onAction = { query ->
                inputText = query
                // 如果用户选择了快捷操作，自动发送
            })

            Surface(tonalElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入指令…") },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp)
                    )
                    FilledTonalButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val q = inputText.trim()
                                inputText = ""
                                processQuery(agent, q, chatItems, scope, listState) { isLoading = it }
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(48.dp)
                    ) { Text("发送") }
                }
            }
        }
        }  // 关闭 Scaffold content lambda
    }  // 关闭 ModalNavigationDrawer content lambda
}  // 关闭 StarRailChatScreen

// ============================================================
// LLM 设置界面
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(
    llmSettings: LlmSettings,
    settingsState: MutableState<LlmSettingsData>,
    onBack: () -> Unit
) {
    val settings = settingsState
    var showApiKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM 设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        llmSettings.save(settings.value)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        llmSettings.save(settings.value)
                        onBack()
                    }) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== 启用开关 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("启用 LLM", fontWeight = FontWeight.Medium)
                        Text(
                            "开启后 AI 能更准确理解复杂指令",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.value.enabled,
                        onCheckedChange = { settings.value = settings.value.copy(enabled = it) }
                    )
                }
            }

            // ===== 深色模式 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("深色模式", fontWeight = FontWeight.Medium)
                        Text(
                            "切换深色/浅色主题",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.value.darkMode,
                        onCheckedChange = {
                            settings.value = settings.value.copy(darkMode = it)
                            llmSettings.save(settings.value)
                        }
                    )
                }
            }

            // ===== 服务提供商 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("服务提供商", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    // 提供商选择
                    LlmProvider.entries.forEach { provider ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.value.provider == provider,
                                onClick = {
                                    settings.value = llmSettings.applyProviderDefaults(provider)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(provider.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    provider.defaultEndpoint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ===== API 端点 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("API 端点", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.value.baseUrl,
                        onValueChange = { settings.value = settings.value.copy(baseUrl = it) },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://api.deepseek.com") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.value.apiKey,
                        onValueChange = { settings.value = settings.value.copy(apiKey = it) },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "隐藏" else "显示"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            }

            // ===== 模型参数 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("模型参数", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.value.model,
                        onValueChange = { settings.value = settings.value.copy(model = it) },
                        label = { Text("模型名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("deepseek-chat / gpt-4o-mini") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // 温度滑块
                    Text(
                        "温度: ${"%.1f".format(settings.value.temperature)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = settings.value.temperature.toFloat(),
                        onValueChange = { settings.value = settings.value.copy(temperature = it.toDouble()) },
                        valueRange = 0f..2f,
                        steps = 19
                    )
                }
            }

            // ===== 测试连接 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            isTesting = true
                            testResult = null
                            scope.launch {
                                testResult = try {
                                    val config = settings.value
                                    val service = OpenAiLlmService(
                                        LlmConfig(
                                            apiKey = config.apiKey,
                                            baseUrl = config.baseUrl.trimEnd('/'),
                                            model = config.model,
                                            temperature = config.temperature,
                                            maxTokens = 64
                                        )
                                    )
                                    val resp = service.chat(
                                        messages = listOf(
                                            com.starrail.agent.agent.llm.LlmMessage(
                                                com.starrail.agent.agent.llm.LlmRole.USER,
                                                "回复'连接成功'即可"
                                            )
                                        ),
                                        config = com.starrail.agent.agent.llm.LlmConfig(maxTokens = 64)
                                    )
                                    if (resp.success) "✅ 连接成功！${resp.content?.take(50) ?: ""}"
                                    else "❌ 失败: ${resp.error?.take(100)}"
                                } catch (e: Exception) {
                                    "❌ 异常: ${e.message?.take(100)}"
                                }
                                isTesting = false
                            }
                        },
                        enabled = settings.value.enabled && settings.value.apiKey.isNotBlank() && !isTesting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("测试连接")
                    }
                    if (testResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = testResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (testResult!!.startsWith("✅"))
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ===== 状态指示 =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (settings.value.enabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (settings.value.enabled) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (settings.value.enabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (settings.value.enabled) "LLM 已启用" else "LLM 未启用",
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (settings.value.enabled) "提供商: ${settings.value.provider.displayName} | 模型: ${settings.value.model}"
                            else "启用后 AI 能借助大模型更准确理解复杂指令",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ===== 数据管理 =====
            DataManagementCard()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ============================================================
// 聊天组件 — 支持 Markdown 和复制
// ============================================================
@Composable
fun MessageBubble(msg: ChatItem.Message) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (msg.isUser) 16.dp else 4.dp,
                bottomEnd = if (msg.isUser) 4.dp else 16.dp
            ),
            color = if (msg.isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
        ) {
            if (msg.isUser) {
                Text(
                    text = msg.text,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 15.sp, lineHeight = 22.sp
                )
            } else {
                Column {
                    MarkdownContent(
                        text = msg.text,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp)
                    )
                    // 复制按钮
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("AI回复", msg.text))
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "复制",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Markdown 渲染器
 * 支持：加粗、斜体、行内代码、代码块、标题、列表、分割线
 */
@Composable
fun MarkdownContent(text: String, modifier: Modifier = Modifier) {
    val annotatedString = remember(text) { parseMarkdown(text) }
    
    // 检查是否包含代码块（代码块需要等宽字体）
    val hasCodeBlock = text.contains("```")
    
    if (hasCodeBlock) {
        renderWithCodeBlocks(text, modifier)
    } else {
        Text(
            text = annotatedString,
            modifier = modifier,
            fontSize = 15.sp, lineHeight = 22.sp
        )
    }
}

/** 渲染包含代码块的内容 */
@Composable
private fun renderWithCodeBlocks(text: String, modifier: Modifier) {
    val segments = text.split(Regex("(?<=```)|(?=```)"))
    var isCode = false
    
    Column(modifier = modifier) {
        for (segment in segments) {
            when {
                segment == "```" -> { isCode = !isCode }
                isCode -> {
                    // 代码块
                    val code = segment.removePrefix("kotlin\n").removePrefix("python\n")
                        .removePrefix("json\n").removePrefix("bash\n").removePrefix("plaintext\n")
                        .trim()
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = code,
                            modifier = Modifier.padding(10.dp),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseMarkdown(segment),
                        fontSize = 15.sp, lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

/** 将 MarkdownParser Segment 列表渲染为 AnnotatedString */
private fun segmentsToAnnotatedString(segments: List<MarkdownParser.Segment>): AnnotatedString {
    return buildAnnotatedString {
        for (seg in segments) {
            val style = when (seg.style) {
                MarkdownParser.Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                MarkdownParser.Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                MarkdownParser.Style.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                MarkdownParser.Style.HEADING1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp)
                MarkdownParser.Style.HEADING2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)
                MarkdownParser.Style.HEADING3 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                MarkdownParser.Style.BULLET, MarkdownParser.Style.ORDERED -> SpanStyle()
                MarkdownParser.Style.NORMAL -> SpanStyle()
            }
            withStyle(style) { append(seg.text) }
        }
    }
}

/** 解析 Markdown 为 AnnotatedString（委托给 MarkdownParser） */
private fun parseMarkdown(text: String): AnnotatedString {
    return segmentsToAnnotatedString(MarkdownParser.parse(text))
}

/** 解析行内 Markdown 样式（委托给 MarkdownParser） */
private fun parseInlineMarkdown(line: String): AnnotatedString {
    return segmentsToAnnotatedString(MarkdownParser.parseInline(line))
}

@Composable
fun ReportCard(report: ChatItem.Report) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (report.isError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (report.title.isNotBlank()) {
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (report.isError) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = report.content,
                fontSize = 14.sp, lineHeight = 20.sp,
                color = if (report.isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun ActionChip(action: ChatItem.Action, onClick: (String) -> Unit) {
    Surface(
        onClick = { onClick(action.query) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Text(
            text = action.label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================================
// 快捷操作栏
// ============================================================
/** 分类快捷操作 */
private data class QuickAction(val label: String, val query: String)

private val quickActions = listOf(
    QuickAction("🔍 查角色", "希儿信息"),
    QuickAction("📦 查光锥", "于夜色中信息"),
    QuickAction("🛡️ 查遗器", "野穗伴行的快枪手"),
    QuickAction("👾 查敌人", "可可利亚弱点"),
    QuickAction("⚔️ 战斗模拟", "模拟希儿战斗"),
    QuickAction("📈 伤害计算", "希儿打可可利亚伤害"),
    QuickAction("💎 星魂", "希儿星魂提升"),
    QuickAction("🏆 光锥推荐", "希儿用什么光锥"),
    QuickAction("🎯 配队", "推荐希儿配队"),
    QuickAction("🔄 升级", "希儿优先升级"),
)

@Composable
fun QuickActionBar(onAction: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        quickActions.forEach { action ->
            SuggestionChip(
                onClick = { onAction(action.query) },
                label = { Text(action.label, fontSize = 12.sp, maxLines = 1) },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

// ============================================================
// 数据管理卡片
// ============================================================
@Composable
fun DataManagementCard() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getFileInstance(context.filesDir).playerDao() }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var syncProgress by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    
    // 读取当前数据状态
    val charCount = remember { dao.getAllCharacters().size }
    val lcCount = remember { dao.getAllLightCones().size }
    val relicCount = remember { dao.getAllRelics().size }
    
    // 检查是否有缓存的 wiki 数据
    val wikiDataFile = remember { File(context.filesDir, "wiki_data.json") }
    val wikiDataSize = remember { if (wikiDataFile.exists()) "${wikiDataFile.length() / 1024}KB" else "无" }
    val syncTime = remember {
        if (wikiDataFile.exists()) {
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            date.format(java.util.Date(wikiDataFile.lastModified()))
        } else null
    }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("数据管理", fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            // 统计数据
            Text(
                "玩家数据 — 角色: $charCount | 光锥: $lcCount | 遗器: $relicCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (syncTime != null) {
                Text(
                    "Wiki数据 — $wikiDataSize (同步于 $syncTime)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 同步进度
            if (isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                if (syncProgress != null) {
                    Text(
                        syncProgress!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // 同步按钮
            OutlinedButton(
                onClick = {
                    isSyncing = true
                    syncProgress = "正在获取数据列表..."
                    kotlinx.coroutines.MainScope().launch {
                        val syncManager = WikiDataSyncManager(context.filesDir)
                        val result = withContext(Dispatchers.IO) {
                            syncManager.syncAll { progress ->
                                syncProgress = "${progress.stage}: ${progress.current}/${progress.total} ${progress.message}"
                            }
                        }
                        isSyncing = false
                        if (result.success) {
                            val size = if (wikiDataFile.exists()) "${wikiDataFile.length() / 1024}KB" else "?"
                            statusMessage = "✅ Wiki数据同步完成 — ${result.charactersCount}角色 + ${result.lightConesCount}光锥 ($size)"
                        } else {
                            statusMessage = "⚠️ 同步部分完成 (${result.errors.size}个错误)，${result.charactersCount}角色 + ${result.lightConesCount}光锥"
                        }
                        syncProgress = null
                    }
                },
                enabled = !isSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isSyncing) "同步中..." else "🔄 Wiki数据同步")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 清除数据按钮
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("清除所有数据")
            }
            
            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    statusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (statusMessage!!.startsWith("✅")) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    // 确认删除对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认清除") },
            text = { Text("此操作将删除所有玩家数据（角色、光锥、遗器），不可恢复。确定继续？") },
            confirmButton = {
                TextButton(onClick = {
                    dao.deleteAllCharacters()
                    dao.deleteAllLightCones()
                    dao.deleteAllRelics()
                    dao.deleteAllSnapshots()
                    statusMessage = "✅ 已清除所有数据"
                    showDeleteConfirm = false
                }) {
                    Text("确认清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun processQuery(
    agent: StarRailAgent,
    query: String,
    chatItems: MutableList<ChatItem>,
    scope: kotlinx.coroutines.CoroutineScope,
    listState: androidx.compose.foundation.lazy.LazyListState,
    setLoading: (Boolean) -> Unit
) {
    if (query.isBlank()) return
    
    scope.launch {
        chatItems.add(ChatItem.Message(query, true))
        setLoading(true)
        // 智能滚动：用户接近底部时自动滚动
        val shouldAutoScroll = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { last ->
            last.index >= listState.layoutInfo.totalItemsCount - 2
        } ?: true
        if (shouldAutoScroll) {
            try { listState.animateScrollToItem(chatItems.size - 1) } catch (_: Exception) {}
        }
        try {
            val reply = agent.chat(query)
            chatItems.add(ChatItem.Message(reply, false))
        } catch (e: Exception) {
            chatItems.add(ChatItem.Report("错误", e.message ?: "处理失败", isError = true))
        }
        setLoading(false)
        if (chatItems.isNotEmpty()) {
            try { listState.animateScrollToItem(chatItems.size - 1) } catch (_: Exception) {}
        }
    }
}