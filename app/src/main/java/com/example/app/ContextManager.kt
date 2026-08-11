package com.example.app

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.serialization.json.*
import android.os.Build

class ContextManager(private val context: Context) {

    companion object {
        const val ACTION_HOME_MONITOR_REFRESH = "com.example.HOME_MONITOR_REFRESH"
    }

    private lateinit var dbHelper: DBHelper
    private lateinit var database: SQLiteDatabase

    fun initDB() {
        dbHelper = DBHelper(context)
        database = dbHelper.writableDatabase
        Log.d("DB_CHECK", "DB 연결 성공: ${database.path}")
    }

    private fun notifyHomeRefresh() {
        LocalBroadcastManager.getInstance(context)
            .sendBroadcast(Intent(ACTION_HOME_MONITOR_REFRESH))
    }

    fun resetDatabases(context: Context) {
        val dbHelper = DBHelper(context)
        val database = dbHelper.writableDatabase
        try {
            database.execSQL("DELETE FROM Notifications")
            database.execSQL("DELETE FROM ContextManager")
            database.execSQL("DELETE FROM NotifLog")
            Log.d("DB_CHECK", "Notifications, ContextManager, NotifLog 테이블 데이터 초기화 완료")
        } catch (e: Exception) {
            Log.e("DB_CHECK", "데이터 초기화 실패: ${e.message}")
        } finally {
            database.close()
        }
    }

    /** DB mode: mute | allow (구 block → mute) */
    private fun readMode(cursor: android.database.Cursor): String {
        return try {
            val idx = cursor.getColumnIndex("mode")
            val raw = if (idx < 0) "mute"
            else cursor.getString(idx)?.takeIf { it.isNotBlank() } ?: "mute"
            normalizeMode(raw)
        } catch (_: Exception) {
            "mute"
        }
    }

