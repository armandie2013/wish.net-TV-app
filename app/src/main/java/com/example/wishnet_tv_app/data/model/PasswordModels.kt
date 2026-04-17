package com.example.wishnet_tv_app.data.model

data class ChangePasswordRequest(
    val password: String,
    val confirmPassword: String
)

data class ChangePasswordResponse(
    val ok: Boolean,
    val message: String? = null,
    val user: ChangePasswordUser? = null
)

data class ChangePasswordUser(
    val id: String,
    val nombre: String,
    val email: String,
    val rol: String,
    val estado: String,
    val localidad: String? = null
)