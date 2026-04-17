package com.example.wishnet_tv_app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wishnet_tv_app.R
import com.example.wishnet_tv_app.ui.live.LiveTvPlayerActivity

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        bindViews()
        setupUI()
        setupActions()
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
        txtHomeSubtitle.text = "Tu acceso a wish.net TV está listo"
        txtStatus.text = "Sesión activa"
        txtUserInfo.text = "Listo para continuar"

        // Foco inicial en TV EN VIVO
        cardLiveTv.requestFocus()
    }

    private fun setupActions() {

        // 👉 CLICK (mouse / touch / OK automático)
        cardLiveTv.setOnClickListener {
            openLiveTv()
        }

        // 👉 CONTROL REMOTO (OK / ENTER)
        cardLiveTv.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                openLiveTv()
                true
            } else {
                false
            }
        }

        // 👉 LOGOUT (placeholder)
        cardLogout.setOnClickListener {
            finish()
        }
    }

    private fun openLiveTv() {
        startActivity(Intent(this, LiveTvPlayerActivity::class.java))
    }
}