    private fun normalizeMode(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "allow" -> "allow"
            "mute", "block" -> "mute"
            else -> "mute"
        }
    }

    private fun isMuteMode(mode: String): Boolean = normalizeMode(mode) == "mute"

    private fun readStatus(cursor: android.database.Cursor): String {
        return try {
            val idx = cursor.getColumnIndex("status")
            if (idx < 0) "active"
            else cursor.getString(idx)?.takeIf { it.isNotBlank() } ?: "active"
        } catch (_: Exception) {
            "active"
        }
    }

    private fun readCol(cursor: android.database.Cursor, col: String, default: String = ""): String {
        return try {
            val idx = cursor.getColumnIndex(col)
            if (idx < 0) default
            else cursor.getString(idx)?.takeIf { it.isNotBlank() } ?: default
        } catch (_: Exception) {
            default
        }
    }

    private fun readRecurrence(cursor: android.database.Cursor): String =
        RecurrenceWindow.normalizeRecurrence(readCol(cursor, "recurrence", RecurrenceWindow.NONE))

    /**
     * 반복 규칙: expires 지나면 다음 회차로 delivery/expires 갱신.
     * @return 롤링된 행 수
     */
    private fun rollRecurringRuleIfNeeded(
        id: Int,
        recurrence: String,
        daysOfWeek: String,
        windowStart: String,
        windowEnd: String,
        expiresMilli: Long,
        currentMilli: Long,
    ): Boolean {
        if (!RecurrenceWindow.isRecurring(recurrence)) return false
        if (currentMilli < expiresMilli) return false
        if (windowStart.isBlank() || windowEnd.isBlank()) return false
        return try {
            val next = RecurrenceWindow.computeOccurrenceWindow(
                nowMs = currentMilli,
                recurrence = recurrence,
                daysOfWeekCsv = daysOfWeek,
                windowStart = windowStart,
                windowEnd = windowEnd,
            )
            val values = ContentValues().apply {
                put("delivery", next.deliveryIso)
                put("expires", next.expiresIso)
                put("status", "active")
            }
            database.update("ContextManager", values, "id = ?", arrayOf(id.toString()))
            Log.d(
                "DB_CHECK",
                "반복 규칙 회차 갱신 id=$id → ${next.deliveryIso} .. ${next.expiresIso}",
            )
            true
        } catch (e: Exception) {
            Log.e("DB_CHECK", "반복 규칙 회차 갱신 실패 id=$id: ${e.message}")
            false
        }
    }

    /**
     * 일회성: expires 지나면 status=completed.
     * 반복: 다음 회차로 delivery/expires 롤링 (completed 하지 않음).
     * @return 일회성 completed 건수 + 반복 롤링 건수
     */
    fun markExpiredRulesCompleted(): Int {
        initDB()
        val timeUtils = TimeUtils()
        val currentMilli = try {
            timeUtils.parseIsoToMillis(timeUtils.getTodayDatetime())
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
        val cursor = database.rawQuery(
            """
            SELECT id, expires, status, recurrence, days_of_week, window_start, window_end
            FROM ContextManager
            """.trimIndent(),
            null,
        )
        val toComplete = mutableListOf<Int>()
        var rolled = 0
        if (cursor.moveToFirst()) {
            do {
                val status = readStatus(cursor)
                if (status.equals("completed", ignoreCase = true)) continue
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val expiresRaw = cursor.getString(cursor.getColumnIndexOrThrow("expires")) ?: continue
                val expiresMilli = try {
                    timeUtils.parseIsoToMillis(expiresRaw)
                } catch (_: Exception) {
                    continue
                }
                if (currentMilli < expiresMilli) continue

                val recurrence = readRecurrence(cursor)
                if (RecurrenceWindow.isRecurring(recurrence)) {
                    if (
                        rollRecurringRuleIfNeeded(
                            id = id,
                            recurrence = recurrence,
                            daysOfWeek = readCol(cursor, "days_of_week"),
                            windowStart = readCol(cursor, "window_start"),
                            windowEnd = readCol(cursor, "window_end"),
                            expiresMilli = expiresMilli,
                            currentMilli = currentMilli,
                        )
                    ) {
                        rolled++
                    }
                } else {
                    toComplete.add(id)
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        var updated = 0
        if (toComplete.isNotEmpty()) {
            val values = ContentValues().apply { put("status", "completed") }
            for (id in toComplete) {
                updated += database.update(
                    "ContextManager",
                    values,
                    "id = ?",
                    arrayOf(id.toString()),
                )
            }
            Log.d("DB_CHECK", "만료 규칙 completed 표시: ${updated}건")
        }
        if (rolled > 0) notifyHomeRefresh()
        return updated + rolled
    }

    private fun logNotifOutcome(
        packageName: String,
        title: String,
        text: String,
        postTime: String,
        status: String,
    ) {
        try {
            val values = ContentValues().apply {
                put("package_name", packageName)
                put("title", title)
                put("text", text)
                put("post_time", postTime)
                put("status", status)
                put("created_at", System.currentTimeMillis())
                put("hidden", 0)
            }
            database.insert("NotifLog", null, values)
            notifyHomeRefresh()
            // SQLite 로컬 저장 후, connect=true 일 때만 Firebase 요약 업로드 (본문 제외)
            FirebaseRemoteLog.logNotifEvent(
                context = context,
                packageName = packageName,
                status = status,
                postTime = postTime,
            )
        } catch (e: Exception) {
            Log.e("DB_CHECK", "NotifLog 기록 실패: ${e.message}")
        }
    }

    /**
     * stored 로그를 삭제하지 않고 [newStatus]로만 변경 (completed / blocked 등).
     * @return 갱신된 행 수
     */
    private fun markStoredNotifLogs(
        packageName: String,
        title: String = "",
        text: String = "",
        postTime: String = "",
        newStatus: String,
    ): Int {
        return try {
            val values = ContentValues().apply { put("status", newStatus) }
            val updated = when {
                postTime.isNotBlank() -> database.update(
                    "NotifLog",
                    values,
                    "status = ? AND package_name = ? AND post_time = ?",
                    arrayOf("stored", packageName, postTime),
                )
                title.isNotBlank() || text.isNotBlank() -> database.update(
                    "NotifLog",
                    values,
                    "status = ? AND package_name = ? AND IFNULL(title,'') = ? AND IFNULL(text,'') = ?",
                    arrayOf("stored", packageName, title, text),
                )
                else -> database.update(
                    "NotifLog",
                    values,
                    "status = ? AND package_name = ?",
                    arrayOf("stored", packageName),
                )
            }
            if (updated > 0) {
                Log.d("DB_CHECK", "NotifLog stored→$newStatus: ${updated}건 pkg=$packageName")
                notifyHomeRefresh()
            }
            updated
        } catch (e: Exception) {
            Log.e("DB_CHECK", "NotifLog 상태 변경 실패: ${e.message}")
            0
        }
    }

    /** 대기열에서 사용자에게 전달됨 → stored를 completed로 표시 (삭제하지 않음) */
    private fun onHeldNotifDelivered(
        packageName: String,
        title: String,
        text: String,
        postTime: String,
    ) {
        markStoredNotifLogs(packageName, title, text, postTime, "completed")
    }

    private fun matchesContent(
        notifTitle: String,
        notifText: String,
        targetContents: List<String>,
    ): Boolean {
        if (targetContents.isEmpty()) return true
        return targetContents.any { keyword ->
            notifTitle.contains(keyword) || notifText.contains(keyword)
        }
    }

    /**
     * mute=true: 이 알림을 보류해야 하면 true.
     * - name 있음 → 해당 앱(+content)만 보류
     * - name 비움 → content 있으면 그 키워드만 보류, 없으면 전체 보류
     * - exceptions는 레거시 호환용(신규 규칙은 비움). 있으면 예외 앱(+content)만 통과
     */
    private fun shouldHoldByMute(
        packageName: String,
        notifTitle: String,
        notifText: String,
        targetNames: List<String>,
        targetContents: List<String>,
        targetExceptions: List<String>,
    ): Boolean {
        val contentOk = matchesContent(notifTitle, notifText, targetContents)
        if (targetExceptions.isNotEmpty()) {
            val passThrough = targetExceptions.contains(packageName) && contentOk
            return !passThrough
        }
        if (targetNames.isNotEmpty()) {
            if (!targetNames.contains(packageName)) return false
            return contentOk
        }
        return if (targetContents.isEmpty()) true else contentOk
    }

    /**
     * mute=false(allow): 즉시 수신해야 하면 true.
     * name/content에 맞으면 허용, 아니면 보류.
     */
    private fun shouldDeliverByAllow(
        packageName: String,
        notifTitle: String,
        notifText: String,
        targetNames: List<String>,
        targetContents: List<String>,
        targetExceptions: List<String>,
    ): Boolean {
        if (!matchesContent(notifTitle, notifText, targetContents)) return false
        if (targetExceptions.contains(packageName)) return false
        if (targetNames.isNotEmpty() && !targetNames.contains(packageName)) return false
        return true
    }

    /** 활성 규칙 기준으로 보류 여부 */
    private fun shouldHoldForActiveRules(
        packageName: String,
        notifTitle: String,
        notifText: String,
        active: List<RuleEval>,
    ): Boolean {
        if (active.isEmpty()) return false
        var held = false
        for (rule in active) {
            if (rule.mute) {
                if (
                    shouldHoldByMute(
                        packageName, notifTitle, notifText,
                        rule.names, rule.contents, rule.exceptions,
                    )
                ) {
                    held = true
                }
            } else {
                if (
                    !shouldDeliverByAllow(
                        packageName, notifTitle, notifText,
                        rule.names, rule.contents, rule.exceptions,
                    )
                ) {
                    held = true
                }
            }
        }
        return held
    }

    private data class RuleEval(
        val delivery: Long,
        val expires: Long,
        val mute: Boolean,
        val names: List<String>,
        val contents: List<String>,
        val exceptions: List<String>,
    )

    private fun storePendingNotif(
        sbn: StatusBarNotification,
        packageName: String,
        notifTitle: String,
        notifText: String,
    ) {
        val dbId = (sbn.key + "_" + sbn.postTime).hashCode()
        val values = ContentValues().apply {
            put(
                "deviceId",
                runCatching { NotificationListener.deviceId }.getOrElse {
                    // 리스너 미기동 등 예외 경로 — prefs에서 폴백
                    context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                        .getString("deviceId", "unknown") ?: "unknown"
                }
            )
            put("package_name", packageName)
            put("id", dbId)
            put("post_time", sbn.postTime)
            put("text", notifText)
            put("title", notifTitle)
        }
        database.insert("Notifications", null, values)
    }

    private fun deliverPendingNotif(sbn: StatusBarNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            NotificationListener.instance?.sendDelayedNotification(sbn)
        }
        val pkg = sbn.packageName
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        onHeldNotifDelivered(pkg, title, text, sbn.postTime.toString())
        NotificationListener.pendingNotifications.remove(sbn.key)
        database.delete(
            "Notifications",
            "id = ?",
            arrayOf((sbn.key + "_" + sbn.postTime).hashCode().toString())
        )
    }

    private fun dropPendingNotif(sbn: StatusBarNotification) {
        NotificationListener.pendingNotifications.remove(sbn.key)
        database.delete(
            "Notifications",
            "id = ?",
            arrayOf((sbn.key + "_" + sbn.postTime).hashCode().toString())
        )
    }

    private fun sendUserFeedback(message: String) {
        val intent = Intent("com.example.APP_NAME_MAPPING_ERROR")
        intent.putExtra("error_message", message)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    /**
     * 수동 규칙 등록 (UI 폼). 수신은 허용(필터/지연)만 지원.
     */
    fun insertManualRule(
        ruleTitle: String,
        appDisplayNames: List<String>,
        contentTags: List<String>,
        exceptionDisplayNames: List<String>,
        deliveryIso: String,
        expiresIso: String,
        mode: String = "allow",
    ): Boolean {
        initDB()

        val namePackages = mutableListOf<String>()
        for (appName in appDisplayNames) {
            val pkg = AppNameMapper.toPackageName(appName)
            if (pkg.isNullOrBlank()) {
                sendUserFeedback("‘$appName’ 앱을 찾을 수 없습니다.")
                return false
            }
            namePackages.add(pkg)
        }

        val exceptionPackages = mutableListOf<String>()
        for (appName in exceptionDisplayNames) {
            val pkg = AppNameMapper.toPackageName(appName)
            if (pkg.isNullOrBlank()) {
                sendUserFeedback("‘$appName’ 앱을 찾을 수 없습니다.")
                return false
            }
            exceptionPackages.add(pkg)
        }

        val values = ContentValues().apply {
            put("name", namePackages.takeIf { it.isNotEmpty() }?.joinToString(","))
            put("content", contentTags.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(","))
            put("exceptions", exceptionPackages.takeIf { it.isNotEmpty() }?.joinToString(","))
            put("delivery", deliveryIso)
            put("activity", ruleTitle.ifBlank { "수동 규칙" })
            put("location", null as String?)
            put("expires", expiresIso)
            put("mode", normalizeMode(mode))
            put("status", "active")
            put("recurrence", RecurrenceWindow.NONE)
            put("days_of_week", "")
            put("window_start", "")
            put("window_end", "")
        }
        database.insert("ContextManager", null, values)
        Log.d("DB_CHECK", "수동 규칙 삽입 완료: $ruleTitle mode=${normalizeMode(mode)}")
        notifyHomeRefresh()
        return true
    }

    /**
     * 기존 규칙 수정. 앱 표시명·키워드·시간(ISO)·mode를 갱신한다.
     */
    fun updateRule(
        ruleId: Int,
        ruleTitle: String,
        appDisplayNames: List<String>,
        contentTags: List<String>,
        deliveryIso: String,
        expiresIso: String,
        mode: String = "allow",
    ): Boolean {
        initDB()

        val namePackages = mutableListOf<String>()
        for (appName in appDisplayNames) {
            val trimmed = appName.trim()
            if (trimmed.isEmpty()) continue
            // 이미 패키지명이면 그대로, 아니면 표시명 매핑
            val pkg = if (trimmed.contains(".")) trimmed else AppNameMapper.toPackageName(trimmed)
            if (pkg.isNullOrBlank()) {
                sendUserFeedback("‘$trimmed’ 앱을 찾을 수 없습니다.")
                return false
            }
            namePackages.add(pkg)
        }

        val normalizedMode = normalizeMode(mode)
        val values = ContentValues().apply {
            put("name", namePackages.takeIf { it.isNotEmpty() }?.joinToString(","))
            put("content", contentTags.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(","))
            put("delivery", deliveryIso)
            put("expires", expiresIso)
            put("activity", ruleTitle.ifBlank { "알림 규칙" })
            put("mode", normalizedMode)
            put("status", "active")
        }
        val updated = database.update(
            "ContextManager",
            values,
            "id = ?",
            arrayOf(ruleId.toString()),
        )
        Log.d("DB_CHECK", "규칙 수정 id=$ruleId rows=$updated mode=$normalizedMode")
        if (updated > 0) notifyHomeRefresh()
        return updated > 0
    }

    fun deleteRule(ruleId: Int): Boolean {
        initDB()
        val deleted = database.delete(
            "ContextManager",
            "id = ?",
            arrayOf(ruleId.toString()),
        )
        Log.d("DB_CHECK", "규칙 삭제 id=$ruleId rows=$deleted")
        if (deleted > 0) notifyHomeRefresh()
        return deleted > 0
    }

    /** GPT가 null / 비배열을 줘도 빈 배열로 처리 (JsonNull은 ?. 로 걸러지지 않음) */
    private fun JsonObject.arrayOrEmpty(key: String): JsonArray {
        val el = this[key] ?: return JsonArray(emptyList())
        return el as? JsonArray ?: JsonArray(emptyList())
    }

    private fun JsonArray.stringItems(): List<String> =
        mapNotNull { el ->
            if (el is JsonNull) null else el.jsonPrimitive.contentOrNull
        }.filter { it.isNotBlank() }

    /**
     * 활성 mute/allow 규칙상 더 이상 보류할 필요가 없는 held만 전달.
     * - mute만 활성 → 매칭 대상은 유지, 비매칭은 전달
     * - allow만 활성 → 허용 매칭만 전달
     * - 둘 다 → shouldHoldForActiveRules와 동일 기준
     */
    private fun flushHeldNotBlockedByRules(active: List<RuleEval>): Int {
        if (active.isEmpty()) {
            return flushHeldNotifications(emptyList(), emptyList(), emptyList())
        }
        val listener = NotificationListener.instance
        var delivered = 0

        fun tryDeliver(pkg: String, title: String, text: String, postTime: String, notifId: Int?, pendingKey: String?) {
            if (shouldHoldForActiveRules(pkg, title, text, active)) return
            try {
                val pending = pendingKey?.let { NotificationListener.pendingNotifications[it] }
                if (pending != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        listener?.sendDelayedNotification(pending)
                    }
                    NotificationListener.pendingNotifications.remove(pendingKey)
                } else {
                    listener?.sendSimpleStoredNotification(pkg, title, text)
                }
                onHeldNotifDelivered(pkg, title, text, postTime)
                if (notifId != null) {
                    database.delete("Notifications", "id = ?", arrayOf(notifId.toString()))
                } else if (pendingKey != null) {
                    val sbn = pending
                    if (sbn != null) {
                        database.delete(
                            "Notifications",
                            "id = ?",
                            arrayOf((pendingKey + "_" + sbn.postTime).hashCode().toString()),
                        )
                    }
                }
                delivered++
            } catch (e: Exception) {
                Log.e("CHK", "규칙 기준 플러시 실패 pkg=$pkg: ${e.message}", e)
            }
        }

        val cursor = database.rawQuery("SELECT * FROM Notifications", null)
        while (cursor.moveToNext()) {
            val notifId = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
            val pkg = cursor.getString(cursor.getColumnIndexOrThrow("package_name")) ?: ""
            val title = cursor.getString(cursor.getColumnIndexOrThrow("title")) ?: ""
            val text = cursor.getString(cursor.getColumnIndexOrThrow("text")) ?: ""
            val postTime = cursor.getString(cursor.getColumnIndexOrThrow("post_time")) ?: ""
            val pendingKey = NotificationListener.pendingNotifications.entries.find { entry ->
                (entry.key + "_" + entry.value.postTime).hashCode() == notifId
            }?.key
            tryDeliver(pkg, title, text, postTime, notifId, pendingKey)
        }
        cursor.close()

        for (entry in NotificationListener.pendingNotifications.entries.toList()) {
            val sbn = entry.value
            val pkg = sbn.packageName
            val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            tryDeliver(pkg, title, text, sbn.postTime.toString(), null, entry.key)
        }

        Log.d("CHK", "규칙 기준 홀드 플러시: delivered=$delivered")
        if (delivered > 0) notifyHomeRefresh()
        return delivered
    }

    /**
     * 홀드된 알림을 즉시 사용자에게 재전송.
     * - Notifications DB + pendingNotifications 둘 다 소진 (이전 버그로 DB만 비고 pending만 남은 경우 대응)
     * - pending 없으면 DB title/text로 폴백 전송
     */
    private fun flushHeldNotifications(
        namePackages: List<String>,
        contentItems: List<String>,
        exceptionPackages: List<String>,
    ): Int {
        val listener = NotificationListener.instance
        var delivered = 0

        fun matchesFilter(pkg: String, title: String, text: String): Boolean {
            if (exceptionPackages.isNotEmpty() && exceptionPackages.contains(pkg)) return false
            if (namePackages.isNotEmpty() && !namePackages.contains(pkg)) return false
            if (contentItems.isNotEmpty()) {
                val hit = contentItems.any { tag ->
                    title.contains(tag, ignoreCase = true) || text.contains(tag, ignoreCase = true)
                }
                if (!hit) return false
            }
            return true
        }

        // 1) DB 행 기준
        val whereClauseList = mutableListOf<String>()
        if (namePackages.isNotEmpty()) {
            val quotedNames = namePackages.joinToString(",") { "'$it'" }
            whereClauseList.add("package_name IN ($quotedNames)")
        }
        if (contentItems.isNotEmpty()) {
            val contentConditions = contentItems.joinToString(" OR ") {
                "(title LIKE '%$it%' OR text LIKE '%$it%')"
            }
            whereClauseList.add(contentConditions)
        }
        if (exceptionPackages.isNotEmpty()) {
            val quotedExceptions = exceptionPackages.joinToString(",") { "'$it'" }
            whereClauseList.add("package_name NOT IN ($quotedExceptions)")
        }
        val whereClause = whereClauseList.joinToString(" AND ")
        val query = if (whereClause.isNotEmpty()) {
            "SELECT * FROM Notifications WHERE $whereClause"
        } else {
            "SELECT * FROM Notifications"
        }

        val cursor = database.rawQuery(query, null)
        Log.d("CHK_NTF_LEN", "DB rows=${cursor.count} pending=${NotificationListener.pendingNotifications.size}")

        while (cursor.moveToNext()) {
            val notifId = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
            val pkg = cursor.getString(cursor.getColumnIndexOrThrow("package_name")) ?: ""
            val title = cursor.getString(cursor.getColumnIndexOrThrow("title")) ?: ""
            val text = cursor.getString(cursor.getColumnIndexOrThrow("text")) ?: ""
            val postTime = cursor.getString(cursor.getColumnIndexOrThrow("post_time")) ?: ""

            val targetEntry = NotificationListener.pendingNotifications.entries.find { entry ->
                (entry.key + "_" + entry.value.postTime).hashCode() == notifId
            }

            try {
                if (targetEntry != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        listener?.sendDelayedNotification(targetEntry.value)
                    }
                    NotificationListener.pendingNotifications.remove(targetEntry.key)
                    Log.d("CHK", "플러시(pending): key=${targetEntry.key} pkg=$pkg")
                } else {
                    listener?.sendSimpleStoredNotification(pkg, title, text)
                    Log.d("CHK", "플러시(DB폴백): id=$notifId pkg=$pkg")
                }
                onHeldNotifDelivered(pkg, title, text, postTime)
                delivered++
            } catch (e: Exception) {
                Log.e("CHK", "플러시 실패 id=$notifId: ${e.message}", e)
            }
            database.delete("Notifications", "id = ?", arrayOf(notifId.toString()))
        }
        cursor.close()

        // 2) DB에 없거나 이전에 DB만 지워진 채 남은 pending 전부 소진
        val remaining = NotificationListener.pendingNotifications.entries.toList()
        for (entry in remaining) {
            val sbn = entry.value
            val pkg = sbn.packageName
            val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (!matchesFilter(pkg, title, text)) continue

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    listener?.sendDelayedNotification(sbn)
                }
                NotificationListener.pendingNotifications.remove(entry.key)
                database.delete(
                    "Notifications",
                    "id = ?",
                    arrayOf((entry.key + "_" + sbn.postTime).hashCode().toString()),
                )
                onHeldNotifDelivered(pkg, title, text, sbn.postTime.toString())
                delivered++
                Log.d("CHK", "플러시(잔여 pending): key=${entry.key} pkg=$pkg")
            } catch (e: Exception) {
                Log.e("CHK", "잔여 pending 플러시 실패: ${e.message}", e)
            }
        }

        Log.d("CHK", "홀드 플러시 완료: delivered=$delivered remainingPending=${NotificationListener.pendingNotifications.size}")
        if (delivered > 0) notifyHomeRefresh()
        return delivered
    }

    /** 홈 「즉시 수신」 — 보류 중인 알림 전부 사용자에게 전달 */
    fun flushAllHeldNotifications(): Int {
        initDB()
        return flushHeldNotifications(
            namePackages = emptyList(),
            contentItems = emptyList(),
            exceptionPackages = emptyList(),
        )
    }

    fun handleIncomingRule(
        target: JsonObject,
        condition: JsonObject,
        ruleTitle: String? = null,
    ): Boolean {
        initDB()

        val nameArray = target.arrayOrEmpty("name")
        val contentArray = target.arrayOrEmpty("content")
        val exceptionsArray = target.arrayOrEmpty("exceptions")

        val namePackages = mutableListOf<String>()
        val exceptionPackages = mutableListOf<String>()

        for (appName in nameArray.stringItems()) {
            val pkg = AppNameMapper.toPackageName(appName)
            if (pkg.isNullOrBlank()) {
                Log.e("AppNameMapper", "❌ 앱 이름 매핑 실패: $appName")
                sendUserFeedback("‘$appName’ 앱을 찾을 수 없습니다. 다시 입력해주세요.")
                return false // 🚫 DB 등록 중단
            }
            namePackages.add(pkg)
        }

        for (appName in exceptionsArray.stringItems()) {
            val pkg = AppNameMapper.toPackageName(appName)
            if (pkg.isNullOrBlank()) {
                Log.e("AppNameMapper", "❌ 예외 앱 이름 매핑 실패: $appName")
                sendUserFeedback("‘$appName’ 앱을 찾을 수 없습니다. 다시 입력해주세요.")
                return false // 🚫 DB 등록 중단
            }
            exceptionPackages.add(pkg)
        }

        val nameStr = if (namePackages.isEmpty()) null else namePackages.joinToString(",")
        val contentItems = contentArray.stringItems()
        val contentStr = if (contentItems.isEmpty()) null else contentItems.joinToString(",")
        val exceptionsStr = if (exceptionPackages.isEmpty()) null else exceptionPackages.joinToString(",")

        fun JsonObject.optString(key: String): String? {
            val el = this[key] ?: return null
            if (el is JsonNull) return null
            return (el as? JsonPrimitive)?.contentOrNull
        }
        fun JsonObject.optIntList(key: String): List<Int> {
            val el = this[key] ?: return emptyList()
            return when (el) {
                is JsonArray -> el.mapNotNull {
                    if (it is JsonNull) null
                    else (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                }
                is JsonPrimitive -> el.contentOrNull
                    ?.split(",")
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?: emptyList()
                else -> emptyList()
            }.filter { it in 1..7 }.distinct().sorted()
        }

        val recurrence = RecurrenceWindow.normalizeRecurrence(condition.optString("recurrence"))
        val daysOfWeekCsv = condition.optIntList("days_of_week").joinToString(",")
        var windowStart = condition.optString("window_start")?.trim().orEmpty()
        var windowEnd = condition.optString("window_end")?.trim().orEmpty()

        val timeUtils = TimeUtils()
        val currentMilli = timeUtils.parseIsoToMillis(timeUtils.getTodayDatetime())
        var deliveryIso = condition["delivery"]!!.jsonObject["absolute"]!!.jsonPrimitive.content
        var expiresIso = condition["expires"]!!.jsonObject["absolute"]!!.jsonPrimitive.content

        // 구버전 delivery=expires 꼼수 제거: 항상 delivery < expires 보장
        run {
            var deliveryMs = timeUtils.parseIsoToMillis(deliveryIso)
            var expiresMs = timeUtils.parseIsoToMillis(expiresIso)
            if (expiresMs <= deliveryMs) {
                if (deliveryMs > currentMilli) {
                    // 둘 다 '끝 시각'으로 온 경우 → [지금, 그 시각)
                    Log.w("RULE", "delivery=expires 꼼수 교정: end=$expiresIso → delivery=now")
                    deliveryIso = timeUtils.getTodayDatetime()
                    deliveryMs = timeUtils.parseIsoToMillis(deliveryIso)
                    expiresIso = timeUtils.formatMillisToIso(expiresMs)
                } else {
                    // 영구간 → 최소 1분
                    expiresMs = deliveryMs + 60_000L
                    expiresIso = timeUtils.formatMillisToIso(expiresMs)
                    Log.w("RULE", "영구간 교정: expires=$expiresIso")
                }
            }
        }

        val effectiveRecurrence =
            if (recurrence == RecurrenceWindow.WEEKLY && daysOfWeekCsv.isBlank()) {
                RecurrenceWindow.DAILY
            } else {
                recurrence
            }

        if (RecurrenceWindow.isRecurring(effectiveRecurrence)) {
            if (windowStart.isBlank() || windowEnd.isBlank()) {
                windowStart = RecurrenceWindow.hmFromIso(deliveryIso)
                windowEnd = RecurrenceWindow.hmFromIso(expiresIso)
            }
            val occ = RecurrenceWindow.computeOccurrenceWindow(
                nowMs = currentMilli,
                recurrence = effectiveRecurrence,
                daysOfWeekCsv = daysOfWeekCsv.ifBlank { null },
                windowStart = windowStart,
                windowEnd = windowEnd,
            )
            deliveryIso = occ.deliveryIso
            expiresIso = occ.expiresIso
        }

        val muteFlag = when (val el = target["mute"]) {
            is JsonPrimitive -> when {
                el.isString -> el.contentOrNull?.equals("true", ignoreCase = true) ?: true
                else -> runCatching { el.content.toBoolean() }.getOrDefault(true)
            }
            else -> true
        }
        val modeStr = if (muteFlag) "mute" else "allow"

        val displayTitle = ruleTitle?.trim()?.takeIf { it.isNotEmpty() }
            ?: deriveRuleTitleFromTarget(
                prompt = "",
                appNames = nameArray.stringItems(),
                exceptions = emptyList(),
                mute = muteFlag,
                recurrence = effectiveRecurrence,
                daysOfWeek = daysOfWeekCsv,
                windowStart = windowStart,
                windowEnd = windowEnd,
            )

        Log.d("RULE", "등록된 조건 package_name: ${nameStr ?: "null"}")
        Log.d("RULE", "등록된 조건 내용: ${contentStr ?: "null"}")
        Log.d("RULE", "등록된 예외(미사용): ${exceptionsStr ?: "null"}")
        Log.d(
            "RULE",
            "표시 제목: $displayTitle mute=$muteFlag recurrence=$effectiveRecurrence window=$windowStart~$windowEnd",
        )

        val deliveryMilli = timeUtils.parseIsoToMillis(deliveryIso)
        val expiresMilli = timeUtils.parseIsoToMillis(expiresIso)

        fun buildValues(): ContentValues = ContentValues().apply {
            put("name", nameStr)
            put("content", contentStr)
            // 신규 mute/allow 툴 경로: exceptions 미사용
            putNull("exceptions")
            put("delivery", deliveryIso)
            put("activity", displayTitle)
            put("location", condition["location"].toString())
            put("expires", expiresIso)
            put("mode", modeStr)
            put("status", "active")
            put("recurrence", effectiveRecurrence)
            put("days_of_week", daysOfWeekCsv)
            put("window_start", windowStart)
            put("window_end", windowEnd)
        }

        if (currentMilli >= deliveryMilli) {
            // allow 규칙만: 허용 매칭분 즉시 플러시. mute는 보류 유지
            if (!muteFlag) {
                flushHeldNotifications(namePackages, contentItems, emptyList())
            }

            if (currentMilli < expiresMilli) {
                database.insert("ContextManager", null, buildValues())
                Log.d("DB_CHECK", "ContextManager 규칙 삽입 mode=$modeStr")
                notifyHomeRefresh()
            } else if (RecurrenceWindow.isRecurring(effectiveRecurrence)) {
                val occ = RecurrenceWindow.computeOccurrenceWindow(
                    nowMs = currentMilli,
                    recurrence = effectiveRecurrence,
                    daysOfWeekCsv = daysOfWeekCsv.ifBlank { null },
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                )
                deliveryIso = occ.deliveryIso
                expiresIso = occ.expiresIso
                database.insert("ContextManager", null, buildValues())
                notifyHomeRefresh()
            }
        } else {
            database.insert("ContextManager", null, buildValues())
            Log.d("DB_CHECK", "딜리버리 이전 → 규칙 저장 mode=$modeStr")
            notifyHomeRefresh()
        }

        FirebaseRemoteLog.logRuleEvent(
            context = context,
            mode = modeStr,
            apps = nameArray.stringItems(),
            contents = contentItems,
            deliveryIso = deliveryIso,
            expiresIso = expiresIso,
            recurrence = effectiveRecurrence,
            title = displayTitle,
        )

        return true
    }

    fun handleIncomingNotif(sbn: StatusBarNotification) {
        initDB()
        Log.d("NOTIF_TEST_SBN", "handleIncomingNotif 실행됨, 알림 ID: ${sbn.id}")

        val timeUtils = TimeUtils()
        val currentMilli = timeUtils.parseIsoToMillis(timeUtils.getTodayDatetime())
        val packageName = sbn.packageName
        val notifTitle = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
        val notifText = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val postTime = sbn.postTime.toString()

        val rules = mutableListOf<RuleEval>()
        val cursor = database.rawQuery("SELECT * FROM ContextManager", null)
        if (cursor.moveToFirst()) {
            do {
                if (readStatus(cursor).equals("completed", ignoreCase = true)) continue
                rules.add(
                    RuleEval(
                        delivery = timeUtils.parseIsoToMillis(
                            cursor.getString(cursor.getColumnIndexOrThrow("delivery"))
                        ),
                        expires = timeUtils.parseIsoToMillis(
                            cursor.getString(cursor.getColumnIndexOrThrow("expires"))
                        ),
                        mute = isMuteMode(readMode(cursor)),
                        names = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                        contents = cursor.getString(cursor.getColumnIndexOrThrow("content"))
                            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                        exceptions = cursor.getString(cursor.getColumnIndexOrThrow("exceptions"))
                            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()

        if (rules.isEmpty()) {
            deliverPendingNotif(sbn)
            logNotifOutcome(packageName, notifTitle, notifText, postTime, "delivered")
            return
        }

        val active = rules.filter { currentMilli in it.delivery until it.expires }

        // 시작 전(future) 규칙은 적용하지 않음 — 평소처럼 수신
        if (active.isEmpty()) {
            deliverPendingNotif(sbn)
            logNotifOutcome(packageName, notifTitle, notifText, postTime, "delivered")
            return
        }

        if (shouldHoldForActiveRules(packageName, notifTitle, notifText, active)) {
            Log.d("NOTIF_TEST_SBN", "stored (mute/allow hold)")
            storePendingNotif(sbn, packageName, notifTitle, notifText)
            logNotifOutcome(packageName, notifTitle, notifText, postTime, "stored")
        } else {
            Log.d("NOTIF_TEST_SBN", "delivered (pass)")
            deliverPendingNotif(sbn)
            logNotifOutcome(packageName, notifTitle, notifText, postTime, "delivered")
        }
    }

    fun periodicDeliveryCheck() {
        initDB()
        val timeUtils = TimeUtils()
        val currentMilli = timeUtils.parseIsoToMillis(timeUtils.getTodayDatetime())
        val cursor = database.rawQuery("SELECT * FROM ContextManager", null)

        val activeConditions = mutableListOf<Map<String, String>>()
        val expiredIds = mutableListOf<Int>()
        var rolled = 0

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val rowStatus = readStatus(cursor)
                if (rowStatus.equals("completed", ignoreCase = true)) continue

                var deliveryMilli = timeUtils.parseIsoToMillis(cursor.getString(cursor.getColumnIndexOrThrow("delivery")))
                var expiresMilli = timeUtils.parseIsoToMillis(cursor.getString(cursor.getColumnIndexOrThrow("expires")))
                val recurrence = readRecurrence(cursor)

                if (currentMilli >= expiresMilli && RecurrenceWindow.isRecurring(recurrence)) {
                    if (
                        rollRecurringRuleIfNeeded(
                            id = id,
                            recurrence = recurrence,
                            daysOfWeek = readCol(cursor, "days_of_week"),
                            windowStart = readCol(cursor, "window_start"),
                            windowEnd = readCol(cursor, "window_end"),
                            expiresMilli = expiresMilli,
                            currentMilli = currentMilli,
                        )
                    ) {
                        rolled++
                        // 회차 종료 시 보류분을 지연 수신으로 넘긴 뒤 다음 회차로 롤
                        flushHeldNotifications(emptyList(), emptyList(), emptyList())
                        val refreshed = database.rawQuery(
                            "SELECT delivery, expires FROM ContextManager WHERE id = ?",
                            arrayOf(id.toString()),
                        )
                        if (refreshed.moveToFirst()) {
                            deliveryMilli = timeUtils.parseIsoToMillis(
                                refreshed.getString(refreshed.getColumnIndexOrThrow("delivery"))
                            )
                            expiresMilli = timeUtils.parseIsoToMillis(
                                refreshed.getString(refreshed.getColumnIndexOrThrow("expires"))
                            )
                        }
                        refreshed.close()
                    }
                }

                if (currentMilli in deliveryMilli until expiresMilli) {
                    val cond = mapOf(
                        "id" to id.toString(),
                        "name" to (cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: ""),
                        "content" to (cursor.getString(cursor.getColumnIndexOrThrow("content")) ?: ""),
                        "exceptions" to (cursor.getString(cursor.getColumnIndexOrThrow("exceptions")) ?: ""),
                        "mode" to readMode(cursor)
                    )
                    activeConditions.add(cond)
                } else if (currentMilli >= expiresMilli) {
                    if (!RecurrenceWindow.isRecurring(recurrence)) {
                        expiredIds.add(id)
                    }
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        // 일회성 만료 → completed + 보류분 전부 지연 수신
        if (expiredIds.isNotEmpty()) {
            val completedValues = ContentValues().apply { put("status", "completed") }
            for (expiredId in expiredIds) {
                database.update(
                    "ContextManager",
                    completedValues,
                    "id = ?",
                    arrayOf(expiredId.toString()),
                )
            }
            Log.d("DB_CHECK", "만료 규칙 completed 표시: ${expiredIds.size}건 → 보류 알림 플러시")
            flushHeldNotifications(emptyList(), emptyList(), emptyList())
            notifyHomeRefresh()
        } else if (rolled > 0) {
            notifyHomeRefresh()
        }

        if (activeConditions.isNotEmpty()) {
            // 실시간 매칭과 동일: 활성 mute/allow 기준으로 더 이상 hold가 아닌 held만 flush
            val activeEvals = activeConditions.map { cond ->
                RuleEval(
                    delivery = 0L,
                    expires = Long.MAX_VALUE,
                    mute = isMuteMode(cond["mode"] ?: "mute"),
                    names = cond["name"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                        ?: emptyList(),
                    contents = cond["content"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                        ?: emptyList(),
                    exceptions = cond["exceptions"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                        ?: emptyList(),
                )
            }
            flushHeldNotBlockedByRules(activeEvals)
        } else if (expiredIds.isEmpty()) {
            // 활성 규칙 없음 → 보류분 전부 지연 수신 (시작 전 규칙은 수신을 막지 않음)
            flushHeldNotifications(emptyList(), emptyList(), emptyList())
        }

        Log.d("DB_CHECK", "periodicDeliveryCheck 완료")
    }
}

/** 채팅 명령 + 추출 결과로 표시용 규칙 이름 생성 (추가 LLM 불필요) */
fun deriveRuleTitleFromTarget(
    prompt: String,
    appNames: List<String>,
    exceptions: List<String>,
    mute: Boolean = true,
    recurrence: String = RecurrenceWindow.NONE,
    daysOfWeek: String = "",
    windowStart: String = "",
    windowEnd: String = "",
): String {
    val p = prompt
    val apps = appNames.filter { it.isNotBlank() && it != "모든" && it != "전부" }
    val appsLabel = apps.joinToString(", ")

        val base = when {
        mute && apps.isEmpty() -> "모든 알림 일시 보류"
        mute && apps.size == 1 -> "${apps[0]}만 받지 않음"
        mute && apps.isNotEmpty() -> "${appsLabel}만 받지 않음"
        !mute && apps.size == 1 -> "${apps[0]}만 허용"
        !mute && apps.isNotEmpty() -> "${appsLabel}만 허용"
        apps.size == 1 -> "${apps[0]} 알림"
        apps.isNotEmpty() -> "${appsLabel} 알림"
        else -> p.trim().take(28).ifBlank { "알림 규칙" }
    }

    if (!RecurrenceWindow.isRecurring(recurrence)) return base
    val whenLabel = RecurrenceWindow.recurrenceLabel(recurrence, daysOfWeek)
    val timeLabel = if (windowStart.isNotBlank() && windowEnd.isNotBlank()) {
        " $windowStart–$windowEnd"
    } else {
        ""
    }
    return listOf(whenLabel + timeLabel, base)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()
}
