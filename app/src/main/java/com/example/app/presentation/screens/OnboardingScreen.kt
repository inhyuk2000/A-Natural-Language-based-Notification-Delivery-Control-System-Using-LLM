package com.example.app.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.presentation.theme.AppColors

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(
        modifier = ComposeModifier
            .fillMaxSize()
            .background(AppColors.Bg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = ComposeModifier.height(28.dp))
        Box(
            modifier = ComposeModifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFB8F0C8), Color(0xFFDCC8FF), Color(0xFFF0E8FF))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Bubble("HELLO WORLD", AppColors.CardBlue, AppColors.Blue, -90, -70)
            Bubble("UPDATES", AppColors.CardOrange, AppColors.Orange, 85, -55)
            Bubble("NEW MESSAGE", AppColors.CardPink, Color(0xFFC2185B), -70, 55)
            Bubble("ALERT", AppColors.CardPurple, AppColors.Primary, 80, 70)
            Box(
                modifier = ComposeModifier
                    .width(120.dp)
                    .height(160.dp)
                    .shadow(10.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(AppColors.White),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📱", fontSize = 42.sp)
                    Spacer(modifier = ComposeModifier.height(8.dp))
                    Text(
                        "Agentnotif",
                        color = AppColors.Primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = ComposeModifier.height(36.dp))
        Text(
            text = "알림 제어 시스템",
            color = AppColors.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = ComposeModifier.height(12.dp))
        Text(
            text = "자연어로 스마트폰 알림을\n간편하게 제어하세요",
            color = AppColors.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = ComposeModifier.height(14.dp))
        Text(
            text = "복잡한 설정 없이 자연어 명령 한 마디로\n알림 수신 조건을 설정하고 관리할 수 있습니다.",
            color = AppColors.Secondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
        Spacer(modifier = ComposeModifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = ComposeModifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(4.dp, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) {
            Text(
                text = "시작하기",
                color = AppColors.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun Bubble(
    text: String,
    bg: Color,
    fg: Color,
    x: Int,
    y: Int,
) {
    Box(
        modifier = ComposeModifier
            .offset(x.dp, y.dp)
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
