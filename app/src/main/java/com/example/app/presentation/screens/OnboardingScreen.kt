package com.example.app.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.R
import com.example.app.presentation.theme.AppColors

/**
 * Figma 온보딩 캐러셀 3장 (스와이프만, 다음 버튼 없음)
 * 1) 알림 제어 시스템 소개
 * 2) 필수 설정 안내
 * 3) 별명 입력 → 시작하기
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: (nickname: String) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    var nickname by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = ComposeModifier
            .fillMaxSize()
            .background(AppColors.White),
    ) {
        OnboardingGlowBackground()

        Column(
            modifier = ComposeModifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = ComposeModifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = true,
            ) { page ->
                when (page) {
                    0 -> PageIntro()
                    1 -> PageSetupGuide()
                    else -> PageNickname(
                        nickname = nickname,
                        error = error,
                        onNicknameChange = {
                            nickname = it
                            if (error != null) error = null
                        },
                        onStart = {
                            val trimmed = nickname.trim()
                            if (trimmed.isEmpty()) {
                                error = "별명을 입력해주세요"
                            } else {
                                onFinished(trimmed)
                            }
                        },
                    )
                }
            }

            // Figma: dots + hint only (no Next button)
            Column(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        val active = pagerState.currentPage == index
                        Box(
                            modifier = ComposeModifier
                                .height(8.dp)
                                .width(if (active) 24.dp else 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (active) AppColors.Primary else Color(0xFFD0D0D8),
                                ),
                        )
                    }
                }
                Spacer(modifier = ComposeModifier.height(8.dp))
                Text(
                    text = when (pagerState.currentPage) {
                        0 -> "밀어서 다음으로"
                        1 -> "밀어서 다음으로"
                        else -> "3/3"
                    },
                    color = AppColors.Secondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun OnboardingGlowBackground() {
    Box(
        modifier = ComposeModifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFF8F7FC), Color(0xFFFFFFFF)),
                ),
            ),
    ) {
        Box(
            modifier = ComposeModifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = 200.dp)
                .size(118.dp)
                .clip(CircleShape)
                .background(AppColors.Primary.copy(alpha = 0.10f)),
        )
        Box(
            modifier = ComposeModifier
                .align(Alignment.CenterStart)
                .offset(x = (-48).dp, y = 120.dp)
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFFB8F0C8).copy(alpha = 0.22f)),
        )
        Box(
            modifier = ComposeModifier
                .align(Alignment.TopStart)
                .offset(x = 48.dp, y = 180.dp)
                .size(74.dp)
                .clip(CircleShape)
                .background(AppColors.CardPurple.copy(alpha = 0.45f)),
        )
        Box(
            modifier = ComposeModifier
                .align(Alignment.BottomCenter)
                .offset(y = (-40).dp)
                .size(58.dp)
                .clip(CircleShape)
                .background(AppColors.Primary.copy(alpha = 0.06f)),
        )
    }
}

/** Page 1 — Screen 1 Onboarding (Figma 1104:179) */
@Composable
private fun PageIntro() {
    Column(
        modifier = ComposeModifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = ComposeModifier.height(76.dp))
        Box(
            modifier = ComposeModifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.onboarding_page1),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = ComposeModifier.fillMaxSize(),
            )
        }
        Spacer(modifier = ComposeModifier.height(36.dp))
        Text(
            text = "알림 제어 시스템",
            color = AppColors.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = ComposeModifier.height(12.dp))
        Text(
            text = "자연어로 스마트폰 알림을\n간편하게 제어하세요",
            color = AppColors.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
        Spacer(modifier = ComposeModifier.height(14.dp))
        Text(
            text = "복잡한 설정 없이 자연어 명령 한 마디로\n알림 수신 조건을 설정하고 관리할 수 있습니다.",
            color = AppColors.Secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
    }
}

