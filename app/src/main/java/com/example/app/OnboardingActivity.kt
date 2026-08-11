package com.example.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.example.app.presentation.screens.OnboardingScreen
import com.example.app.presentation.theme.AgentnotifTheme

/**
 * 런처: Figma 3장 캐러셀(소개 → 필수설정 안내 → 별명) 후 Main.
 * UserData에 deviceId+별명이 있으면 온보딩 스킵.
 */
class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        UserRepository.ensureDeviceId(this)

        if (UserRepository.hasUser(this)) {
            goToMain()
            return
        }

        setContent {
            AgentnotifTheme(dynamicColor = false, darkTheme = false) {
                OnboardingScreen(
                    onFinished = { nickname ->
                        if (UserRepository.saveUser(this, nickname)) {
                            goToMain()
                        }
                    },
                )
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
