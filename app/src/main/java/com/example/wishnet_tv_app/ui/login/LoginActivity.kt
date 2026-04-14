package com.example.wishnet_tv_app.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
import com.example.wishnet_tv_app.utils.ApiErrorParser
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

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        emailEditText = findViewById(R.id.email)
        passwordEditText = findViewById(R.id.password)
        loginButton = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressLogin)
        errorText = findViewById(R.id.txtError)

        loginButton.requestFocus()

        // 🔥 EFECTO FOCO (ZOOM SUAVE)
        loginButton.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(120)
                    .start()
            } else {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
        }

        loginButton.setOnClickListener {
            attemptLogin()
        }

        passwordEditText.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            ) {
                attemptLogin()
                true
            } else {
                false
            }
        }

        loginButton.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            ) {
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

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Ingresá un correo válido")
            return
        }

        // Quitar foco de los campos y dejarlo en el botón
        emailEditText.clearFocus()
        passwordEditText.clearFocus()
        loginButton.requestFocus()

        // Ocultar teclado
        hideKeyboard()

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
                            startActivity(
                                Intent(
                                    this@LoginActivity,
                                    ChangePasswordActivity::class.java
                                )
                            )
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
                    showError(ApiErrorParser.getMessage(e))
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        // Botón
        loginButton.isEnabled = !isLoading
        loginButton.text = if (isLoading) "Ingresando..." else "Ingresar"

        // Inputs (bloqueo mientras carga)
        emailEditText.isEnabled = !isLoading
        passwordEditText.isEnabled = !isLoading

        // Loader
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: loginButton
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}