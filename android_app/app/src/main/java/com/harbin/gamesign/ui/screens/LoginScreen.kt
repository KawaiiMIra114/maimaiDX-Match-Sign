package com.harbin.gamesign.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.harbin.gamesign.viewmodel.MainViewModel
import com.harbin.gamesign.viewmodel.UiState

enum class LoginStep {
    CHECK_NAME, LOGIN, REGISTER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    
    var step by remember { mutableStateOf(LoginStep.CHECK_NAME) }
    var isLoading by remember { mutableStateOf(false) }
    var hasNavigated by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
    
    // States
    val playerState by viewModel.currentPlayer.collectAsState()
    val authCheckState by viewModel.authCheckState.collectAsState()
    val message by viewModel.message.collectAsState(initial = null)

    // Scanner Logic
    var showScanner by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showScanner = true
        }
    }
    
    val scannedName by viewModel.scannedName.collectAsState(initial = null)
    LaunchedEffect(scannedName) {
        if (scannedName != null) {
            name = scannedName!!
        }
    }

    if (showScanner) {
        ScannerScreen(
            onResult = { uidStr ->
                showScanner = false
                try {
                    val uid = uidStr.toInt()
                    isLoading = true
                    viewModel.fetchPlayerById(uid)
                } catch (e: Exception) {
                    // ignore
                }
            },
            onClose = { showScanner = false }
        )
        return
    }

    // Image Picker
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        avatarUri = uri
    }

    // Logo Animation
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Auth Check Logic
    LaunchedEffect(authCheckState) {
        if (step == LoginStep.CHECK_NAME && authCheckState is UiState.Success) {
            val data = (authCheckState as UiState.Success).data
            isLoading = false
            if (data.exists) {
                if (data.registered) {
                    step = LoginStep.LOGIN
                } else {
                    step = LoginStep.REGISTER
                }
            } else {
                // User not found, handled in UI
            }
        } else if (authCheckState is UiState.Error) {
            isLoading = false
        }
    }

    // Login/Register Success Logic
    LaunchedEffect(playerState) {
        if (playerState is UiState.Success && !hasNavigated) {
            hasNavigated = true
            isLoading = false
            onLoginSuccess()
        } else if (playerState is UiState.Error) {
            isLoading = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo
                AnimatedVisibility(visible = true) {
                    Card(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(scale),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(50.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "术力口大赛",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.9f) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "subtitle"
                ) { targetStep ->
                    Text(
                        text = when(targetStep) {
                            LoginStep.CHECK_NAME -> "选手签到"
                            LoginStep.LOGIN -> "欢迎回来"
                            LoginStep.REGISTER -> "激活账号"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Error Message
                val currentError = if (step == LoginStep.CHECK_NAME) {
                    (authCheckState as? UiState.Error)?.message
                } else {
                    (playerState as? UiState.Error)?.message
                }
                
                val notFoundError = if (step == LoginStep.CHECK_NAME && authCheckState is UiState.Success && !(authCheckState as UiState.Success).data.exists) {
                    "未找到该选手，请确认姓名是否正确"
                } else null

                AnimatedVisibility(
                    visible = currentError != null || notFoundError != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = currentError ?: notFoundError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // Form Content
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            
                            (slideInHorizontally { width -> direction * width } + fadeIn(animationSpec = tween(400)))
                                .togetherWith(
                                    slideOutHorizontally { width -> -direction * width } + fadeOut(animationSpec = tween(400))
                                ).using(
                                    SizeTransform(clip = false) { _, _ ->
                                        spring(
                                            stiffness = Spring.StiffnessMediumLow,
                                            visibilityThreshold = IntSize.VisibilityThreshold
                                        )
                                    }
                                )
                        },
                        label = "login_form_content"
                    ) { targetStep ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            when (targetStep) {
                                LoginStep.CHECK_NAME -> {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = { Text("报名姓名") },
                                        leadingIcon = { Icon(Icons.Default.Person, null) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = {
                                            if (name.isNotBlank()) {
                                                isLoading = true
                                                viewModel.authCheck(name.trim())
                                            }
                                        })
                                    )
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    Button(
                                        onClick = {
                                            focusManager.clearFocus()
                                            if (name.isNotBlank()) {
                                                isLoading = true
                                                viewModel.authCheck(name.trim())
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        enabled = !isLoading && name.isNotBlank()
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Text("下一步")
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    OutlinedButton(
                                        onClick = {
                                            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        enabled = !isLoading
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("扫码签到")
                                    }
                                }
                                
                                LoginStep.LOGIN -> {
                                    // Read-only Name
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = {},
                                        label = { Text("姓名") },
                                        leadingIcon = { Icon(Icons.Default.Person, null) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = false,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text("密码") },
                                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                                        trailingIcon = {
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                                            }
                                        },
                                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = {
                                            if (password.isNotBlank()) {
                                                isLoading = true
                                                viewModel.authLogin(name, password)
                                            }
                                        })
                                    )
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    Button(
                                        onClick = {
                                            focusManager.clearFocus()
                                            if (password.isNotBlank()) {
                                                isLoading = true
                                                viewModel.authLogin(name, password)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        enabled = !isLoading && password.isNotBlank()
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Text("登录")
                                        }
                                    }
                                    
                                    TextButton(
                                        onClick = { 
                                            step = LoginStep.CHECK_NAME 
                                            viewModel.resetAuthCheck()
                                            password = ""
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    ) {
                                        Text("返回上一步")
                                    }
                                }
                                
                                LoginStep.REGISTER -> {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = {},
                                        label = { Text("姓名") },
                                        leadingIcon = { Icon(Icons.Default.Person, null) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        enabled = false,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text("设置密码") },
                                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                                        trailingIcon = {
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                                            }
                                        },
                                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    OutlinedTextField(
                                        value = confirmPassword,
                                        onValueChange = { confirmPassword = it },
                                        label = { Text("确认密码") },
                                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    // Avatar Picker
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .clickable { launcher.launch("image/*") },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (avatarUri != null) {
                                                AsyncImage(
                                                    model = avatarUri,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Icon(Icons.Default.AddAPhoto, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text("上传头像 (可选)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text("点击左侧图标选择", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    Button(
                                        onClick = {
                                            focusManager.clearFocus()
                                            if (password.isNotBlank() && password == confirmPassword) {
                                                isLoading = true
                                                viewModel.authRegister(name, password, avatarUri, context)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        enabled = !isLoading && password.isNotBlank() && password == confirmPassword
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Text("激活并签到")
                                        }
                                    }
                                    
                                    TextButton(
                                        onClick = { 
                                            step = LoginStep.CHECK_NAME 
                                            viewModel.resetAuthCheck()
                                            password = ""
                                            confirmPassword = ""
                                            avatarUri = null
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    ) {
                                        Text("返回上一步")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
