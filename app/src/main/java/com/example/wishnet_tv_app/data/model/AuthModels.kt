package com.example.wishnet_tv_app.data.model

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val ok: Boolean,
    val token: String? = null,
    val mustChangePassword: Boolean = false,
    val user: LoginUser? = null,
    val message: String? = null
)

data class LoginUser(
    val id: String,
    val nombre: String,
    val email: String,
    val rol: String,
    val estado: String,
    val localidad: String? = null
)