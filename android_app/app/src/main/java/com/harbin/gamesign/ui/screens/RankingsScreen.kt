package com.harbin.gamesign.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harbin.gamesign.data.model.RankingItem
import com.harbin.gamesign.ui.theme.*
import com.harbin.gamesign.viewmodel.MainViewModel
import com.harbin.gamesign.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val rankingsState by viewModel.rankings.collectAsState()
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(selectedGroup) {
        viewModel.loadRankings(selectedGroup)
    }
    
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("排行榜") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = { viewModel.loadRankings(selectedGroup) }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 分组筛选
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedGroup == null,
                    onClick = { selectedGroup = null },
                    label = { Text("全部") },
                    leadingIcon = if (selectedGroup == null) {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null
                )
                FilterChip(
                    selected = selectedGroup == "beginner",
                    onClick = { selectedGroup = "beginner" },
                    label = { Text("萌新组") },
                    leadingIcon = if (selectedGroup == "beginner") {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null
                )
                FilterChip(
                    selected = selectedGroup == "advanced",
                    onClick = { selectedGroup = "advanced" },
                    label = { Text("进阶组") },
                    leadingIcon = if (selectedGroup == "advanced") {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null
                )
                FilterChip(
                    selected = selectedGroup == "peak",
                    onClick = { selectedGroup = "peak" },
                    label = { Text("巅峰组") },
                    leadingIcon = if (selectedGroup == "peak") {
                        { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }
            
            // 排行榜列表
            when (val state = rankingsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Error,
                                null,
                                Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(state.message)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadRankings(selectedGroup) }) {
                                Text("重试")
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Leaderboard,
                                    null,
                                    Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "暂无排名数据",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                start = 16.dp,
                                end = 16.dp,
                                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(state.data) { index, item ->
                                RankingCard(item, index)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingCard(item: RankingItem, index: Int) {
    val animatedVisibility = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        animatedVisibility.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = index * 50,
                easing = EaseOutBack
            )
        )
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (item.rank) {
                1 -> GoldColor.copy(alpha = 0.15f)
                2 -> SilverColor.copy(alpha = 0.15f)
                3 -> BronzeColor.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when (item.rank) {
                            1 -> GoldColor
                            2 -> SilverColor
                            3 -> BronzeColor
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.rank <= 3) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = when (item.rank) {
                            1 -> GoldColor.copy(alpha = 0.3f)
                            2 -> SilverColor.copy(alpha = 0.3f)
                            else -> BronzeColor.copy(alpha = 0.3f)
                        }.let { MaterialTheme.colorScheme.onPrimary }
                    )
                }
                Text(
                    text = "${item.rank}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (item.rank <= 3) 
                        MaterialTheme.colorScheme.onPrimary 
                    else MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 选手信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.groupLabel ?: item.group,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 成绩
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.score?.let { String.format("%.4f", it) } ?: "-",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "分",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
