package com.example.wishnet_tv_app.data.api

import com.example.wishnet_tv_app.data.model.LoginRequest
import com.example.wishnet_tv_app.data.model.LoginResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("api/app/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse
}