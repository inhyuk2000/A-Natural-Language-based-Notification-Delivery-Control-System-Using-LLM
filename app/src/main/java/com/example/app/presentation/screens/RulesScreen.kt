package com.example.app.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.AppNameMapper
import com.example.app.ContextManagerEntry
import com.example.app.RecurrenceWindow
import com.example.app.TimeUtils
import com.example.app.presentation.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

enum class RulesFilter { ALL, ACTIVE, INACTIVE, EXPIRED }

enum class RuleStatus { ACTIVE, INACTIVE, EXPIRED }

fun ContextManagerEntry.status(nowMillis: Long = System.currentTimeMillis()): RuleStatus {
    if (isCompleted) return RuleStatus.EXPIRED
    val timeUtils = TimeUtils()
    return try {
        val deliveryMs = timeUtils.parseIsoToMillis(delivery)
        val expiresMs = timeUtils.parseIsoToMillis(expires)
        when {
            // 반복 규칙은 시간만으로 완료되지 않음 (회차 롤링 전/후에도 예약됨)
            nowMillis >= expiresMs && isRecurring -> RuleStatus.INACTIVE
            nowMillis >= expiresMs -> RuleStatus.EXPIRED
            nowMillis < deliveryMs -> RuleStatus.INACTIVE
            else -> RuleStatus.ACTIVE
        }
    } catch (_: Exception) {
        RuleStatus.ACTIVE
    }
}

data class RuleEditDraft(
    val appsText: String,
    val keywordsText: String,
    /** 저장 시점 기준 N분 뒤 → delivery */
    val startMinutesAfter: Int,
    /** 저장 시점 기준 N분 뒤 → expires */
    val endMinutesAfter: Int,
    val title: String,
)

