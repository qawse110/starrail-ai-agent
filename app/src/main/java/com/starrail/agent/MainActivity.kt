package com.starrail.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.*
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
import com.starrail.agent.agent.StarRailAgent
import com.starrail.agent.agent.llm.LlmConfig
import com.starrail.agent.agent.llm.OpenAiLlmService
import com.starrail.agent.player.db.AppDatabase
import com.starrail.agent.settings.LlmProvider
import com.starrail.agent.settings.LlmSettings
import com.starrail.agent.settings.LlmSettingsData
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
private enum class Page { CHAT, SETTINGS }

// ============================================================
// MainActivity
// ============================================================
class MainActivity : ComponentActivity() {
    private lateinit var llmSettings: LlmSettings
    private var agent: StarRailAgent = StarRailAgent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        llmSettings = LlmSettings.getInstance(this)
        AppDatabase.getFileInstance(filesDir)
        agent = createAgentFromSettings()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(agent, llmSettings, ::recreateAgent)
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
            StarRailAgent(llmService)
        } else {
            StarRailAgent()
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
    onSettingsChanged: () -> Unit
) {
    var currentPage by remember { mutableStateOf(Page.CHAT) }

    when (currentPage) {
        Page.CHAT -> StarRailChatScreen(
            agent = agent,
            onOpenSettings = { currentPage = Page.SETTINGS }
        )
        Page.SETTINGS -> LlmSettingsScreen(
            llmSettings = llmSettings,
            onBack = {
                onSettingsChanged()
                currentPage = Page.CHAT
            }
        )
    }
}

// ============================================================
// 聊天界面
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarRailChatScreen(agent: StarRailAgent, onOpenSettings: () -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val chatItems = remember { mutableStateListOf<ChatItem>() }
    var showClearConfirm by remember { mutableStateOf(false) }
    // 追踪当前会话 ID（一个空会话）
    var currentConvId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        chatItems.add(ChatItem.Message("你好！我是星穹铁道 AI 助手 ✨\n\n我可以帮你：", false))
        chatItems.add(ChatItem.Action("📋 查询角色", "请列出所有角色"))
        chatItems.add(ChatItem.Action("⚔️ 战斗模拟", "模拟希儿战斗"))
        chatItems.add(ChatItem.Action("💎 星魂分析", "希儿星魂提升有多大"))
        chatItems.add(ChatItem.Action("🎯 推荐配队", "配队推荐"))
        scope.launch { listState.animateScrollToItem(chatItems.size - 1) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("星穹铁道 AI 助手", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空对话",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        currentConvId?.let { agent.clearConversation(it) }
                        chatItems.clear()
                        currentConvId = null
                        chatItems.add(ChatItem.Message("你好！我是星穹铁道 AI 助手 ✨\n\n我可以帮你：", false))
                        chatItems.add(ChatItem.Action("📋 查询角色", "请列出所有角色"))
                        chatItems.add(ChatItem.Action("⚔️ 战斗模拟", "模拟希儿战斗"))
                        chatItems.add(ChatItem.Action("💎 星魂分析", "希儿星魂提升有多大"))
                        chatItems.add(ChatItem.Action("🎯 推荐配队", "配队推荐"))
                        scope.launch { listState.animateScrollToItem(chatItems.size - 1) }
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
    }
}

// ============================================================
// LLM 设置界面
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(
    llmSettings: LlmSettings,
    onBack: () -> Unit
) {
    val settings = remember { mutableStateOf(llmSettings.load()) }
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

/** 解析 Markdown 为 AnnotatedString */
private fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        for ((lineIdx, line) in lines.withIndex()) {
            if (lineIdx > 0) append("\n")
            
            when {
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(line.removePrefix("### "))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                        append(line.removePrefix("## "))
                    }
                }
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp)) {
                        append(line.removePrefix("# "))
                    }
                }
                line.matches(Regex("^-{3,}$")) -> {
                    append("─".repeat(30))
                }
                else -> {
                    val listMatch = Regex("""^(\s*)([-*+]|\d+\.)\s+(.*)""").find(line)
                    if (listMatch != null) {
                        val indent = listMatch.groupValues[1]
                        val marker = listMatch.groupValues[2]
                        val content = listMatch.groupValues[3]
                        val bullet = if (marker.all { it.isDigit() }) "$marker " else "• "
                        if (indent.isNotEmpty()) append("  ".repeat(indent.length / 2))
                        append(bullet)
                        append(parseInlineMarkdown(content))
                    } else {
                        append(parseInlineMarkdown(line))
                    }
                }
            }
        }
    }
}

/** 解析行内 Markdown 样式 */
private fun parseInlineMarkdown(line: String): AnnotatedString {
    return buildAnnotatedString {
        var remaining = line
        while (remaining.isNotEmpty()) {
            val boldClose = remaining.indexOf("**")
            val italicClose = remaining.indexOf("*")
            val codeClose = remaining.indexOf("`")
            
            val firstPos = listOf(
                if (boldClose >= 0) boldClose else Int.MAX_VALUE,
                if (italicClose >= 0 && (boldClose < 0 || italicClose < boldClose)) italicClose else Int.MAX_VALUE,
                if (codeClose >= 0) codeClose else Int.MAX_VALUE
            ).minOrNull() ?: Int.MAX_VALUE
            
            if (firstPos == Int.MAX_VALUE) {
                append(remaining)
                break
            }
            
            val (endIdx, open, close) = when (firstPos) {
                boldClose -> Triple(boldClose + 2, "**", "**")
                italicClose -> Triple(italicClose, "*", "*")
                else -> Triple(codeClose, "`", "`")
            }
            
            if (firstPos > 0) append(remaining.substring(0, firstPos))
            
            val closeLen = close.length
            val end = remaining.indexOf(close, firstPos + open.length)
            if (end >= 0) {
                val content = remaining.substring(firstPos + open.length, end)
                when (open) {
                    "**" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(content) }
                    "*" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(content) }
                    "`" -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)) { append(content) }
                }
                remaining = remaining.substring(end + closeLen)
            } else {
                append(remaining.substring(firstPos))
                break
            }
        }
    }
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
// 数据管理卡片
// ============================================================
@Composable
fun DataManagementCard() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getFileInstance(context.filesDir).playerDao() }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    
    // 读取当前数据状态
    val charCount = remember { dao.getAllCharacters().size }
    val lcCount = remember { dao.getAllLightCones().size }
    val relicCount = remember { dao.getAllRelics().size }
    
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
                "角色: $charCount | 光锥: $lcCount | 遗器: $relicCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "存储: ${context.filesDir.absolutePath}/player_data/",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
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
                    color = MaterialTheme.colorScheme.error
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