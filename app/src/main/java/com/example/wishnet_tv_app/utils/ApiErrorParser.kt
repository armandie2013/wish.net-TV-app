package com.example.wishnet_tv_app.utils

import com.example.wishnet_tv_app.data.model.ApiErrorResponse
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

object ApiErrorParser {

    fun getMessage(exception: Exception): String {
        return when (exception) {
            is HttpException -> {
                try {
                    val errorBody = exception.response()?.errorBody()?.string()

                    if (errorBody.isNullOrBlank()) {
                        "Error ${exception.code()}"
                    } else {
                        val parsed = Gson().fromJson(errorBody, ApiErrorResponse::class.java)
                        parsed.message ?: "Error ${exception.code()}"
                    }
                } catch (_: Exception) {
                    "Error ${exception.code()}"
                }
            }

            is IOException -> "Sin conexión al servidor"

            else -> exception.message ?: "Error inesperado"
        }
    }
}