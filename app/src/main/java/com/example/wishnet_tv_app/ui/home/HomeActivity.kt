package com.example.wishnet_tv_app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wishnet_tv_app.R
import com.example.wishnet_tv_app.ui.login.LoginActivity
import com.example.wishnet_tv_app.utils.SessionManager

class HomeActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var welcomeText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var statusText: TextView
    private lateinit var userInfoText: TextView
    private lateinit var btnGoChannels: Button
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        sessionManager = SessionManager(this)

        welcomeText = findViewById(R.id.txtWelcome)
        subtitleText = findViewById(R.id.txtHomeSubtitle)
        statusText = findViewById(R.id.txtStatus)
        userInfoText = findViewById(R.id.txtUserInfo)
        btnGoChannels = findViewById(R.id.btnGoChannels)
        btnLogout = findViewById(R.id.btnLogout)

        val userName = sessionManager.getUserName() ?: "Usuario"
        val token = sessionManager.getToken()

        welcomeText.text = "Bienvenida, $userName"
        subtitleText.text = "Tu acceso a wish.net TV está listo."

        statusText.text = if (token.isNullOrEmpty()) {
            "Sesión no encontrada"
        } else {
            "Sesión activa"
        }

        userInfoText.text = if (token.isNullOrEmpty()) {
            "No se encontraron datos de sesión"
        } else {
            "Ya podés continuar al catálogo de canales"
        }

        btnGoChannels.requestFocus()

        btnGoChannels.setOnClickListener {
            // Más adelante acá vamos a abrir la pantalla de canales
        }

        btnLogout.setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnGoChannels.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            ) {
                // Más adelante abrir canales
                true
            } else {
                false
            }
        }

        btnLogout.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            ) {
                sessionManager.clearSession()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            } else {
                false
            }
        }

        btnGoChannels.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
            } else {
                view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
        }

        btnLogout.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
            } else {
                view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
        }
    }
}