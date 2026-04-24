package com.example.wishnet_tv_app.utils

class SessionExpiredException(
    message: String = "La sesión expiró. Iniciá sesión nuevamente."
) : Exception(message)