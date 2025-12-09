@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)

package com.harbin.gamesign.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harbin.gamesign.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlin.math.max

import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun NfcOverlay(
    viewModel: MainViewModel
) {
    val nfcState by viewModel.nfcState.collectAsState()

    // 动画状态控制
    val borderProgress = remember { Animatable(0f) }
    // 控制光效的可见性/透明度，用于最后的消失
    val lightAlpha = remember { Animatable(1f) }
    // 底部扩散光效透明度 (用于开场和退场)
    val bottomGlowAlpha = remember { Animatable(0f) }
    
    // 退场动画控制 (Genie Effect)
    var isExiting by remember { mutableStateOf(false) }
    val exitScale = remember { Animatable(1f) }
    val exitOffsetY = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }

    var showLoadingPopup by remember { mutableStateOf(false) }
    
    // 背景遮罩透明度：仅在弹窗出现后变暗
    // 当 isExiting 为 true 时，背景也应该淡出
    val bgAlpha = animateFloatAsState(
        targetValue = if (!isExiting && (showLoadingPopup || nfcState is MainViewModel.NfcState.Found || nfcState is MainViewModel.NfcState.Success || nfcState is MainViewModel.NfcState.Error)) 0.6f else 0.0f,
        animationSpec = tween(500),
        label = "bgAlpha"
    )

    // 重置状态的辅助函数
    fun triggerExit() {
        isExiting = true
    }

    // 监听退出状态，执行动画后重置 VM
    LaunchedEffect(isExiting) {
        if (isExiting) {
            // Genie Effect: Scale down, Move down, Fade out
            // 同时底部光雾亮起接住它
            
            // 1. 底部光雾亮起
            bottomGlowAlpha.animateTo(1f, tween(300))
            
            // 2. 卡片收缩下落
            launch { exitScale.animateTo(0f, tween(400, easing = FastOutSlowInEasing)) }
            launch { exitOffsetY.animateTo(300f, tween(400, easing = FastOutSlowInEasing)) }
            launch { exitAlpha.animateTo(0f, tween(300)) }
            
            // 3. 稍微等待
            delay(400)
            
            // 4. 重置 VM
            viewModel.resetNfcState()
            
            // 5. 重置本地状态 (为下一次准备)
            isExiting = false
            exitScale.snapTo(1f)
            exitOffsetY.snapTo(0f)
            exitAlpha.snapTo(1f)
            bottomGlowAlpha.snapTo(0f)
            showLoadingPopup = false
            borderProgress.snapTo(0f)
            lightAlpha.snapTo(1f)
        }
    }

    // 监听状态变化
    LaunchedEffect(nfcState) {
        when (nfcState) {
            is MainViewModel.NfcState.Reading -> {
                // 重置
                borderProgress.snapTo(0f)
                lightAlpha.snapTo(1f)
                bottomGlowAlpha.snapTo(0f)
                showLoadingPopup = false
                isExiting = false
                exitScale.snapTo(1f)
                exitOffsetY.snapTo(0f)
                exitAlpha.snapTo(1f)

                // 1. 边框光效蔓延 (极速: 0.4s)
                borderProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(400, easing = LinearEasing)
                )

                // 2. 蔓延到底部后，底部向上扩散光效
                bottomGlowAlpha.animateTo(1f, tween(300))
                
                // 3. 弹出椭圆形加载卡片
                showLoadingPopup = true
            }
            is MainViewModel.NfcState.Found -> {
                // 4. 状态切换为 Found，卡片将自动展开（由 UI 状态驱动）
                
                // 底部扩散光效消失
                bottomGlowAlpha.animateTo(0f, tween(300))
                
                // 延迟一小会儿
                delay(100)
                
                // 5. 光条缩回
                borderProgress.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
                
                // 6. 光回到原点后，直接消失
                lightAlpha.animateTo(0f, tween(100))
            }
            is MainViewModel.NfcState.Idle -> {
                if (!isExiting) {
                    borderProgress.snapTo(0f)
                    lightAlpha.snapTo(1f)
                    bottomGlowAlpha.snapTo(0f)
                    showLoadingPopup = false
                }
            }
            else -> {}
        }
    }

    if (nfcState is MainViewModel.NfcState.Idle && !isExiting) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha.value)) // 动态背景
    ) {
        // --- 1. 边框光效层 ---
        if (lightAlpha.value > 0f || bottomGlowAlpha.value > 0f) {
            // Pass pulse value or calculate it inside
            BorderLightLayer(borderProgress.value, lightAlpha.value, bottomGlowAlpha.value)
        }

        // --- 2. 弹窗/卡片层 ---
        // 只有在 Reading 阶段的后半段 (showLoadingPopup=true) 或者 Found/Success/Error 阶段显示
        // 或者是正在退出的阶段
        if (isExiting || showLoadingPopup || nfcState is MainViewModel.NfcState.Found || nfcState is MainViewModel.NfcState.Success || nfcState is MainViewModel.NfcState.Error) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = exitScale.value,
                        scaleY = exitScale.value,
                        translationY = exitOffsetY.value,
                        alpha = exitAlpha.value
                    )
            ) {
                PopupCardLayer(nfcState, viewModel, onCancel = { triggerExit() })
            }
        }
    }
}

