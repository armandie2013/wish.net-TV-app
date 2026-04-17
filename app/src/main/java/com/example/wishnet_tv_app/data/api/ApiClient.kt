package com.example.wishnet_tv_app.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.wishnet_tv_app.config.AppConfig

object ApiClient {

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(AppConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

}