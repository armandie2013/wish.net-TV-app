package com.example.wishnet_tv_app.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wishnet_tv_app.R

class LoginEmailActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var continueButton: TextView
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(R.layout.activity_login_email)

        emailEditText = findViewById(R.id.email)
        continueButton = findViewById(R.id.btnContinue)
        errorText = findViewById(R.id.txtError)

        continueButton.requestFocus()

        continueButton.setOnClickListener {
            goToPasswordStep()
        }



        emailEditText.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            ) {
                goToPasswordStep()
                true
            } else {
                false
            }
        }

        continueButton.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            ) {
                goToPasswordStep()
                true
            } else {
                false
            }
        }
    }

    private fun goToPasswordStep() {
        val email = emailEditText.text.toString().trim()

        errorText.text = ""
        errorText.visibility = View.GONE

        if (email.isEmpty()) {
            showError("Ingresá tu correo electrónico")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Ingresá un correo válido")
            return
        }

        val intent = Intent(this, LoginPasswordActivity::class.java)
        intent.putExtra("email", email)
        startActivity(intent)
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
}