@Composable
fun BorderLightLayer(progress: Float, globalAlpha: Float, bottomGlowAlpha: Float) {
    // 创建一个无限循环的脉冲动画，让光效更有生命力
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // 使用 Spacer + drawWithCache 代替 Canvas 以优化性能
    // Path 和 PathMeasure 对象的创建非常昂贵，我们只在 size 变化时重新创建它们
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .alpha(globalAlpha)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val cx = w / 2
                
                // 缓存路径计算结果
                val cornerRadius = 40.dp.toPx()
                val inset = 2.dp.toPx()
                
                // 左侧完整路径
                val fullPathLeft = Path().apply {
                    moveTo(cx, inset)
                    lineTo(inset + cornerRadius, inset)
                    quadraticBezierTo(inset, inset, inset, inset + cornerRadius)
                    lineTo(inset, h - inset - cornerRadius)
                    quadraticBezierTo(inset, h - inset, inset + cornerRadius, h - inset)
                    lineTo(cx, h - inset)
                }
                
                // 右侧完整路径
                val fullPathRight = Path().apply {
                    moveTo(cx, inset)
                    lineTo(w - inset - cornerRadius, inset)
                    quadraticBezierTo(w - inset, inset, w - inset, inset + cornerRadius)
                    lineTo(w - inset, h - inset - cornerRadius)
                    quadraticBezierTo(w - inset, h - inset, w - inset - cornerRadius, h - inset)
                    lineTo(cx, h - inset)
                }

                val measureLeft = PathMeasure()
                measureLeft.setPath(fullPathLeft, false)
                val lengthLeft = measureLeft.length
                
                val measureRight = PathMeasure()
                measureRight.setPath(fullPathRight, false)
                val lengthRight = measureRight.length

                // 用于绘制的复用 Path 对象
                val currentPathLeft = Path()
                val currentPathRight = Path()

                onDrawBehind {
                    // 纯白光效
                    val coreColor = Color.White
                    val glowColor = Color.White
                    
                    // 顶部初始光点 (带呼吸)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(coreColor, glowColor.copy(alpha = 0.5f), Color.Transparent),
                            center = Offset(cx, 0f),
                            radius = 20.dp.toPx() * pulse
                        ),
                        radius = 20.dp.toPx() * pulse,
                        center = Offset(cx, 0f)
                    )

                    if (progress > 0f) {
                        // 重置路径以便复用
                        currentPathLeft.reset()
                        currentPathRight.reset()

                        // 截取当前进度的路径
                        measureLeft.getSegment(0f, lengthLeft * progress, currentPathLeft, true)
                        measureRight.getSegment(0f, lengthRight * progress, currentPathRight, true)

                        // 绘制多层光效
                        // 1. 宽大的外发光 (Low Alpha)
                        val outerStroke = Stroke(width = 12.dp.toPx() * pulse, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        drawPath(currentPathLeft, color = glowColor.copy(alpha = 0.2f), style = outerStroke)
                        drawPath(currentPathRight, color = glowColor.copy(alpha = 0.2f), style = outerStroke)
                        
                        // 2. 中等辉光 (Medium Alpha)
                        val midStroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        drawPath(currentPathLeft, color = glowColor.copy(alpha = 0.5f * alphaPulse), style = midStroke)
                        drawPath(currentPathRight, color = glowColor.copy(alpha = 0.5f * alphaPulse), style = midStroke)
                        
                        // 3. 核心亮白 (High Alpha)
                        val coreStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        drawPath(currentPathLeft, color = coreColor.copy(alpha = alphaPulse), style = coreStroke)
                        drawPath(currentPathRight, color = coreColor.copy(alpha = alphaPulse), style = coreStroke)
                    }
                    
                    // 底部向上扩散的光效 (Bottom Up Spread) - 纯白
                    if (bottomGlowAlpha > 0f) {
                        val spreadHeight = h * 0.35f 
                        
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, glowColor.copy(alpha = 0.4f * bottomGlowAlpha)),
                                startY = h - spreadHeight,
                                endY = h
                            ),
                            topLeft = Offset(0f, h - spreadHeight),
                            size = Size(w, spreadHeight)
                        )
                        
                        // 底部加强光晕 (带呼吸)
                         drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(coreColor.copy(alpha = 0.6f * bottomGlowAlpha), glowColor.copy(alpha = 0.3f * bottomGlowAlpha), Color.Transparent),
                                center = Offset(cx, h),
                                radius = w * 0.5f * pulse
                            ),
                            center = Offset(cx, h),
                            radius = w * 0.5f * pulse
                        )
                    }
                }
            }
    )
}

