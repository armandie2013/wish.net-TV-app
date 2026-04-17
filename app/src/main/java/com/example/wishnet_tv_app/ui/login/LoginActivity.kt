package com.example.wishnet_tv_app.ui.login

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.Patterns
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wishnet_tv_app.R
import com.example.wishnet_tv_app.data.api.ApiClient
import com.example.wishnet_tv_app.ui.home.HomeActivity
import com.example.wishnet_tv_app.utils.ApiErrorParser
import com.example.wishnet_tv_app.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.wishnet_tv_app.data.model.LoginRequest
import com.example.wishnet_tv_app.ui.password.ChangePasswordStep1Activity

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var sessionManager: SessionManager
    private lateinit var loginFormContainer: LinearLayout

    private var keyboardLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        emailEditText = findViewById(R.id.email)
        passwordEditText = findViewById(R.id.password)
        loginButton = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressLogin)
        errorText = findViewById(R.id.txtError)
        loginFormContainer = findViewById(R.id.loginFormContainer)

        loginButton.requestFocus()

        setupKeyboardAwareForm()

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

    private fun setupKeyboardAwareForm() {
        val rootView = findViewById<View>(android.R.id.content)

        keyboardLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            rootView.getWindowVisibleDisplayFrame(rect)

            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val keyboardVisible = keypadHeight > screenHeight * 0.15

            if (keyboardVisible) {
                loginFormContainer.animate()
                    .translationY(-240f)
                    .setDuration(180)
                    .start()
            } else {
                loginFormContainer.animate()
                    .translationY(0f)
                    .setDuration(180)
                    .start()
            }
        }

        rootView.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener)
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

        emailEditText.clearFocus()
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
                                    this@LoginActivity,
                                    ChangePasswordStep1Activity::class.java
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
        loginButton.isEnabled = !isLoading
        loginButton.text = if (isLoading) "Ingresando..." else "Ingresar"

        emailEditText.isEnabled = !isLoading
        passwordEditText.isEnabled = !isLoading

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

    override fun onDestroy() {
        val rootView = findViewById<View>(android.R.id.content)
        keyboardLayoutListener?.let {
            rootView.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        super.onDestroy()
    }
}