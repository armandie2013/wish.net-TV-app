package com.example.wishnet_tv_app.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wishnet_tv_app.R
import com.example.wishnet_tv_app.data.api.ApiClient
import com.example.wishnet_tv_app.data.model.LoginRequest
import com.example.wishnet_tv_app.ui.home.HomeActivity
import com.example.wishnet_tv_app.ui.password.ChangePasswordActivity
import com.example.wishnet_tv_app.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        emailEditText = findViewById(R.id.email)
        passwordEditText = findViewById(R.id.password)
        loginButton = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressLogin)
        errorText = findViewById(R.id.txtError)

        loginButton.setOnClickListener {
            attemptLogin()
        }

        passwordEditText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                attemptLogin()
                true
            } else {
                false
            }
        }
    }

    private fun attemptLogin() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        errorText.visibility = View.GONE
        errorText.text = ""

        if (email.isEmpty() || password.isEmpty()) {
            showError("Completá email y contraseña")
            return
        }

        setLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.api.login(
                    LoginRequest(
                        email = email,
                        password = password
                    )
                )

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    if (response.ok && !response.token.isNullOrEmpty() && response.user != null) {
                        sessionManager.saveSession(
                            token = response.token,
                            userId = response.user.id,
                            nombre = response.user.nombre,
                            email = response.user.email,
                            rol = response.user.rol,
                            localidad = response.user.localidad
                        )

                        if (response.mustChangePassword) {
                            startActivity(Intent(this@LoginActivity, ChangePasswordActivity::class.java))
                            finish()
                            return@withContext
                        }

                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    } else {
                        showError(response.message ?: "Credenciales inválidas")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    showError("Error de conexión: ${e.message}")
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        loginButton.isEnabled = !isLoading
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.text = if (isLoading) "Ingresando..." else "Ingresar"
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
}