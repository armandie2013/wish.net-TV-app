package com.example.wishnet_tv_app.utils

import android.content.Context

class LiveTvPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("live_tv_prefs", Context.MODE_PRIVATE)

    fun saveLastChannelId(channelId: String) {
        prefs.edit().putString("last_channel_id", channelId).apply()
    }

    fun getLastChannelId(): String? {
        return prefs.getString("last_channel_id", null)
    }

    fun clearLastChannelId() {
        prefs.edit().remove("last_channel_id").apply()
    }
}