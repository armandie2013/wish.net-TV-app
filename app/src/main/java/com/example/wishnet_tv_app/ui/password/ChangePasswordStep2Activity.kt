package com.example.wishnet_tv_app.ui.password

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wishnet_tv_app.R
import com.example.wishnet_tv_app.data.api.ApiClient
import com.example.wishnet_tv_app.data.model.ChangePasswordRequest
import com.example.wishnet_tv_app.ui.home.HomeActivity
import com.example.wishnet_tv_app.utils.ApiErrorParser
import com.example.wishnet_tv_app.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangePasswordStep2Activity : AppCompatActivity() {

    private lateinit var confirmPasswordEditText: EditText
    private lateinit var saveButton: TextView
    private lateinit var backButton: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var sessionManager: SessionManager

    private var newPassword: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(R.layout.activity_change_password_step2)

        sessionManager = SessionManager(this)
        newPassword = intent.getStringExtra("newPassword")?.trim().orEmpty()

        confirmPasswordEditText = findViewById(R.id.edtConfirmPassword)
        saveButton = findViewById(R.id.btnSavePassword)
        backButton = findViewById(R.id.btnBackPassword)
        progressBar = findViewById(R.id.progressChangePassword)
        errorText = findViewById(R.id.txtPasswordError)

        saveButton.requestFocus()

        saveButton.setOnClickListener {
            attemptChangePassword()
        }

        backButton.setOnClickListener {
            finish()
        }

        confirmPasswordEditText.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            ) {
                attemptChangePassword()
                true
            } else {
                false
            }
        }
    }

    private fun attemptChangePassword() {
        val confirmPassword = confirmPasswordEditText.text.toString().trim()

        errorText.text = ""
        errorText.visibility = View.GONE

        if (newPassword.isEmpty()) {
            showError("No se encontró la nueva contraseña")
            return
        }

        if (confirmPassword.isEmpty()) {
            showError("Confirmá tu contraseña")
            return
        }

        val token = sessionManager.getToken()
        if (token.isNullOrEmpty()) {
            showError("Sesión no encontrada")
            return
        }

        setLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = ApiClient.api.changePassword(
                    authorization = "Bearer $token",
                    request = ChangePasswordRequest(
                        password = newPassword,
                        confirmPassword = confirmPassword
                    )
                )

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    if (response.ok) {
                        startActivity(Intent(this@ChangePasswordStep2Activity, HomeActivity::class.java))
                        finish()
                    } else {
                        showError(response.message ?: "Error al cambiar contraseña")
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
        saveButton.isEnabled = !isLoading
        backButton.isEnabled = !isLoading
        confirmPasswordEditText.isEnabled = !isLoading
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        saveButton.text = if (isLoading) "Guardando..." else "Guardar contraseña"
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
}