/** Page 2 — option-b-setup-guide (Figma 1150:328) */
@Composable
private fun PageSetupGuide() {
    Column(
        modifier = ComposeModifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text = "필수 설정 안내",
            color = AppColors.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = ComposeModifier.height(8.dp))
        Text(
            text = "앱을 사용하기 전에 아래 설정을 완료해주세요.",
            color = AppColors.Secondary,
            fontSize = 14.sp,
        )
        Spacer(modifier = ComposeModifier.height(20.dp))

        Column(
            modifier = ComposeModifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = Color(0x125F33E1))
                .border(1.dp, AppColors.Primary.copy(alpha = 0.10f), RoundedCornerShape(24.dp))
                .background(AppColors.White, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "설정 흐름도",
                color = AppColors.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = ComposeModifier.fillMaxWidth(),
            )
            Spacer(modifier = ComposeModifier.height(12.dp))

            FlowStep(title = "휴대폰 설정", subtitle = "기본 시스템 설정 앱")
            FlowArrow()
            FlowStep(title = "알림", subtitle = "앱 및 장치 알림 제어")
            FlowArrow()
            FlowStep(title = "고급 설정", subtitle = "추가 옵션 제어")
            FlowArrow()
            FlowStepHighlighted(
                title = "앱 아이콘을 알림에 표시",
                subtitle = "해당 스위치를 비활성화(OFF)",
            )
        }

        Spacer(modifier = ComposeModifier.height(16.dp))
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
}

@Composable
private fun FlowStep(title: String, subtitle: String) {
    Column(
        modifier = ComposeModifier
            .fillMaxWidth()
            .border(1.5.dp, Color(0xFFE2E2E9), RoundedCornerShape(12.dp))
            .background(AppColors.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, color = AppColors.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = ComposeModifier.height(2.dp))
        Text(subtitle, color = AppColors.Secondary, fontSize = 11.sp)
    }
}

@Composable
private fun FlowStepHighlighted(title: String, subtitle: String) {
    Row(
        modifier = ComposeModifier
            .fillMaxWidth()
            .border(1.5.dp, AppColors.Primary.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .background(AppColors.Nav, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = ComposeModifier.weight(1f)) {
            Text(title, color = AppColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = ComposeModifier.height(2.dp))
            Text(subtitle, color = AppColors.Secondary, fontSize = 11.sp)
        }
        // Toggle OFF visual
        Box(
            modifier = ComposeModifier
                .width(36.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFD0D0D8)),
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
}

@Composable
private fun FlowArrow() {
    Text(
        text = "↓",
        color = AppColors.Primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = ComposeModifier.padding(vertical = 4.dp),
    )
}

/** Page 3 — nickname (Figma 1197:389) */
@Composable
private fun PageNickname(
    nickname: String,
    error: String?,
    onNicknameChange: (String) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = ComposeModifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = ComposeModifier.height(48.dp))
        Box(
            modifier = ComposeModifier
                .size(64.dp)
                .shadow(8.dp, RoundedCornerShape(32.dp), spotColor = Color(0x335F33E1))
                .clip(RoundedCornerShape(32.dp))
                .background(AppColors.Primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("🔔", fontSize = 28.sp)
        }
        Spacer(modifier = ComposeModifier.height(16.dp))
        Text(
            text = "알림 제어 시스템",
            color = AppColors.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = ComposeModifier.height(6.dp))
        Text(
            text = "별명을 입력하고 시작하세요",
            color = AppColors.Secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = ComposeModifier.height(28.dp))

        Column(modifier = ComposeModifier.fillMaxWidth()) {
            Text(
                text = "별명",
                color = AppColors.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = ComposeModifier.height(8.dp))
            Box(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.InputBg)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👤", fontSize = 16.sp)
                    Spacer(modifier = ComposeModifier.width(12.dp))
                    BasicTextField(
                        value = nickname,
                        onValueChange = onNicknameChange,
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.Black, fontSize = 14.sp),
                        cursorBrush = SolidColor(AppColors.Primary),
                        modifier = ComposeModifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (nickname.isEmpty()) {
                                Text("예: 홍길동", color = AppColors.Secondary, fontSize = 14.sp)
                            }
                            inner()
                        },
                    )
                }
            }
            if (error != null) {
                Spacer(modifier = ComposeModifier.height(8.dp))
                Text(error, color = Color(0xFFE53333), fontSize = 12.sp)
            }
        }

        Spacer(modifier = ComposeModifier.height(24.dp))
        Button(
            onClick = onStart,
            modifier = ComposeModifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0x405F33E1)),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
        ) {
            Text(
                text = "시작하기",
                color = AppColors.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = ComposeModifier.height(12.dp))
        Text(
            text = "동일 디바이스에서 자동으로 인식됩니다",
            color = AppColors.Secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
