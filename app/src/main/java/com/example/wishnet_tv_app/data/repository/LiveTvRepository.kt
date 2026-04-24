package com.example.wishnet_tv_app.data.repository

import android.util.Log
import com.example.wishnet_tv_app.data.api.ApiService
import com.example.wishnet_tv_app.data.model.LiveChannelItem
import com.example.wishnet_tv_app.data.model.LiveResponse
import com.example.wishnet_tv_app.data.model.PlayResponse
import com.example.wishnet_tv_app.utils.SessionExpiredException
import com.example.wishnet_tv_app.utils.SessionManager

class LiveTvRepository(
    private val api: ApiService,
    private val sessionManager: SessionManager
) {

    private fun getBearerToken(): String {
        val token = sessionManager.getToken()

        if (token.isNullOrBlank()) {
            throw SessionExpiredException()
        }

        return "Bearer $token"
    }

    private fun getDeviceId(): String {
        return sessionManager.getDeviceId()
    }

    suspend fun getLiveGrid(): Result<LiveResponse> {
        return runCatching {
            val bearer = getBearerToken()
            val deviceId = getDeviceId()

            Log.d("LIVE_DEBUG", "GET /api/app/live")
            Log.d("LIVE_DEBUG", "deviceId = $deviceId")

            val response = api.getLive(
                token = bearer,
                deviceId = deviceId
            )

            if (response.code() == 401) {
                sessionManager.clearSession()
                throw SessionExpiredException()
            }

            if (!response.isSuccessful) {
                throw IllegalStateException("Error cargando grilla: HTTP ${response.code()}")
            }

            response.body() ?: throw IllegalStateException("Respuesta vacía en /live")
        }
    }

    suspend fun getPlayInfo(channelId: String): Result<PlayResponse> {
        return runCatching {
            val bearer = getBearerToken()
            val deviceId = getDeviceId()

            Log.d("LIVE_DEBUG", "GET /api/app/channel/$channelId/play")
            Log.d("LIVE_DEBUG", "deviceId = $deviceId")

            val response = api.getPlay(
                channelId = channelId,
                token = bearer,
                deviceId = deviceId
            )

            if (response.code() == 401) {
                sessionManager.clearSession()
                throw SessionExpiredException()
            }

            if (!response.isSuccessful) {
                val message = when (response.code()) {
                    403 -> "Cuenta sin permiso o límite de conexiones alcanzado."
                    404 -> "Canal no encontrado."
                    else -> "Error resolviendo canal: HTTP ${response.code()}"
                }

                throw IllegalStateException(message)
            }

            response.body() ?: throw IllegalStateException("Respuesta vacía en /play")
        }
    }

    suspend fun sendPresence(): Result<Unit> {
        return runCatching {
            val bearer = getBearerToken()
            val deviceId = getDeviceId()

            val response = api.sendPresence(
                token = bearer,
                deviceId = deviceId
            )

            if (response.code() == 401) {
                sessionManager.clearSession()
                throw SessionExpiredException()
            }

            if (!response.isSuccessful) {
                throw IllegalStateException("Error enviando presencia: HTTP ${response.code()}")
            }

            Unit
        }
    }

    fun findChannelIndex(grid: List<LiveChannelItem>, channelId: String): Int {
        return grid.indexOfFirst { it.id == channelId }
    }
}