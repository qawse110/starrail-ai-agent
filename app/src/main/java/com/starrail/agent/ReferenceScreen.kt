package com.starrail.agent

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starrail.agent.core.datasource.InMemoryGameDataSource
import com.starrail.agent.core.model.*

/**
 * 资料参考页面
 * 三个 Tab：光锥 / 遗器套装 / 敌人
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceScreen(
    dataSource: InMemoryGameDataSource,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("光锥", "遗器套装", "敌人")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("游戏资料", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    when (index) {
                                        0 -> Icons.Default.Stars
                                        1 -> Icons.Default.Bolt // 用不同图标区分
                                        2 -> Icons.Default.Shield
                                        else -> Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(title)
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> LightConeTab(dataSource)
                1 -> RelicSetTab(dataSource)
                2 -> EnemyTab(dataSource)
            }
        }
    }
}

// ============================================================
// 光锥 Tab
// ============================================================
@Composable
private fun LightConeTab(dataSource: InMemoryGameDataSource) {
    val lightCones = remember { dataSource.getAllLightCones() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPath by remember { mutableStateOf<PathType?>(null) }
    var showOnly5Star by remember { mutableStateOf(false) }
    var selectedLc by remember { mutableStateOf<LightCone?>(null) }
    var showDetail by remember { mutableStateOf(false) }

    val filtered = lightCones.filter { lc ->
        (searchQuery.isEmpty() || lc.name.contains(searchQuery, ignoreCase = true)) &&
        (selectedPath == null || lc.path == selectedPath) &&
        (!showOnly5Star || lc.rarity == 5)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索 + 筛选
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索光锥...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors()
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = showOnly5Star,
                onClick = { showOnly5Star = !showOnly5Star },
                label = { Text("★5", fontSize = 12.sp) }
            )
        }
        // 命途筛选
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PathType.entries.take(7).forEach { path ->
                FilterChip(
                    selected = selectedPath == path,
                    onClick = { selectedPath = if (selectedPath == path) null else path },
                    label = { Text(path.displayName, fontSize = 12.sp) }
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { lc ->
                LightConeRow(lc) {
                    selectedLc = lc
                    showDetail = true
                }
            }
        }
    }

    if (showDetail && selectedLc != null) {
        LightConeDetailDialog(selectedLc!!) { showDetail = false }
    }
}

@Composable
private fun LightConeRow(lc: LightCone, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 稀有度 + 首字头像
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(
                    if (lc.rarity == 5) Color(0xFFFFD700).copy(alpha = 0.2f)
                    else Color(0xFFC0C0C0).copy(alpha = 0.2f)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(lc.name.take(1), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                     color = if (lc.rarity == 5) Color(0xFFFFD700) else Color(0xFFC0C0C0))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lc.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("★${lc.rarity}", fontSize = 12.sp,
                         color = if (lc.rarity == 5) Color(0xFFFFD700) else Color(0xFFC0C0C0))
                }
                Text("${lc.path.displayName} · ATK${lc.baseStats.attack.toInt()} HP${lc.baseStats.hp.toInt()}",
                     fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp),
                 tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LightConeDetailDialog(lc: LightCone, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${lc.name} ★${lc.rarity}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("适配命途: ${lc.path.displayName}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text("基础: HP${lc.baseStats.hp.toInt()} ATK${lc.baseStats.attack.toInt()} DEF${lc.baseStats.defense.toInt()} SPD${lc.baseStats.speed.toInt()}",
                     fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text("技能: ${lc.skill.name}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(lc.skill.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                lc.superimposeLevels.forEach { level ->
                    Text("精${level.level}: ${level.description}", fontSize = 12.sp,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ============================================================
// 遗器套装 Tab
// ============================================================
@Composable
private fun RelicSetTab(dataSource: InMemoryGameDataSource) {
    val allSets = remember { dataSource.getAllRelicSets() }
    var searchQuery by remember { mutableStateOf("") }
    var showOrnament by remember { mutableStateOf(false) }

    val filtered = allSets.filter { rs ->
        (searchQuery.isEmpty() || rs.name.contains(searchQuery, ignoreCase = true)) &&
        (!showOrnament || rs.type == RelicSetType.ORNAMENT)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索遗器套装...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors()
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = showOrnament,
                onClick = { showOrnament = !showOrnament },
                label = { Text("位面饰品", fontSize = 12.sp) }
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { set ->
                RelicSetCard(set)
            }
        }
    }
}

@Composable
private fun RelicSetCard(set: RelicSet) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(set.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Text(if (set.type == RelicSetType.ORNAMENT) "位面" else "遗器",
                     fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            set.setBonuses.forEach { bonus ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("${bonus.requiredCount}件套: ", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                         color = MaterialTheme.colorScheme.primary)
                    Text(bonus.effects.joinToString("、") { "${it.type.displayName} +${(it.value * 100).toInt()}%" },
                         fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (set.recommendedStats.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("推荐主词条: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                set.recommendedStats.forEach { (slot, stats) ->
                    Text("${slot.name}: ${stats.joinToString("/") { it.name }}",
                         fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ============================================================
// 敌人 Tab
// ============================================================
@Composable
private fun EnemyTab(dataSource: InMemoryGameDataSource) {
    val enemies = remember { dataSource.getAllEnemies() }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = enemies.filter { e ->
        searchQuery.isEmpty() || e.name.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("搜索敌人...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors()
        )

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.id }) { enemy ->
                EnemyCard(enemy)
            }
        }
    }
}

@Composable
private fun EnemyCard(enemy: Enemy) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(enemy.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text("Lv${enemy.level}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val loc = enemy.location
                if (!loc.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(loc, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
            }
            Spacer(Modifier.height(6.dp))

            // 弱点展示
            if (enemy.weakness.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("弱点: ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    enemy.weakness.forEach { elem ->
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(elem.color.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(elem.name, fontSize = 11.sp, color = elem.color, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            // 抗性
            if (enemy.resistance.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("抗性: ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    enemy.resistance.keys.forEach { elem ->
                        Text(elem.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                             modifier = Modifier.padding(end = 6.dp))
                    }
                }
            }
            // 韧性值
            Text("韧性: ${enemy.toughness}", fontSize = 12.sp,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 效果类型中文名 */