@Composable
fun PopupCardLayer(nfcState: MainViewModel.NfcState, viewModel: MainViewModel, onCancel: () -> Unit) {
    val isExpanded = nfcState is MainViewModel.NfcState.Found || nfcState is MainViewModel.NfcState.Success || nfcState is MainViewModel.NfcState.Error
    
    val transition = updateTransition(targetState = isExpanded, label = "card_morph")
    
    val width by transition.animateDp(label = "width", transitionSpec = { tween(500, easing = FastOutSlowInEasing) }) { expanded ->
        if (expanded) 320.dp else 200.dp 
    }
    
    val height by transition.animateDp(label = "height", transitionSpec = { tween(500, easing = FastOutSlowInEasing) }) { expanded ->
        if (expanded) 420.dp else 56.dp 
    }
    
    val cornerRadius by transition.animateDp(label = "corner", transitionSpec = { tween(500, easing = FastOutSlowInEasing) }) { expanded ->
        if (expanded) 28.dp else 28.dp 
    }
    
    val alignmentBias by transition.animateFloat(label = "bias", transitionSpec = { tween(500, easing = FastOutSlowInEasing) }) { expanded ->
        if (expanded) 0f else 0.8f 
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center 
    ) {
        Surface(
            modifier = Modifier
                .offset(y = 300.dp * alignmentBias) 
                .size(width, height), 
            shape = RoundedCornerShape(cornerRadius),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp,
            shadowElevation = 12.dp
        ) {
            // 使用 AnimatedContent 实现无缝切换
            AnimatedContent(
                targetState = nfcState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) with fadeOut(animationSpec = tween(300))
                },
                label = "content_anim"
            ) { targetState ->
                if (targetState is MainViewModel.NfcState.Reading) {
                    // Loading State
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "少女祈祷中...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Expanded Card State
                    ExpandedCardContent(targetState, viewModel, onCancel)
                }
            }
        }
    }
}

@Composable
fun ExpandedCardContent(nfcState: MainViewModel.NfcState, viewModel: MainViewModel, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. 图标区域 (Animated)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(targetState = nfcState, label = "icon_anim") { state ->
                when (state) {
                    is MainViewModel.NfcState.Found -> ThickCheckmarkIcon()
                    is MainViewModel.NfcState.Success -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Success",
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    is MainViewModel.NfcState.Error -> Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = "Error",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    else -> {}
                }
            }
        }

        // 2. 文字区域
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(targetState = nfcState, label = "text_anim") { state ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state is MainViewModel.NfcState.Found) {
                        Text(
                            text = "读取到 ${state.type} Ban卡",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "是否立即兑换？",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else if (state is MainViewModel.NfcState.Success) {
                        Text("兑换成功！", style = MaterialTheme.typography.titleLarge)
                    } else if (state is MainViewModel.NfcState.Error) {
                        Text(state.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // 3. 按钮区域
        AnimatedContent(targetState = nfcState, label = "btn_anim") { state ->
            if (state is MainViewModel.NfcState.Found) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.confirmRedeem() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("那正是我想要的！", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onCancel() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.5f), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消", fontSize = 16.sp)
                    }
                }
            } else {
                 Button(
                    onClick = { onCancel() },
                    modifier = Modifier.fillMaxWidth(),
                     colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.5f))
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
fun ThickCheckmarkIcon() {
    Canvas(modifier = Modifier.size(100.dp)) {
        val strokeWidth = 8.dp.toPx()
        val color = Color(0xFF4CAF50)
        drawCircle(color = color, style = Stroke(width = strokeWidth))
        val path = Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.5f)
            lineTo(size.width * 0.45f, size.height * 0.7f)
            lineTo(size.width * 0.75f, size.height * 0.3f)
        }
        drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
