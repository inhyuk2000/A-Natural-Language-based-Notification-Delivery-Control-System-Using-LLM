package com.example.app.presentation.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.presentation.theme.AppColors

@Composable
fun ChatTutorialScreen(
    isFirstTime: Boolean,
    onContinue: () -> Unit,
) {
    val ctaLabel = if (isFirstTime) "대화 시작하기" else "대화로 이동하기"

    Box(
        modifier = ComposeModifier
            .fillMaxSize()
            .background(AppColors.White)
    ) {
        // Figma blur-purple / blur-blue atmosphere
        Box(
            modifier = ComposeModifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = 48.dp)
                .size(280.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x665F33E1),
                            Color(0x335F33E1),
                            Color.Transparent,
                        )
                    ),
                    shape = CircleShape,
                )
        )
        Box(
            modifier = ComposeModifier
                .align(Alignment.CenterStart)
                .offset(x = (-100).dp, y = 80.dp)
                .size(240.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x550087FF),
                            Color(0x220087FF),
                            Color.Transparent,
                        )
                    ),
                    shape = CircleShape,
                )
        )

        Column(
            modifier = ComposeModifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = ComposeModifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 28.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "처음이신가요?",
                        color = AppColors.Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "알림 제어 챗봇 사용법",
                        color = AppColors.Black,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TutorialStepCard(
                        number = "1",
                        title = "자연어로 명령하세요",
                        body = "“카톡이랑 인스타 게임 알림만 받아줘” 처럼 일상 언어로 알림 제어 조건을 편하게 말해주세요.",
                    )
                    TutorialStepCard(
                        number = "2",
                        title = "AI가 조건을 추출해요",
                        body = "LLM이 대화를 즉시 분석해서 수신해야 할 앱, 핵심 키워드 필터, 수신 시간대를 자동으로 정밀 판별합니다.",
                    )
                    TutorialStepCard(
                        number = "3",
                        title = "알림이 자동 관리돼요",
                        body = "설정된 규칙에 딱 들어맞는 필수 알림만 안전하게 전송되고 나머지는 자동 필터링됩니다. 규칙은 언제든 챗봇에서 손쉽게 편집해 보세요.",
                    )
                }
            }

            Column(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = onContinue,
                    modifier = ComposeModifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(10.dp, RoundedCornerShape(100.dp), spotColor = Color(0x335F33E1)),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                ) {
                    Text(
                        text = ctaLabel,
                        color = AppColors.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = ComposeModifier.size(10.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = AppColors.White,
                        modifier = ComposeModifier.size(20.dp),
                    )
                }
                if (isFirstTime) {
                    Spacer(modifier = ComposeModifier.height(12.dp))
                    Text(
                        text = "이 안내는 처음 한 번만 표시됩니다",
                        color = AppColors.Secondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = ComposeModifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialStepCard(
    number: String,
    title: String,
    body: String,
) {
    Row(
        modifier = ComposeModifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = Color(0x145F33E1))
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.White)
            .border(1.dp, AppColors.Border, RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = ComposeModifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.Nav),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = AppColors.Primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(modifier = ComposeModifier.weight(1f)) {
            Text(
                text = title,
                color = AppColors.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = ComposeModifier.height(4.dp))
            Text(
                text = body,
                color = AppColors.Secondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
