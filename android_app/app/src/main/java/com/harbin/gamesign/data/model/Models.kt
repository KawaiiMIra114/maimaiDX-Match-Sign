package com.harbin.gamesign.data.model

import com.google.gson.annotations.SerializedName

// API 通用响应
data class ApiResponse<T>(
    val success: Boolean,
    val code: Int,
    val data: T?,
    val message: String?
)

// 选手信息
data class Player(
    val id: Int,
    val name: String,
    val group: String,
    @SerializedName("group_label")
    val groupLabel: String?,
    @SerializedName("match_number")
    val matchNumber: Int?,
    @SerializedName("checked_in")
    val checkedIn: Boolean,
    @SerializedName("on_machine")
    val onMachine: Boolean,
    @SerializedName("promotion_status")
    val promotionStatus: String?,
    val rating: Int?,
    @SerializedName("score_round1")
    val scoreRound1: Double?,
    @SerializedName("score_revival")
    val scoreRevival: Double?,
    val forfeited: Boolean = false,
    @SerializedName("ban_used")
    val banUsed: Boolean = false,
    @SerializedName("match_started")
    val matchStarted: Boolean = false,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null
) {
    val isAdvanced: Boolean get() = group == "advanced"
    val isPeak: Boolean get() = group == "peak"
    val displayGroup: String get() = groupLabel ?: when(group) {
        "advanced" -> "进阶组"
        "peak" -> "巅峰组"
        else -> "萌新组"
    }
    
    val statusText: String get() = if (forfeited && promotionStatus != "eliminated") "已弃权" else when (promotionStatus) {
        "top16" -> "16强"
        "top8" -> "8强"
        "top4" -> "4强"
        "top4_peak" -> "巅峰4强"
        "final" -> "决赛"
        "revival" -> "复活赛"
        "champion" -> "🏆 冠军"
        "runner_up" -> "🥈 亚军"
        "third" -> "🥉 季军"
        "fourth" -> "第四名"
        "eliminated" -> "已淘汰"
        "top16_out" -> "16强淘汰"
        "top8_out" -> "8强淘汰"
        else -> ""
    }


    
    // 是否处于晋级赛阶段（16强、8强、4强等，此时不能提交成绩）
    val isAdvancedStage: Boolean get() = promotionStatus in listOf(
        "top16", "top8", "top4", "top4_peak", "final", "top16_out", "top8_out",
        "champion", "runner_up", "third", "fourth"
    )
    
    // 是否可以提交成绩
    val canSubmitScore: Boolean get() = when {
        // 必须在上机状态才能提交
        !onMachine -> false
        // 已淘汰不能提交
        promotionStatus == "eliminated" -> false
        // 晋级赛阶段不能提交
        isAdvancedStage -> false
        // 复活赛：状态为 revival 且复活赛成绩为空
        promotionStatus == "revival" -> scoreRevival == null
        // 海选：成绩为空时可提交
        else -> scoreRound1 == null
    }
    
    // 是否已晋级
    val isPromoted: Boolean get() = promotionStatus in listOf(
        "top16", "top8", "top4", "top4_peak", "final", "champion", "runner_up", "third", "fourth"
    )
    
    // 是否已淘汰
    val isEliminated: Boolean get() = promotionStatus in listOf(
        "eliminated", "top16_out", "top8_out"
    )
}

// 比赛信息 (1v1)
data class MatchInfo(
    @SerializedName("match_id") val matchId: Int,
    val phase: String,
    val group: String,
    @SerializedName("opponent") val opponent: MatchOpponent,
    @SerializedName("my_selection") val mySelection: SongSelection?,
    @SerializedName("op_selection") val opSelection: SongSelection?,
    @SerializedName("has_banned_this_match") val hasBannedThisMatch: Boolean,
    @SerializedName("ban_used") val banUsed: Boolean,
    @SerializedName("was_banned") val wasBanned: Boolean
)

data class MatchOpponent(
    val name: String,
    val rating: Int?,
    val forfeited: Boolean
)

data class SongSelection(
    @SerializedName("song_name") val songName: String,
    val difficulty: Int,
    val hidden: Boolean
)

// 请求体
data class CheckinRequest(val name: String, val lat: Double?, val lon: Double?)
data class SubmitScoreRequest(val score: Double, val phase: String)
data class SubmitPeakSongRequest(val song_name: String, val difficulty: Int)

// 系统信息
data class SystemInfo(
    @SerializedName("server_time") val serverTime: String
)

data class DashboardStats(
    @SerializedName("total_players") val total: Int,
    @SerializedName("checked_in") val checked: Int,
    @SerializedName("match_generated") val matchGenerated: Boolean
)

data class MachineStatus(
    @SerializedName("on_machine") val onMachine: Boolean
)

// 排行榜
data class RankingItem(
    val rank: Int,
    val name: String,
    val group: String,
    @SerializedName("group_label") val groupLabel: String?,
    @SerializedName("match_number") val matchNumber: Int?,
    val score: Double?,
    val status: String?,
    val forfeited: Boolean = false
) {
    val displayGroup: String get() = groupLabel ?: when(group) {
        "advanced" -> "进阶组"
        "peak" -> "巅峰组"
        else -> "萌新组"
    }
}

// Auth Models
data class CheckStatusRequest(val name: String)
data class CheckStatusResponse(
    val exists: Boolean,
    val registered: Boolean,
    @SerializedName("avatar_url") val avatarUrl: String?
)

data class LoginRequest(val name: String, val password: String, val lat: Double?, val lon: Double?)
data class LoginResponse(val success: Boolean, val msg: String)

// 曲目相关
data class Song(
    val id: Int,
    val name: String,
    @SerializedName("image_url") val imageUrl: String?
)

data class SongDrawState(
    val status: String, // idle, rolling, finished
    val phase: String?,
    val group: String?,
    @SerializedName("phase_label") val phaseLabel: String?,
    @SerializedName("group_label") val groupLabel: String?,
    val songs: List<Song>?,
    @SerializedName("selected_songs") val selectedSongs: List<Song>?
) {
    val displayGroup: String get() = groupLabel ?: when(group) {
        "advanced" -> "进阶组"
        "peak" -> "巅峰组"
        else -> "萌新组"
    }
}
