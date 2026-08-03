package com.example.app.presentation.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.app.AppNameMapper
import com.example.app.AuthSession
import com.example.app.ContextManager
import com.example.app.ContextManagerEntry
import com.example.app.HomeDashboardData
import com.example.app.hideNotifLogsFromUi
import com.example.app.loadContextManagerData
import com.example.app.presentation.screens.AddRuleScreen
import com.example.app.presentation.screens.HomeScreen
import com.example.app.presentation.screens.ProfileScreen
import com.example.app.presentation.screens.RuleStatus
import com.example.app.presentation.screens.RulesFilter
import com.example.app.presentation.screens.RulesScreen
import com.example.app.presentation.screens.formatRuleIso
import com.example.app.presentation.screens.status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HomeRoute(
    userName: String,
    avatarPath: String?,
    serviceOn: Boolean,
    dashboard: HomeDashboardData,
    rules: List<ContextManagerEntry>,
    onToggleService: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenAddRule: () -> Unit,
    onOpenProfile: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    HomeScreen(
        userName = userName,
        avatarPath = avatarPath,
        serviceOn = serviceOn,
        dashboard = dashboard,
        rules = rules,
        onToggleService = onToggleService,
        onOpenRules = onOpenRules,
        onOpenAddRule = onOpenAddRule,
        onOpenProfile = onOpenProfile,
        onRefresh = onRefresh,
        onClearLogs = {
            scope.launch {
                withContext(Dispatchers.IO) { hideNotifLogsFromUi(context) }
                onRefresh()
            }
        },
        onFlushHeld = {
            scope.launch {
                val delivered = withContext(Dispatchers.IO) {
                    ContextManager(context).flushAllHeldNotifications()
                }
                val msg = if (delivered > 0) {
                    "보류 알림 ${delivered}건을 수신했습니다"
                } else {
                    "보류 중인 알림이 없습니다"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                onRefresh()
            }
        },
    )
}

@Composable
fun RulesRoute(
    refreshKey: Int = 0,
    onRulesChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allRules by remember { mutableStateOf<List<ContextManagerEntry>>(emptyList()) }
    var filter by remember { mutableStateOf(RulesFilter.ALL) }
    var visibleRules by remember { mutableStateOf<List<ContextManagerEntry>>(emptyList()) }

    fun applyFilter(source: List<ContextManagerEntry>, selected: RulesFilter) {
        visibleRules = when (selected) {
            RulesFilter.ALL -> source
            RulesFilter.ACTIVE -> source.filter { it.status() == RuleStatus.ACTIVE }
            RulesFilter.INACTIVE -> source.filter { it.status() == RuleStatus.INACTIVE }
            RulesFilter.EXPIRED -> source.filter { it.status() == RuleStatus.EXPIRED }
        }
    }

    suspend fun reload() {
        AppNameMapper.loadInstalledApps(context)
        val loaded = withContext(Dispatchers.IO) { loadContextManagerData(context) }
        allRules = loaded
        applyFilter(loaded, filter)
    }

    LaunchedEffect(refreshKey) {
        reload()
    }

    RulesScreen(
        rules = visibleRules,
        filter = filter,
        onFilterChange = {
            filter = it
            applyFilter(allRules, it)
        },
        onSaveRule = { ruleId, draft ->
            scope.launch {
                val now = System.currentTimeMillis()
                val startMs = TimeUnit.MINUTES.toMillis(draft.startMinutesAfter.toLong())
                val endMs = TimeUnit.MINUTES.toMillis(
                    maxOf(draft.endMinutesAfter, draft.startMinutesAfter).toLong()
                )
                // 저장 순간의 현재 시각 + N분 뒤 → delivery / expires
                val deliveryIso = formatRuleIso(now + startMs)
                val expiresIso = formatRuleIso(now + endMs)
                val apps = draft.appsText.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val keywords = draft.keywordsText.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val ok = withContext(Dispatchers.IO) {
                    val cm = ContextManager(context)
                    val updated = cm.updateRule(
                        ruleId = ruleId,
                        ruleTitle = draft.title.trim().ifBlank { "알림 규칙" },
                        appDisplayNames = apps,
                        contentTags = keywords,
                        deliveryIso = deliveryIso,
                        expiresIso = expiresIso,
                        mode = "allow",
                    )
                    // DB 반영 직후 선별 전송 재평가 (이후 MyChecker가 10초마다 계속 모니터링)
                    if (updated) cm.periodicDeliveryCheck()
                    updated
                }
                if (ok) {
                    Toast.makeText(context, "규칙이 저장되었습니다", Toast.LENGTH_SHORT).show()
                    onRulesChanged()
                    reload()
                } else {
                    Toast.makeText(context, "저장 실패: 앱 이름을 확인하세요", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onDeleteRule = { ruleId ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    ContextManager(context).deleteRule(ruleId)
                }
                if (ok) {
                    Toast.makeText(context, "규칙이 삭제되었습니다", Toast.LENGTH_SHORT).show()
                    onRulesChanged()
                    reload()
                } else {
                    Toast.makeText(context, "삭제에 실패했습니다", Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}

@Composable
fun AddRuleRoute(
    onBack: () -> Unit,
    onRegistered: () -> Unit,
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val apps = remember {
        AppNameMapper.loadInstalledApps(context)
        AppNameMapper.getAllAppNames().distinct().sorted()
    }

    AddRuleScreen(
        availableApps = apps,
        errorMessage = errorMessage,
        onBack = onBack,
        onSubmit = { title, selectedApps, tags, startMillis, endMillis ->
            if (title.isBlank()) {
                errorMessage = "규칙 이름을 입력하세요"
                return@AddRuleScreen
            }
            if (endMillis <= startMillis) {
                errorMessage = "조건 종료는 시작보다 늦어야 합니다"
                return@AddRuleScreen
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            val ok = ContextManager(context).insertManualRule(
                ruleTitle = title.trim(),
                appDisplayNames = selectedApps,
                contentTags = tags,
                exceptionDisplayNames = emptyList(),
                deliveryIso = sdf.format(Date(startMillis)),
                expiresIso = sdf.format(Date(endMillis)),
                mode = "allow",
            )
            if (ok) {
                Toast.makeText(context, "규칙이 등록되었습니다", Toast.LENGTH_SHORT).show()
                onRegistered()
            } else {
                errorMessage = "규칙 등록에 실패했습니다. 앱 이름을 확인하세요."
            }
        },
    )
}

@Composable
fun ProfileRoute(
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    onProfileUpdated: (name: String, avatarPath: String?) -> Unit,
) {
    val context = LocalContext.current
    var displayName by remember { mutableStateOf(AuthSession.displayName(context)) }
    var avatarPath by remember { mutableStateOf(AuthSession.avatarPath(context)) }

    ProfileScreen(
        displayName = displayName,
        email = AuthSession.email(context),
        avatarPath = avatarPath,
        onBack = onBack,
        onSave = { name, pendingUri ->
            AuthSession.updateDisplayName(context, name)
            if (pendingUri != null) {
                runCatching {
                    avatarPath = AuthSession.updateAvatarFromUri(context, pendingUri)
                }
            }
            displayName = AuthSession.displayName(context)
            onProfileUpdated(displayName, avatarPath)
        },
        onLogout = {
            AuthSession.logout(context)
            onLoggedOut()
        },
    )
}
