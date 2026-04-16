package com.example.wishnet_tv_app.ui.password

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wishnet_tv_app.R

class ChangePasswordStep1Activity : AppCompatActivity() {

    private lateinit var passwordEditText: EditText
    private lateinit var continueButton: TextView
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        setContentView(R.layout.activity_change_password_step1)

        passwordEditText = findViewById(R.id.edtNewPassword)
        continueButton = findViewById(R.id.btnContinue)
        errorText = findViewById(R.id.txtPasswordError)

        continueButton.requestFocus()

        continueButton.setOnClickListener {
            goToStep2()
        }



        passwordEditText.setOnKeyListener { _, keyCode, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            ) {
                goToStep2()
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
                goToStep2()
                true
            } else {
                false
            }
        }
    }

    private fun goToStep2() {
        val password = passwordEditText.text.toString().trim()

        errorText.text = ""
        errorText.visibility = View.GONE

        if (password.isEmpty()) {
            showError("Ingresá una nueva contraseña")
            return
        }

        val intent = Intent(this, ChangePasswordStep2Activity::class.java)
        intent.putExtra("newPassword", password)
        startActivity(intent)
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
}