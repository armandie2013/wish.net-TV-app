package com.example.wishnet_tv_app.data.model

data class LoginResponse(
    val ok: Boolean,
    val token: String? = null,
    val mustChangePassword: Boolean = false,
    val user: UserResponse? = null,
    val message: String? = null
)