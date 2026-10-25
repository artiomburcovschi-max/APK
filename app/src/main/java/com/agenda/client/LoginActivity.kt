package com.agenda.client

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

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
        SystemBars.matchTheme(this)
        setupInsets()

        val toggleRole = findViewById<MaterialButtonToggleGroup>(R.id.toggleRole)
        val tilUsername = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        fun submit() {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val type = if (toggleRole.checkedButtonId == R.id.btnRoleStudent) {
                AccountType.STUDENT
            } else {
                AccountType.PARENT
            }

            // Ошибка показывается под полем (а не всплывашкой): видно, какое именно поле пустое
            tilUsername.error = if (username.isEmpty()) "Введите имя пользователя" else null
            tilPassword.error = if (password.isEmpty()) "Введите пароль" else null
            if (username.isEmpty() || password.isEmpty()) return

            store.save(username, password, type)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        btnLogin.setOnClickListener { submit() }
        // Кнопка «Готово» на клавиатуре делает то же, что «Войти»
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        // Как только человек начал исправлять поле, красная ошибка под ним больше не нужна
        etUsername.setOnFocusChangeListener { _, focused -> if (focused) tilUsername.error = null }
        etPassword.setOnFocusChangeListener { _, focused -> if (focused) tilPassword.error = null }
    }

    /**
     * С targetSdk 35+ окно рисуется под системными панелями, и клавиатура окно уже не «сжимает».
     * Поэтому сами добавляем отступы под панели И под клавиатуру: форма остаётся прокручиваемой,
     * и кнопка «Войти» не прячется за клавиатурой.
     */
    private fun setupInsets() {
        val root = findViewById<android.view.View>(R.id.loginRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
