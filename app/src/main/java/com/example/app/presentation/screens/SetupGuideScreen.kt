package com.example.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.presentation.theme.AppColors

/**
 * Figma 1150:328 — 온보딩 다음, 로그인 전 「필수 설정 안내」
 * (알림 → 고급 설정 → 앱 아이콘을 알림에 표시 OFF)
 */
@Composable
fun SetupGuideScreen(
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
) {
    Box(
        modifier = ComposeModifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF8F7FC),
                        Color(0xFFEDE8FF),
                        Color(0xFFF8F7FC),
                    )
                )
            )
    ) {
        Box(
            modifier = ComposeModifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp)
                .size(260.dp)
                .clip(CircleShape)
                .background(AppColors.Primary.copy(alpha = 0.08f))
        )
        Box(
            modifier = ComposeModifier
                .align(Alignment.BottomStart)
                .padding(bottom = 120.dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(AppColors.Primary.copy(alpha = 0.06f))
        )

        Column(
            modifier = ComposeModifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = ComposeModifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "필수 설정 안내",
                        color = AppColors.Black,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "앱을 사용하기 전에 아래 설정을 완료해주세요.",
                        color = AppColors.Secondary,
                        fontSize = 14.sp,
                    )
                }

                Column(
                    modifier = ComposeModifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AppColors.Primary.copy(alpha = 0.12f))
                        .clip(RoundedCornerShape(24.dp))
                        .background(AppColors.White)
                        .border(1.dp, AppColors.Primary.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "설정 흐름도",
                        color = AppColors.Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = ComposeModifier.fillMaxWidth(),
                    )

                    FlowStep(
                        title = "휴대폰 설정",
                        subtitle = "기본 시스템 설정 앱",
                        highlighted = false,
                    )
                    FlowArrow()
                    FlowStep(
                        title = "알림",
                        subtitle = "앱 및 장치 알림 제어",
                        highlighted = false,
                    )
                    FlowArrow()
                    FlowStep(
                        title = "고급 설정",
                        subtitle = "추가 옵션 제어",
                        highlighted = false,
                    )
                    FlowArrow()
                    FlowStep(
                        title = "앱 아이콘을 알림에 표시",
                        subtitle = "해당 스위치를 비활성화(OFF)",
                        highlighted = true,
                        showToggleOff = true,
                    )
                }

                Row(
                    modifier = ComposeModifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("⚠️", fontSize = 14.sp)
                    Text(
                        text = "이 설정을 꺼야 알림 제어 시스템이 정상적으로 작동합니다.",
                        color = AppColors.Orange,
                        fontSize = 11.sp,
                        modifier = ComposeModifier.weight(1f),
                        lineHeight = 16.sp,
                    )
                }
            }

            Column(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = ComposeModifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(8.dp, RoundedCornerShape(15.dp), ambientColor = AppColors.Primary.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                ) {
                    Text(
                        text = "설정 완료",
                        color = AppColors.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "나중에 하기",
                    color = AppColors.Secondary,
                    fontSize = 14.sp,
                    modifier = ComposeModifier
                        .clickable(onClick = onSkip)
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun FlowStep(
    title: String,
    subtitle: String,
    highlighted: Boolean,
    showToggleOff: Boolean = false,
) {
    val bg = if (highlighted) Color(0xFFF4F0FF) else AppColors.White
    val border = if (highlighted) AppColors.Primary.copy(alpha = 0.3f) else Color(0xFFE2E2E9)
    val titleColor = if (highlighted) AppColors.Primary else AppColors.Black

    Row(
        modifier = ComposeModifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = ComposeModifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = ComposeModifier.height(2.dp))
            Text(
                text = subtitle,
                color = AppColors.Secondary,
                fontSize = 11.sp,
            )
        }
        if (showToggleOff) {
            ToggleOffVisual()
        }
    }
}

@Composable
private fun ToggleOffVisual() {
    Box(
        modifier = ComposeModifier
            .width(36.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(Color(0xFFD0CDD8)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = ComposeModifier
                .padding(start = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(AppColors.White),
        )
    }
}

@Composable
private fun FlowArrow() {
    Text(
        text = "▼",
        color = AppColors.Primary.copy(alpha = 0.55f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
    )
}
