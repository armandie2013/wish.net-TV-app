package com.example.wishnet_tv_app.data

import android.content.Context
import com.example.wishnet_tv_app.data.api.ApiClient
import com.example.wishnet_tv_app.data.repository.LiveTvRepository
import com.example.wishnet_tv_app.utils.SessionManager

object AppContainer {

    fun sessionManager(context: Context): SessionManager {
        return SessionManager(context)
    }

    fun liveTvRepository(context: Context): LiveTvRepository {
        return LiveTvRepository(
            api = ApiClient.api,
            sessionManager = sessionManager(context)
        )
    }
}