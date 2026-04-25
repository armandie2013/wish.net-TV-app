package com.example.wishnet_tv_app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wishnet_tv_app.R
import com.example.wishnet_tv_app.data.AppContainer
import com.example.wishnet_tv_app.ui.live.LiveTvPlayerActivity
import com.example.wishnet_tv_app.ui.login.LoginEmailActivity

class HomeActivity : AppCompatActivity() {

    private lateinit var txtWelcome: TextView
    private lateinit var txtHomeSubtitle: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtUserInfo: TextView

    private lateinit var cardLiveTv: LinearLayout
    private lateinit var cardCategories: LinearLayout
    private lateinit var cardFavorites: LinearLayout
    private lateinit var cardAccount: LinearLayout
    private lateinit var cardLogout: LinearLayout

    private val sessionManager by lazy {
        AppContainer.sessionManager(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (sessionManager.getToken().isNullOrBlank()) {
            goToLogin()
            return
        }

        setContentView(R.layout.activity_home)

        bindViews()
        setupUI()
        setupActions()
    }

    override fun onResume() {
        super.onResume()

        if (sessionManager.getToken().isNullOrBlank()) {
            goToLogin()
        }
    }

    private fun bindViews() {
        txtWelcome = findViewById(R.id.txtWelcome)
        txtHomeSubtitle = findViewById(R.id.txtHomeSubtitle)
        txtStatus = findViewById(R.id.txtStatus)
        txtUserInfo = findViewById(R.id.txtUserInfo)

        cardLiveTv = findViewById(R.id.cardLiveTv)
        cardCategories = findViewById(R.id.cardCategories)
        cardFavorites = findViewById(R.id.cardFavorites)
        cardAccount = findViewById(R.id.cardAccount)
        cardLogout = findViewById(R.id.cardLogout)
    }

    private fun setupUI() {
        txtWelcome.text = "Bienvenido"
        txtHomeSubtitle.text = getString(R.string.home_subtitle)
        txtStatus.text = "Sesión activa"
        txtUserInfo.text = sessionManager.getUserName() ?: "Listo para continuar"

        cardLiveTv.requestFocus()
    }

    private fun setupActions() {
        cardLiveTv.setOnClickListener {
            openLiveTv()
        }

        cardLiveTv.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                openLiveTv()
                true
            } else {
                false
            }
        }

        cardLogout.setOnClickListener {
            logout()
        }

        cardLogout.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                logout()
                true
            } else {
                false
            }
        }
    }

    private fun openLiveTv() {
        startActivity(Intent(this, LiveTvPlayerActivity::class.java))
    }

    private fun logout() {
        sessionManager.clearSession()
        goToLogin()
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginEmailActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}