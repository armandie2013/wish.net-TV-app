package com.example.wishnet_tv_app.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
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
import com.example.wishnet_tv_app.ui.password.ChangePasswordStep1Activity
import com.example.wishnet_tv_app.utils.ApiErrorParser
import com.example.wishnet_tv_app.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginPasswordActivity : AppCompatActivity() {

    private lateinit var emailText: TextView
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var changeEmailButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var sessionManager: SessionManager

    private var email: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(R.layout.activity_login_password)

        sessionManager = SessionManager(this)

        email = intent.getStringExtra("email")?.trim().orEmpty()

        emailText = findViewById(R.id.txtEmailValue)
        passwordEditText = findViewById(R.id.password)
        loginButton = findViewById(R.id.btnLogin)
        changeEmailButton = findViewById(R.id.btnChangeEmail)
        progressBar = findViewById(R.id.progressLogin)
        errorText = findViewById(R.id.txtError)

        emailText.text = email

        loginButton.requestFocus()

        loginButton.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
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

        changeEmailButton.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate()
                    .scaleX(1.03f)
                    .scaleY(1.03f)
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

        changeEmailButton.setOnClickListener {
            finish()
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
        val password = passwordEditText.text.toString().trim()

        errorText.text = ""
        errorText.visibility = TextView.GONE

        if (email.isEmpty()) {
            showError("No se encontró el correo electrónico")
            return
        }

        if (password.isEmpty()) {
            showError("Ingresá tu contraseña")
            return
        }

        passwordEditText.clearFocus()
        loginButton.requestFocus()
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
                                    this@LoginPasswordActivity,
                                    ChangePasswordStep1Activity::class.java
                                )
                            )
                            finish()
                            return@withContext
                        }

                        startActivity(Intent(this@LoginPasswordActivity, HomeActivity::class.java))
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
        loginButton.isEnabled = !isLoading
        changeEmailButton.isEnabled = !isLoading
        passwordEditText.isEnabled = !isLoading

        loginButton.text = if (isLoading) "Ingresando..." else "Ingresar"
        progressBar.visibility = if (isLoading) ProgressBar.VISIBLE else ProgressBar.GONE
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = TextView.VISIBLE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: loginButton
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}