private val EffectType.displayName: String get() = when (this) {
    EffectType.ATK_UP -> "攻击力"
    EffectType.HP_UP -> "生命值"
    EffectType.DEF_UP -> "防御力"
    EffectType.SPD_UP -> "速度"
    EffectType.CRIT_RATE_UP -> "暴击率"
    EffectType.CRIT_DMG_UP -> "暴击伤害"
    EffectType.DMG_UP -> "伤害加成"
    EffectType.ELEMENTAL_DMG_UP -> "元素伤害"
    EffectType.DEF_DOWN -> "减防"
    EffectType.DEF_PENETRATION -> "无视防御"
    EffectType.RES_PENETRATION -> "无视抗性"
    EffectType.EFFECT_HIT_UP -> "效果命中"
    EffectType.EFFECT_RES_UP -> "效果抵抗"
    EffectType.BREAK_DMG_UP -> "击破伤害"
    EffectType.HEAL_BONUS_UP -> "治疗加成"
    EffectType.ENERGY_REGEN_UP -> "充能效率"
}

/** 元素颜色 */
private val ElementType.color: Color get() = when (this) {
    ElementType.物理 -> Color(0xFFA38C7C)
    ElementType.火 -> Color(0xFFE03939)
    ElementType.冰 -> Color(0xFF6BB5C5)
    ElementType.雷 -> Color(0xFFB45BC8)
    ElementType.风 -> Color(0xFF7CC47C)
    ElementType.量子 -> Color(0xFF878BBF)
    ElementType.虚数 -> Color(0xFFE1C25E)
}

/** 遗器槽位中文名 */
private val RelicSlot.name: String get() = when (this) {
    RelicSlot.头部 -> "头部"
    RelicSlot.手部 -> "手部"
    RelicSlot.躯干 -> "躯干"
    RelicSlot.脚部 -> "脚部"
    RelicSlot.位面球 -> "位面球"
    RelicSlot.连结绳 -> "连结绳"
}

/** RelicSetType 中文 */
private val RelicSetType.label: String get() = when (this) {
    RelicSetType.RELIC -> "遗器"
    RelicSetType.ORNAMENT -> "位面饰品"
}