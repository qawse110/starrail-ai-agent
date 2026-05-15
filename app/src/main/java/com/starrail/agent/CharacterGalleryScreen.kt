package com.starrail.agent

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.starrail.agent.core.model.PathType
import com.starrail.agent.core.model.ElementType
import com.starrail.agent.core.model.Character as HsrCharacter
import com.starrail.agent.core.model.SkillType
import com.starrail.agent.player.db.PlayerDao
import com.starrail.agent.player.db.PlayerCharacterEntity
import java.util.UUID

/**
 * 角色图鉴页面
 * 网格展示所有角色，支持搜索/筛选，点击查看详情，标记拥有
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterGalleryScreen(
    dataSource: InMemoryGameDataSource,
    playerDao: PlayerDao?,
    onBack: () -> Unit
) {
    val characters = remember { dataSource.getAllCharacters() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPath by remember { mutableStateOf<PathType?>(null) }
    var selectedElement by remember { mutableStateOf<ElementType?>(null) }
    var showOwnedOnly by remember { mutableStateOf(false) }
    var selectedCharacter by remember { mutableStateOf<HsrCharacter?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }

    // 玩家拥有状态
    var ownedChars by remember { mutableStateOf(playerDao?.getAllCharacters() ?: emptyList()) }

    // 根据筛选条件过滤
    val filtered = characters.filter { c ->
        (searchQuery.isEmpty() || c.name.contains(searchQuery, ignoreCase = true)) &&
        (selectedPath == null || c.path == selectedPath) &&
        (selectedElement == null || c.element == selectedElement) &&
        (!showOwnedOnly || ownedChars.any { it.characterId == c.id })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色图鉴", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 拥有/全部切换
                    IconButton(onClick = { showOwnedOnly = !showOwnedOnly }) {
                        Icon(
                            if (showOwnedOnly) Icons.Default.CheckCircle else Icons.Default.Person,
                            contentDescription = if (showOwnedOnly) "显示全部" else "仅显示拥有",
                            tint = if (showOwnedOnly) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("搜索角色...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors()
            )

            // 筛选芯片
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 命途筛选
                PathType.entries.take(7).forEach { path ->
                    FilterChip(
                        selected = selectedPath == path,
                        onClick = { selectedPath = if (selectedPath == path) null else path },
                        label = { Text(path.displayName, fontSize = 12.sp) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                // 属性筛选
                ElementType.entries.forEach { elem ->
                    FilterChip(
                        selected = selectedElement == elem,
                        onClick = { selectedElement = if (selectedElement == elem) null else elem },
                        label = { Text(elem.name, fontSize = 12.sp) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(elem.color)
                            )
                        },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有匹配的角色", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered.size) { index ->
                        val character = filtered[index]
                        val isOwned = ownedChars.any { pc -> pc.characterId == character.id }
                        CharacterCard(
                            character = character,
                            isOwned = isOwned,
                            onClick = {
                                selectedCharacter = character
                                showDetailDialog = true
                            },
                            onToggleOwned = {
                                if (playerDao != null) {
                                    if (isOwned) {
                                        ownedChars.find { pc -> pc.characterId == character.id }?.let {
                                            playerDao.deleteCharacter(it)
                                        }
                                    } else {
                                        val entity = PlayerCharacterEntity(
                                            instanceId = UUID.randomUUID().toString(),
                                            characterId = character.id,
                                            level = 80,
                                            ascension = 6,
                                            eidolon = 0,
                                            skillLevelsJson = "{}",
                                            equippedLightConeId = null,
                                            equippedRelicsJson = "{}",
                                            tracesJson = "[]",
                                            isOwned = true
                                        )
                                        playerDao.insertCharacter(entity)
                                    }
                                    ownedChars = playerDao.getAllCharacters()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 角色详情对话框
    if (showDetailDialog && selectedCharacter != null) {
        CharacterDetailDialog(
            character = selectedCharacter!!,
            onDismiss = { showDetailDialog = false }
        )
    }
}

@Composable
private fun CharacterCard(
    character: HsrCharacter,
    isOwned: Boolean,
    onClick: () -> Unit,
    onToggleOwned: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isOwned) character.element.color.copy(alpha = 0.15f)
                             else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 稀有度星数
            Text(
                text = "★".repeat(character.rarity),
                fontSize = 10.sp,
                color = if (character.rarity == 5) Color(0xFFFFD700) else Color(0xFFC0C0C0)
            )
            Spacer(Modifier.height(4.dp))

            // 头像占位（用首字代替）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(character.element.color.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = character.name.take(1),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = character.element.color
                )
            }
            Spacer(Modifier.height(4.dp))

            // 角色名
            Text(
                text = character.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))

            // 命途
            Text(
                text = character.path.displayName,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 拥有标记
            if (isOwned) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "已拥有",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CharacterDetailDialog(
    character: HsrCharacter,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(character.element.color.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(character.name.take(1), fontWeight = FontWeight.Bold, color = character.element.color)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("${character.name} ★${character.rarity}", fontWeight = FontWeight.Bold)
                    Text("${character.path.displayName} · ${character.element.name}",
                         fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 基础属性
                Text("基础属性", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem("HP", "${character.baseStats.hp.toInt()}")
                    StatItem("ATK", "${character.baseStats.attack.toInt()}")
                    StatItem("DEF", "${character.baseStats.defense.toInt()}")
                    StatItem("SPD", "${character.baseStats.speed.toInt()}")
                }
                Spacer(Modifier.height(12.dp))

                // 技能列表
                Text("技能", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                character.skills.forEach { skill ->
                    Text(
                        text = "【${skill.type.displayName}】${skill.name}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = skill.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // 星魂列表
                if (character.eidolons.isNotEmpty()) {
                    Text("星魂", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    character.eidolons.forEach { eidolon ->
                        Text(
                            text = "★${eidolon.rank} ${eidolon.name}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = eidolon.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 元素类型 → 显示颜色 */
private val ElementType.color: Color get() = when (this) {
    ElementType.物理 -> Color(0xFFA38C7C)
    ElementType.火 -> Color(0xFFE03939)
    ElementType.冰 -> Color(0xFF6BB5C5)
    ElementType.雷 -> Color(0xFFB45BC8)
    ElementType.风 -> Color(0xFF7CC47C)
    ElementType.量子 -> Color(0xFF878BBF)
    ElementType.虚数 -> Color(0xFFE1C25E)
}

/** 技能类型中文名 */
private val SkillType.displayName: String get() = when (this) {
    SkillType.BASIC -> "普攻"
    SkillType.SKILL -> "战技"
    SkillType.ULTIMATE -> "终结技"
    SkillType.TALENT -> "天赋"
    SkillType.TECHNIQUE -> "秘技"
}