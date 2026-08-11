package com.example.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.app.NotificationListener.Companion.receivedNotificationApps
import com.example.app.presentation.navigation.AppRoutes
import com.example.app.presentation.navigation.navigateChat
import com.example.app.presentation.navigation.navigateTab
import com.example.app.presentation.theme.AgentnotifTheme
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.speech.RecognizerIntent
import com.example.app.ContextManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity() {
    // 앱에서 사용하는 각종 권환 및 서비스에 대한 요청 코드와 알림 채널 ID 정의
    companion object {
        const val PREFS_NAME = "AppPrefs"
        const val KEY_SERVICE_ON_TIME = "serviceOnTime"
        const val KEY_SERVICE_OFF_TIME = "serviceOffTime"
        private const val KEY_LAST_TIMESTAMP = "lastTimestamp"
        private const val KEY_FEATURE_ENABLED = "featureEnabled"

        const val EXTRA_NAV_ROUTE = "extra_nav_route"

    }
    //    private lateinit var realtimeDatabase: FirebaseDatabase
    private lateinit var deviceId: String
    private lateinit var handler: Handler

    // 토글 버튼 정의(서비스 시작/중지)
    private var isFeatureEnabled: Boolean = false

    // 앱 추가/삭제를 위한 RecyclerView 어댑터 정의 (중요도 UI — 현재 홈에서 숨김, 어댑터 초기화만 유지)
    private lateinit var highImportanceAdapter: AppAdapter
    private lateinit var mediumImportanceAdapter: AppAdapter
    private lateinit var lowImportanceAdapter: AppAdapter
    private val highImportanceTexts = mutableSetOf<String>()
    private val mediumImportanceTexts = mutableSetOf<String>()
    private val lowImportanceTexts = mutableSetOf<String>()
    private val addedApps = mutableSetOf<String>()
    private var receivedNotificationApps = mutableSetOf<String>() // 기존 알림을 받은 앱 저장

    // Compose 홈 상태
    private var homeRules by mutableStateOf<List<ContextManagerEntry>>(emptyList())
    private var homeDashboard by mutableStateOf(
        HomeDashboardData(
            filteredToday = 0,
            activeRuleCount = 0,
            totalRuleCount = 0,
            appProgress = emptyList(),
            recentLogs = emptyList(),
            weeklySummary = WeeklySummaryStats(),
            weeklyTimeSlots = emptyList(),
        )
    )
    private var serviceOn by mutableStateOf(false)
    private var homeReady by mutableStateOf(false)
    private var rulesRefreshKey by mutableStateOf(0)
    private var pendingRoute by mutableStateOf<String?>(null)

    fun navigateInternally(route: String) {
        pendingRoute = route
    }

    private val receivedNotificationAppsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.RECEIVED_NOTIFICATION_APPS_UPDATED") {
                loadReceivedNotificationAppsFromSharedPreferences()
                Log.d("MainActivity", "Received notification apps updated and reloaded.")

                // Get the latest low importance count from SharedPreferences
                val lowImportanceCount = getCurrentLowImportanceNotificationCount()
                // Update the button with the latest count
                updateLowImportanceNotificationCount(lowImportanceCount)
            }
        }
    }

    // 데이터 전송 상태를 받는 BroadcastReceiver를 정의
    private val transferDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val isSuccess = intent.getBooleanExtra("TransferStatus", false)
            if (isSuccess) {
                Toast.makeText(context, "Data Transfer Successful", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Data Transfer Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val lowImportanceCountReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("NotificationListener", "BroadcastReceiver triggered")
            if (intent?.action == "UPDATE_LOW_IMPORTANCE_NOTIFICATION_COUNT") {
                val lowImportanceCount = intent.getIntExtra("count", 0)
                Log.d("NotificationListener", "Received low importance notification count: $lowImportanceCount")
                updateLowImportanceNotificationCount(lowImportanceCount)
            }
        }
    }

    private val keywordUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loadKeywordsFromSharedPreferences()
        }
    }

    private val homeMonitorRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!homeReady) return
            refreshMonitorLists()
            rulesRefreshKey++
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateLowImportanceNotificationCount(count: Int) {
        // Compose 홈에서는 미사용 (레거시 버튼 UI 제거)
        Log.d("MainActivity", "Updating count in UI: $count")
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "InlinedApi", "MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!UserRepository.hasUser(this)) {
            startActivity(
                Intent(this, OnboardingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
            return
        }

                setContent {
            AgentnotifTheme(dynamicColor = false, darkTheme = false) {
                val navController = androidx.navigation.compose.rememberNavController()
                var userName by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(UserRepository.getNickname(this@MainActivity))
                }
                var avatarPath by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(AuthSession.avatarPath(this@MainActivity))
                }

                androidx.compose.runtime.LaunchedEffect(pendingRoute, intent) {
                    val route = pendingRoute
                        ?: intent?.getStringExtra(EXTRA_NAV_ROUTE)
                    if (!route.isNullOrBlank()) {
                        pendingRoute = null
                        intent?.removeExtra(EXTRA_NAV_ROUTE)
                        when (route) {
                            AppRoutes.CHAT -> navController.navigateChat()
                            AppRoutes.RULES, AppRoutes.ADD_RULE, AppRoutes.PROFILE, AppRoutes.HOME ->
                                navController.navigateTab(route)
                        }
                    }
                }

                com.example.app.presentation.navigation.AppShell(
                    navController = navController,
                    userName = userName,
                    avatarPath = avatarPath,
                    serviceOn = serviceOn,
                    dashboard = homeDashboard,
                    homeRules = homeRules,
                    rulesRefreshKey = rulesRefreshKey,
                    onToggleService = { toggleFeature() },
                    onRefreshHome = {
                        refreshMonitorLists()
                        rulesRefreshKey++
                    },
                    onRulesChanged = { rulesRefreshKey++ },
                    onProfileUpdated = { name, path ->
                        userName = name
                        avatarPath = path
                    },
                )
            }
        }

        // 기기에 설치된 앱 정보를 불러오기
        AppNameMapper.loadInstalledApps(this)

        // 불러온 데이터 로그 확인
        AppNameMapper.logCurrentMapping()

        // realtimeDatabase = FirebaseDatabase.getInstance()

        // 홈 모니터링을 위해 시작 시 DB 초기화는 하지 않음
        // val contextManager = ContextManager(this)
        // contextManager.resetDatabases(this)
        // Log.d("DB_INIT", "앱 시작 시 DB 초기화 완료")

        startService(Intent(this, DummyService::class.java))

        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        deviceId = sharedPreferences.getString("deviceId", "UnknownDeviceId") ?: "UnknownDeviceId"

        // 위치, 활동 인식, 알림, 배터리 최적화 권한을 요청하는 메소드 호출
        requestNotificationPermission()
        requestBatteryOptimizationPermission()
        checkNotificationListenerPermission()
        // 중요도 어댑터 초기화만 유지 (UI는 gone)
        setupRecyclerViews()
        loadAppListsFromSharedPreferences()
        loadReceivedNotificationAppsFromSharedPreferences()
        loadKeywordsFromSharedPreferences()

        // ===== 레거시: 즉시/사용중/요청시 UI 및 별도 Activity 진입 (주석 처리) =====
        // findViewById<ImageButton>(R.id.openKeywordButton).setOnClickListener {
        //     startActivity(Intent(this, KeywordActivity::class.java))
        // }
        // findViewById<Button>(R.id.prompt_button).setOnClickListener {
        //     startActivity(Intent(this, PromptActivity::class.java))
        // }
        // findViewById<Button>(R.id.db_view_button).setOnClickListener {
        //     startActivity(Intent(this, DbViewActivity::class.java))
        // }
        // val requestLowImportanceButton: Button = findViewById(R.id.request_low_importance_button)
        // requestLowImportanceButton.setOnClickListener {
        //     LocalBroadcastManager.getInstance(this)
        //         .sendBroadcast(Intent("com.example.REQUEST_LOW_IMPORTANCE_NOTIFICATIONS"))
        // }
        // val highImportanceText: TextView = findViewById(R.id.highImportanceText)
        // val mediumImportanceText: TextView = findViewById(R.id.mediumImportanceText)
        // val lowImportanceText: TextView = findViewById(R.id.lowImportanceText)
        // val recyclerViewHigh: RecyclerView = findViewById(R.id.recyclerView_high)
        // val recyclerViewMedium: RecyclerView = findViewById(R.id.recyclerView_medium)
        // val recyclerViewLow: RecyclerView = findViewById(R.id.recyclerView_low)
        // highImportanceText.setOnClickListener { showRecyclerView(recyclerViewHigh, highImportanceText, mediumImportanceText, lowImportanceText) }
        // mediumImportanceText.setOnClickListener { showRecyclerView(recyclerViewMedium, highImportanceText, mediumImportanceText, lowImportanceText) }
        // lowImportanceText.setOnClickListener { showRecyclerView(recyclerViewLow, highImportanceText, mediumImportanceText, lowImportanceText) }
        // showRecyclerView(recyclerViewHigh, highImportanceText, mediumImportanceText, lowImportanceText)
        // updateLowImportanceNotificationCount(0)

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receivedNotificationAppsUpdateReceiver, IntentFilter("com.example.RECEIVED_NOTIFICATION_APPS_UPDATED"))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(keywordUpdateReceiver, IntentFilter("com.example.KEYWORDS_UPDATED"))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                homeMonitorRefreshReceiver,
                IntentFilter(ContextManager.ACTION_HOME_MONITOR_REFRESH),
            )
        // LocalBroadcastManager.getInstance(this).registerReceiver(
        //     lowImportanceCountReceiver, IntentFilter("UPDATE_LOW_IMPORTANCE_NOTIFICATION_COUNT")
        // )

        startForegroundService(Intent(this, UsageStatsService::class.java))

        if (!hasUsageStatsPermission(this)) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else {
            startService(Intent(this, UsageStatsService::class.java))
        }

        // val importanceUpdateIntent = Intent("com.example.APP_IMPORTANCE_UPDATED")
        // LocalBroadcastManager.getInstance(this).sendBroadcast(importanceUpdateIntent)

        val transferDataFilter = IntentFilter("com.example.app.TRANSFER_DATA")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerReceiver(
                transferDataReceiver,
                transferDataFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(transferDataReceiver, transferDataFilter)
        }
        handler = Handler(Looper.getMainLooper())

        isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)
        updateButtonColor(isFeatureEnabled)

        // 홈: 모니터링 + 프롬프트 통합
        setupHomeMonitorAndPrompt()
        startUsageStatsService()
    }

    /** Figma 홈: 실시간 통계 + 규칙/최근 알림 */
    private fun setupHomeMonitorAndPrompt() {
        homeReady = true
        refreshMonitorLists()
    }

    private fun refreshMonitorLists() {
        lifecycleScope.launch {
            val rules = withContext(Dispatchers.IO) { loadContextManagerData(this@MainActivity) }
            val dashboard = withContext(Dispatchers.IO) { loadHomeDashboard(this@MainActivity) }
            // 완료(만료) 규칙은 홈에서 숨김 — 규칙 목록 > 완료에서만 표시
            homeRules = rules.filter { !it.isCompleted }.takeLast(8).asReversed()
            homeDashboard = dashboard
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        intent.getStringExtra(EXTRA_NAV_ROUTE)?.let { route ->
            pendingRoute = route
        }
    }

    private fun showRecyclerView(
        selectedRecyclerView: RecyclerView,
        highImportanceText: TextView,
        mediumImportanceText: TextView,
        lowImportanceText: TextView
    ) {
        // 각 RecyclerView의 가시성을 설정
        findViewById<RecyclerView>(R.id.recyclerView_high).visibility = if (selectedRecyclerView == findViewById(R.id.recyclerView_high)) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.recyclerView_medium).visibility = if (selectedRecyclerView == findViewById(R.id.recyclerView_medium)) View.VISIBLE else View.GONE
        findViewById<RecyclerView>(R.id.recyclerView_low).visibility = if (selectedRecyclerView == findViewById(R.id.recyclerView_low)) View.VISIBLE else View.GONE

        // 모든 탭의 선택 상태를 초기화
        highImportanceText.isSelected = false
        mediumImportanceText.isSelected = false
        lowImportanceText.isSelected = false

        // 선택된 탭의 상태를 활성화
        when (selectedRecyclerView) {
            findViewById<RecyclerView>(R.id.recyclerView_high) -> highImportanceText.isSelected = true
            findViewById<RecyclerView>(R.id.recyclerView_medium) -> mediumImportanceText.isSelected = true
            findViewById<RecyclerView>(R.id.recyclerView_low) -> lowImportanceText.isSelected = true
        }
    }

    private fun updateButtonColor(isFeatureEnabled: Boolean) {
        this.isFeatureEnabled = isFeatureEnabled
        serviceOn = isFeatureEnabled
    }

    // 기능 토글 버튼의 상태를 업데이트
    private fun toggleFeature() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val currentTimestamp = System.currentTimeMillis()
        val lastTimestamp = sharedPreferences.getLong(KEY_LAST_TIMESTAMP, currentTimestamp)
        val elapsedTime = currentTimestamp - lastTimestamp

        val isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)

        if (isFeatureEnabled) {
            val currentOnTime = sharedPreferences.getLong(KEY_SERVICE_ON_TIME, 0)
            editor.putLong(KEY_SERVICE_ON_TIME, currentOnTime + elapsedTime)
            Toast.makeText(this, "기능이 비활성화되었습니다.", Toast.LENGTH_SHORT).show()

            // 기능 비활성화 시 storedNotifications를 비우지 않음
        } else {
            val currentOffTime = sharedPreferences.getLong(KEY_SERVICE_OFF_TIME, 0)
            editor.putLong(KEY_SERVICE_OFF_TIME, currentOffTime + elapsedTime)
            Toast.makeText(this, "기능이 활성화되었습니다.", Toast.LENGTH_SHORT).show()
        }

        editor.putBoolean(KEY_FEATURE_ENABLED, !isFeatureEnabled)
        editor.putLong(KEY_LAST_TIMESTAMP, currentTimestamp)

        if (editor.commit()) {
            updateButtonColor(!isFeatureEnabled)
        } else {
            Toast.makeText(this, "설정 저장 실패", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkNotificationListenerPermission() {
        // 알림 접근 권한이 부여되었는지 확인, 부여되지 않았다면 사용자에게 설정 변경을 요청
        if (!permissionGranted()) {
            AlertDialog.Builder(this)
                .setTitle("알림 서비스 허용")
                .setMessage("앱의 기능을 완전히 사용하기 위해서는 알림 허용이 필요합니다.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun permissionGranted(): Boolean {
        // 알림 접근 권한이 부여되었는지 확인
        return NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationPermission() {
        // 배터리 최적화 무시 권한 요청
        val packageName = packageName
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent()
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestNotificationPermission() {
        // 알림 허용 여부 확인
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("알림 권한 허용")
                .setMessage("앱의 기능을 완전히 사용하기 위해서는 알림 허용이 필요합니다.")
                .setPositiveButton("설정으로 이동") { _, _ ->
                    // 사용자가 설정으로 이동하길 원할 경우, 앱의 알림 설정 화면으로 이동
                    val intent = Intent().apply {
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            putExtra(Settings.EXTRA_CHANNEL_ID, applicationInfo.uid)
                        }
                    }
                    startActivity(intent)
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    // 앱이 다시 활성화될 때 호출
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onResume() {
        super.onResume()
        startUsageStatsService()
        loadAppListsFromSharedPreferences()
        loadReceivedNotificationAppsFromSharedPreferences()
        // val lowImportanceCount = getCurrentLowImportanceNotificationCount()
        // updateLowImportanceNotificationCount(lowImportanceCount)
        if (homeReady) {
            refreshMonitorLists()
        }
    }

    private fun getCurrentLowImportanceNotificationCount(): Int {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("lowImportanceCount", 0)
    }

    private fun checkServiceEnabled(): Boolean {
        // 서비스 활성화 여부를 SharedPreferences에서 가져옴
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)
    }

    override fun onPause() {
        super.onPause()
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isFeatureEnabled = sharedPreferences.getBoolean(KEY_FEATURE_ENABLED, false)
        saveAppListsToSharedPreferences()
        saveReceivedNotificationAppsToSharedPreferences()
    }

    override fun onStop() {
        super.onStop()
        saveAppListsToSharedPreferences()
        saveReceivedNotificationAppsToSharedPreferences()// Also save when the app is stopped
    }

    private fun startUsageStatsService() {
        val serviceIntent = Intent(this, UsageStatsService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    // 사용 통계 권한이 있는지 확인
//    @RequiresApi(Build.VERSION_CODES.Q)
    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = applicationContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
//        appOps?.let {
//            // API 레벨 29 이상에서는 unsafeCheckOpNoThrow를 사용
//            val mode = it.unsafeCheckOpNoThrow(
//                AppOpsManager.OPSTR_GET_USAGE_STATS,
//                android.os.Process.myUid(),
//                packageName
//            )
//            return mode == AppOpsManager.MODE_ALLOWED
//        }

        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // ✅ Android 10(API 29) 이상
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            // ✅ Android 9(API 28) 이하
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
//        return false
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // 중요도별 앱 목록 저장 및 방송 전송
    private fun saveAppListsToSharedPreferences() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        val highImportanceApps = highImportanceAdapter.getAppList().map { "${it.first}:${it.second}" }.toSet()
        val mediumImportanceApps = mediumImportanceAdapter.getAppList().map { "${it.first}:${it.second}" }.toSet()
        val lowImportanceApps = lowImportanceAdapter.getAppList().map { "${it.first}:${it.second}" }.toSet()
        Log.d("MainActivity", "Saving highImportanceApps: $highImportanceApps")
        Log.d("MainActivity", "Saving mediumImportanceApps: $mediumImportanceApps")
        Log.d("MainActivity", "Saving lowImportanceApps: $lowImportanceApps")
        Log.d("MainActivity", "Saving receivedNotificationApps: $receivedNotificationApps")
        editor.putStringSet("highImportanceApps", highImportanceApps)
        editor.putStringSet("mediumImportanceApps", mediumImportanceApps)
        editor.putStringSet("lowImportanceApps", lowImportanceApps)

        if (editor.commit()) {
            Log.d("MainActivity", "App lists and receivedNotificationApps saved successfully.")
            val intent = Intent("com.example.APP_IMPORTANCE_UPDATED")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } else{
            Log.e("MainActivity", "Failed to save app lists and receivedNotificationApps.")
        }
    }

    private fun loadAppListsFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val highImportanceApps = sharedPreferences.getStringSet("highImportanceApps", emptySet())?.map {
            val parts = it.split(":")
            parts[0] to parts[1]  // Convert back to Pair(appName, packageName)
        }?.toMutableList() ?: mutableListOf()

        val mediumImportanceApps = sharedPreferences.getStringSet("mediumImportanceApps", emptySet())?.map {
            val parts = it.split(":")
            parts[0] to parts[1]
        }?.toMutableList() ?: mutableListOf()

        val lowImportanceApps = sharedPreferences.getStringSet("lowImportanceApps", emptySet())?.map {
            val parts = it.split(":")
            parts[0] to parts[1]
        }?.toMutableList() ?: mutableListOf()

        Log.d("MainActivity", "Loaded highImportanceApps: $highImportanceApps")
        Log.d("MainActivity", "Loaded mediumImportanceApps: $mediumImportanceApps")
        Log.d("MainActivity", "Loaded lowImportanceApps: $lowImportanceApps")

        highImportanceAdapter.updateAppList(highImportanceApps)
        mediumImportanceAdapter.updateAppList(mediumImportanceApps)
        lowImportanceAdapter.updateAppList(lowImportanceApps)
    }


    private fun setupRecyclerViews() {
        // Compose 홈으로 전환: 중요도 RecyclerView UI는 없애고, SharedPreferences 연동용 어댑터만 유지
        Log.d("MainActivity", "Setting up importance adapters (headless)")
        highImportanceAdapter = AppAdapter(mutableListOf(), this, {}, { _, _ -> })
        mediumImportanceAdapter = AppAdapter(mutableListOf(), this, {}, { _, _ -> })
        lowImportanceAdapter = AppAdapter(mutableListOf(), this, {}, { _, _ -> })
    }

    private fun getImportanceLevelByRecyclerViewId(recyclerViewId: Int): String {
        return when (recyclerViewId) {
            R.id.recyclerView_high -> "high"
            R.id.recyclerView_medium -> "medium"
            R.id.recyclerView_low -> "low"
            else -> "unknown"
        }
    }

    private fun setupRecyclerView(
        recyclerViewId: Int,
        appList: MutableList<Pair<String, String>>,
        onAppDeleted: (String) -> Unit
    ) {
        val recyclerView = findViewById<RecyclerView>(recyclerViewId)
        recyclerView.layoutManager = LinearLayoutManager(this)

        lateinit var adapter: AppAdapter
        adapter = AppAdapter(appList, this, onAppDeleted) { appName, packageName ->
            showAppPickerDialog { selectedName, selectedPackage ->
                if (selectedPackage.isEmpty()) {
                    if (isKeywordInAnotherImportanceLevel(selectedName)) {
                        Toast.makeText(this, "키워드가 이미 다른 레벨에 추가되었습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        removeKeywordFromAllLevels(selectedName) // Ensure previous level removal
                        adapter.addApp(selectedName to "")
                        saveKeywordToLevel(selectedName, recyclerViewId)
                        saveKeywordsToSharedPreferences()
                    }
                } else {
                    if (isAppInAnotherImportanceLevel(selectedPackage)) {
                        Toast.makeText(this, "앱이 이미 다른 레벨에 추가되었습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        adapter.addApp(selectedName to selectedPackage)
                        addedApps.add(selectedPackage)
                        receivedNotificationApps.remove(selectedPackage)
                        saveAppListsToSharedPreferences()
                        saveReceivedNotificationAppsToSharedPreferences()
                    }
                }
            }
        }

        recyclerView.adapter = adapter
        when (recyclerViewId) {
            R.id.recyclerView_high -> highImportanceAdapter = adapter
            R.id.recyclerView_medium -> mediumImportanceAdapter = adapter
            R.id.recyclerView_low -> lowImportanceAdapter = adapter
        }

        // 앱 삭제 시 처리
        adapter.onAppDeleted = { packageName ->
            Log.d("MainActivity", "onAppDeleted callback triggered with package: $packageName")

            // packageName이 키워드 리스트에 있는지 확인
            if (highImportanceTexts.contains(packageName) || mediumImportanceTexts.contains(packageName) || lowImportanceTexts.contains(packageName)) {
                Log.d("MainActivity", "Removing keyword from specific level: $packageName")
                removeKeywordFromLevel(packageName, recyclerViewId)
                saveKeywordsToSharedPreferences()
            } else {
                // 일반 앱 삭제 처리
                addedApps.remove(packageName)
                receivedNotificationApps.add(packageName)
                saveReceivedNotificationAppsToSharedPreferences()
            }
            saveAppListsToSharedPreferences()
        }
    }

    private fun removeKeywordFromAllLevels(keyword: String) {
        highImportanceTexts.remove(keyword)
        mediumImportanceTexts.remove(keyword)
        lowImportanceTexts.remove(keyword)
        saveKeywordsToSharedPreferences()
    }

    // 키워드를 각 중요도별로 저장하는 함수
    private fun saveKeywordToLevel(keyword: String, recyclerViewId: Int) {
        when (recyclerViewId) {
            R.id.recyclerView_high -> highImportanceTexts.add(keyword)
            R.id.recyclerView_medium -> mediumImportanceTexts.add(keyword)
            R.id.recyclerView_low -> lowImportanceTexts.add(keyword)
        }
    }

    // 키워드를 각 중요도별에서 제거하는 함수
    private fun removeKeywordFromLevel(keyword: String, recyclerViewId: Int) {
        Log.d("MainActivity", "removeKeywordFromLevel called with keyword: '$keyword' and recyclerViewId: $recyclerViewId")
        when (recyclerViewId) {
            R.id.recyclerView_high -> {
                highImportanceTexts.remove(keyword) // 정확히 해당 키워드만 삭제
                Log.d("MainActivity", "Keyword '$keyword' removed from High Importance Texts")
            }
            R.id.recyclerView_medium -> {
                mediumImportanceTexts.remove(keyword)
                Log.d("MainActivity", "Keyword '$keyword' removed from Medium Importance Texts")
            }
            R.id.recyclerView_low -> {
                lowImportanceTexts.remove(keyword)
                Log.d("MainActivity", "Keyword '$keyword' removed from Low Importance Texts")
            }
        }
    }

    // Check if a keyword is in another importance level
    private fun isKeywordInAnotherImportanceLevel(keyword: String): Boolean {
        return highImportanceAdapter.getAppList().any { it.first == keyword && it.second.isEmpty() } ||
                mediumImportanceAdapter.getAppList().any { it.first == keyword && it.second.isEmpty() } ||
                lowImportanceAdapter.getAppList().any { it.first == keyword && it.second.isEmpty() }
    }

    private fun isAppInAnotherImportanceLevel(packageName: String): Boolean {
        return highImportanceAdapter.getAppList().any { it.second == packageName } ||
                mediumImportanceAdapter.getAppList().any { it.second == packageName } ||
                lowImportanceAdapter.getAppList().any { it.second == packageName }
    }

    // 사용자가 키워드를 중요도에 추가할 때 해당 키워드를 SharedPreferences에 저장하는 함수
    private fun saveKeywordsToSharedPreferences() {
        Log.d("MainActivity", "Saving keywords to SharedPreferences. High: $highImportanceTexts, Medium: $mediumImportanceTexts, Low: $lowImportanceTexts")
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putStringSet("highImportanceTexts", highImportanceTexts)
        editor.putStringSet("mediumImportanceTexts", mediumImportanceTexts)
        editor.putStringSet("lowImportanceTexts", lowImportanceTexts)
        editor.apply()
        Log.d("MainActivity", "Keywords saved successfully.")

        // 키워드 변경 사항을 브로드캐스트
        val intent = Intent("com.example.KEYWORDS_UPDATED")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun loadKeywordsFromSharedPreferences() {
        Log.d("MainActivity", "Loading keywords from SharedPreferences.")
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        highImportanceTexts.clear()
        mediumImportanceTexts.clear()
        lowImportanceTexts.clear()
        val highTexts = sharedPreferences.getStringSet("highImportanceTexts", null)
        val mediumTexts = sharedPreferences.getStringSet("mediumImportanceTexts", null)
        val lowTexts = sharedPreferences.getStringSet("lowImportanceTexts", null)
        if (highTexts != null) highImportanceTexts.addAll(highTexts)
        if (mediumTexts != null) mediumImportanceTexts.addAll(mediumTexts)
        if (lowTexts != null) lowImportanceTexts.addAll(lowTexts)
        Log.d("MainActivity", "Loaded keywords: High=$highImportanceTexts, Medium=$mediumImportanceTexts, Low=$lowImportanceTexts")
    }

    private fun loadNotificationTitles(): MutableMap<String, String> {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val titleMapString = sharedPreferences.getString("notificationTitles", "")

        val notificationTitles = mutableMapOf<String, String>()
        titleMapString?.split(";")?.forEach {
            val parts = it.split(":")
            if (parts.size == 2) { // Ensure there are exactly two parts
                val (packageName, title) = parts
                notificationTitles[packageName] = title
            } else {
                Log.w("MainActivity", "Skipping malformed entry in notificationTitles: $it")
            }
        }

        return notificationTitles
    }

    // 앱 선택 다이얼로그
    @SuppressLint("QueryPermissionsNeeded")
    private fun showAppPickerDialog(onAppSelected: (String, String) -> Unit) {
        val packageManager = packageManager
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        Log.d("MainActivity", "receivedNotificationApps: $receivedNotificationApps")

        // Retrieve keywords from SharedPreferences
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedKeywords =
            sharedPreferences.getStringSet("savedKeywords", emptySet())?.toList() ?: emptyList()

        // Filter out keywords already assigned to an importance level
        val assignedKeywords = mutableSetOf<String>().apply {
            addAll(highImportanceAdapter.getAppList().filter { it.second.isEmpty() }
                .map { it.first })
            addAll(mediumImportanceAdapter.getAppList().filter { it.second.isEmpty() }
                .map { it.first })
            addAll(lowImportanceAdapter.getAppList().filter { it.second.isEmpty() }
                .map { it.first })
        }
        val unassignedKeywords = savedKeywords.filterNot { assignedKeywords.contains(it) }

        // Verify receivedNotificationApps is populated
        if (receivedNotificationApps.isEmpty()) {
            Log.w("MainActivity", "No received notifications found in receivedNotificationApps")
        }
        val notificationTitles = loadNotificationTitles()
        // Get only the apps that have sent notifications

        val notificationSendingApps = apps.filter { appInfo ->
            val isInReceived = receivedNotificationApps.contains(appInfo.packageName)
            val isExcluded = appInfo.packageName == "com.example.uxchannel_proto"
            isInReceived && !isExcluded
        }.map { appInfo ->
            val appName = appInfo.loadLabel(packageManager).toString()
            val packageName = appInfo.packageName
            val title = notificationTitles[packageName]
            val displayName = if (title != null && title != "No Title") "$appName - $title" else appName
            displayName to packageName
        }.sortedBy { it.first } //앱 이름순 으로 보여주기

        // Combine unassigned apps and keywords for display
        val combinedList = notificationSendingApps.map { it.first } + unassignedKeywords
        val combinedPackages =
            notificationSendingApps.map { it.second } + List(unassignedKeywords.size) { "" }

        if (combinedList.isEmpty()) {
            // Show an AlertDialog with a message indicating no items are available
            AlertDialog.Builder(this)
                .setTitle("앱 및 키워드 선택")
                .setMessage("아직 새로운 알림이 없습니다. 새로운 알림이 오면 설정해주세요.")
                .setPositiveButton("확인", null)
                .show()
            return
        }

        val styledList = combinedList.mapIndexed { index, item ->
            val spannable = SpannableString(item)
            val parts = item.split(" - ")

            val color = if (combinedPackages[index].isEmpty()) {
                ContextCompat.getColor(this, R.color.sky_blue) // Sky blue for keywords
            } else {
                val typedValue = TypedValue()
                theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                ContextCompat.getColor(this, typedValue.resourceId) // Adaptive primary text color for apps
            }
            spannable.setSpan(
                ForegroundColorSpan(color),
                0,
                parts[0].length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Set different sizes for the app name and the title (if available)
            if (parts.size > 1) {
                spannable.setSpan(
                    RelativeSizeSpan(0.7f), // Smaller size for the title part
                    parts[0].length + 3, // Start after " - "
                    item.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            spannable
        }

        // Use ArrayAdapter to display styled items
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, styledList)

        AlertDialog.Builder(this)
            .setTitle("앱 및 키워드 선택")
            .setAdapter(adapter) { _, which ->
                val selectedName = combinedList[which]
                val selectedPackage = combinedPackages[which]

                // If it's an app, extract only the app name without the title part
                val appName =
                    if (selectedPackage.isNotEmpty()) selectedName.split(" - ")[0] else selectedName

                // Pass the app name or keyword to the callback
                onAppSelected(appName, selectedPackage)
            }
            .show()
    }

    // 앱이 알림을 보낸 앱 목록을 SharedPreferences에 저장
    private fun saveReceivedNotificationAppsToSharedPreferences() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        editor.putStringSet("receivedNotificationApps", receivedNotificationApps)
        Log.d("MainActivity", "Saving receivedNotificationApps: $receivedNotificationApps")

        if (editor.commit()) {
            Log.d("MainActivity", "Received notification apps saved successfully.")
            val intent = Intent("com.example.RECEIVED_NOTIFICATION_APPS_UPDATED")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } else {
            Log.e("MainActivity", "Failed to save received notification apps.")
        }
    }

    // 앱 시작 시 SharedPreferences에서 알림을 보낸 앱 목록을 불러옴
    private fun loadReceivedNotificationAppsFromSharedPreferences() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        receivedNotificationApps = sharedPreferences.getStringSet("receivedNotificationApps", emptySet())?.toMutableSet()
            ?: mutableSetOf()

        Log.d("MainActivity", "Loaded receivedNotificationApps: $receivedNotificationApps")

        filterOutExistingImportanceApps()
    }

    private fun filterOutExistingImportanceApps() {
        val allImportanceApps = mutableSetOf<String>().apply {
            addAll(highImportanceAdapter.getAppList().map { it.second })
            addAll(mediumImportanceAdapter.getAppList().map { it.second })
            addAll(lowImportanceAdapter.getAppList().map { it.second })
        }
        receivedNotificationApps.removeAll(allImportanceApps)
        Log.d("MainActivity", "Filtered received notification apps after load: $receivedNotificationApps")
    }

    // 액티비티가 파괴될 때 BroadcastReceiver를 등록 해제
    override fun onDestroy() {
        super.onDestroy()
        saveAppListsToSharedPreferences()
        saveReceivedNotificationAppsToSharedPreferences()
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(keywordUpdateReceiver)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(receivedNotificationAppsUpdateReceiver)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(homeMonitorRefreshReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("MainActivity", "Receiver was not registered.")
        }
        try {
            unregisterReceiver(transferDataReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("MainActivity", "Transfer data receiver was not registered.")
        }
        stopService(Intent(this, NotificationListener::class.java))
        Log.d("MainActivity", "All receivers unregistered, NotificationListener stopped.")
    }
}