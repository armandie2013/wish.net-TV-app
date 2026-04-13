package com.example.wishnet_tv_app.data.model

data class ChangePasswordRequest(
    val password: String,
    val confirmPassword: String
)