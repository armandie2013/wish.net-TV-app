package com.example.wishnet_tv_app.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wishnet_tv_app.R
import com.example.wishnet_tv_app.data.api.ApiClient
import com.example.wishnet_tv_app.ui.home.HomeActivity
import com.example.wishnet_tv_app.ui.password.ChangePasswordStep1Activity
import com.example.wishnet_tv_app.utils.ApiErrorParser
import com.example.wishnet_tv_app.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.wishnet_tv_app.data.model.LoginRequest

class LoginPasswordActivity : AppCompatActivity() {

    private lateinit var emailText: TextView
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: TextView
    private lateinit var changeEmailButton: TextView
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
        errorText.visibility = View.GONE

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
                        android.util.Log.d("LOGIN_DEBUG", "token = ${response.token}")

                        sessionManager.clearSession()

                        sessionManager.saveSession(
                            token = response.token,
                            userId = response.user.id,
                            nombre = response.user.nombre,
                            email = response.user.email,
                            rol = response.user.rol,
                            localidad = response.user.localidad ?: "principal"
                        )

                        android.util.Log.d("LOGIN_DEBUG", "saved token = ${sessionManager.getToken()}")

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