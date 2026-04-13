package com.example.wishnet_tv_app.ui.password

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
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

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var newPasswordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(R.layout.activity_change_password)

        sessionManager = SessionManager(this)

        newPasswordEditText = findViewById(R.id.edtNewPassword)
        confirmPasswordEditText = findViewById(R.id.edtConfirmPassword)
        saveButton = findViewById(R.id.btnSavePassword)
        progressBar = findViewById(R.id.progressChangePassword)
        errorText = findViewById(R.id.txtPasswordError)

        saveButton.requestFocus()

        saveButton.setOnClickListener {
            attemptChangePassword()
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
        val password = newPasswordEditText.text.toString().trim()
        val confirmPassword = confirmPasswordEditText.text.toString().trim()

        errorText.visibility = View.GONE
        errorText.text = ""

        if (password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Completá ambos campos")
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
                        password = password,
                        confirmPassword = confirmPassword
                    )
                )

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    if (response.ok) {
                        startActivity(Intent(this@ChangePasswordActivity, HomeActivity::class.java))
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
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        saveButton.text = if (isLoading) "Guardando..." else "Guardar nueva contraseña"
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
}