package com.example.app

import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class DbViewActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DbViewScreen(
                onBackClick = { finish() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DbViewScreen(
    onBackClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var contextManagerData by remember { mutableStateOf<List<ContextManagerEntry>>(emptyList()) }
    var notificationsData by remember { mutableStateOf<List<NotificationEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // DB 데이터 로드 함수
    fun loadData() {
        isLoading = true
        contextManagerData = emptyList()
        notificationsData = emptyList()
        
        // 비동기로 데이터 로드
        coroutineScope.launch {
            val contextManagerResult = withContext(Dispatchers.IO) {
                loadContextManagerData(context)
            }
            val notificationsResult = withContext(Dispatchers.IO) {
                loadNotificationsData(context)
            }
            
            contextManagerData = contextManagerResult
            notificationsData = notificationsResult
            isLoading = false
        }
    }
    
    // 초기 데이터 로드
    LaunchedEffect(Unit) {
        loadData()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFECF0F9))
    ) {
        // 상단 앱바
        TopAppBar(
            title = {
                Text(
                    text = "DB 보기",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기"
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { loadData() },
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "새로고침"
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            )
        )
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // ContextManager DB 섹션
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f)
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "ContextManager DB",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (contextManagerData.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "데이터가 없습니다",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(contextManagerData) { entry ->
                                ContextManagerEntryCard(entry = entry)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
            
            // Notifications DB 섹션
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Notifications DB",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (notificationsData.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "데이터가 없습니다",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(notificationsData) { entry ->
                                NotificationEntryCard(entry = entry)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContextManagerEntryCard(entry: ContextManagerEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "ID: ${entry.id}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            // Name 필드 - null이면 "null", 빈 문자열이면 "(empty)" 표시
            Text(
                text = "Name: ${if (entry.name.isEmpty()) "(empty)" else entry.name}",
                fontSize = 12.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            
            // Content 필드 - null이면 "null", 빈 문자열이면 "(empty)" 표시
            Text(
                text = "Content: ${if (entry.content.isEmpty()) "(empty)" else entry.content}",
                fontSize = 12.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))

            // Exceptions 필드 - null이면 "null", 빈 문자열이면 "empty" 표시
            Text(
                text = "Exceptions: ${if (entry.exceptions.isEmpty()) "(empty)" else entry.exceptions}",
                fontSize = 12.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            
            // Delivery 필드 - null이면 "null", 빈 문자열이면 "(empty)" 표시
            Text(
                text = "Delivery: ${if (entry.delivery.isEmpty()) "(empty)" else entry.delivery}",
                fontSize = 12.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            
            // Expires 필드 - null이면 "null", 빈 문자열이면 "(empty)" 표시
            Text(
                text = "Expires: ${if (entry.expires.isEmpty()) "(empty)" else entry.expires}",
                fontSize = 12.sp,
                color = Color.Black
            )
        }
    }
}

@Composable
fun NotificationEntryCard(entry: NotificationEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "ID: ${entry.id}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            // Device ID 필드 - null이면 "null", 빈 문자열이면 "(empty)" 표시
            Text(
                text = "Device ID: ${if (entry.deviceId.isEmpty()) "(empty)" else entry.deviceId}",
                fontSize = 12.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            
            // Package Name 필드 - null이면 "null", 빈 문자열이면 "(empty)" 표시
            Text(
                text = "Package: ${if (entry.packageName.isEmpty()) "(empty)" else entry.packageName}",
                fontSize = 12.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            
            // Post Time 필드 - null이면 "null", 빈 문자열이면 "(empty)" 표시
            Text(
                text = "Post Time: ${if (entry.postTime.isEmpty()) "(empty)" else entry.postTime}",
                fontSize = 12.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            
            // Title 필드 - null이면 "null", 빈 문자열이면 "(empty)" 표시
            Text(
                text = "Title: ${if (entry.title.isEmpty()) "(empty)" else entry.title}",
                fontSize = 12.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            
            // Text 필드 - null이면 "null", 빈 문자열이면 "(empty)" 표시
            Text(
                text = "Text: ${if (entry.text.isEmpty()) "(empty)" else entry.text}",
                fontSize = 12.sp,
                color = Color.Black
            )
        }
    }
}

// 데이터 클래스들
data class ContextManagerEntry(
    val id: Int,
    val name: String,
    val content: String,
    val exceptions: String,
    val delivery: String,
    val expires: String,
    val activity: String = "",
    val mode: String = "allow",
    /** active | completed — 만료 시 completed로 표시(삭제하지 않음) */
    val status: String = "active",
    /** none | daily | weekly */
    val recurrence: String = RecurrenceWindow.NONE,
    /** weekly: ISO 요일 CSV "1,3,5" */
    val daysOfWeek: String = "",
    val windowStart: String = "",
    val windowEnd: String = "",
) {
    val isCompleted: Boolean
        get() = status.equals("completed", ignoreCase = true)

    val isRecurring: Boolean
        get() = RecurrenceWindow.isRecurring(recurrence)
}

data class NotificationEntry(
    val id: Int,
    val deviceId: String,
    val packageName: String,
    val postTime: String,
    val title: String,
    val text: String
)

data class NotifLogEntry(
    val id: Int,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: String,
    val status: String,
    val createdAt: Long,
    val hidden: Boolean = false,
)

data class AppRuleProgress(
    val label: String,
    val progress: Float,
)

/** 홈 캐러셀 1 — 이번 주 NotifLog 집계 */
data class WeeklySummaryStats(
    val total: Int = 0,
    val filtered: Int = 0,
    val delivered: Int = 0,
) {
    val filterRate: Float
        get() = if (total <= 0) 0f else filtered.toFloat() / total.toFloat()
}

/** 홈 캐러셀 3 — 주간 시간대 버킷 */
data class TimeSlotStat(
    val label: String,
    val rangeLabel: String,
    val count: Int,
)

data class HomeDashboardData(
    val filteredToday: Int,
    val activeRuleCount: Int,
    val totalRuleCount: Int,
    val appProgress: List<AppRuleProgress>,
    val recentLogs: List<NotifLogEntry>,
    val weeklySummary: WeeklySummaryStats = WeeklySummaryStats(),
    val weeklyTimeSlots: List<TimeSlotStat> = emptyList(),
)

// DB 데이터 로드 함수들 (홈 모니터링에서도 사용)
internal fun loadContextManagerData(context: android.content.Context): List<ContextManagerEntry> {
    // 만료된 active 규칙을 completed로 표시한 뒤 로드
    runCatching { ContextManager(context).markExpiredRulesCompleted() }

    val entries = mutableListOf<ContextManagerEntry>()
    
    try {
        val dbHelper = DBHelper(context)
        val database = dbHelper.readableDatabase
        
        val cursor = database.rawQuery(
            """
            SELECT id, name, content, exceptions, delivery, expires, activity, mode, status,
                   recurrence, days_of_week, window_start, window_end
            FROM ContextManager
            """.trimIndent(),
            null
        )
        
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: ""
                val content = cursor.getString(cursor.getColumnIndexOrThrow("content")) ?: ""
                val exceptions = cursor.getString(cursor.getColumnIndexOrThrow("exceptions")) ?: ""
                val delivery = cursor.getString(cursor.getColumnIndexOrThrow("delivery")) ?: ""
                val expires = cursor.getString(cursor.getColumnIndexOrThrow("expires")) ?: ""
                val activity = try {
                    cursor.getString(cursor.getColumnIndexOrThrow("activity")) ?: ""
                } catch (_: Exception) {
                    ""
                }
                val mode = try {
                    cursor.getString(cursor.getColumnIndexOrThrow("mode"))?.takeIf { it.isNotBlank() } ?: "allow"
                } catch (_: Exception) {
                    "allow"
                }
                val status = try {
                    cursor.getString(cursor.getColumnIndexOrThrow("status"))?.takeIf { it.isNotBlank() } ?: "active"
                } catch (_: Exception) {
                    "active"
                }
                fun colOrEmpty(col: String): String = try {
                    val idx = cursor.getColumnIndex(col)
                    if (idx < 0) "" else cursor.getString(idx)?.takeIf { it.isNotBlank() } ?: ""
                } catch (_: Exception) {
                    ""
                }
                val recurrence = RecurrenceWindow.normalizeRecurrence(
                    colOrEmpty("recurrence").ifBlank { RecurrenceWindow.NONE }
                )
                
                entries.add(
                    ContextManagerEntry(
                        id = id,
                        name = name,
                        content = content,
                        exceptions = exceptions,
                        delivery = delivery,
                        expires = expires,
                        activity = activity,
                        mode = mode,
                        status = status,
                        recurrence = recurrence,
                        daysOfWeek = colOrEmpty("days_of_week"),
                        windowStart = colOrEmpty("window_start"),
                        windowEnd = colOrEmpty("window_end"),
                    )
                )
                
            } while (cursor.moveToNext())
        }
        
        cursor.close()
        database.close()
    } catch (e: Exception) {
        // status/mode 컬럼 없는 구버전 DB 대비
        try {
            val dbHelper = DBHelper(context)
            val database = dbHelper.readableDatabase
            val cursor = database.rawQuery(
                "SELECT id, name, content, exceptions, delivery, expires, activity FROM ContextManager",
                null
            )
            if (cursor.moveToFirst()) {
                do {
                    entries.add(
                        ContextManagerEntry(
                            cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                            cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: "",
                            cursor.getString(cursor.getColumnIndexOrThrow("content")) ?: "",
                            cursor.getString(cursor.getColumnIndexOrThrow("exceptions")) ?: "",
                            cursor.getString(cursor.getColumnIndexOrThrow("delivery")) ?: "",
                            cursor.getString(cursor.getColumnIndexOrThrow("expires")) ?: "",
                            cursor.getString(cursor.getColumnIndexOrThrow("activity")) ?: "",
                            "allow",
                            "active",
                        )
                    )
                } while (cursor.moveToNext())
            }
            cursor.close()
            database.close()
        } catch (e2: Exception) {
            Log.e("DbViewActivity", "ContextManager 데이터 로드 실패: ${e2.message}")
        }
    }
    
    return entries
}

internal fun loadNotificationsData(context: android.content.Context): List<NotificationEntry> {
    val entries = mutableListOf<NotificationEntry>()
    
    try {
        val dbHelper = DBHelper(context)
        val database = dbHelper.readableDatabase
        
        val cursor = database.rawQuery("SELECT * FROM Notifications", null)
        
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                val deviceId = cursor.getString(cursor.getColumnIndexOrThrow("deviceId")) ?: ""
                val packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")) ?: ""
                val postTime = cursor.getString(cursor.getColumnIndexOrThrow("post_time")) ?: ""
                val title = cursor.getString(cursor.getColumnIndexOrThrow("title")) ?: ""
                val text = cursor.getString(cursor.getColumnIndexOrThrow("text")) ?: ""
                
                entries.add(NotificationEntry(id, deviceId, packageName, postTime, title, text))
                
            } while (cursor.moveToNext())
        }
        
        cursor.close()
        database.close()
    } catch (e: Exception) {
        Log.e("DbViewActivity", "Notifications 데이터 로드 실패: ${e.message}")
    }
    
    return entries
}

internal fun loadNotifLogData(context: android.content.Context, limit: Int = 30): List<NotifLogEntry> {
    val entries = mutableListOf<NotifLogEntry>()
    try {
        val dbHelper = DBHelper(context)
        val database = dbHelper.readableDatabase
        val cursor = try {
            database.rawQuery(
                "SELECT id, package_name, title, text, post_time, status, created_at, hidden FROM NotifLog ORDER BY created_at DESC LIMIT ?",
                arrayOf(limit.toString())
            )
        } catch (_: Exception) {
            database.rawQuery(
                "SELECT id, package_name, title, text, post_time, status, created_at FROM NotifLog ORDER BY created_at DESC LIMIT ?",
                arrayOf(limit.toString())
            )
        }
        if (cursor.moveToFirst()) {
            do {
                val hidden = try {
                    cursor.getInt(cursor.getColumnIndexOrThrow("hidden")) != 0
                } catch (_: Exception) {
                    false
                }
                entries.add(
                    NotifLogEntry(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")) ?: "",
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")) ?: "",
                        text = cursor.getString(cursor.getColumnIndexOrThrow("text")) ?: "",
                        postTime = cursor.getString(cursor.getColumnIndexOrThrow("post_time")) ?: "",
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status")) ?: "",
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        hidden = hidden,
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        database.close()
    } catch (e: Exception) {
        Log.e("DbViewActivity", "NotifLog 로드 실패: ${e.message}")
    }
    return entries
}

internal fun deleteNotifLogById(context: android.content.Context, id: Int): Boolean {
    return try {
        val dbHelper = DBHelper(context)
        val database = dbHelper.writableDatabase
        val deleted = database.delete("NotifLog", "id = ?", arrayOf(id.toString()))
        database.close()
        deleted > 0
    } catch (e: Exception) {
        Log.e("DbViewActivity", "NotifLog 삭제 실패: ${e.message}")
        false
    }
}

/** 최근 알림 휴지통: DB/대기열은 유지하고 UI에서만 숨김 (시간 되면 여전히 전송) */
internal fun hideNotifLogsFromUi(context: android.content.Context): Boolean {
    return try {
        val dbHelper = DBHelper(context)
        val database = dbHelper.writableDatabase
        val values = android.content.ContentValues().apply { put("hidden", 1) }
        val updated = database.update(
            "NotifLog",
            values,
            "status IN ('stored') AND IFNULL(hidden,0) = 0",
            null,
        )
        database.close()
        Log.d("DbViewActivity", "최근 알림 UI 숨김: ${updated}건")
        true
    } catch (e: Exception) {
        Log.e("DbViewActivity", "NotifLog UI 숨김 실패: ${e.message}")
        false
    }
}

/** @deprecated 휴지통은 hideNotifLogsFromUi 사용. 개발용 전체 삭제만 유지 */
internal fun clearNotifLog(context: android.content.Context): Boolean {
    return hideNotifLogsFromUi(context)
}

internal fun loadHomeDashboard(context: android.content.Context): HomeDashboardData {
    val rules = loadContextManagerData(context)
    val timeUtils = TimeUtils()
    val now = try {
        timeUtils.parseIsoToMillis(timeUtils.getTodayDatetime())
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
    val activeRules = rules.filter {
        if (it.isCompleted) return@filter false
        try {
            val d = timeUtils.parseIsoToMillis(it.delivery)
            val e = timeUtils.parseIsoToMillis(it.expires)
            now in d until e
        } catch (_: Exception) {
            false
        }
    }

    val startOfDay = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    var filteredToday = 0
    val pkgCounts = mutableMapOf<String, Int>()
    try {
        val dbHelper = DBHelper(context)
        val database = dbHelper.readableDatabase
        val cursor = database.rawQuery(
            """
            SELECT package_name, status, COUNT(*) as cnt
            FROM NotifLog
            WHERE created_at >= ? AND status IN ('stored','completed')
            GROUP BY package_name, status
            """.trimIndent(),
            arrayOf(startOfDay.toString())
        )
        if (cursor.moveToFirst()) {
            do {
                val pkg = cursor.getString(cursor.getColumnIndexOrThrow("package_name")) ?: ""
                val cnt = cursor.getInt(cursor.getColumnIndexOrThrow("cnt"))
                filteredToday += cnt
                pkgCounts[pkg] = (pkgCounts[pkg] ?: 0) + cnt
            } while (cursor.moveToNext())
        }
        cursor.close()
        database.close()
    } catch (e: Exception) {
        Log.e("DbViewActivity", "홈 통계 로드 실패: ${e.message}")
    }

    val totalFiltered = filteredToday.coerceAtLeast(1)
    val sortedPkgs = pkgCounts.entries.sortedByDescending { it.value }
    val appProgress = if (sortedPkgs.isNotEmpty()) {
        val topN = sortedPkgs.take(5)
        val topSum = topN.sumOf { it.value }
        val otherCnt = (filteredToday - topSum).coerceAtLeast(0)
        buildList {
            topN.forEach { (pkg, cnt) ->
                val label = AppNameMapper.toDisplayName(pkg).let { name ->
                    if (name.contains('.')) name.substringAfterLast('.').ifBlank { "앱" } else name
                }
                add(AppRuleProgress(label = label, progress = cnt.toFloat() / totalFiltered))
            }
            if (otherCnt > 0 || sortedPkgs.size > 5) {
                add(AppRuleProgress(label = "기타", progress = otherCnt.toFloat() / totalFiltered))
            }
        }
    } else {
        emptyList()
    }

    val weekStart = java.util.Calendar.getInstance().apply {
        firstDayOfWeek = java.util.Calendar.MONDAY
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
    }.timeInMillis

    var weekTotal = 0
    var weekFiltered = 0
    var weekDelivered = 0
    // 인덱스: 0=오전6-12, 1=오후12-18, 2=저녁18-24, 3=새벽0-6
    val slotCounts = IntArray(4)
    try {
        val dbHelper = DBHelper(context)
        val database = dbHelper.readableDatabase
        val cursor = database.rawQuery(
            """
            SELECT status, created_at
            FROM NotifLog
            WHERE created_at >= ?
            """.trimIndent(),
            arrayOf(weekStart.toString()),
        )
        if (cursor.moveToFirst()) {
            do {
                val status = cursor.getString(cursor.getColumnIndexOrThrow("status")) ?: ""
                val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                weekTotal++
                when {
                    status.equals("stored", ignoreCase = true) ||
                        status.equals("completed", ignoreCase = true) -> weekFiltered++
                    status.equals("delivered", ignoreCase = true) -> weekDelivered++
                }
                val hour = java.util.Calendar.getInstance().apply {
                    timeInMillis = createdAt
                }.get(java.util.Calendar.HOUR_OF_DAY)
                val slot = when (hour) {
                    in 6 until 12 -> 0
                    in 12 until 18 -> 1
                    in 18 until 24 -> 2
                    else -> 3 // 0..5 새벽
                }
                slotCounts[slot]++
            } while (cursor.moveToNext())
        }
        cursor.close()
        database.close()
    } catch (e: Exception) {
        Log.e("DbViewActivity", "주간 통계 로드 실패: ${e.message}")
    }

    val weeklySummary = WeeklySummaryStats(
        total = weekTotal,
        filtered = weekFiltered,
        delivered = weekDelivered,
    )
    val weeklyTimeSlots = listOf(
        TimeSlotStat("오전", "6-12시", slotCounts[0]),
        TimeSlotStat("오후", "12-18시", slotCounts[1]),
        TimeSlotStat("저녁", "18-24시", slotCounts[2]),
        TimeSlotStat("새벽", "0-6시", slotCounts[3]),
    )

    return HomeDashboardData(
        filteredToday = filteredToday,
        activeRuleCount = activeRules.size,
        totalRuleCount = rules.size,
        appProgress = appProgress,
        recentLogs = loadNotifLogData(context, 80).filter {
            !it.hidden && it.status == "stored"
        },
        weeklySummary = weeklySummary,
        weeklyTimeSlots = weeklyTimeSlots,
    )
}
