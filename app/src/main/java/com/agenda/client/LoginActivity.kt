package com.agenda.client

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val store = CredentialStore(this)

        // Уже входили раньше -> сразу на главный экран, без ввода
        if (store.load() != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val rgRole = findViewById<RadioGroup>(R.id.rgRole)
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val type = if (rgRole.checkedRadioButtonId == R.id.rbStudent) {
                AccountType.STUDENT
            } else {
                AccountType.PARENT
            }

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
            } else {
                store.save(username, password, type)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}
