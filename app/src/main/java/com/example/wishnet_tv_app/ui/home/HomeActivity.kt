package com.example.wishnet_tv_app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
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

    private lateinit var cardLiveTv: LinearLayout
    private lateinit var cardCategories: LinearLayout
    private lateinit var cardFavorites: LinearLayout
    private lateinit var cardAccount: LinearLayout
    private lateinit var cardLogout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        sessionManager = SessionManager(this)

        welcomeText = findViewById(R.id.txtWelcome)
        subtitleText = findViewById(R.id.txtHomeSubtitle)
        statusText = findViewById(R.id.txtStatus)
        userInfoText = findViewById(R.id.txtUserInfo)

        cardLiveTv = findViewById(R.id.cardLiveTv)
        cardCategories = findViewById(R.id.cardCategories)
        cardFavorites = findViewById(R.id.cardFavorites)
        cardAccount = findViewById(R.id.cardAccount)
        cardLogout = findViewById(R.id.cardLogout)

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

        cardLiveTv.requestFocus()

        setupPrimaryCardFocus(cardLiveTv)
        setupSecondaryCardFocus(cardCategories)
        setupSecondaryCardFocus(cardFavorites)
        setupSecondaryCardFocus(cardAccount)
        setupSecondaryCardFocus(cardLogout)

        cardLiveTv.setOnClickListener {
            // Próximo paso: abrir catálogo real de canales
        }

        cardCategories.setOnClickListener {
            // Próximo paso: abrir categorías
        }

        cardFavorites.setOnClickListener {
            // Próximo paso: abrir favoritos
        }

        cardAccount.setOnClickListener {
            // Próximo paso: abrir cuenta
        }

        cardLogout.setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        setEnterAction(cardLiveTv) { }
        setEnterAction(cardCategories) { }
        setEnterAction(cardFavorites) { }
        setEnterAction(cardAccount) { }
        setEnterAction(cardLogout) {
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setupPrimaryCardFocus(view: LinearLayout) {
        view.setOnFocusChangeListener { target, hasFocus ->
            if (hasFocus) {
                target.animate()
                    .scaleX(1.01f)
                    .scaleY(1.01f)
                    .setDuration(120)
                    .start()
            } else {
                target.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
        }
    }

    private fun setupSecondaryCardFocus(view: LinearLayout) {
        view.setOnFocusChangeListener { target, hasFocus ->
            if (hasFocus) {
                target.animate()
                    .scaleX(1.02f)
                    .scaleY(1.02f)
                    .setDuration(120)
                    .start()
            } else {
                target.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
        }
    }

    private fun setEnterAction(view: LinearLayout, action: () -> Unit) {
        view.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            ) {
                action()
                true
            } else {
                false
            }
        }
    }
}