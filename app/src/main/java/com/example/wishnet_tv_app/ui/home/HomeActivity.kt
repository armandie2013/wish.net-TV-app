package com.example.wishnet_tv_app.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wishnet_tv_app.R
import com.example.wishnet_tv_app.ui.login.LoginActivity
import com.example.wishnet_tv_app.utils.SessionManager

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val sessionManager = SessionManager(this)

        val welcomeText = findViewById<TextView>(R.id.txtWelcome)
        val userInfoText = findViewById<TextView>(R.id.txtUserInfo)
        val logoutButton = findViewById<Button>(R.id.btnLogout)

        val userName = sessionManager.getUserName() ?: "Usuario"
        val token = sessionManager.getToken()

        welcomeText.text = "Bienvenida, $userName"
        userInfoText.text = if (token.isNullOrEmpty()) {
            "Sesión no encontrada"
        } else {
            "Sesión iniciada correctamente"
        }

        logoutButton.setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}