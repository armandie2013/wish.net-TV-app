package com.example.wishnet_tv_app.data.api

import com.example.wishnet_tv_app.data.model.BasicResponse
import com.example.wishnet_tv_app.data.model.ChangePasswordRequest
import com.example.wishnet_tv_app.data.model.ChangePasswordResponse
import com.example.wishnet_tv_app.data.model.LiveResponse
import com.example.wishnet_tv_app.data.model.LoginRequest
import com.example.wishnet_tv_app.data.model.LoginResponse
import com.example.wishnet_tv_app.data.model.PlayResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("/api/app/login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @POST("/api/app/change-password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Body request: ChangePasswordRequest
    ): ChangePasswordResponse

    @GET("/api/app/live")
    suspend fun getLive(
        @Header("Authorization") token: String,
        @Header("X-Device-Id") deviceId: String
    ): Response<LiveResponse>

    @GET("/api/app/channel/{id}/play")
    suspend fun getPlay(
        @Path("id") channelId: String,
        @Header("Authorization") token: String,
        @Header("X-Device-Id") deviceId: String
    ): Response<PlayResponse>

    @POST("/api/presence")
    suspend fun sendPresence(
        @Header("Authorization") token: String,
        @Header("X-Device-Id") deviceId: String
    ): Response<BasicResponse>
}