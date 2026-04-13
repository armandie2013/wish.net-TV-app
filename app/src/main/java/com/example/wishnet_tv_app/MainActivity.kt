package com.example.wishnet_tv_app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.wishnet_tv_app.ui.home.HomeActivity
import com.example.wishnet_tv_app.ui.login.LoginActivity
import com.example.wishnet_tv_app.utils.SessionManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)

        val nextScreen = if (sessionManager.getToken().isNullOrEmpty()) {
            LoginActivity::class.java
        } else {
            HomeActivity::class.java
        }

        startActivity(Intent(this, nextScreen))
        finish()
    }
}