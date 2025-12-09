package com.harbin.gamesign

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.harbin.gamesign.ui.screens.*
import com.harbin.gamesign.ui.theme.GameSignTheme
import com.harbin.gamesign.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameSignTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameSignApp(intent = intent)
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GameSignApp(intent: android.content.Intent? = null) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    
    // 处理 Deep Link / Intent
    LaunchedEffect(intent) {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            val data = intent.data
            // 假设 scheme 是 gamesign://open
            if (data?.scheme == "gamesign" && data.host == "open") {
                // 这里可以根据 path 跳转到特定页面，例如 /rankings
                // 如果需要可以在这里做导航逻辑
                // 目前只处理打开应用，所以不做额外操作，默认进入应用流程
            }
        }
    }

    // 消息提示
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.message.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    
    // 根据登录状态决定起始页面
    val startDestination = if (isLoggedIn) "home" else "login"
    
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { 300 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -300 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -300 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { 300 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToRankings = {
                    navController.navigate("rankings")
                },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        
        composable("rankings") {
            RankingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
