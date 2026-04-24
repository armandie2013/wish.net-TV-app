package com.example.wishnet_tv_app.utils

import android.content.Context
import android.provider.Settings
import java.util.UUID

class SessionManager(context: Context) {

    private val appContext = context.applicationContext

    private val prefs =
        appContext.getSharedPreferences("wishnet_tv_session", Context.MODE_PRIVATE)

    fun saveSession(
        token: String,
        userId: String,
        nombre: String,
        email: String,
        rol: String,
        localidad: String
    ) {
        prefs.edit()
            .putString("auth_token", token)
            .putString("user_id", userId)
            .putString("user_nombre", nombre)
            .putString("user_email", email)
            .putString("user_rol", rol)
            .putString("user_localidad", localidad)
            .apply()
    }

    fun getToken(): String? = prefs.getString("auth_token", null)

    fun getUserName(): String? = prefs.getString("user_nombre", null)

    fun getDeviceId(): String {
        val existing = prefs.getString("device_id", null)

        if (!existing.isNullOrBlank()) {
            return existing
        }

        val androidId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        val generated = if (!androidId.isNullOrBlank()) {
            "android-tv-$androidId"
        } else {
            "android-tv-${UUID.randomUUID()}"
        }

        prefs.edit()
            .putString("device_id", generated)
            .apply()

        return generated
    }

    fun clearSession() {
        val deviceId = getDeviceId()

        prefs.edit()
            .clear()
            .putString("device_id", deviceId)
            .apply()
    }
}