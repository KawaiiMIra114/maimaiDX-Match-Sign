package com.harbin.gamesign.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harbin.gamesign.data.UserPreferences
import com.harbin.gamesign.data.api.ApiClient
import com.harbin.gamesign.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val prefs = UserPreferences(application)
    private val api = ApiClient.api
    
    // 登录状态
    val isLoggedIn: StateFlow<Boolean> = prefs.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    val savedPlayerId: StateFlow<Int?> = prefs.playerId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    val savedPlayerName: StateFlow<String?> = prefs.playerName
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    // 当前选手信息
    private val _currentPlayer = MutableStateFlow<UiState<Player>>(UiState.Loading)
    val currentPlayer: StateFlow<UiState<Player>> = _currentPlayer.asStateFlow()
    
    // 仪表盘统计
    private val _dashboard = MutableStateFlow<UiState<DashboardStats>>(UiState.Loading)
    val dashboard: StateFlow<UiState<DashboardStats>> = _dashboard.asStateFlow()
    
    // 排行榜
    private val _rankings = MutableStateFlow<UiState<List<RankingItem>>>(UiState.Loading)
    val rankings: StateFlow<UiState<List<RankingItem>>> = _rankings.asStateFlow()
    
    // 曲目抽选状态
    private val _songDrawState = MutableStateFlow<UiState<SongDrawState>>(UiState.Loading)
    val songDrawState: StateFlow<UiState<SongDrawState>> = _songDrawState.asStateFlow()

    // 对战信息 (Phase 3)
    private val _matchInfo = MutableStateFlow<UiState<MatchInfo?>>(UiState.Success(null))
    val matchInfo: StateFlow<UiState<MatchInfo?>> = _matchInfo.asStateFlow()
    
    // 消息提示
    private val _message = MutableSharedFlow<String>()
    val message: SharedFlow<String> = _message.asSharedFlow()

    // 序号变更通知 (New Match Number)
    private val _matchNumberChanged = MutableSharedFlow<Int>()
    val matchNumberChanged: SharedFlow<Int> = _matchNumberChanged.asSharedFlow()
    
    init {
        viewModelScope.launch {
            savedPlayerId.collect { id ->
                if (id != null) {
                    refreshPlayer(id, quiet = true)
                    startGlobalPolling(id)
                }
            }
        }
    }
    
    // 签到
    fun checkin(name: String) {
        viewModelScope.launch {
            _currentPlayer.value = UiState.Loading
            try {
                val response = api.checkin(CheckinRequest(name))
                if (response.success && response.data != null) {
                    _currentPlayer.value = UiState.Success(response.data)
                    prefs.savePlayer(response.data.id, response.data.name)
                    _message.emit(response.message ?: "签到成功！")
                } else {
                    _currentPlayer.value = UiState.Error(response.message ?: "签到失败")
                    _message.emit(response.message ?: "签到失败")
                }
            } catch (e: Exception) {
                _currentPlayer.value = UiState.Error("网络错误：${e.message}")
                _message.emit("网络错误，请检查网络连接")
            }
        }
    }
    
    // Auth Logic
    private val _authCheckState = MutableStateFlow<UiState<CheckStatusResponse>>(UiState.Success(CheckStatusResponse(false, false, null)))
    val authCheckState: StateFlow<UiState<CheckStatusResponse>> = _authCheckState.asStateFlow()

    fun resetAuthCheck() {
         _authCheckState.value = UiState.Success(CheckStatusResponse(false, false, null))
    }

    fun authCheck(name: String) {
        viewModelScope.launch {
            _authCheckState.value = UiState.Loading
            try {
                val res = api.checkPlayerStatus(CheckStatusRequest(name))
                if (res.success && res.data != null) {
                    _authCheckState.value = UiState.Success(res.data)
                } else {
                    _authCheckState.value = UiState.Error(res.message ?: "查询失败")
                }
            } catch (e: Exception) {
                _authCheckState.value = UiState.Error("网络错误: ${e.message}")
            }
        }
    }

    // Scanned Name
    private val _scannedName = MutableSharedFlow<String>()
    val scannedName: SharedFlow<String> = _scannedName.asSharedFlow()

    fun fetchPlayerById(id: Int) {
        viewModelScope.launch {
            try {
                val response = api.getPlayer(id)
                if (response.success && response.data != null) {
                    val name = response.data.name
                    _scannedName.emit(name)
                    authCheck(name)
                } else {
                    _message.emit("无效的二维码")
                }
            } catch (e: Exception) {
                _message.emit("扫描失败：${e.message}")
            }
        }
    }

    fun authLogin(name: String, pass: String) {
        viewModelScope.launch {
            _currentPlayer.value = UiState.Loading
            try {
                val res = api.login(LoginRequest(name, pass))
                if (res.success) {
                     checkin(name) 
                } else {
                    _currentPlayer.value = UiState.Error(res.message ?: "登录失败")
                    _message.emit(res.message ?: "登录失败")
                }
            } catch (e: Exception) {
                _currentPlayer.value = UiState.Error("网络错误: ${e.message}")
            }
        }
    }

    fun authRegister(name: String, pass: String, avatarUri: Uri?, context: Context) {
        viewModelScope.launch {
            _currentPlayer.value = UiState.Loading
            try {
                val namePart = name.toRequestBody("text/plain".toMediaTypeOrNull())
                val passPart = pass.toRequestBody("text/plain".toMediaTypeOrNull())
                
                var avatarPart: MultipartBody.Part? = null
                if (avatarUri != null) {
                    val contentResolver = context.contentResolver
                    val inputStream = contentResolver.openInputStream(avatarUri)
                    if (inputStream != null) {
                        val tempFile = File(context.cacheDir, "upload_avatar_${System.currentTimeMillis()}.jpg")
                        val outputStream = FileOutputStream(tempFile)
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        
                        val reqFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                        avatarPart = MultipartBody.Part.createFormData("avatar", tempFile.name, reqFile)
                    }
                }

                val res = api.register(namePart, passPart, avatarPart)
                if (res.success) {
                    _message.emit("注册成功")
                    checkin(name)
                } else {
                    _currentPlayer.value = UiState.Error(res.message ?: "注册失败")
                    _message.emit(res.message ?: "注册失败")
                }
            } catch (e: Exception) {
                 _currentPlayer.value = UiState.Error("网络错误: ${e.message}")
            }
        }
    }
    
    // 刷新选手信息
    fun refreshPlayer(playerId: Int? = savedPlayerId.value, quiet: Boolean = false) {
        if (playerId == null) return
        viewModelScope.launch {
            if (!quiet) {
                _currentPlayer.value = UiState.Loading
            }
            try {
                // 保存旧状态以便比较
                val oldState = _currentPlayer.value
                val oldMatchNumber = if (oldState is UiState.Success) oldState.data.matchNumber else null

                val response = api.getPlayer(playerId)
                if (response.success && response.data != null) {
                    val newData = response.data
                    _currentPlayer.value = UiState.Success(newData)
                    
                    // 检测序号变化 (排除从 null 变有值的情况，即刚登录/刚签到不弹窗，只有值改变才弹窗)
                    // 或者如果之前是签到但没序号(不太可能)，现在有序号了？
                    // 需求是：点击按钮重新分配时弹窗。
                    // 只有当 oldMatchNumber != null 且 newData.matchNumber != null 且两者不同时，才视为“更改”。
                    // 如果 oldMatchNumber 是 null，说明是第一次加载，不弹窗。
                    if (oldMatchNumber != null && newData.matchNumber != null && oldMatchNumber != newData.matchNumber) {
                        _matchNumberChanged.emit(newData.matchNumber)
                    }
                } else {
                    if (!quiet) {
                        _currentPlayer.value = UiState.Error(response.message ?: "获取信息失败")
                    }
                }
            } catch (e: Exception) {
                if (!quiet) {
                    _currentPlayer.value = UiState.Error("网络错误：${e.message}")
                }
            }
        }
    }
    
    // 切换上机状态
    fun toggleMachine() {
        val playerId = savedPlayerId.value ?: return
        viewModelScope.launch {
            try {
                val response = api.toggleMachine(playerId)
                if (response.success) {
                    _message.emit(response.message ?: "状态已更新")
                    refreshPlayer(playerId)
                } else {
                    _message.emit(response.message ?: "操作失败")
                }
            } catch (e: Exception) {
                _message.emit("网络错误：${e.message}")
            }
        }
    }
    
    // 提交成绩
    fun submitScore(score: Double, phase: String) {
        val playerId = savedPlayerId.value ?: return
        viewModelScope.launch {
            try {
                val response = api.submitScore(playerId, SubmitScoreRequest(score = score, phase = phase))
                if (response.success) {
                    _message.emit(response.message ?: "成绩已提交")
                    refreshPlayer(playerId)
                } else {
                    _message.emit(response.message ?: "提交失败")
                }
            } catch (e: Exception) {
                _message.emit("网络错误：${e.message}")
            }
        }
    }
    
    // 获取仪表盘
    fun loadDashboard() {
        viewModelScope.launch {
            _dashboard.value = UiState.Loading
            try {
                val response = api.getDashboard()
                if (response.success && response.data != null) {
                    _dashboard.value = UiState.Success(response.data)
                } else {
                    _dashboard.value = UiState.Error(response.message ?: "获取失败")
                }
            } catch (e: Exception) {
                _dashboard.value = UiState.Error("网络错误：${e.message}")
            }
        }
    }
    
    // 获取排行榜
    fun loadRankings(group: String? = null) {
        viewModelScope.launch {
            _rankings.value = UiState.Loading
            try {
                val response = api.getRankings(group)
                if (response.success && response.data != null) {
                    _rankings.value = UiState.Success(response.data)
                } else {
                    _rankings.value = UiState.Error(response.message ?: "获取失败")
                }
            } catch (e: Exception) {
                _rankings.value = UiState.Error("网络错误：${e.message}")
            }
        }
    }
    
    // 获取曲目抽选状态
    fun loadSongDrawState() {
        viewModelScope.launch {
            try {
                val response = api.getSongDrawState()
                if (response.success && response.data != null) {
                    val previousState = (_songDrawState.value as? UiState.Success)?.data
                    _songDrawState.value = UiState.Success(response.data)
                    
                    // 如果状态是 rolling，启动自动轮询
                    if (response.data.status == "rolling") {
                        startSongDrawPolling()
                    }
                }
            } catch (e: Exception) {
                // 静默失败，但保留之前的状态
            }
        }
    }
    
    // 自动轮询曲目抽选状态（当 rolling 时）
    private fun startSongDrawPolling() {
        viewModelScope.launch {
            while (true) {
                delay(2000) // 每 2 秒轮询一次
                
                try {
                    val response = api.getSongDrawState()
                    if (response.success && response.data != null) {
                        _songDrawState.value = UiState.Success(response.data)
                        if (response.data.status != "rolling") {
                            break // 停止轮询
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    // 全局轮询 (每 3 秒刷新选手状态和对战信息)
    private fun startGlobalPolling(playerId: Int) {
        viewModelScope.launch {
            while (savedPlayerId.value == playerId) {
                delay(3000)
                refreshPlayer(playerId, quiet = true)
                loadMatchInfo(playerId)
                loadSongDrawState() // 确保能持续获取最新的抽选状态
            }
        }
    }

    // 加载对战信息
    private fun loadMatchInfo(playerId: Int) {
        viewModelScope.launch {
            try {
                val response = api.getPlayerMatch(playerId)
                if (response.success) {
                    _matchInfo.value = UiState.Success(response.data)
                } else {
                    _matchInfo.value = UiState.Error(response.message ?: "获取对战信息失败")
                }
            } catch (e: Exception) {
               // ignore errors during silent refresh
            }
        }
    }

    // 弃权
    fun forfeitPlayer() {
        val playerId = savedPlayerId.value ?: return
        viewModelScope.launch {
            try {
                val response = api.forfeitPlayer(playerId)
                if (response.success) {
                    _message.emit(response.message ?: "弃权成功")
                    refreshPlayer(playerId)
                } else {
                    _message.emit(response.message ?: "操作失败")
                }
            } catch (e: Exception) {
                _message.emit("网络错误：${e.message}")
            }
        }
    }

    // 提交巅峰组选曲
    fun submitPeakSong(songName: String, difficulty: Int) {
        val playerId = savedPlayerId.value ?: return
        viewModelScope.launch {
            try {
                val response = api.submitPeakSong(playerId, SubmitPeakSongRequest(song_name = songName, difficulty = difficulty))
                if (response.success) {
                    _message.emit("选曲提交成功")
                    loadMatchInfo(playerId)
                } else {
                    _message.emit(response.message ?: "提交失败")
                }
            } catch (e: Exception) {
                _message.emit("网络错误：${e.message}")
            }
        }
    }

    // 巅峰组 Ban
    fun banPeakSong() {
        val playerId = savedPlayerId.value ?: return
        viewModelScope.launch {
            try {
                val response = api.banPeakSong(playerId)
                if (response.success) {
                    _message.emit("Ban 成功")
                    loadMatchInfo(playerId)
                } else {
                    _message.emit(response.message ?: "Ban 失败")
                }
            } catch (e: Exception) {
                _message.emit("网络错误：${e.message}")
            }
        }
    }

    
    // 退出登录
    fun logout() {
        viewModelScope.launch {
            prefs.logout()
            _currentPlayer.value = UiState.Loading
            _message.emit("已退出登录")
        }
    }
}
