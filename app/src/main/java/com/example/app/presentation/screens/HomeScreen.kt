package com.example.app.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.example.app.BuildConfig
import com.example.app.AppNameMapper
import com.example.app.AppRuleProgress
import com.example.app.ContextManagerEntry
import com.example.app.HomeDashboardData
import com.example.app.NotifAiSummarizer
import com.example.app.NotifLogEntry
import com.example.app.TimeSlotStat
import com.example.app.TimeUtils
import com.example.app.WeeklySummaryStats
import com.example.app.presentation.theme.AppColors
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    userName: String,
    avatarPath: String? = null,
    serviceOn: Boolean,
    dashboard: HomeDashboardData,
    rules: List<ContextManagerEntry>,
    onToggleService: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenAddRule: () -> Unit,
    onOpenProfile: () -> Unit,
    onRefresh: () -> Unit,
    onClearLogs: () -> Unit,
    onFlushHeld: () -> Unit,
) {
    val notifFolders = remember(dashboard.recentLogs) {
        groupRecentLogsByApp(dashboard.recentLogs)
    }
    var expandedPkg by remember { mutableStateOf<String?>(null) }
    val openAI = remember {
        OpenAI(
            OpenAIConfig(
                token = BuildConfig.OPENAI_API_KEY
            )
        )
    }
    val ruleHint = remember(rules) {
        rules.firstOrNull { !it.isCompleted && it.activity.isNotBlank() && !it.activity.equals("null", true) }
            ?.activity
            ?.let { "규칙 '$it'에 의해 필터링됨" }
            ?: "활성 알림 규칙에 의해 필터링됨"
    }
    Box(
        modifier = ComposeModifier
            .fillMaxSize()
            .background(AppColors.Bg)
    ) {
        Column(
            modifier = ComposeModifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 고정: 인사 + 아바타 (상단 여백을 조금 더 내려 Figma와 맞춤)
            HomeTopBar(
                userName = userName,
                avatarPath = avatarPath,
                serviceOn = serviceOn,
                onOpenProfile = onOpenProfile,
                onToggleService = onToggleService,
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
            )

            // 고정: 통계 캐러셀 (이번 주 요약 / 실시간 통계 / 주간 시간대 별 알림)
            StatsCarousel(
                weeklySummary = dashboard.weeklySummary,
                filteredToday = dashboard.filteredToday,
                appProgress = dashboard.appProgress,
                timeSlots = dashboard.weeklyTimeSlots,
                onRefresh = onRefresh,
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
            )

            // 스크롤: 등록된 규칙 + 최근 알림만
            LazyColumn(
                modifier = ComposeModifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 22.dp,
                    end = 22.dp,
                    top = 16.dp,
                    bottom = 100.dp,
                ),
            ) {
                item(key = "rules_header") {
                    SectionHeader(
                        title = "등록된 규칙",
                        count = rules.size,
                        action = "전체 보기",
                        onAction = onOpenRules,
                    )
                    Spacer(modifier = ComposeModifier.height(12.dp))
                    if (rules.isEmpty()) {
                        Text("등록된 규칙이 없습니다", color = AppColors.Secondary, fontSize = 13.sp)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(rules) { index, rule ->
                                RuleCard(rule = rule, index = index)
                            }
                        }
                    }
                    Spacer(modifier = ComposeModifier.height(26.dp))
                }

                item(key = "recent_header") {
                    RecentLogsHeader(
                        count = notifFolders.size,
                        onFlushHeld = onFlushHeld.takeIf { notifFolders.isNotEmpty() },
                        onClearLogs = onClearLogs.takeIf { notifFolders.isNotEmpty() },
                    )
                    Spacer(modifier = ComposeModifier.height(12.dp))
                }

                if (notifFolders.isEmpty()) {
                    item(key = "recent_logs_empty") {
                        Text("처리된 알림이 없습니다", color = AppColors.Secondary, fontSize = 13.sp)
                    }
                } else {
                    items(
                        items = notifFolders,
                        key = { folder -> folder.packageName },
                    ) { folder ->
                        Column(
                            modifier = ComposeModifier.animateItem(
                                fadeInSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                                fadeOutSpec = tween(durationMillis = 220),
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        ) {
                            NotifFolderSection(
                                folder = folder,
                                expanded = expandedPkg == folder.packageName,
                                ruleHint = ruleHint,
                                openAI = openAI,
                                onToggle = {
                                    expandedPkg =
                                        if (expandedPkg == folder.packageName) null
                                        else folder.packageName
                                },
                            )
                            Spacer(modifier = ComposeModifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    userName: String,
    avatarPath: String?,
    serviceOn: Boolean,
    onOpenProfile: () -> Unit,
    onToggleService: () -> Unit,
    modifier: ComposeModifier = ComposeModifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = ComposeModifier
                .clip(CircleShape)
                .clickable(onClick = onOpenProfile),
        ) {
            ProfileAvatarImage(
                avatarPath = avatarPath,
                displayName = userName,
                size = 46.dp,
            )
        }
        Spacer(modifier = ComposeModifier.width(16.dp))
        Column(modifier = ComposeModifier.weight(1f)) {
            Text(
                text = "안녕하세요!",
                color = AppColors.Black,
                fontSize = 14.sp,
            )
            Spacer(modifier = ComposeModifier.height(4.dp))
            Text(
                text = "$userName 님",
                color = AppColors.Black,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Button(
            onClick = onToggleService,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (serviceOn) AppColors.Primary else AppColors.Badge,
                contentColor = if (serviceOn) AppColors.White else AppColors.Primary,
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (serviceOn) "Service On" else "Service Off",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private val TealAccent = Color(0xFF33A68C)
/** 캐러셀 1·2·3 공통 고정 높이 — 내용이 잘리지 않도록 */
private val StatsCarouselCardHeight = 232.dp

@Composable
private fun StatsCarousel(
    weeklySummary: WeeklySummaryStats,
    filteredToday: Int,
    appProgress: List<AppRuleProgress>,
    timeSlots: List<TimeSlotStat>,
    onRefresh: () -> Unit,
    modifier: ComposeModifier = ComposeModifier,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 22.dp),
            pageSpacing = 12.dp,
            modifier = ComposeModifier
                .fillMaxWidth()
                .height(StatsCarouselCardHeight),
        ) { page ->
            val pageModifier = ComposeModifier.fillMaxSize()
            when (page) {
                0 -> WeeklySummaryCard(
                    stats = weeklySummary,
                    onRefresh = onRefresh,
                    modifier = pageModifier,
                )
                1 -> RealtimeStatsCard(
                    filteredToday = filteredToday,
                    appProgress = appProgress,
                    onRefresh = onRefresh,
                    modifier = pageModifier,
                )
                else -> TimeSlotStatsCard(
                    slots = timeSlots,
                    onRefresh = onRefresh,
                    modifier = pageModifier,
                )
            }
        }
        Spacer(modifier = ComposeModifier.height(10.dp))
        Row(
            modifier = ComposeModifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = ComposeModifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 7.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (active) Color(0xFF666673) else Color(0xFFD0D0D8))
                )
            }
        }
    }
}

@Composable
private fun StatsCardShell(
    accent: Color,
    onRefresh: () -> Unit,
    modifier: ComposeModifier = ComposeModifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0x0F000000))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.White)
            .clickable(onClick = onRefresh)
    ) {
        Box(
            modifier = ComposeModifier
                .fillMaxWidth()
                .height(4.dp)
                .background(accent)
        )
        Column(
            modifier = ComposeModifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            content = content,
        )
    }
}

