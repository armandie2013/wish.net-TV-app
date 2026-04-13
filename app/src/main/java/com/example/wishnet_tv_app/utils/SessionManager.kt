package com.example.wishnet_tv_app.utils

import android.content.Context

class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("wishnet_tv_session", Context.MODE_PRIVATE)

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

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}