@Composable
fun RulesScreen(
    rules: List<ContextManagerEntry>,
    filter: RulesFilter,
    onFilterChange: (RulesFilter) -> Unit,
    onSaveRule: (ruleId: Int, draft: RuleEditDraft) -> Unit,
    onDeleteRule: (ruleId: Int) -> Unit,
) {
    var expandedId by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = ComposeModifier
            .fillMaxSize()
            .background(AppColors.Bg)
    ) {
        Column(modifier = ComposeModifier.fillMaxSize()) {
            Text(
                text = "규칙 목록",
                color = AppColors.Black,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 20.dp, bottom = 16.dp)
            )

            Row(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("전체", filter == RulesFilter.ALL) { onFilterChange(RulesFilter.ALL) }
                FilterChip("진행중", filter == RulesFilter.ACTIVE) { onFilterChange(RulesFilter.ACTIVE) }
                FilterChip("예약됨", filter == RulesFilter.INACTIVE) { onFilterChange(RulesFilter.INACTIVE) }
                FilterChip("완료", filter == RulesFilter.EXPIRED) { onFilterChange(RulesFilter.EXPIRED) }
            }

            LazyColumn(
                modifier = ComposeModifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (rules.isEmpty()) {
                    item {
                        Text("등록된 규칙이 없습니다", color = AppColors.Secondary, fontSize = 13.sp)
                    }
                } else {
                    items(rules, key = { it.id }) { rule ->
                        val expanded = expandedId == rule.id
                        RuleCardRow(
                            rule = rule,
                            expanded = expanded,
                            onToggleExpand = {
                                expandedId = if (expanded) null else rule.id
                            },
                            onSave = { draft ->
                                onSaveRule(rule.id, draft)
                                expandedId = null
                            },
                            onDelete = {
                                onDeleteRule(rule.id)
                                expandedId = null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = ComposeModifier
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) AppColors.Primary else AppColors.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.White else AppColors.Primary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun RuleCardRow(
    rule: ContextManagerEntry,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSave: (RuleEditDraft) -> Unit,
    onDelete: () -> Unit,
) {
    val status = rule.status()
    val appsLabel = formatAppsLabel(rule.name)
    val title = rule.activity
        .takeIf { it.isNotBlank() && !it.equals("null", true) }
        ?: when {
            rule.content.isNotBlank() -> rule.content.split(",").first().trim() + " 관련 알림"
            else -> "알림 규칙 #${rule.id}"
        }
    val recurLabel = RecurrenceWindow.recurrenceLabel(rule.recurrence, rule.daysOfWeek)
    val windowLabel = if (rule.windowStart.isNotBlank() && rule.windowEnd.isNotBlank()) {
        "${rule.windowStart}–${rule.windowEnd}"
    } else {
        ""
    }
    val filterLabel = when {
        rule.isRecurring && (recurLabel.isNotBlank() || windowLabel.isNotBlank()) ->
            listOfNotNull(
                recurLabel.takeIf { it.isNotBlank() }?.let { "반복: $it" },
                windowLabel.takeIf { it.isNotBlank() },
                rule.content.takeIf { it.isNotBlank() }?.let { "필터: ${it.replace(",", ", ")}" },
            ).joinToString(" · ")
        rule.content.isNotBlank() -> "수신필터: " + rule.content.replace(",", ", ")
        rule.delivery.isNotBlank() && rule.expires.isNotBlank() ->
            "시간대: ${shortTime(rule.delivery)} ~ ${shortTime(rule.expires)}"
        else -> "지연 수신 규칙"
    }

    val (badgeBg, badgeFg, badgeText) = when {
        status == RuleStatus.ACTIVE -> Triple(AppColors.CardPurple, AppColors.Primary, "진행중")
        status == RuleStatus.INACTIVE -> Triple(AppColors.CardOrange, AppColors.Orange, "예약됨")
        else -> Triple(AppColors.CardBlue, AppColors.Blue, "완료")
    }

    val cardBg = if (expanded) AppColors.CardPurple else AppColors.White

    Column(
        modifier = ComposeModifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(15.dp), spotColor = Color(0x0A000000))
            .clip(RoundedCornerShape(15.dp))
            .background(cardBg)
    ) {
        Row(
            modifier = ComposeModifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = ComposeModifier.weight(1f)) {
                Text(
                    text = appsLabel,
                    color = AppColors.Secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = ComposeModifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = AppColors.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = ComposeModifier.weight(1f),
                    )
                    Box(
                        modifier = ComposeModifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(badgeBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(badgeText, color = badgeFg, fontSize = 9.sp)
                    }
                    if (expanded) {
                        Spacer(modifier = ComposeModifier.width(6.dp))
                        Text("▼", color = AppColors.Primary, fontSize = 10.sp)
                    }
                }
                Spacer(modifier = ComposeModifier.height(6.dp))
                Text(
                    text = "⏱  $filterLabel",
                    color = Color(0xFFAB94FF),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            RuleEditPanel(
                rule = rule,
                defaultTitle = title,
                onSave = onSave,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun RuleEditPanel(
    rule: ContextManagerEntry,
    defaultTitle: String,
    onSave: (RuleEditDraft) -> Unit,
    onDelete: () -> Unit,
) {
    var appsText by remember(rule.id) {
        mutableStateOf(packagesToDisplayCsv(rule.name))
    }
    var keywordsText by remember(rule.id) {
        mutableStateOf(rule.content.replace(",", ", ").trim())
    }
    var startMinutesText by remember(rule.id) {
        mutableStateOf(minutesFromNowLabel(rule.delivery))
    }
    var endMinutesText by remember(rule.id) {
        mutableStateOf(minutesFromNowLabel(rule.expires))
    }

    LaunchedEffect(rule.id, rule.name, rule.content, rule.delivery, rule.expires) {
        appsText = packagesToDisplayCsv(rule.name)
        keywordsText = rule.content.replace(",", ", ").trim()
        startMinutesText = minutesFromNowLabel(rule.delivery)
        endMinutesText = minutesFromNowLabel(rule.expires)
    }

    Column(
        modifier = ComposeModifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .shadow(4.dp, RoundedCornerShape(15.dp), spotColor = Color(0x145F33E1))
            .border(1.5.dp, Color(0x4D5F33E1), RoundedCornerShape(15.dp))
            .background(AppColors.White, RoundedCornerShape(15.dp))
            .padding(14.dp),
    ) {
        // Figma 1147:267 — 대상 앱
        EditFieldLabel("대상 앱")
        EditTextBox(
            value = appsText,
            onValueChange = { appsText = it },
            placeholder = "예: 카카오톡, 인스타그램 (비우면 전체)",
        )
        Spacer(modifier = ComposeModifier.height(8.dp))

        // 수신 필터 키워드
        EditFieldLabel("수신 필터 키워드")
        EditTextBox(
            value = keywordsText,
            onValueChange = { keywordsText = it },
            placeholder = "예: 게임, 리그, 길드전",
        )
        Spacer(modifier = ComposeModifier.height(8.dp))

        MinutesAfterRow(
            label = "시작 시간",
            value = startMinutesText,
            onValueChange = { startMinutesText = filterDigits(it) },
        )
        Spacer(modifier = ComposeModifier.height(6.dp))
        MinutesAfterRow(
            label = "종료 시간",
            value = endMinutesText,
            onValueChange = { endMinutesText = filterDigits(it) },
        )
        Spacer(modifier = ComposeModifier.height(10.dp))

        Row(
            modifier = ComposeModifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = ComposeModifier
                    .weight(1f)
                    .height(22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Primary)
                    .clickable {
                        val startMin = startMinutesText.toIntOrNull() ?: 0
                        val endMin = max(endMinutesText.toIntOrNull() ?: 0, startMin)
                        onSave(
                            RuleEditDraft(
                                appsText = appsText,
                                keywordsText = keywordsText,
                                startMinutesAfter = startMin.coerceAtLeast(0),
                                endMinutesAfter = endMin.coerceAtLeast(0),
                                title = defaultTitle,
                            )
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("저장", color = AppColors.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = ComposeModifier
                    .weight(1f)
                    .height(22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFE3E0))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Text("삭제", color = Color(0xFFE53333), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MinutesAfterRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = Color(0xFF6E6B7D),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = ComposeModifier.width(64.dp),
        )
        Box(
            modifier = ComposeModifier
                .width(60.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFF5F2FF)),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = AppColors.Black,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(AppColors.Primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            )
        }
        Spacer(modifier = ComposeModifier.width(6.dp))
        Text(
            text = "분 뒤",
            color = Color(0xFF6E6B7D),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EditFieldLabel(text: String) {
    Text(
        text = text,
        color = Color(0xFF6E6B7D),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = ComposeModifier.height(4.dp))
}

@Composable
private fun EditTextBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: ComposeModifier = ComposeModifier.fillMaxWidth(),
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F2FF))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = AppColors.Black, fontSize = 11.sp),
            cursorBrush = SolidColor(AppColors.Primary),
            modifier = ComposeModifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, color = AppColors.Secondary, fontSize = 11.sp)
                }
                inner()
            },
        )
    }
}

private fun formatAppsLabel(nameField: String): String {
    if (nameField.isBlank()) return "수신할 앱: 전체"
    val displays = packagesToDisplayList(nameField)
    return "수신할 앱: " + displays.joinToString(", ")
}

private fun packagesToDisplayList(nameField: String): List<String> {
    if (nameField.isBlank()) return emptyList()
    return nameField.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { pkg -> if (pkg.contains(".")) AppNameMapper.toDisplayName(pkg) else pkg }
}

private fun packagesToDisplayCsv(nameField: String): String =
    packagesToDisplayList(nameField).joinToString(", ")

private fun shortTime(iso: String): String {
    return try {
        val t = iso.substringAfter("T", iso)
        t.take(5)
    } catch (_: Exception) {
        iso.take(16)
    }
}

/** 기존 delivery/expires → 현재 시각 기준 'N분 뒤' 기본값 (이미 지났으면 00) */
fun minutesFromNowLabel(iso: String, nowMillis: Long = System.currentTimeMillis()): String {
    if (iso.isBlank()) return "00"
    return try {
        val target = TimeUtils().parseIsoToMillis(iso)
        val mins = ((target - nowMillis) / 60_000.0).roundToLong().coerceAtLeast(0L)
        mins.toString().padStart(2, '0')
    } catch (_: Exception) {
        "00"
    }
}

private fun filterDigits(raw: String): String =
    raw.filter { it.isDigit() }.take(5)

fun formatRuleIso(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
    return sdf.format(Date(millis))
}
