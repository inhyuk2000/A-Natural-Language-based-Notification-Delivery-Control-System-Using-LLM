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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.presentation.theme.AppColors

@Composable
fun LoginScreen(
    isRegisterMode: Boolean,
    errorMessage: String?,
    onBack: (() -> Unit)?,
    onToggleMode: () -> Unit,
    onSubmit: (email: String, password: String, displayName: String) -> Unit,
    onSocialClick: (provider: String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Box(
        modifier = ComposeModifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8F7FC), Color(0xFFEDE8FF), Color(0xFFF8F7FC))
                )
            )
    ) {
        // soft blobs
        Box(
            modifier = ComposeModifier
                .align(Alignment.TopEnd)
                .padding(top = 80.dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(AppColors.Primary.copy(alpha = 0.08f))
        )
        Box(
            modifier = ComposeModifier
                .align(Alignment.BottomStart)
                .padding(bottom = 120.dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(AppColors.Blue.copy(alpha = 0.07f))
        )

        Column(
            modifier = ComposeModifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = ComposeModifier.height(12.dp))
            if (onBack != null) {
                Text(
                    text = "<-",
                    fontSize = 22.sp,
                    color = AppColors.Black,
                    modifier = ComposeModifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp)
                )
            } else {
                Spacer(modifier = ComposeModifier.height(36.dp))
            }

            Spacer(modifier = ComposeModifier.height(12.dp))
            Box(
                modifier = ComposeModifier
                    .align(Alignment.CenterHorizontally)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Text("N", color = AppColors.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = ComposeModifier.height(16.dp))
            Text(
                text = if (isRegisterMode) "회원가입" else "알림 제어",
                color = AppColors.Black,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = ComposeModifier.fillMaxWidth()
            )
            Spacer(modifier = ComposeModifier.height(6.dp))
            Text(
                text = if (isRegisterMode) {
                    "계정을 만들고 알림 규칙을 관리하세요"
                } else {
                    "다시 오신 것을 환영합니다"
                },
                color = AppColors.Secondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = ComposeModifier.fillMaxWidth()
            )

            Spacer(modifier = ComposeModifier.height(28.dp))

            if (isRegisterMode) {
                FieldLabel("이름")
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    modifier = ComposeModifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("홍길동", color = AppColors.Secondary) },
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors()
                )
                Spacer(modifier = ComposeModifier.height(14.dp))
            }

            FieldLabel("이메일")
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = ComposeModifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("name@example.com", color = AppColors.Secondary) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors()
            )

            Spacer(modifier = ComposeModifier.height(14.dp))
            FieldLabel("비밀번호")
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = ComposeModifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("••••••••", color = AppColors.Secondary) },
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(
                            text = if (showPassword) "숨김" else "표시",
                            color = AppColors.Primary,
                            fontSize = 12.sp
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors()
            )

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = ComposeModifier.height(10.dp))
                Text(errorMessage, color = Color(0xFFD32F2F), fontSize = 13.sp)
            }

            Spacer(modifier = ComposeModifier.height(24.dp))
            Button(
                onClick = { onSubmit(email, password, displayName) },
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Text(
                    text = if (isRegisterMode) "가입하기" else "로그인",
                    color = AppColors.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = ComposeModifier.height(24.dp))
            Row(
                modifier = ComposeModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = ComposeModifier
                        .weight(1f)
                        .height(1.dp)
                        .background(AppColors.Border)
                )
                Text(
                    "  or  ",
                    color = AppColors.Secondary,
                    fontSize = 12.sp
                )
                Box(
                    modifier = ComposeModifier
                        .weight(1f)
                        .height(1.dp)
                        .background(AppColors.Border)
                )
            }

            Spacer(modifier = ComposeModifier.height(16.dp))
            SocialButton("Google로 계속하기", Color.White, AppColors.Black, AppColors.Border) {
                onSocialClick("google")
            }
            Spacer(modifier = ComposeModifier.height(10.dp))
            SocialButton("카카오로 계속하기", Color(0xFFFEE500), AppColors.Black, Color(0xFFFEE500)) {
                onSocialClick("kakao")
            }

            Spacer(modifier = ComposeModifier.height(28.dp))
            Row(
                modifier = ComposeModifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isRegisterMode) "이미 계정이 있으신가요? " else "계정이 없으신가요? ",
                    color = AppColors.Secondary,
                    fontSize = 13.sp
                )
                Text(
                    text = if (isRegisterMode) "로그인" else "회원가입",
                    color = AppColors.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = ComposeModifier.clickable(onClick = onToggleMode)
                )
            }
            Spacer(modifier = ComposeModifier.height(32.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = AppColors.Black,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = ComposeModifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AppColors.White,
    unfocusedContainerColor = AppColors.White,
    focusedBorderColor = AppColors.Primary,
    unfocusedBorderColor = AppColors.Border,
    cursorColor = AppColors.Primary
)

@Composable
private fun SocialButton(
    label: String,
    bg: Color,
    fg: Color,
    border: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = ComposeModifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
