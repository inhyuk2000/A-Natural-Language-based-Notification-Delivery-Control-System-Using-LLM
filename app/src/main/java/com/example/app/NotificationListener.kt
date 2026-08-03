package com.example.app

import android.Manifest
import androidx.core.app.ActivityCompat
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Resources
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.*
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat.stopForeground
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.app.NotificationListener.Companion.receivedNotificationApps
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.drawable.toBitmap

class NotificationListener : NotificationListenerService() {
    companion object {
        private const val KEY_FEATURE_ENABLED = "featureEnabled"
        val receivedNotificationApps = mutableSetOf<String>()
        val pendingNotifications = mutableMapOf<String, StatusBarNotification>() // 데이터베이스를 통한 통신을 위해 임시로 구현 -> sbn 자체를 데이터베이스에 저장할 수 없어서 임시 방편
        lateinit var deviceId: String
        var instance: NotificationListener? = null
            private set
    }
    // 각 알림의 데이터를 저장
    private val notificationChannelId = "NotiServiceChannel" // 알림 채널 ID
    private lateinit var notificationManager: NotificationManager // 알림 관리자
    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper()) // 핸들러 정의
    private var screenOnTime: Long = 0
    private var screenOffTime: Long = 0
    private lateinit var screenOnOffReceiver: BroadcastReceiver
    private var screenOnFlag = false

    private val highImportanceApps = mutableSetOf<String>() // 중요도 상 앱 목록
    private val mediumImportanceApps = mutableSetOf<String>() // 중요도 중 앱 목록
    private val lowImportanceApps = mutableSetOf<String>() // 중요도 하 앱 목록
    private val highImportanceTexts = mutableSetOf<String>()
    private val mediumImportanceTexts = mutableSetOf<String>()
    private val lowImportanceTexts = mutableSetOf<String>()
    private val notificationTitles = mutableMapOf<String, String>() // 패키지와 제목을 저장하는 맵
    private val pendingMediumNotifications = mutableMapOf<String, StatusBarNotification>() // 중요도 중 알림 대기 목록
    private val pendingLowNotifications = mutableMapOf<String, StatusBarNotification>() // 중요도 하 알림 대기 목록

    private lateinit var myChecker: MyChecker

    // contextManager 인스턴스 생성
    val contextManager = ContextManager(this)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        instance = this
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 다른 초기화가 실패해도 알림 홀드 시 크래시 나지 않도록 최우선 설정
        Companion.deviceId = generateUniqueDeviceId()

        // MyChecker 초기화 및 주기 체크 시작
        myChecker = MyChecker(applicationContext)
        myChecker.startPeriodicCheck()
        Log.d("DB_CHECK_PERIODIC", "Periodic Checker started.")

        try {
            Log.d("NotificationListener", "onCreate called")
            loadNotificationTitlesFromSharedPreferences()

            val importanceUpdateFilter = IntentFilter().apply {
                addAction("com.example.APP_IMPORTANCE_UPDATED")
                addAction("com.example.KEYWORDS_UPDATED") // 추가하여 키워드 업데이트를 감지
            }

            // LocalBroadcastManager를 사용하여 리시버 등록
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(appImportanceUpdateReceiver, importanceUpdateFilter)
            loadImportanceLists() // 중요도 리스트 초기화
            loadImportanceTexts() // 중요도별 텍스트 로드

            val lowImportanceFilter = IntentFilter("com.example.REQUEST_LOW_IMPORTANCE_NOTIFICATIONS")
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(lowImportanceReceiver, lowImportanceFilter)
            Log.d("NotificationListener", "LocalBroadcastManager for low importance notifications registered")

            val specificNotificationFilter = IntentFilter("com.example.REQUEST_SPECIFIC_APP_NOTIFICATIONS")
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(specificAppNotificationReceiver,specificNotificationFilter)

            registerScreenOnOffReceiver()
            createNotificationChannel() // 알림 채널 생성
            startForegroundService() // 포그라운드 서비스 시작
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager // 알림 서비스 접근
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        } catch (e: Exception) {
            Log.e("NotificationListener", "Error during onCreate: ${e.message}", e)
        }
    }

    private val appImportanceUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.APP_IMPORTANCE_UPDATED" -> {
                    Log.d("NotificationListener", "Received importance update broadcast for apps")
                    loadImportanceLists() // 앱 중요도 업데이트
                }
                "com.example.KEYWORDS_UPDATED" -> {
                    Log.d("NotificationListener", "Received keyword update broadcast")
                    loadImportanceTexts() // 키워드 중요도 업데이트
                }
            }
            Log.d("NotificationListener", "Importance lists and texts reloaded.")
        }
    }

    private val lowImportanceReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.REQUEST_LOW_IMPORTANCE_NOTIFICATIONS") {
                Log.d("NotificationListener_check", "Received local broadcast for low importance notifications")
                sendRequestedLowImportanceNotifications()  // 대기 중인 중요도 낮은 알림 전송
            }
        }
    }

    private val specificAppNotificationReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.REQUEST_SPECIFIC_APP_NOTIFICATIONS") {

                // 메타데이터 추출
                val selectionArgs = intent.getStringExtra("app_name_list")?.split(", ")
                val isFiltered = intent.getBooleanExtra("is_filtered", false)
                Log.d("NotificationListener_check", "selectionArgs: ${selectionArgs}, isFiltered: ${isFiltered}")

                // 받은 데이터로 알림 처리 로직 호출
                sendRequestedSpecificAppNotifications(selectionArgs, isFiltered)
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendRequestedSpecificAppNotifications(filteredApps: List<String>?, isFiltered: Boolean) {

        // 사용자가 요청한 앱 이름 (filteredApps)을 매핑된 키 값(packageName 기준)으로 변환
        val filteredPkgnameKeys = mutableSetOf<String>()
        if (isFiltered && filteredApps != null) {
            filteredApps.forEach { requestedName ->
                val pkg = AppNameMapper.toPackageName(requestedName)
                if (pkg != null) {
                    filteredPkgnameKeys.add(pkg) // ex) "카톡" -> "com.kakao.talk"
                    Log.d("NotificationListener_check", "Mapped user input '$requestedName' -> $pkg")
                }
            }
        }
        Log.d("NotificationListener_check", "filteredPkgnameKeys: ${filteredPkgnameKeys}")

        val iterator = pendingNotifications.entries.iterator()
        Log.d("NotificationListener_check", "Pending notifications exist: ${iterator.hasNext()}")

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val sbn = entry.value
            val packageName = sbn.packageName
            val extras = sbn.notification.extras
            val category = sbn.notification.category // 알림 앱의 종류 -> 카테고리
            var title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "No Text"
            val postTime = formatDate(sbn.postTime)

            Log.d("NotificationListener_check", "sbn_check: ${sbn.toString()}")

            if (title.isNullOrEmpty()) {
                title = try {
                    val packageManager = applicationContext.packageManager
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                    ).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e(
                        "NotificationListener_check",
                        "App name not found for package: $packageName",
                        e
                    )
                    packageName
                }
            }

            if (isFiltered && filteredPkgnameKeys.isNotEmpty()) { // 필터링 대상이 아니므로 다음 알림으로 넘어감
                if (!filteredPkgnameKeys.contains(packageName)) {
                    Log.d("NotificationListener_check", "Skipping notification: $text ($packageName)")
                    continue
                }
            } else if (filteredPkgnameKeys.isEmpty()) {
                continue
            }

            // 사용자의 자연어 명령과 맞는 알림들만 전송하고 알림 대기열에서 삭제
            Log.d(
                "NotificationListener_check",
                "Sending notification: Key='${sbn.key}', Title='$title', packageName='$packageName', category='$category', text='$text', postTime='$postTime'"
            )
            sendDelayedNotification(sbn)
            iterator.remove()
            // sendLowImportanceNotificationCountBroadcast(pendingNotifications.size) -> 실제 알림 전송과는 무관한 코드, 단순히 알림 개수 알려주는 코드
        }

        Log.d("NotificationListener_check", "Completed sending filtered notifications")
    }

    private fun loadImportanceLists() {
        val sharedPreferences = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val highSet = sharedPreferences.getStringSet("highImportanceApps", null)
        val mediumSet = sharedPreferences.getStringSet("mediumImportanceApps", null)
        val lowSet = sharedPreferences.getStringSet("lowImportanceApps", null)

        highImportanceApps.clear()
        mediumImportanceApps.clear()
        lowImportanceApps.clear()

        if (highSet != null) {
            highImportanceApps.addAll(highSet.map { it.split(":")[1] })
        }
        if (mediumSet != null) {
            mediumImportanceApps.addAll(mediumSet.map { it.split(":")[1] })
        }
        if (lowSet != null) {
            lowImportanceApps.addAll(lowSet.map { it.split(":")[1] })
        }

        Log.d("NotificationListener", "Loaded High Importance Apps: $highImportanceApps")
        Log.d("NotificationListener", "Loaded Medium Importance Apps: $mediumImportanceApps")
        Log.d("NotificationListener", "Loaded Low Importance Apps: $lowImportanceApps")
    }

    private fun registerScreenOnOffReceiver() {
        screenOnOffReceiver = object : BroadcastReceiver() {
            @RequiresApi(Build.VERSION_CODES.P)
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        screenOnTime = System.currentTimeMillis()
                        handleScreenOn()
                        Log.d("NotificationListener", "Screen ON")

                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOffTime = System.currentTimeMillis()
                        handleScreenOff()
                        Log.d("NotificationListener", "Screen OFF")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // Android 13+: RECEIVER_EXPORTED / NOT_EXPORTED 필수
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOnOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenOnOffReceiver, filter)
        }
        Log.d("NotificationListener", "ScreenOnOffReceiver registered")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channelName = "Notification Stats Service Channel"
        val importance = NotificationManager.IMPORTANCE_MIN
        val channel = NotificationChannel(notificationChannelId, channelName, importance)
        channel.description = "Collecting notification stats"

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel) // 채널 생성
        Log.d("NotificationListener", "Notification channel created")
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification: Notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Notification Stats Service")
            .setContentText("Collecting notification stats")
            .setSmallIcon(R.mipmap.ic_launcher) // Icon 찾기
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // 포그라운드 서비스로 서비스 시작
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
        Log.d("NotificationListener", "Foreground service started")
    }

    private fun generateUniqueDeviceId(): String { // 기기의 고유 ID 생성
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        var id = sharedPreferences.getString("deviceId", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            sharedPreferences.edit { putString("deviceId", id) }
        }
        return id
    }

    //타임 스탬프 문자열로 반환
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault() // 서버의 시간대 설정이 필요하면 이 부분을 조정
        return sdf.format(timestamp)
    }

    private fun anonymizeText(text: String): String {
        // 텍스트 길이가 10글자 미만인 경우 전체 텍스트 반환
        if (text.length < 13) {
            return text
        }
        // 앞 5글자, 뒤 5글자 유지, 나머지는 별표 처리
        val prefix = text.substring(0, 8)
        val suffix = text.substring(text.length - 5)
        val masked = "*".repeat(text.length - 13)
        return "$prefix$masked$suffix"
    }

    private fun loadImportanceTexts() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // Clear all existing keywords to prevent residual data
        highImportanceTexts.clear()
        mediumImportanceTexts.clear()
        lowImportanceTexts.clear()

        highImportanceTexts.addAll(sharedPreferences.getStringSet("highImportanceTexts", emptySet()) ?: emptySet())
        mediumImportanceTexts.addAll(sharedPreferences.getStringSet("mediumImportanceTexts", emptySet()) ?: emptySet())
        lowImportanceTexts.addAll(sharedPreferences.getStringSet("lowImportanceTexts", emptySet()) ?: emptySet())

        Log.d("NotificationListener", "Keywords reloaded - High: $highImportanceTexts, Medium: $mediumImportanceTexts, Low: $lowImportanceTexts")
    }

    private fun saveNotificationTitlesToSharedPreferences() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit {

            // Convert the notificationTitles map to a single string format for storage
            val titleMapString =
                notificationTitles.entries.joinToString(";") { "${it.key}:${it.value}" }
            putString("notificationTitles", titleMapString)
        }

        Log.d("NotificationListener", "Notification titles saved to SharedPreferences.")
    }

    private fun loadNotificationTitlesFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val titleMapString = sharedPreferences.getString("notificationTitles", "")

        // Convert back to map
        notificationTitles.clear()
        titleMapString?.split(";")?.forEach {
            val (packageName, title) = it.split(":")
            notificationTitles[packageName] = title
        }

        Log.d("NotificationListener", "Notification titles loaded from SharedPreferences.")
    }

    @SuppressLint("NewApi")
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "No Title"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "No Text"
        val packageName = sbn.packageName
        val postTime = formatDate(sbn.postTime)
        val key = sbn.key
        val notificationId = sbn.id

        // 자기 자신 앱의 알림은 무시
        if (packageName == this.packageName) {
            Log.d("NotificationListener", "Ignoring self notification: $packageName")
            return
        }

        val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (isGroupSummary) {
            Log.d("NotificationListener", "Group summary notification ignored: Key='$key', Package='$packageName'")
            return // 그룹 요약 알림은 저장하지 않음
        }

        // 알림이라면 Set에 추가
        notificationTitles[packageName] = title
        saveNotificationTitlesToSharedPreferences()
        if (receivedNotificationApps.add(packageName)) {
            saveReceivedNotificationAppsToSharedPreferences()
            Log.d("NotificationListener", "New app added and saved: $packageName")
        }

        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)
        Log.d("NotificationListener", "Notification posted: Title='$title', Text='$text', Package='$packageName', FeatureEnabled='$isFeatureEnabled'")
        Log.d("NotificationListener", sbn.toString())
        if (title == "No Title" && text == "No Text") {
            Log.d("NotificationListener", "Ignoring placeholder notification: Title='$title', Text='$text', Package='$packageName'")
            return
        }
        // 중요도 리스트 상태를 로그로 출력
        Log.d("NotificationListener", "Loaded High Importance Keywords: $highImportanceTexts")
        Log.d("NotificationListener", "Loaded Medium Importance Keywords: $mediumImportanceTexts")
        Log.d("NotificationListener", "Loaded Low Importance Keywords: $lowImportanceTexts")
        Log.d("NotificationListener", "Loaded High Importance Apps: $highImportanceApps")
        Log.d("NotificationListener", "Loaded Medium Importance Apps: $mediumImportanceApps")
        Log.d("NotificationListener", "Loaded Low Importance Apps: $lowImportanceApps")

        Log.d("TEP_DEBUG", "high=$highImportanceApps, medium=$mediumImportanceApps, low=$lowImportanceApps, current=$packageName")

        if (isFeatureEnabled && (notificationId != 0 || sbn.tag != null)) {
            // ===== 레거시: 즉시/사용중/요청시(중요도 상·중·하) 분기 — 주석 처리 =====
            // when {
            //     highImportanceTexts.any { title.contains(it, ignoreCase = true) } -> { ... return }
            //     mediumImportanceTexts... pendingMediumNotifications ...
            //     lowImportanceTexts... pendingLowNotifications ...
            // }
            // when { /* text keyword 동일 */ }
            // when { /* package high/medium/low */ }

            // 핵심: ContextManager 규칙 기반으로만 처리
            Log.d("NotificationListener", "ContextManager path for: $packageName")
            val isOngoing = sbn.isOngoing
            val isForeGroundService = (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
            val isMediaPlayback = notification.category == Notification.CATEGORY_TRANSPORT || notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
            val isSystemUiMediaOngoing = packageName == "com.android.systemui" && title == "MediaOngoingActivity"

            if (isForeGroundService || isOngoing || isMediaPlayback || isSystemUiMediaOngoing) {
                Log.d("NotificationListener", "to skip")
                super.onNotificationPosted(sbn)
            } else {
                cancelNotification(key)
                pendingNotifications[key] = sbn
                Log.d("NOTIF_TEST_PENDING", pendingNotifications.toString())
                contextManager.handleIncomingNotif(sbn)
            }
        } else {
            // 기능 꺼져 있는 경우
            Log.d("NotificationListener", "Feature disabled or invalid notification ID. Default behavior for notification.")
            Log.d("TEP_DEBUG", "기능 꺼져 있음.")
             super.onNotificationPosted(sbn)

        }
    }

    private fun saveReceivedNotificationAppsToSharedPreferences() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit {
            putStringSet("receivedNotificationApps", receivedNotificationApps)
        } // 즉시 저장
        Log.d("NotificationListener", "Received notification apps saved to SharedPreferences.")

        // 브로드캐스트 전송
        val intent = Intent("com.example.RECEIVED_NOTIFICATION_APPS_UPDATED")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d("NotificationListener", "Broadcast sent for receivedNotificationApps update.")
    }

    // 알림이 제거될때 호출
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        try {
            val packageName = sbn.packageName
            val notificationId = sbn.id
            val key = sbn.key
            val removalReason = parseRemovalReason(reason) // 제거 이유
            val removalTime = formatDate(System.currentTimeMillis())
            val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE) ?: "No Title"
            val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "No Text"

            Log.d("NotificationListener", "Notification removed: ID=$notificationId, Title='$title', Text='$text', Package='$packageName', Reason='$removalReason', Removal Time='$removalTime'")

        } catch (e: DeadObjectException) {
            Log.e("NotificationListener", "DeadObjectException: ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error during onNotificationRemoved: ${e.message}")
        }
    }

    // 제거 이유를 문자열로 변환
    private fun parseRemovalReason(reason: Int): String {
        return when (reason) {
            REASON_APP_CANCEL -> "App Specific Cancel"
            REASON_APP_CANCEL_ALL -> "App Cancel All Notifications"
            REASON_ASSISTANT_CANCEL -> "Assistant Cancel"
            REASON_CANCEL -> "Notification Swiped"
            REASON_CANCEL_ALL -> "All Notifications Cleared"
            REASON_CHANNEL_BANNED -> "Channel Banned"
            REASON_CHANNEL_REMOVED -> "Channel Removed"
            REASON_CLEAR_DATA -> "Data Cleared"
            REASON_CLICK -> "Notification Clicked"
            REASON_ERROR -> "Error"
            REASON_GROUP_OPTIMIZATION -> "Group Optimization"
            REASON_GROUP_SUMMARY_CANCELED -> "Group Summary Canceled"
            REASON_LISTENER_CANCEL -> "Listener Cancel"
            REASON_LISTENER_CANCEL_ALL -> "Listener Cancel All"
            // REASON_LOCKDOWN -> "Lockdown"
            REASON_PACKAGE_BANNED -> "Package Banned"
            REASON_PACKAGE_CHANGED -> "Package Changed"
            REASON_PACKAGE_SUSPENDED -> "Package Suspended"
            REASON_PROFILE_TURNED_OFF -> "Profile Turned Off"
            REASON_SNOOZED -> "Snoozed"
            REASON_TIMEOUT -> "Timeout"
            REASON_UNAUTOBUNDLED -> "Unautobundled"
            REASON_USER_STOPPED -> "User Stopped"
            else -> "Other"
        }
    }

    // Icon 객체 -> Bitmap 변환
    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 100
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 100

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    /** 앱별 재전송 그룹에 올라간 자식 알림 수 (요약 문구용) */
    private val delayedGroupCounts = mutableMapOf<String, Int>()

    private fun delayedGroupKey(packageName: String) = "agentnotif_delayed_$packageName"

    private fun delayedSummaryId(packageName: String) = ("agentnotif_summary_$packageName").hashCode()

    private fun ensureDelayedChannel() {
        if (!::notificationManager.isInitialized) {
            notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "delayed_channel_id",
                "Delayed Notifications",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    /**
     * 같은 앱 재전송 알림을 하나의 그룹으로 묶는 요약 알림.
     * 자식 notify 직후 호출하면 상태바에 앱 단위로 접혀 보임.
     */
    private fun notifyAppGroupSummary(
        packageName: String,
        smallBitmap: Bitmap,
        largeBitmap: Bitmap?,
        color: Int,
        contentIntent: PendingIntent?,
    ) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val groupKey = delayedGroupKey(packageName)
        val count = delayedGroupCounts[packageName] ?: 1
        val appName = getAppNameFromPackage(packageName)
        val summaryText = if (count <= 1) "알림 1건" else "알림 ${count}건"

        val summary = NotificationCompat.Builder(this, "delayed_channel_id")
            .setContentTitle(appName)
            .setContentText(summaryText)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText(summaryText)
                    .setBigContentTitle(appName)
            )
            .setSmallIcon(IconCompat.createWithBitmap(smallBitmap))
            .setShowWhen(true)
            .setColor(color)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .addExtras(Bundle().apply {
                putBoolean("isDelayedNotification", true)
                putBoolean("isDelayedGroupSummary", true)
            })
            .apply {
                if (largeBitmap != null) setLargeIcon(largeBitmap)
            }
            .build()

        NotificationManagerCompat.from(this).notify(delayedSummaryId(packageName), summary)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun sendDelayedNotification(sbn: StatusBarNotification) {
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)
            ?: getAppNameFromPackage(sbn.packageName)

        Log.d("sbn_data", sbn.toString())

        val uniqueNotificationId = UUID.randomUUID().hashCode()
        ensureDelayedChannel()

        val originalNotification = sbn.notification
        val extras = originalNotification.extras
        val packageName = sbn.packageName
        val groupKey = delayedGroupKey(packageName)

        val smallBitmap = runCatching {
            originalNotification.smallIcon?.loadDrawable(this)?.let { drawableToBitmap(it) }
        }.getOrNull() ?: runCatching {
            packageManager.getApplicationIcon(packageName).let { drawableToBitmap(it) }
        }.getOrNull()

        val largeBitmap = runCatching {
            originalNotification.getLargeIcon()?.loadDrawable(this)?.let { drawableToBitmap(it) }
        }.getOrNull() ?: smallBitmap

        if (smallBitmap == null) {
            Log.e("NOTI", "아이콘을 만들 수 없어 알림 재전송 스킵: $packageName")
            return
        }

        val color = sbn.notification.color
        val contentIntent = originalNotification.contentIntent

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: "No details available"

        val builder = NotificationCompat.Builder(this, "delayed_channel_id")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(IconCompat.createWithBitmap(smallBitmap))
            .setShowWhen(true)
            .setWhen(sbn.postTime)
            .setColor(color)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .addExtras(Bundle().apply { putBoolean("isDelayedNotification", true) })

        if (largeBitmap != null) {
            builder.setLargeIcon(largeBitmap)
        }

        val notificationManagerCompat = NotificationManagerCompat.from(this)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("NOTI", "No POST_NOTIFICATIONS permission")
            return
        }

        delayedGroupCounts[packageName] = (delayedGroupCounts[packageName] ?: 0) + 1
        notificationManagerCompat.notify(uniqueNotificationId, builder.build())
        notifyAppGroupSummary(packageName, smallBitmap, largeBitmap, color, contentIntent)
        Log.d("NOTI", "재전송 완료(그룹=$groupKey): pkg=$packageName title=$title")
    }

    /** pending 없이 DB에만 남은 홀드 알림을 최소 정보로 재표시 */
    fun sendSimpleStoredNotification(packageName: String, title: String, text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        ensureDelayedChannel()

        val appName = getAppNameFromPackage(packageName)
        val groupKey = delayedGroupKey(packageName)
        val smallBitmap = runCatching {
            packageManager.getApplicationIcon(packageName).let { drawableToBitmap(it) }
        }.getOrNull()
        if (smallBitmap == null) {
            Log.e("NOTI", "simple 재전송 아이콘 실패: $packageName")
            return
        }

        val builder = NotificationCompat.Builder(this, "delayed_channel_id")
            .setContentTitle(title.ifBlank { appName })
            .setContentText(text.ifBlank { "저장된 알림" })
            .setSmallIcon(IconCompat.createWithBitmap(smallBitmap))
            .setLargeIcon(smallBitmap)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .addExtras(Bundle().apply { putBoolean("isDelayedNotification", true) })

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("NOTI", "No POST_NOTIFICATIONS permission")
            return
        }

        delayedGroupCounts[packageName] = (delayedGroupCounts[packageName] ?: 0) + 1
        NotificationManagerCompat.from(this).notify(UUID.randomUUID().hashCode(), builder.build())
        notifyAppGroupSummary(packageName, smallBitmap, smallBitmap, 0, null)
        Log.d("NOTI", "DB 폴백 재전송(그룹=$groupKey): pkg=$packageName title=$title")
    }


    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val packageManager = this.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // Fallback to package name if app name cannot be retrieved
        }
    }

    private fun handleScreenOff() {
        screenOnFlag = false
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun handleScreenOn() {
        screenOnFlag = true
        // Send notifications from medium importance apps when the screen turns on
        pendingMediumNotifications.values.forEach { sbn ->
            val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)
            if (!title.isNullOrEmpty()) {
                sendDelayedNotification(sbn)
            }
        }
        pendingMediumNotifications.clear() // Clear the waiting list even if some titles are empty
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendRequestedLowImportanceNotifications() {

        val count = pendingLowNotifications.size
        sendLowImportanceNotificationCountBroadcast(count)

        Log.d("NotificationListener_check", "Sending low importance notifications")
        pendingLowNotifications.values.forEach { sbn ->
            Log.d("Notif_check", sbn.toString())
            val key = sbn.key
            var title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)

            if (title.isNullOrEmpty()) {
                // Retrieve the app name using the package manager
                val packageName = sbn.packageName
                val appName = try {
                    val packageManager = applicationContext.packageManager
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                    ).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.e("NotificationListener_check", "App name not found for package: $packageName", e)
                    packageName // Fallback to package name if app name is not found
                }
                title = appName // Set the app name as the title
            }

            Log.d("NotificationListener_check", "Sending notification: Key='$key', Title='$title', packageName='$packageName', category='${sbn.notification.category}', sbn='$sbn'")
            sendDelayedNotification(sbn) // Send notification with the ensured title
        }
        pendingLowNotifications.clear()
        sendLowImportanceNotificationCountBroadcast(0)
    }

    private fun sendLowImportanceNotificationCountBroadcast(count: Int) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit { putInt("lowImportanceCount", count) }

        val intent = Intent("UPDATE_LOW_IMPORTANCE_NOTIFICATION_COUNT")
        intent.putExtra("count", count)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun unregisterScreenOnOffReceiver() {
        try {
            if (::screenOnOffReceiver.isInitialized) {
                unregisterReceiver(screenOnOffReceiver)
                Log.d("NotificationListener", "ScreenOnOffReceiver unregistered")
            }
        } catch (e: IllegalArgumentException) {
            Log.e("NotificationListener", "ScreenOnOffReceiver not registered: ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error during unregisterScreenOnOffReceiver: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Log.d("NotificationListener", "Service destroyed")

            // 핸들러 콜백 및 메시지 제거
            handler.removeCallbacksAndMessages(null)

            // 포그라운드 서비스 종료
            stopForeground(Service.STOP_FOREGROUND_REMOVE)

            // LocalBroadcastManager 리시버 해제
            LocalBroadcastManager.getInstance(this).unregisterReceiver(appImportanceUpdateReceiver)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(lowImportanceReceiver)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(specificAppNotificationReceiver)

            // ScreenOnOffReceiver 해제
            unregisterScreenOnOffReceiver()

        } catch (e: IllegalArgumentException) {
            Log.e("NotificationListener", "Receiver not registered: ${e.message}")
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error during onDestroy: ${e.message}")
        }

        // 주기 체크 종료
        myChecker.stopPeriodicCheck()
        Log.d("DB_CHECK_PERIODIC", "Periodic Checker ended.")
    }
}

