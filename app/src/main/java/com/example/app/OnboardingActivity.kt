package com.example.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.app.presentation.screens.OnboardingScreen
import com.example.app.presentation.screens.SetupGuideScreen
import com.example.app.presentation.theme.AgentnotifTheme

/**
 * 런처: 온보딩 → 필수 설정 안내 → 로그인
 * (이미 로그인 시 MainActivity)
 */
class OnboardingActivity : AppCompatActivity() {

    private var phase by mutableStateOf(Phase.ONBOARDING)

    private enum class Phase { ONBOARDING, SETUP_GUIDE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        if (AuthSession.isLoggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val onboardingDone = prefs.getBoolean("onboarding_done", false)
        val setupGuideDone = prefs.getBoolean("setup_guide_done", false)

        when {
            !onboardingDone -> phase = Phase.ONBOARDING
            !setupGuideDone -> phase = Phase.SETUP_GUIDE
            else -> {
                goToLogin()
                return
            }
        }

        setContent {
            AgentnotifTheme(dynamicColor = false, darkTheme = false) {
                when (phase) {
                    Phase.ONBOARDING -> OnboardingScreen(
                        onStart = {
                            prefs.edit().putBoolean("onboarding_done", true).apply()
                            phase = Phase.SETUP_GUIDE
                        }
                    )
                    Phase.SETUP_GUIDE -> SetupGuideScreen(
                        onOpenSettings = {
                            prefs.edit().putBoolean("setup_guide_done", true).apply()
                            openNotificationSettings()
                            goToLogin()
                        },
                        onSkip = {
                            prefs.edit().putBoolean("setup_guide_done", true).apply()
                            goToLogin()
                        },
                    )
                }
            }
        }
    }

    private fun openNotificationSettings() {
        try {
            // API 26+ 상수. 하위 호환을 위해 action 문자열 사용
            startActivity(Intent("android.settings.NOTIFICATION_SETTINGS"))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
