package com.example.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.app.presentation.screens.LoginScreen
import com.example.app.presentation.theme.AgentnotifTheme

class LoginActivity : AppCompatActivity() {

    private var isRegisterMode by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthSession.isLoggedIn(this)) {
            goHome()
            return
        }

        isRegisterMode = intent.getBooleanExtra(EXTRA_REGISTER, false)

        setContent {
            AgentnotifTheme(dynamicColor = false, darkTheme = false) {
                LoginScreen(
                    isRegisterMode = isRegisterMode,
                    errorMessage = errorMessage,
                    onBack = {
                        if (isRegisterMode) {
                            isRegisterMode = false
                            errorMessage = null
                        } else {
                            finish()
                        }
                    },
                    onToggleMode = {
                        isRegisterMode = !isRegisterMode
                        errorMessage = null
                    },
                    onSubmit = { email, password, displayName ->
                        if (isRegisterMode) {
                            handleRegister(email, password, displayName)
                        } else {
                            handleLogin(email, password)
                        }
                    },
                    onSocialClick = { provider ->
                        Toast.makeText(
                            this,
                            "$provider 로그인은 곧 지원 예정입니다 (로컬 목)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    private fun handleLogin(email: String, password: String) {
        when (val result = AuthSession.validateLogin(this, email, password)) {
            is AuthSession.LoginResult.Error -> {
                errorMessage = result.message
            }
            AuthSession.LoginResult.Ok -> {
                AuthSession.login(this, email, password)
                goHome()
            }
        }
    }

    private fun handleRegister(email: String, password: String, displayName: String) {
        when (val result = AuthSession.validateLogin(this, email, password)) {
            is AuthSession.LoginResult.Error -> {
                errorMessage = result.message
            }
            AuthSession.LoginResult.Ok -> {
                AuthSession.register(this, email, password, displayName.ifBlank { null })
                goHome()
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    companion object {
        const val EXTRA_REGISTER = "extra_register"
    }
}
