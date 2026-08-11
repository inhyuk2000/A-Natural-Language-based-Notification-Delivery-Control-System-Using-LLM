package com.example.app

import android.content.ContentValues
import android.content.Context
import java.util.UUID

/**
 * 사용자 별명 + deviceId를 SQLite UserData 테이블에 저장/조회.
 */
object UserRepository {
    private const val PREFS = "AppPrefs"
    private const val KEY_DEVICE_ID = "deviceId"
    private const val TABLE = "UserData"

    fun ensureDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun hasUser(context: Context): Boolean {
        val deviceId = ensureDeviceId(context)
        val db = DBHelper(context).readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM $TABLE WHERE device_id = ? AND nickname IS NOT NULL AND TRIM(nickname) != '' LIMIT 1",
            arrayOf(deviceId),
        )
        val exists = cursor.moveToFirst()
        cursor.close()
        return exists
    }

    fun getNickname(context: Context): String {
        val deviceId = ensureDeviceId(context)
        val db = DBHelper(context).readableDatabase
        val cursor = db.rawQuery(
            "SELECT nickname FROM $TABLE WHERE device_id = ? LIMIT 1",
            arrayOf(deviceId),
        )
        val name = if (cursor.moveToFirst()) {
            cursor.getString(0)?.trim().orEmpty()
        } else {
            ""
        }
        cursor.close()
        return name.ifBlank { "사용자" }
    }

    fun saveUser(context: Context, nickname: String): Boolean {
        val trimmed = nickname.trim()
        if (trimmed.isEmpty()) return false
        val deviceId = ensureDeviceId(context)
        val db = DBHelper(context).writableDatabase
        val values = ContentValues().apply {
            put("device_id", deviceId)
            put("nickname", trimmed)
            put("created_at", System.currentTimeMillis())
        }
        val existing = db.rawQuery(
            "SELECT id FROM $TABLE WHERE device_id = ? LIMIT 1",
            arrayOf(deviceId),
        )
        val updated = if (existing.moveToFirst()) {
            val id = existing.getInt(0)
            existing.close()
            db.update(TABLE, values, "id = ?", arrayOf(id.toString())) > 0
        } else {
            existing.close()
            db.insert(TABLE, null, values) != -1L
        }
        if (updated) {
            // SQLite 저장 후, connect=true 일 때만 Firebase 프로필 동기화
            FirebaseRemoteLog.logUserProfile(context, trimmed)
        }
        return updated
    }

    fun updateNickname(context: Context, nickname: String): Boolean =
        saveUser(context, nickname)
}
