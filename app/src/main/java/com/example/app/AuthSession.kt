package com.example.app

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 로컬 목 로그인 세션. 서버 연동 전까지 SharedPreferences로 유지한다.
 */
object AuthSession {
    private const val PREFS = "AppPrefs"
    private const val KEY_LOGGED_IN = "is_logged_in"
    private const val KEY_EMAIL = "user_email"
    private const val KEY_DISPLAY_NAME = "user_display_name"
    private const val KEY_PASSWORD = "user_password_mock" // 목용 저장 (실제 앱에서는 사용 금지)
    private const val KEY_AVATAR_PATH = "user_avatar_path"
    private const val AVATAR_FILE = "profile_avatar.jpg"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGGED_IN, false)

    fun email(context: Context): String =
        prefs(context).getString(KEY_EMAIL, "").orEmpty()

    fun displayName(context: Context): String {
        val saved = prefs(context).getString(KEY_DISPLAY_NAME, null)
        if (!saved.isNullOrBlank()) return saved
        val mail = email(context)
        return mail.substringBefore("@").ifBlank { "사용자" }
    }

    /** 앱 내부 저장 아바타 절대경로. 없으면 null */
    fun avatarPath(context: Context): String? {
        val path = prefs(context).getString(KEY_AVATAR_PATH, null)?.trim().orEmpty()
        if (path.isBlank()) return null
        return path.takeIf { File(it).exists() }
    }

    fun login(context: Context, email: String, password: String, displayName: String? = null) {
        prefs(context).edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_PASSWORD, password)
            .putString(
                KEY_DISPLAY_NAME,
                displayName?.trim()?.ifBlank { null }
                    ?: email.trim().substringBefore("@").ifBlank { "사용자" }
            )
            .apply()
    }

    /** 목 회원가입: 계정 저장 후 바로 로그인 처리 */
    fun register(context: Context, email: String, password: String, displayName: String? = null) {
        login(context, email, password, displayName)
    }

    fun updateDisplayName(context: Context, name: String) {
        val trimmed = name.trim().ifBlank { "사용자" }
        prefs(context).edit()
            .putString(KEY_DISPLAY_NAME, trimmed)
            .apply()
    }

    /**
     * 갤러리 URI를 앱 내부 파일로 복사해 영구 경로로 저장한다.
     * @return 저장된 절대경로
     */
    fun updateAvatarFromUri(context: Context, uri: Uri): String {
        val dest = File(context.filesDir, AVATAR_FILE)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("이미지를 읽을 수 없습니다")
        val path = dest.absolutePath
        prefs(context).edit().putString(KEY_AVATAR_PATH, path).apply()
        return path
    }

    fun logout(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_LOGGED_IN, false)
            // 이메일은 남겨 두면 재로그인 시 편의, 비밀번호는 유지해 목 검증에 사용
            .apply()
    }

    /** 목 로그인 검증: 가입된 계정이 있으면 비밀번호 일치, 없으면 비어 있지 않으면 통과(데모) */
    fun validateLogin(context: Context, email: String, password: String): LoginResult {
        val e = email.trim()
        val p = password
        if (e.isEmpty() || !e.contains("@")) {
            return LoginResult.Error("올바른 이메일을 입력하세요")
        }
        if (p.length < 4) {
            return LoginResult.Error("비밀번호는 4자 이상이어야 합니다")
        }
        val savedEmail = prefs(context).getString(KEY_EMAIL, null)
        val savedPassword = prefs(context).getString(KEY_PASSWORD, null)
        if (!savedEmail.isNullOrBlank() && savedEmail.equals(e, ignoreCase = true)) {
            if (savedPassword != p) {
                return LoginResult.Error("비밀번호가 올바르지 않습니다")
            }
        }
        return LoginResult.Ok
    }

    sealed class LoginResult {
        data object Ok : LoginResult()
        data class Error(val message: String) : LoginResult()
    }
}