@Composable
private fun WeeklySummaryCard(
    stats: WeeklySummaryStats,
    onRefresh: () -> Unit,
    modifier: ComposeModifier = ComposeModifier,
) {
    val ratePct = (stats.filterRate * 100f).roundToInt().coerceIn(0, 100)
    val animatedTotal by animateIntAsState(stats.total, tween(550, easing = FastOutSlowInEasing), label = "weekTotal")
    val animatedFiltered by animateIntAsState(stats.filtered, tween(550, easing = FastOutSlowInEasing), label = "weekFiltered")
    val animatedDelivered by animateIntAsState(stats.delivered, tween(550, easing = FastOutSlowInEasing), label = "weekDelivered")
    val animatedRate by animateFloatAsState(stats.filterRate.coerceIn(0f, 1f), tween(550, easing = FastOutSlowInEasing), label = "weekRate")

    StatsCardShell(accent = AppColors.Orange, onRefresh = onRefresh, modifier = modifier) {
        Column(modifier = ComposeModifier.fillMaxWidth()) {
            Text("이번 주 요약", color = AppColors.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = ComposeModifier.height(6.dp))
            Text("총 알림", color = AppColors.Secondary, fontSize = 10.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$animatedTotal",
                    color = AppColors.Black,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = ComposeModifier.width(4.dp))
                Text(
                    text = "개",
                    color = AppColors.Secondary,
                    fontSize = 12.sp,
                    modifier = ComposeModifier.padding(bottom = 3.dp),
                )
            }
        }
        Row(modifier = ComposeModifier.fillMaxWidth()) {
            Column(modifier = ComposeModifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("↓", color = AppColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = ComposeModifier.width(4.dp))
                    Text("자동 필터링", color = AppColors.Secondary, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$animatedFiltered",
                        color = AppColors.Primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = ComposeModifier.width(2.dp))
                    Text(
                        "개",
                        color = AppColors.Primary,
                        fontSize = 10.sp,
                        modifier = ComposeModifier.padding(bottom = 2.dp),
                    )
                }
            }
            Column(modifier = ComposeModifier.weight(1f)) {
                Text("전달됨", color = AppColors.Secondary, fontSize = 10.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$animatedDelivered",
                        color = TealAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = ComposeModifier.width(2.dp))
                    Text(
                        "개",
                        color = TealAccent,
                        fontSize = 10.sp,
                        modifier = ComposeModifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
        Column(modifier = ComposeModifier.fillMaxWidth()) {
            Box(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppColors.Badge)
            ) {
                Box(
                    modifier = ComposeModifier
                        .fillMaxHeight()
                        .fillMaxWidth(
                            if (stats.total > 0) animatedRate.coerceIn(0.02f, 1f) else 0f
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.Primary)
                )
            }
            Spacer(modifier = ComposeModifier.height(6.dp))
            Text(
                text = "필터링률 ${ratePct}%",
                color = AppColors.Primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RealtimeStatsCard(
    filteredToday: Int,
    appProgress: List<AppRuleProgress>,
    onRefresh: () -> Unit,
    modifier: ComposeModifier = ComposeModifier,
) {
    val segmentColors = listOf(
        AppColors.Primary,
        AppColors.Orange,
        AppColors.Blue,
        TealAccent,
        Color(0xFFC2185B),
        Color(0xFFE8E7EC),
    )
    val statsAnim = tween<Float>(durationMillis = 550, easing = FastOutSlowInEasing)
    val animatedCount by animateIntAsState(
        targetValue = filteredToday,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "filteredToday",
    )
    val animatedSegments = appProgress.map { item ->
        val p by animateFloatAsState(
            targetValue = item.progress.coerceIn(0f, 1f),
            animationSpec = statsAnim,
            label = "seg_${item.label}",
        )
        item.copy(progress = p)
    }

    StatsCardShell(accent = AppColors.Black, onRefresh = onRefresh, modifier = modifier) {
        Column(modifier = ComposeModifier.fillMaxWidth()) {
            Text("실시간 통계", color = AppColors.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = ComposeModifier.height(2.dp))
            Text("지연·필터된 알림 수", color = AppColors.Secondary, fontSize = 10.sp)
        }
        Row(
            modifier = ComposeModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$animatedCount",
                    color = AppColors.Primary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = ComposeModifier.width(8.dp))
                Text(
                    text = "건 / 오늘",
                    color = AppColors.Secondary,
                    fontSize = 12.sp,
                    modifier = ComposeModifier.padding(bottom = 6.dp),
                )
            }
            RatioDonutChart(
                segments = animatedSegments,
                colors = segmentColors,
                modifier = ComposeModifier.size(76.dp),
            )
        }
        if (animatedSegments.isNotEmpty()) {
            Row(
                modifier = ComposeModifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                animatedSegments.take(4).forEachIndexed { index, item ->
                    if (index > 0) Spacer(modifier = ComposeModifier.width(10.dp))
                    val pct = (item.progress * 100f).roundToInt().coerceIn(0, 100)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = ComposeModifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(segmentColors[index % segmentColors.size])
                        )
                        Spacer(modifier = ComposeModifier.width(3.dp))
                        Text(
                            text = "${item.label} $pct%",
                            color = Color(0xFF666673),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            Text(
                "오늘 필터링된 알림이 없습니다",
                color = AppColors.Secondary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun TimeSlotStatsCard(
    slots: List<TimeSlotStat>,
    onRefresh: () -> Unit,
    modifier: ComposeModifier = ComposeModifier,
) {
    val displaySlots = remember(slots) {
        slots.ifEmpty {
            listOf(
                TimeSlotStat("오전", "6-12시", 0),
                TimeSlotStat("오후", "12-18시", 0),
                TimeSlotStat("저녁", "18-24시", 0),
                TimeSlotStat("새벽", "0-6시", 0),
            )
        }
    }
    val maxCount = displaySlots.maxOf { it.count }.coerceAtLeast(1)

    StatsCardShell(accent = TealAccent, onRefresh = onRefresh, modifier = modifier) {
        Text(
            "주간 시간대 별 알림",
            color = AppColors.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = ComposeModifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            displaySlots.forEach { slot ->
                TimeSlotBar(
                    slot = slot,
                    maxCount = maxCount,
                    modifier = ComposeModifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun TimeSlotBar(
    slot: TimeSlotStat,
    maxCount: Int,
    modifier: ComposeModifier = ComposeModifier,
) {
    val animatedCount by animateIntAsState(
        targetValue = slot.count,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "slot_${slot.label}",
    )
    val frac by animateFloatAsState(
        targetValue = if (slot.count <= 0) 0.08f
        else (slot.count.toFloat() / maxCount).coerceIn(0.08f, 1f),
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "slotH_${slot.label}",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            text = "$animatedCount",
            color = AppColors.Primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = ComposeModifier.height(4.dp))
        Box(
            modifier = ComposeModifier
                .width(40.dp)
                .weight(1f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .fillMaxHeight(frac)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(AppColors.Primary)
            )
        }
        Spacer(modifier = ComposeModifier.height(6.dp))
        Text(slot.label, color = AppColors.Secondary, fontSize = 10.sp)
        Text(slot.rangeLabel, color = Color(0xFFA0A0AA), fontSize = 8.sp)
    }
}

@Composable
private fun RatioDonutChart(
    segments: List<AppRuleProgress>,
    colors: List<Color>,
    modifier: ComposeModifier = ComposeModifier,
) {
    Canvas(modifier = modifier) {
        val stroke = min(size.minDimension * 0.18f, 14.dp.toPx())
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        if (segments.isEmpty() || segments.all { it.progress <= 0.001f }) {
            drawArc(
                color = Color(0xFFE8E7EC),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            return@Canvas
        }
        var start = -90f
        segments.forEachIndexed { index, item ->
            val sweep = item.progress.coerceIn(0f, 1f) * 360f
            if (sweep <= 0f) return@forEachIndexed
            drawArc(
                color = colors[index % colors.size],
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            start += sweep
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = ComposeModifier.fillMaxWidth()) {
        Text(title, color = AppColors.Black, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = ComposeModifier.width(8.dp))
        Box(
            modifier = ComposeModifier
                .size(16.dp)
                .clip(CircleShape)
                .background(AppColors.Badge),
            contentAlignment = Alignment.Center
        ) {
            Text("$count", color = AppColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Normal)
        }
        Spacer(modifier = ComposeModifier.weight(1f))
        Text(
            text = action,
            color = AppColors.Primary,
            fontSize = 12.sp,
            modifier = ComposeModifier.clickable(onClick = onAction)
        )
    }
}

@Composable
private fun RecentLogsHeader(
    count: Int,
    onFlushHeld: (() -> Unit)?,
    onClearLogs: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = ComposeModifier.fillMaxWidth()) {
        Text("최근 알림", color = AppColors.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = ComposeModifier.weight(1f))
        if (onFlushHeld != null) {
            Box(
                modifier = ComposeModifier
                    .height(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AppColors.Primary)
                    .clickable(onClick = onFlushHeld)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "즉시 수신",
                    color = AppColors.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (onClearLogs != null) {
            Spacer(modifier = ComposeModifier.width(6.dp))
            IconButton(
                onClick = onClearLogs,
                modifier = ComposeModifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "전체 삭제",
                    tint = AppColors.Secondary,
                    modifier = ComposeModifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun RuleCard(rule: ContextManagerEntry, index: Int) {
    val bg = if (index % 2 == 0) AppColors.CardBlue else AppColors.CardOrange
    val timeColor = if (index % 2 == 0) AppColors.Primary else AppColors.Orange
    val timeUtils = remember { TimeUtils() }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(rule.id) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val deliveryMs = remember(rule.delivery) {
        runCatching { timeUtils.parseIsoToMillis(rule.delivery) }.getOrNull()
    }
    val expiresMs = remember(rule.expires) {
        runCatching { timeUtils.parseIsoToMillis(rule.expires) }.getOrNull()
    }
    // GPT '받지마': delivery ≈ expires → UI는 그 시점까지 남은 시간으로 표시
    val isMuteUntil = deliveryMs != null && expiresMs != null &&
        kotlin.math.abs(expiresMs - deliveryMs) <= 2_000L

    val timer = remember(rule.delivery, rule.expires, nowMillis, isMuteUntil) {
        ruleTimerState(
            deliveryIso = rule.delivery,
            expiresIso = rule.expires,
            nowMillis = nowMillis,
            timeUtils = timeUtils,
            isMuteUntil = isMuteUntil,
        )
    }

    val appsLabel = formatRuleApps(rule.name)
    val title = rule.activity
        .takeIf { it.isNotBlank() && !it.equals("null", true) }
        ?: when {
            rule.content.isNotBlank() -> rule.content.split(",").first().trim() + " 관련 알림"
            else -> "알림 규칙 #${rule.id}"
        }

    Column(
        modifier = ComposeModifier
            .width(168.dp)
            .height(124.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(14.dp)
    ) {
        Text(
            text = appsLabel,
            color = AppColors.Secondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = ComposeModifier.height(6.dp))
        Text(
            text = title,
            color = AppColors.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = ComposeModifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = timer.clock,
                color = timeColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (timer.suffix.isNotEmpty()) {
                Spacer(modifier = ComposeModifier.width(6.dp))
                Text(
                    text = timer.suffix,
                    color = Color(0xFF80808C),
                    fontSize = 10.sp,
                    modifier = ComposeModifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

private data class RuleTimerUi(
    val clock: String,
    val suffix: String,
)

private fun ruleTimerState(
    deliveryIso: String,
    expiresIso: String,
    nowMillis: Long,
    timeUtils: TimeUtils,
    isMuteUntil: Boolean = false,
): RuleTimerUi {
    return try {
        val delivery = timeUtils.parseIsoToMillis(deliveryIso)
        val expires = timeUtils.parseIsoToMillis(expiresIso)
        val endAt = maxOf(delivery, expires)

        // '받지마': 시작=종료=미래 → 그 시각까지 카운트다운 (진행 중처럼)
        if (isMuteUntil) {
            return if (nowMillis >= endAt) {
                RuleTimerUi(clock = "00:00", suffix = "남음")
            } else {
                RuleTimerUi(clock = formatClock(endAt - nowMillis), suffix = "남음")
            }
        }

        when {
            nowMillis < delivery -> {
                RuleTimerUi(clock = formatClock(delivery - nowMillis), suffix = "후 시작")
            }
            nowMillis >= expires -> {
                RuleTimerUi(clock = "00:00", suffix = "남음")
            }
            else -> {
                RuleTimerUi(clock = formatClock(expires - nowMillis), suffix = "남음")
            }
        }
    } catch (_: Exception) {
        RuleTimerUi(clock = "--:--", suffix = "")
    }
}

/** Figma: 07:23 / 1:23:45 */
private fun formatClock(millis: Long): String {
    val totalSec = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatRuleApps(nameField: String): String {
    if (nameField.isBlank()) return "전체 앱"
    return nameField.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ") { pkg ->
            if (pkg.contains(".")) AppNameMapper.toDisplayName(pkg) else pkg
        }
}

private data class NotifAppFolder(
    val packageName: String,
    val displayName: String,
    val delivered: Int,
    val blocked: Int,
    val stored: Int,
    val total: Int,
    val latestCreatedAt: Long,
    val items: List<NotifLogEntry>,
) {
    val summaryLabel: String
        get() {
            val parts = mutableListOf<String>()
            if (delivered > 0) parts += "${delivered}건 전송"
            if (stored > 0) parts += "${stored}건 지연"
            return parts.joinToString(" · ").ifBlank { "${total}건" }
        }
}

private fun groupRecentLogsByApp(logs: List<NotifLogEntry>): List<NotifAppFolder> {
    return logs
        .groupBy { it.packageName }
        .map { (pkg, items) ->
            val display = AppNameMapper.toDisplayName(pkg).let { name ->
                when {
                    name.isBlank() -> pkg.substringAfterLast('.').ifBlank { "앱" }
                    name.contains('.') -> name.substringAfterLast('.').ifBlank { "앱" }
                    else -> name
                }
            }
            NotifAppFolder(
                packageName = pkg,
                displayName = display,
                delivered = items.count { it.status == "delivered" || it.status == "completed" },
                blocked = items.count { it.status == "blocked" },
                stored = items.count { it.status == "stored" },
                total = items.size,
                latestCreatedAt = items.maxOfOrNull { it.createdAt } ?: 0L,
                items = items.sortedByDescending { it.createdAt },
            )
        }
        .sortedByDescending { it.latestCreatedAt }
}

private fun folderAccentColor(packageName: String, displayName: String): Color {
    val key = (displayName.ifBlank { packageName }).lowercase()
    return when {
        key.contains("카카오") || key.contains("kakao") -> AppColors.Primary
        key.contains("인스타") || key.contains("instagram") -> AppColors.Orange
        key.contains("슬랙") || key.contains("slack") -> Color(0xFF33A68C)
        else -> {
            val palette = listOf(
                AppColors.Primary,
                AppColors.Orange,
                Color(0xFF33A68C),
                AppColors.Blue,
                Color(0xFFC2185B),
            )
            palette[(packageName.ifBlank { displayName }.hashCode().and(0x7fffffff)) % palette.size]
        }
    }
}

@Composable
private fun NotifFolderSection(
    folder: NotifAppFolder,
    expanded: Boolean,
    ruleHint: String,
    openAI: OpenAI,
    onToggle: () -> Unit,
) {
    val accent = folderAccentColor(folder.packageName, folder.displayName)
    val headerBg = if (expanded) Color(0xFFEDE8FF) else Color.White.copy(alpha = 0.92f)
    val headerShape = if (expanded) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    } else {
        RoundedCornerShape(14.dp)
    }

    var summary by remember(folder.packageName) { mutableStateOf<String?>(null) }
    var loading by remember(folder.packageName) { mutableStateOf(false) }
    var summarizedKey by remember(folder.packageName) { mutableStateOf<String?>(null) }
    // v3: 문장형 요약 프롬프트 — 캐시 무효화
    val itemsKey = remember(folder.items) { "v3:" + folder.items.map { it.id }.joinToString(",") }

    LaunchedEffect(expanded, folder.packageName, itemsKey) {
        if (!expanded) return@LaunchedEffect
        if (summarizedKey == itemsKey && summary != null) return@LaunchedEffect
        if (loading) return@LaunchedEffect
        loading = true
        summary = NotifAiSummarizer.summarizeAppNotifications(
            openAI = openAI,
            appDisplayName = folder.displayName,
            items = folder.items,
        )
        summarizedKey = itemsKey
        loading = false
    }

    Column(modifier = ComposeModifier.fillMaxWidth()) {
        Row(
            modifier = ComposeModifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(headerShape)
                .background(headerBg)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = ComposeModifier
                    .padding(vertical = 12.dp)
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(modifier = ComposeModifier.width(12.dp))
            Column(modifier = ComposeModifier.weight(1f)) {
                Text(
                    text = folder.displayName,
                    color = Color(0xFF24262B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = ComposeModifier.height(2.dp))
                Text(
                    text = folder.summaryLabel,
                    color = Color(0xFF80808C),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded) {
                Text(
                    text = "▼",
                    color = AppColors.Primary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = ComposeModifier.padding(end = 6.dp),
                )
            }
            Box(
                modifier = ComposeModifier
                    .padding(end = 12.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${folder.total}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(Color(0xFFF5F2FF))
                    .shadow(
                        elevation = 0.dp,
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                        ambientColor = Color(0x0F5F33E1),
                        spotColor = Color(0x0F5F33E1),
                    )
                    .padding(bottom = 12.dp),
            ) {
                // AI 요약 헤더
                Row(
                    modifier = ComposeModifier
                        .fillMaxWidth()
                        .background(AppColors.Primary.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("✦", color = AppColors.Primary, fontSize = 11.sp)
                    Spacer(modifier = ComposeModifier.width(6.dp))
                    Text(
                        text = "AI 알림 요약",
                        color = AppColors.Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                when {
                    loading -> {
                        Row(
                            modifier = ComposeModifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = ComposeModifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AppColors.Primary,
                            )
                            Text(
                                text = "알림을 요약하는 중…",
                                color = Color(0xFF474754),
                                fontSize = 11.sp,
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = summary.orEmpty(),
                            color = Color(0xFF474754),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = ComposeModifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                Box(
                    modifier = ComposeModifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.Primary.copy(alpha = 0.12f))
                )

                Spacer(modifier = ComposeModifier.height(8.dp))

                // 목록은 최근 상위 3개만 (요약은 전체 items로 LLM 호출)
                folder.items.take(3).forEach { entry ->
                    NotifSummaryItemRow(entry = entry)
                    Spacer(modifier = ComposeModifier.height(6.dp))
                }

                Text(
                    text = ruleHint,
                    color = Color(0xFF8C8C99),
                    fontSize = 9.sp,
                    modifier = ComposeModifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun NotifSummaryItemRow(entry: NotifLogEntry) {
    data class StatusUi(
        val badgeBg: Color,
        val badgeFg: Color,
        val badgeLabel: String,
        val dotColor: Color,
        val titleColor: Color,
    )
    val ui = when (entry.status) {
        "completed", "delivered" -> StatusUi(
            Color(0xFFE0F5E5), Color(0xFF268C4D), "전송됨", Color(0xFF268C4D), Color(0xFF24262B),
        )
        else -> StatusUi(
            Color(0xFFEDE8FF), Color(0xFF5F33E1), "지연됨", Color(0xFF5F33E1), Color(0xFF24262B),
        )
    }

    val line = buildString {
        val t = entry.title.trim()
        val b = entry.text.trim()
        when {
            t.isNotEmpty() && b.isNotEmpty() -> append("$t — $b")
            t.isNotEmpty() -> append(t)
            b.isNotEmpty() -> append(b)
            else -> append("(내용 없음)")
        }
    }

    Row(
        modifier = ComposeModifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = ComposeModifier
                .size(6.dp)
                .clip(CircleShape)
                .background(ui.dotColor)
        )
        Spacer(modifier = ComposeModifier.width(8.dp))
        Text(
            text = line,
            color = ui.titleColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = ComposeModifier.weight(1f),
        )
        Spacer(modifier = ComposeModifier.width(8.dp))
        Box(
            modifier = ComposeModifier
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ui.badgeBg)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ui.badgeLabel,
                color = ui.badgeFg,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
