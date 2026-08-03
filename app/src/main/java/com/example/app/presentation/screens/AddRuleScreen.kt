package com.example.app.presentation.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.R
import com.example.app.presentation.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val TagColors = listOf(
    Color(0xFFFFE08A) to Color(0xFF8A6A00),
    Color(0xFFD6EBFF) to AppColors.Blue,
    Color(0xFFFFD6E8) to Color(0xFFC2185B),
    Color(0xFFEDE4FF) to AppColors.Primary,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddRuleScreen(
    availableApps: List<String>,
    errorMessage: String?,
    onBack: () -> Unit,
    onSubmit: (
        title: String,
        apps: List<String>,
        tags: List<String>,
        startMillis: Long,
        endMillis: Long,
    ) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var appsExpanded by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }

    val dateTimeFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val dateOnlyFmt = remember { SimpleDateFormat("yyyy. M. d.", Locale.getDefault()) }
    val timeOnlyFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var startCal by remember { mutableStateOf(Calendar.getInstance()) }
    var endCal by remember {
        mutableStateOf(Calendar.getInstance().apply { add(Calendar.MINUTE, 10) })
    }
    var startExpanded by remember { mutableStateOf(false) }
    var endExpanded by remember { mutableStateOf(false) }

    val filteredApps = remember(availableApps, appQuery, selectedApps) {
        val q = appQuery.trim()
        availableApps
            .filter { it !in selectedApps }
            .filter { q.isEmpty() || it.contains(q, ignoreCase = true) }
    }

    fun pickDate(current: Calendar, onPicked: (Calendar) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onPicked(
                    (current.clone() as Calendar).apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, day)
                    }
                )
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun pickTime(current: Calendar, onPicked: (Calendar) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                onPicked(
                    (current.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                )
            },
            current.get(Calendar.HOUR_OF_DAY),
            current.get(Calendar.MINUTE),
            true,
        ).show()
    }

    fun relativeFromNow(minutes: Int): Calendar =
        Calendar.getInstance().apply {
            add(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    Box(
        modifier = ComposeModifier
            .fillMaxSize()
            .background(AppColors.Bg)
    ) {
        Column(modifier = ComposeModifier.fillMaxSize()) {
            Row(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp)
                    .padding(top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "←",
                    fontSize = 22.sp,
                    color = AppColors.Black,
                    modifier = ComposeModifier
                        .clickable(onClick = onBack)
                        .padding(12.dp)
                )
                Text(
                    text = "규칙 추가",
                    color = AppColors.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = ComposeModifier.weight(1f)
                )
                Spacer(modifier = ComposeModifier.size(46.dp))
            }

            Column(
                modifier = ComposeModifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 8.dp)
            ) {
                FormCard {
                    Text("규칙 이름", color = AppColors.Secondary, fontSize = 11.sp)
                    Spacer(modifier = ComposeModifier.height(6.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = ComposeModifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                "게임 집중 및 긴급 수신 규칙",
                                color = AppColors.Secondary,
                                fontSize = 14.sp
                            )
                        },
                        colors = fieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = ComposeModifier.height(12.dp))
                FormCard {
                    Row(
                        modifier = ComposeModifier
                            .fillMaxWidth()
                            .clickable { appsExpanded = !appsExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = ComposeModifier.weight(1f)) {
                            Text(
                                "수신할 앱",
                                color = AppColors.Secondary,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = ComposeModifier.height(2.dp))
                            Text(
                                text = when {
                                    selectedApps.isEmpty() -> "미선택 = 전체 · 탭해서 앱 고르기"
                                    else -> "${selectedApps.size}개 선택됨"
                                },
                                color = AppColors.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Text(
                            text = if (appsExpanded) "접기" else "펼치기",
                            color = AppColors.Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (selectedApps.isNotEmpty()) {
                        Spacer(modifier = ComposeModifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedApps.forEach { app ->
                                Box(
                                    modifier = ComposeModifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(AppColors.Primary)
                                        .clickable { selectedApps = selectedApps - app }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(app, color = AppColors.White, fontSize = 12.sp)
                                }
                            }
                        }
                        Text(
                            "선택된 앱을 탭하면 해제",
                            color = AppColors.Secondary,
                            fontSize = 10.sp,
                            modifier = ComposeModifier.padding(top = 6.dp),
                        )
                    }

                    AnimatedVisibility(
                        visible = appsExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            Spacer(modifier = ComposeModifier.height(12.dp))
                            OutlinedTextField(
                                value = appQuery,
                                onValueChange = { appQuery = it },
                                modifier = ComposeModifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = {
                                    Text("앱 이름 검색", fontSize = 13.sp, color = AppColors.Secondary)
                                },
                                colors = fieldColors(),
                                shape = RoundedCornerShape(12.dp),
                            )
                            Spacer(modifier = ComposeModifier.height(10.dp))
                            if (filteredApps.isEmpty()) {
                                Text(
                                    if (appQuery.isBlank()) "선택할 앱이 없습니다" else "검색 결과가 없습니다",
                                    color = AppColors.Secondary,
                                    fontSize = 12.sp,
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    filteredApps.forEach { app ->
                                        Box(
                                            modifier = ComposeModifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(AppColors.Badge)
                                                .clickable { selectedApps = selectedApps + app }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(app, color = AppColors.Primary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = ComposeModifier.height(12.dp))
                FormCard {
                    Text(
                        "수신할 텍스트 필터 (태그 등록)",
                        color = AppColors.Secondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = ComposeModifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = tagInput,
                            onValueChange = { tagInput = it },
                            modifier = ComposeModifier.weight(1f),
                            singleLine = true,
                            placeholder = {
                                Text("키워드 입력 후 추가", fontSize = 13.sp, color = AppColors.Secondary)
                            },
                            colors = fieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = ComposeModifier.size(8.dp))
                        Text(
                            text = "추가",
                            color = AppColors.Primary,
                            fontWeight = FontWeight.Bold,
                            modifier = ComposeModifier
                                .clickable {
                                    val t = tagInput.trim().removePrefix("#")
                                    if (t.isNotEmpty() && t !in tags) {
                                        tags = tags + t
                                        tagInput = ""
                                    }
                                }
                                .padding(8.dp)
                        )
                    }
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = ComposeModifier.height(12.dp))
                        Row(
                            modifier = ComposeModifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEachIndexed { index, tag ->
                                val (bg, fg) = TagColors[index % TagColors.size]
                                Box(
                                    modifier = ComposeModifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bg)
                                        .clickable { tags = tags - tag }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("#$tag", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Text("탭하면 삭제", color = AppColors.Secondary, fontSize = 10.sp)
                    }
                }

                Spacer(modifier = ComposeModifier.height(12.dp))
                ConditionTimeCard(
                    label = "조건 시작",
                    valueText = dateTimeFmt.format(startCal.time),
                    dateChip = dateOnlyFmt.format(startCal.time),
                    timeChip = timeOnlyFmt.format(startCal.time),
                    expanded = startExpanded,
                    onToggle = {
                        startExpanded = !startExpanded
                        if (startExpanded) endExpanded = false
                    },
                    onPickDate = { pickDate(startCal) { startCal = it } },
                    onPickTime = { pickTime(startCal) { startCal = it } },
                    quickPresets = listOf(
                        "지금" to {
                            startCal = Calendar.getInstance().apply {
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                        },
                    ),
                )
                Spacer(modifier = ComposeModifier.height(10.dp))
                ConditionTimeCard(
                    label = "조건 종료",
                    valueText = dateTimeFmt.format(endCal.time),
                    dateChip = dateOnlyFmt.format(endCal.time),
                    timeChip = timeOnlyFmt.format(endCal.time),
                    expanded = endExpanded,
                    onToggle = {
                        endExpanded = !endExpanded
                        if (endExpanded) startExpanded = false
                    },
                    onPickDate = { pickDate(endCal) { endCal = it } },
                    onPickTime = { pickTime(endCal) { endCal = it } },
                    quickPresets = listOf(
                        "+10분" to { endCal = relativeFromNow(minutes = 10) },
                        "+30분" to { endCal = relativeFromNow(minutes = 30) },
                        "+1시간" to { endCal = relativeFromNow(minutes = 60) },
                    ),
                )

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = ComposeModifier.height(10.dp))
                    Text(errorMessage, color = Color(0xFFD32F2F), fontSize = 13.sp)
                }

                Spacer(modifier = ComposeModifier.height(20.dp))
            }

            Button(
                onClick = {
                    onSubmit(
                        title,
                        selectedApps.toList(),
                        tags,
                        startCal.timeInMillis,
                        endCal.timeInMillis,
                    )
                },
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 100.dp, top = 8.dp)
                    .height(52.dp)
                    .shadow(6.dp, RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Text(
                    "규칙 등록",
                    color = AppColors.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ConditionTimeCard(
    label: String,
    valueText: String,
    dateChip: String,
    timeChip: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    quickPresets: List<Pair<String, () -> Unit>>,
) {
    val chevronRot by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "chevron",
    )

    Column(
        modifier = ComposeModifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(15.dp), spotColor = Color(0x0A000000))
            .clip(RoundedCornerShape(15.dp))
            .background(AppColors.White)
    ) {
        Row(
            modifier = ComposeModifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_nav_calendar),
                contentDescription = null,
                modifier = ComposeModifier.size(24.dp),
                colorFilter = ColorFilter.tint(AppColors.Primary),
            )
            Spacer(modifier = ComposeModifier.width(12.dp))
            Column(modifier = ComposeModifier.weight(1f)) {
                Text(label, color = AppColors.Secondary, fontSize = 9.sp)
                Spacer(modifier = ComposeModifier.height(3.dp))
                Text(
                    text = valueText,
                    color = AppColors.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = "▼",
                color = AppColors.Primary,
                fontSize = 11.sp,
                modifier = ComposeModifier.rotate(chevronRot),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8F5FF))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "날짜와 시간을 따로 바꿀 수 있어요",
                    color = AppColors.Secondary,
                    fontSize = 11.sp,
                )
                Spacer(modifier = ComposeModifier.height(10.dp))
                Row(
                    modifier = ComposeModifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeEditChip(
                        caption = "날짜",
                        value = dateChip,
                        modifier = ComposeModifier.weight(1f),
                        onClick = onPickDate,
                    )
                    TimeEditChip(
                        caption = "시간",
                        value = timeChip,
                        modifier = ComposeModifier.weight(1f),
                        onClick = onPickTime,
                    )
                }
                if (quickPresets.isNotEmpty()) {
                    Spacer(modifier = ComposeModifier.height(10.dp))
                    Row(
                        modifier = ComposeModifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quickPresets.forEach { (labelText, action) ->
                            Box(
                                modifier = ComposeModifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppColors.Primary.copy(alpha = 0.1f))
                                    .clickable(onClick = action)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = labelText,
                                    color = AppColors.Primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeEditChip(
    caption: String,
    value: String,
    modifier: ComposeModifier = ComposeModifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.White)
            .border(1.dp, AppColors.Primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(caption, color = AppColors.Secondary, fontSize = 10.sp)
        Spacer(modifier = ComposeModifier.height(4.dp))
        Text(
            text = value,
            color = AppColors.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Column(
        modifier = ComposeModifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.White)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AppColors.InputBg,
    unfocusedContainerColor = AppColors.InputBg,
    focusedBorderColor = AppColors.Border,
    unfocusedBorderColor = AppColors.Border,
    cursorColor = AppColors.Primary
)
