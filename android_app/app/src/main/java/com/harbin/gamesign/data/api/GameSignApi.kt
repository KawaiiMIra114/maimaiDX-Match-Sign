package com.harbin.gamesign.data.api

import com.harbin.gamesign.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface GameSignApi {
    
    // ============ Auth ============
    @POST("api/auth/check_status")
    suspend fun checkPlayerStatus(@Body request: CheckStatusRequest): ApiResponse<CheckStatusResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponse>

    @Multipart
    @POST("api/auth/register")
    suspend fun register(
        @Part("name") name: RequestBody,
        @Part("password") password: RequestBody,
        @Part avatar: MultipartBody.Part?
    ): ApiResponse<LoginResponse>

    // ============ 系统信息 ============
    
    @GET("api/v1/system/info")
    suspend fun getSystemInfo(): ApiResponse<SystemInfo>
    
    @GET("api/v1/dashboard")
    suspend fun getDashboard(): ApiResponse<DashboardStats>
    
    // ============ 选手相关 ============
    
    @POST("api/v1/player/checkin")
    suspend fun checkin(@Body request: CheckinRequest): ApiResponse<Player>
    
    @GET("api/v1/player/{id}")
    suspend fun getPlayer(@Path("id") playerId: Int): ApiResponse<Player>
    
    @GET("api/v1/player/search")
    suspend fun searchPlayer(@Query("name") name: String): ApiResponse<Player>
    
    @GET("api/v1/players")
    suspend fun getPlayers(
        @Query("group") group: String? = null,
        @Query("checked_in") checkedIn: String? = null
    ): ApiResponse<List<Player>>
    
    @POST("api/v1/player/{id}/toggle_machine")
    suspend fun toggleMachine(@Path("id") playerId: Int): ApiResponse<MachineStatus>
    
    @POST("api/v1/player/{id}/submit_score")
    suspend fun submitScore(
        @Path("id") playerId: Int,
        @Body request: SubmitScoreRequest
    ): ApiResponse<Any>
    
    // ============ 排行榜 ============
    
    @GET("api/v1/rankings")
    suspend fun getRankings(@Query("group") group: String? = null): ApiResponse<List<RankingItem>>
    
    @GET("api/v1/on_machine")
    suspend fun getOnMachine(): ApiResponse<List<Player>>
    
    // ============ 曲目抽选 ============
    
    @GET("api/v1/song_draw/state")
    suspend fun getSongDrawState(): ApiResponse<SongDrawState>
    
    @GET("api/v1/songs")
    suspend fun getSongs(
        @Query("phase") phase: String? = null,
        @Query("group") group: String? = null
    ): ApiResponse<List<Song>>

    // ============ 新增功能 (Phase 3) ============

    @POST("api/v1/player/{id}/forfeit")
    suspend fun forfeitPlayer(@Path("id") playerId: Int): ApiResponse<Any>

    @GET("api/v1/player/{id}/match")
    suspend fun getPlayerMatch(@Path("id") playerId: Int): ApiResponse<MatchInfo>

    @POST("api/v1/player/{id}/peak/submit_song")
    suspend fun submitPeakSong(
        @Path("id") playerId: Int,
        @Body request: SubmitPeakSongRequest
    ): ApiResponse<Any>

    @POST("api/v1/player/{id}/peak/ban_song")
    suspend fun banPeakSong(@Path("id") playerId: Int): ApiResponse<Any>
}
