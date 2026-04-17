package com.example.wishnet_tv_app.data.repository

import android.util.Log
import com.example.wishnet_tv_app.data.api.ApiService
import com.example.wishnet_tv_app.data.model.LiveChannelItem
import com.example.wishnet_tv_app.data.model.LiveResponse
import com.example.wishnet_tv_app.data.model.PlayResponse
import com.example.wishnet_tv_app.utils.SessionManager

class LiveTvRepository(
    private val api: ApiService,
    private val sessionManager: SessionManager
) {

    suspend fun getLiveGrid(): Result<LiveResponse> {
        return runCatching {
            val token = sessionManager.getToken()

            Log.d("LIVE_DEBUG", "token from session (live) = $token")

            if (token.isNullOrBlank()) {
                throw IllegalStateException("Token vacío o usuario no autenticado")
            }

            val bearer = "Bearer $token"
            Log.d("LIVE_DEBUG", "Authorization header = $bearer")

            api.getLive(bearer)
        }
    }

    suspend fun getPlayInfo(channelId: String): Result<PlayResponse> {
        return runCatching {
            val token = sessionManager.getToken()

            Log.d("LIVE_DEBUG", "token from session (play) = $token")
            Log.d("LIVE_DEBUG", "channelId = $channelId")

            if (token.isNullOrBlank()) {
                throw IllegalStateException("Token vacío o usuario no autenticado")
            }

            val bearer = "Bearer $token"
            Log.d("LIVE_DEBUG", "Authorization header = $bearer")

            api.getPlay(channelId, bearer)
        }
    }

    fun getFirstAvailableChannel(grid: List<LiveChannelItem>): LiveChannelItem? {
        return grid.firstOrNull { it.enabled }
    }

    fun findChannelIndex(grid: List<LiveChannelItem>, channelId: String): Int {
        return grid.indexOfFirst { it.id == channelId }
    }

    fun getNextChannel(grid: List<LiveChannelItem>, currentIndex: Int): LiveChannelItem? {
        if (grid.isEmpty()) return null
        val nextIndex = (currentIndex + 1) % grid.size
        return grid.getOrNull(nextIndex)
    }

    fun getPreviousChannel(grid: List<LiveChannelItem>, currentIndex: Int): LiveChannelItem? {
        if (grid.isEmpty()) return null
        val previousIndex = if (currentIndex - 1 < 0) grid.lastIndex else currentIndex - 1
        return grid.getOrNull(previousIndex)
    }
}