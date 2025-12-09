package com.harbin.gamesign.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.PaddingValues
import coil.compose.AsyncImage
import com.harbin.gamesign.data.api.ApiClient
import com.harbin.gamesign.data.model.*
import com.harbin.gamesign.ui.theme.*
import com.harbin.gamesign.viewmodel.MainViewModel
import com.harbin.gamesign.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToRankings: () -> Unit,
    onLogout: () -> Unit
) {
    val playerState by viewModel.currentPlayer.collectAsState()
    val matchInfoState by viewModel.matchInfo.collectAsState()
    val scrollState = rememberScrollState()
    var showScoreDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showForfeitDialog by remember { mutableStateOf(false) }
    
    // 序号变更弹窗
    var showMatchNumberChangedDialog by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshPlayer(quiet = true)
        viewModel.loadSongDrawState()
        
        viewModel.matchNumberChanged.collect { newNum ->
            showMatchNumberChangedDialog = newNum
        }
    }
    
    if (showMatchNumberChangedDialog != null) {
        AlertDialog(
            onDismissRequest = { showMatchNumberChangedDialog = null },
            title = { Text("序号已更改") },
            text = { Text("您的新序号为：${showMatchNumberChangedDialog}") },
            confirmButton = {
                TextButton(onClick = { showMatchNumberChangedDialog = null }) {
                    Text("知道了")
                }
            }
        )
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "术力口大赛",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (playerState is UiState.Success)
                                "欢迎，${(playerState as UiState.Success<Player>).data.name}"
                            else "请先登录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToRankings) {
                        Icon(Icons.Outlined.Leaderboard, "排行榜")
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.Logout, "退出登录")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = playerState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.align(Alignment.Center)) {}
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(state.message)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refreshPlayer() }) {
                            Text("重试")
                        }
                    }
                }
                is UiState.Success -> {
                    val player = state.data
                    val matchInfo = (matchInfoState as? UiState.Success)?.data
                    
                    if (!player.matchStarted) {
                        MatchNotStartedOverlay(
                            title = "比赛未开始",
                            message = "请您耐心等待！"
                        )
                    } else if (player.promotionStatus == "timeout_eliminated") {
                        MatchNotStartedOverlay(
                            title = "取消参赛资格",
                            message = "您未能在签到截止前到达比赛现场，已取消您的参赛资格。",
                            icon = Icons.Outlined.Cancel
                        )
                    } else if (player.forfeited) {
                        MatchNotStartedOverlay(
                            title = "您已手动弃权",
                            message = "很遗憾您未能参与整场比赛，但我们仍然欢迎您下次继续参与！",
                            icon = Icons.Outlined.Flag
                        )
                    } else {
                        PlayerContent(
                            player = player,
                            matchInfo = matchInfo,
                            viewModel = viewModel,
                            scrollState = scrollState,
                            onShowScoreDialog = { showScoreDialog = true },
                            onShowForfeitDialog = { showForfeitDialog = true },
                            onNavigateToRankings = onNavigateToRankings
                        )
                    }
                }
            }
        }
    }
    
    if (showScoreDialog) {
        ScoreDialog(
            onDismiss = { showScoreDialog = false },
            onSubmit = { score ->
                val phase = if (playerState is UiState.Success && ((playerState as UiState.Success<Player>).data.promotionStatus == "revival")) "revival" else "round1"
                viewModel.submitScore(score, phase)
                showScoreDialog = false
            }
        )
    }
    
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出登录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logout()
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    if (showForfeitDialog) {
        AlertDialog(
            onDismissRequest = { showForfeitDialog = false },
            title = { Text("确认弃权") },
            text = { Text("确定要放弃本次比赛吗？此操作无法撤销。如果您正在进行 1v1 对战，您的对手将自动晋级。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.forfeitPlayer()
                        showForfeitDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认弃权")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForfeitDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun MatchNotStartedOverlay(
    title: String,
    message: String,
    icon: ImageVector = Icons.Outlined.HourglassEmpty
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerContent(
    player: Player,
    matchInfo: MatchInfo?,
    viewModel: MainViewModel,
    scrollState: androidx.compose.foundation.ScrollState,
    onShowScoreDialog: () -> Unit,
    onShowForfeitDialog: () -> Unit,
    onNavigateToRankings: () -> Unit
) {
    val songDrawState by viewModel.songDrawState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
    ) {
        PlayerInfoCard(player)
        Spacer(modifier = Modifier.height(16.dp))
        
        // 曲目抽选卡片 (仅当组别匹配或抽选状态为空/idle时显示，或者不匹配时隐藏？)
        // 需求：只能看到自己组别或公共的抽选
        // 逻辑：如果 songDrawState.group == player.group，显示
        // 如果 songDrawState 为 idle，是否显示？ -> 显示 "等待抽选"
        // 如果 songDrawState.group != player.group -> 隐藏，或显示 "其他组正在抽选"
        
        if (songDrawState is UiState.Success) {
            val state = (songDrawState as UiState.Success<SongDrawState>).data
            // 只有当抽选状态的组别与玩家组别一致，或者状态为 idle 时才显示
            // 另外，如果 state.group 为空 (idle)，也显示
            if (state.group == null || state.group == player.group) {
                SongDrawCard(songDrawState)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        if (matchInfo != null) {
            OneVsOneMatchCard(
                player = player,
                matchInfo = matchInfo,
                onSubmitPeakSong = { name, diff -> viewModel.submitPeakSong(name, diff) },
                onBanPeakSong = { viewModel.banPeakSong() }
            )
        } else {
            if (player.group == "peak" && (player.promotionStatus == "top4_peak" || player.promotionStatus == "final")) {
                PeakAwaitMatchCard()
            } else {
                MatchStatusCard(player)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val canToggleMachine = !player.isEliminated && !player.forfeited && player.promotionStatus !in listOf("eliminated", "top16_out", "top8_out")
            
            // 是否应该显示上机按钮
            // 逻辑：如果当前阶段的比赛已完成（提交了成绩），则隐藏上机按钮，直到被标记为下一阶段（如复活赛、晋级赛）
            val shouldShowMachineButton = when {
                // 1. 海选阶段：如果还没提交成绩，显示
                player.scoreRound1 == null -> true
                // 2. 复活赛阶段：如果状态是 revival 且还没提交复活赛成绩，显示
                player.promotionStatus == "revival" && player.scoreRevival == null -> true
                // 3. 晋级赛阶段 (16强及以后)：始终显示（因为可能有多轮比赛或需要上机进行对战）
                player.isAdvancedStage -> true
                // 其他情况（如海选已提交但状态仍为空，或已淘汰）：隐藏
                else -> false
            }

            if (shouldShowMachineButton) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = if (player.onMachine) Icons.Default.PersonOff else Icons.Default.Person,
                    text = if (player.onMachine) "下机" else "上机",
                    containerColor = if (player.onMachine) 
                        MaterialTheme.colorScheme.errorContainer 
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (player.onMachine)
                        MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    enabled = canToggleMachine,
                    onClick = { viewModel.toggleMachine() }
                )
            }
            
            // 只有在可以提交成绩且已上机时才显示按钮
            if (player.canSubmitScore && player.onMachine) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Upload,
                    text = if (player.promotionStatus == "revival") "提交复活赛成绩" else "提交成绩",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onShowScoreDialog
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        ActionButton(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Leaderboard,
            text = "查看排行榜",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onNavigateToRankings
        )
        
        // 弃权按钮 (仅在非淘汰且非完成状态显示)
        val canForfeit = player.promotionStatus !in listOf("eliminated", "top8_out", "top16_out", "champion", "runner_up", "third", "fourth") && !player.forfeited
        if (canForfeit) {
             Spacer(modifier = Modifier.height(12.dp))
             TextButton(
                 onClick = onShowForfeitDialog,
                 modifier = Modifier.fillMaxWidth(),
                 colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
             ) {
                 Text("弃权")
             }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
    AnimatedVisibility(
        visible = player.scoreRound1 != null,
        enter = fadeIn(animationSpec = tween(500)) +
                slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialOffsetY = { it / 2 }
                ) +
                expandVertically(animationSpec = tween(500)),
        exit = fadeOut(animationSpec = tween(300)) + shrinkVertically()
    ) {
        ScoreInfoCard(player)
    }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PlayerInfoCard(player: Player) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (player.avatarUrl != null) {
                        val baseUrl = ApiClient.BASE_URL.trimEnd('/')
                        val relUrl = player.avatarUrl.trimStart('/')
                        val fullUrl = if (player.avatarUrl.startsWith("http")) player.avatarUrl else "$baseUrl/$relUrl"
                        
                        AsyncImage(
                            model = fullUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = player.name.take(1),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = { },
                            label = { Text(player.displayGroup) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        
                        if (player.rating != null && player.rating > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = { },
                                label = { Text("Rating: ${player.rating}") }
                            )
                        }
                    }
                }
            }
            
            if (player.matchNumber != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    InfoItem(
                        icon = Icons.Outlined.Numbers,
                        label = "比赛序号",
                        value = "#${player.matchNumber}"
                    )
                    
                    InfoItem(
                        icon = if (player.checkedIn) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        label = "签到状态",
                        value = if (player.checkedIn) "已签到" else "未签到",
                        valueColor = if (player.checkedIn) StatusGreen else StatusOrange
                    )
                    
                    InfoItem(
                        icon = if (player.onMachine) Icons.Default.Gamepad else Icons.Outlined.Gamepad,
                        label = "上机状态",
                        value = if (player.onMachine) "比赛中" else "等待中",
                        valueColor = if (player.onMachine) StatusBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoItem(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun SongDrawCard(songDrawState: UiState<SongDrawState>) {
    when (val state = songDrawState) {
        is UiState.Success -> {
            val drawState = state.data
            
            // 只在 rolling 或 finished 状态显示
            if (drawState.status == "rolling" || drawState.status == "finished") {
                val infiniteTransition = rememberInfiniteTransition(label = "song_rolling")
                val animatedAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "rolling_alpha"
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (drawState.status == "rolling")
                            SongCardRollingStart.copy(alpha = animatedAlpha)
                        else
                            SongCardGradientStart
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (drawState.status == "rolling") "曲目抽选中..." else "抽选曲目",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        if (drawState.status == "finished" && !drawState.selectedSongs.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            drawState.selectedSongs.forEachIndexed { index, song ->
                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(animationSpec = tween(500, delayMillis = index * 200)) +
                                            slideInVertically(
                                                animationSpec = tween(500, delayMillis = index * 200),
                                                initialOffsetY = { it / 2 }
                                            )
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.2f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = GoldColor
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            if (song.imageUrl != null) {
                                                val baseUrl = ApiClient.BASE_URL.trimEnd('/')
                                                val relUrl = song.imageUrl.trimStart('/')
                                                val fullUrl = if (song.imageUrl.startsWith("http")) song.imageUrl else "$baseUrl/$relUrl"
                                                
                                                AsyncImage(
                                                    model = fullUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.LightGray.copy(alpha = 0.3f)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                            }
                                            Text(
                                                text = song.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (drawState.phase != null || drawState.group != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row {
                                if (drawState.phase != null) {
                                    Text(
                                        text = when (drawState.phase) {
                                            "qualifier" -> "海选赛"
                                            "revival" -> "复活赛"
                                            "semifinal" -> "半决赛"
                                            "final" -> "决赛"
                                            else -> drawState.phase
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                if (drawState.phase != null && drawState.group != null) {
                                    Text(
                                        text = " · ",
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                if (drawState.group != null) {
                                    Text(
                                        text = if (drawState.group == "beginner") "萌新组" else (if (drawState.group == "peak") "巅峰组" else "进阶组"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        else -> { /* 加载中或错误时不显示 */ }
    }
}

@Composable
private fun MatchStatusCard(player: Player) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(player.statusText) {
        isVisible = player.statusText.isNotEmpty()
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(600)) + 
                slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialOffsetY = { -it / 2 }
                ) +
                expandVertically(animationSpec = tween(400)),
        exit = fadeOut() + shrinkVertically()
    ) {
        // 根据状态选择颜色和图标
        val (backgroundColor, iconTint, icon) = when {
            player.promotionStatus == "champion" -> Triple(
                GoldColor.copy(alpha = 0.25f),
                GoldColor,
                Icons.Default.EmojiEvents
            )
            player.promotionStatus == "runner_up" -> Triple(
                SilverColor.copy(alpha = 0.25f),
                SilverColor,
                Icons.Default.EmojiEvents
            )
            player.promotionStatus == "third" -> Triple(
                BronzeColor.copy(alpha = 0.25f),
                BronzeColor,
                Icons.Default.EmojiEvents
            )
            player.isPromoted -> Triple(
                PromotedGreenLight.copy(alpha = 0.3f),
                PromotedGreen,
                Icons.Default.TrendingUp
            )
            player.isEliminated -> Triple(
                EliminatedRedLight.copy(alpha = 0.3f),
                EliminatedRed,
                Icons.Default.TrendingDown
            )
            player.promotionStatus == "revival" -> Triple(
                RevivalPurpleLight.copy(alpha = 0.3f),
                RevivalPurple,
                Icons.Default.Replay
            )
            else -> Triple(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primary,
                Icons.Default.Info
            )
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标容器，增加背景强调
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = iconTint
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "比赛状态",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = player.statusText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = iconTint
                    )
                }
                
                // 状态标签
                if (player.isPromoted && player.promotionStatus !in listOf("champion", "runner_up", "third")) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PromotedGreen
                    ) {
                        Text(
                            text = "晋级",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else if (player.isEliminated) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EliminatedRed
                    ) {
                        Text(
                            text = "淘汰",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreInfoCard(player: Player) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "比赛成绩",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            if (player.scoreRound1 != null) {
                ScoreRow("海选成绩", player.scoreRound1)
            }
            if (player.scoreRevival != null) {
                Spacer(modifier = Modifier.height(8.dp))
                ScoreRow("复活赛成绩", player.scoreRevival)
            }
        }
    }
}

@Composable
private fun ScoreRow(label: String, score: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = String.format("%.4f", score),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        ),
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun ScoreDialog(
    onDismiss: () -> Unit,
    onSubmit: (Double) -> Unit
) {
    var scoreText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提交成绩") },
        text = {
            OutlinedTextField(
                value = scoreText,
                onValueChange = { scoreText = it },
                label = { Text("请输入成绩") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { scoreText.toDoubleOrNull()?.let { onSubmit(it) } },
                enabled = scoreText.toDoubleOrNull() != null
            ) {
                Text("提交")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun OneVsOneMatchCard(
    player: Player,
    matchInfo: MatchInfo,
    onSubmitPeakSong: (String, Int) -> Unit,
    onBanPeakSong: () -> Unit
) {
    var showPeakDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SportsKabaddi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "当前对阵 (${matchInfo.phase})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if(player.group == "peak") "巅峰组 BO1" else "1v1 对决",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            
            // 对手信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("对手", style = MaterialTheme.typography.labelMedium)
                    Text(
                        matchInfo.opponent.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (matchInfo.opponent.forfeited) {
                        Text("(已弃权)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (matchInfo.opponent.rating != null) {
                    AssistChip(onClick = {}, label = { Text("Rating: ${matchInfo.opponent.rating}") })
                }
            }
            
            // 巅峰组特殊逻辑
            if (player.group == "peak") {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("选曲与 Ban (BO1)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                // 我方选曲
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("我方: ", style = MaterialTheme.typography.labelMedium)
                    if (matchInfo.mySelection != null) {
                         Text("${matchInfo.mySelection.songName} (Lv.${matchInfo.mySelection.difficulty})", fontWeight = FontWeight.Medium)
                    } else {
                        if (matchInfo.wasBanned) {
                            Text("您的曲目被 Ban，请重新提交！", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        } else {
                            TextButton(onClick = { showPeakDialog = true }) {
                                Text("提交自选曲")
                            }
                        }
                    }
                    // Separate check: Show submit button even if banned (since mySelection is null)
                    if (matchInfo.mySelection == null && matchInfo.wasBanned) {
                         TextButton(onClick = { showPeakDialog = true }) {
                            Text("重新提交")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 对方选曲
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("对方: ", style = MaterialTheme.typography.labelMedium)
                    if (matchInfo.opSelection != null) {
                        if (matchInfo.opSelection.hidden) {
                            Text("已提交 (等待所有选手完成...)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("${matchInfo.opSelection.songName} (Lv.${matchInfo.opSelection.difficulty})", fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // Ban 按钮
                            if (!matchInfo.banUsed && !matchInfo.hasBannedThisMatch) {
                                Button(
                                    onClick = onBanPeakSong,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("BAN", fontSize = 12.sp)
                                }
                            } else if (matchInfo.hasBannedThisMatch) {
                                Text("(已 Ban)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    } else {
                        Text("等待选择...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    
    if (showPeakDialog) {
        PeakSongDialog(
            onDismiss = { showPeakDialog = false },
            onSubmit = { name, diff -> 
                onSubmitPeakSong(name, diff)
                showPeakDialog = false
            }
        )
    }
}

@Composable
private fun PeakSongDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, Int) -> Unit
) {
    var songName by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提交自选曲") },
        text = {
            Column {
                OutlinedTextField(
                    value = songName,
                    onValueChange = { songName = it },
                    label = { Text("曲目名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = difficulty,
                    onValueChange = { if (it.all { char -> char.isDigit() }) difficulty = it },
                    label = { Text("难度等级 (1-15)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    val diff = difficulty.toIntOrNull()
                    if (songName.isNotBlank() && diff != null) {
                        onSubmit(songName, diff)
                    }
                },
                enabled = songName.isNotBlank() && difficulty.isNotEmpty()
            ) {
                Text("提交")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
@Composable
private fun PeakAwaitMatchCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("巅峰组选曲", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "等待后台生成对阵后，您即可提交自选曲；当双方都提交后将显示对手选曲，并可使用一次 BAN 技能。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "提示：4进2与决赛阶段仅显示对战与选曲，不需要提交分数。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
