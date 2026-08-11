package com.example.app

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

/**
 * Firebase Realtime Database 모니터링 로그.
 *
 * SQLite = 앱 동작용 로컬 저장
 * Firebase = 배포/테스트 시 원격에서 확인하는 요약 이벤트 (connect로 ON/OFF)
 *
 * 사용법:
 * ```
 * FirebaseRemoteLog.connect = true   // 연결·업로드 ON
 * FirebaseRemoteLog.connect = false  // 완전 OFF (기본값)
 * ```
 *
 * Console 경로 예:
 * `notillm/devices/{deviceId}/rule_events/...`
 * `notillm/devices/{deviceId}/notif_events/...`
 * `notillm/devices/{deviceId}/profile`
 */
object FirebaseRemoteLog {
    private const val TAG = "FirebaseRemoteLog"
    private const val ROOT = "notillm"

    /**
     * Firebase 원격 로그 연결 스위치.
     * - true  → Realtime Database에 이벤트 업로드
     * - false → no-op (SQLite만 사용)
     */
    @JvmField
    var connect: Boolean = true

    private fun canUpload(): Boolean {
        if (!connect) {
            Log.d(TAG, "skip: connect=false")
            return false
        }
        return try {
            FirebaseApp.getInstance()
            true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "FirebaseApp 미초기화 — google-services.json 확인 필요: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Firebase 사용 불가: ${e.message}")
            false
        }
    }

    private fun deviceRef(context: Context) =
        FirebaseDatabase.getInstance()
            .reference
            .child(ROOT)
            .child("devices")
            .child(UserRepository.ensureDeviceId(context))

    /** 온보딩/프로필: 별명 등록 */
    fun logUserProfile(context: Context, nickname: String) {
        if (!canUpload()) return
        try {
            val payload = mapOf(
                "nickname" to nickname,
                "deviceId" to UserRepository.ensureDeviceId(context),
                "updatedAt" to System.currentTimeMillis(),
            )
            deviceRef(context).child("profile").setValue(payload)
                .addOnSuccessListener { Log.d(TAG, "profile uploaded") }
                .addOnFailureListener { e -> Log.e(TAG, "profile fail: ${e.message}") }
        } catch (e: Exception) {
            Log.e(TAG, "logUserProfile: ${e.message}")
        }
    }

    /** 규칙 생성/저장 요약 (알림 본문 제외) */
    fun logRuleEvent(
        context: Context,
        mode: String,
        apps: List<String>,
        contents: List<String>,
        deliveryIso: String,
        expiresIso: String,
        recurrence: String,
        title: String,
    ) {
        if (!canUpload()) return
        try {
            val payload = mapOf(
                "type" to "rule",
                "mode" to mode,
                "apps" to apps,
                "contents" to contents,
                "delivery" to deliveryIso,
                "expires" to expiresIso,
                "recurrence" to recurrence,
                "title" to title,
                "createdAt" to System.currentTimeMillis(),
            )
            deviceRef(context).child("rule_events").push().setValue(payload)
                .addOnSuccessListener { Log.d(TAG, "rule_event uploaded mode=$mode") }
                .addOnFailureListener { e -> Log.e(TAG, "rule_event fail: ${e.message}") }
        } catch (e: Exception) {
            Log.e(TAG, "logRuleEvent: ${e.message}")
        }
    }

    /**
     * 알림 처리 요약.
     * 개인정보 보호를 위해 title/text 원문은 올리지 않음.
     */
    fun logNotifEvent(
        context: Context,
        packageName: String,
        status: String,
        postTime: String,
    ) {
        if (!canUpload()) return
        try {
            val payload = mapOf(
                "type" to "notif",
                "packageName" to packageName,
                "status" to status,
                "postTime" to postTime,
                "createdAt" to System.currentTimeMillis(),
            )
            deviceRef(context).child("notif_events").push().setValue(payload)
                .addOnSuccessListener { Log.d(TAG, "notif_event uploaded status=$status") }
                .addOnFailureListener { e -> Log.e(TAG, "notif_event fail: ${e.message}") }
        } catch (e: Exception) {
            Log.e(TAG, "logNotifEvent: ${e.message}")
        }
    }
}
