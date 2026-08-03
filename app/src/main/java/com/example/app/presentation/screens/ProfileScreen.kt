package com.example.app.presentation.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app.R
import com.example.app.presentation.theme.AppColors
import kotlinx.coroutines.delay

@Composable
fun ProfileScreen(
    displayName: String,
    email: String,
    avatarPath: String?,
    onBack: () -> Unit,
    onSave: (name: String, pendingAvatarUri: Uri?) -> Unit,
    onLogout: () -> Unit,
) {
    var nameDraft by remember(displayName) { mutableStateOf(displayName) }
    var pendingAvatarUri by remember { mutableStateOf<Uri?>(null) }
    var savedHint by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) pendingAvatarUri = uri
    }

    LaunchedEffect(savedHint) {
        if (savedHint) {
            delay(1600)
            savedHint = false
        }
    }

    Box(
        modifier = ComposeModifier
            .fillMaxSize()
            .background(AppColors.White)
    ) {
        Box(
            modifier = ComposeModifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = 48.dp)
                .size(280.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x445F33E1), Color(0x225F33E1), Color.Transparent)
                    ),
                    CircleShape,
                )
        )
        Box(
            modifier = ComposeModifier
                .align(Alignment.CenterStart)
                .offset(x = (-100).dp, y = 120.dp)
                .size(240.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x440087FF), Color(0x180087FF), Color.Transparent)
                    ),
                    CircleShape,
                )
        )

        Column(
            modifier = ComposeModifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 100.dp),
        ) {
            Row(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로",
                        tint = AppColors.Black,
                    )
                }
                Text(
                    text = "내 정보",
                    color = AppColors.Black,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = ComposeModifier.weight(1f),
                )
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = AppColors.Black,
                    )
                }
            }

            Column(
                modifier = ComposeModifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = ComposeModifier.height(12.dp))
                Box(modifier = ComposeModifier.size(100.dp)) {
                    ProfileAvatarImage(
                        avatarPath = avatarPath,
                        pendingUri = pendingAvatarUri,
                        displayName = nameDraft,
                        size = 100.dp,
                    )
                    Box(
                        modifier = ComposeModifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color(0x405F33E1))
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.Primary)
                            .border(2.dp, AppColors.White, RoundedCornerShape(16.dp))
                            .clickable {
                                pickImage.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_profile_camera),
                            contentDescription = "사진 변경",
                            tint = Color.Unspecified,
                            modifier = ComposeModifier.size(14.dp),
                        )
                    }
                }

                Spacer(modifier = ComposeModifier.height(16.dp))
                Text(
                    text = nameDraft.ifBlank { "사용자" },
                    color = AppColors.Black,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = ComposeModifier.fillMaxWidth(),
                )

                Spacer(modifier = ComposeModifier.height(24.dp))
                ProfileFieldCard(
                    label = "이름",
                    editable = true,
                    onEditClick = { nameFocus.requestFocus() },
                ) {
                    BasicTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = AppColors.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        cursorBrush = SolidColor(AppColors.Primary),
                        modifier = ComposeModifier
                            .fillMaxWidth()
                            .focusRequester(nameFocus),
                        decorationBox = { inner ->
                            if (nameDraft.isEmpty()) {
                                Text("이름을 입력하세요", color = AppColors.Secondary, fontSize = 14.sp)
                            }
                            inner()
                        },
                    )
                }

                Spacer(modifier = ComposeModifier.height(16.dp))
                ProfileFieldCard(
                    label = "이메일",
                    editable = false,
                    muted = true,
                ) {
                    Text(
                        text = email.ifBlank { "-" },
                        color = Color(0xFF9CA3AF),
                        fontSize = 14.sp,
                    )
                }

                if (savedHint) {
                    Spacer(modifier = ComposeModifier.height(16.dp))
                    Text(
                        text = "저장되었습니다",
                        color = AppColors.Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = ComposeModifier.height(32.dp))
            }

            Column(
                modifier = ComposeModifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = {
                        onSave(nameDraft, pendingAvatarUri)
                        pendingAvatarUri = null
                        savedHint = true
                    },
                    modifier = ComposeModifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0x405F33E1)),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                ) {
                    Text(
                        text = "저장하기",
                        color = AppColors.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = ComposeModifier.height(16.dp))
                Text(
                    text = "로그아웃",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    modifier = ComposeModifier
                        .clickable(onClick = onLogout)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileFieldCard(
    label: String,
    editable: Boolean,
    muted: Boolean = false,
    onEditClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = ComposeModifier
            .fillMaxWidth()
            .then(
                if (muted) {
                    ComposeModifier
                        .border(1.dp, AppColors.Border, RoundedCornerShape(15.dp))
                        .background(Color(0xFFF8F6FC), RoundedCornerShape(15.dp))
                } else {
                    ComposeModifier
                        .shadow(8.dp, RoundedCornerShape(15.dp), spotColor = Color(0x0A000000))
                        .background(AppColors.White, RoundedCornerShape(15.dp))
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = ComposeModifier.weight(1f)) {
            Text(
                text = label,
                color = if (muted) Color(0xFF9CA3AF) else AppColors.Secondary,
                fontSize = 11.sp,
            )
            Spacer(modifier = ComposeModifier.height(4.dp))
            content()
        }
        if (editable) {
            IconButton(onClick = { onEditClick?.invoke() }, modifier = ComposeModifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "수정",
                    tint = AppColors.Secondary,
                    modifier = ComposeModifier.size(16.dp),
                )
            }
        } else {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = ComposeModifier.size(16.dp),
            )
        }
    }
}

@Composable
fun ProfileAvatarImage(
    avatarPath: String?,
    pendingUri: Uri? = null,
    displayName: String,
    size: androidx.compose.ui.unit.Dp,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fileStamp = remember(avatarPath) {
        avatarPath?.let { java.io.File(it).takeIf { f -> f.exists() }?.lastModified() }
    }
    val bitmap = remember(avatarPath, pendingUri, fileStamp) {
        runCatching {
            when {
                pendingUri != null -> {
                    context.contentResolver.openInputStream(pendingUri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                !avatarPath.isNullOrBlank() -> BitmapFactory.decodeFile(avatarPath)
                else -> null
            }
        }.getOrNull()
    }

    Box(
        modifier = ComposeModifier
            .size(size)
            .clip(CircleShape)
            .background(AppColors.Badge),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = ComposeModifier.fillMaxSize(),
            )
        } else {
            Text(
                text = displayName.take(1).uppercase().ifBlank { "U" },
                color = AppColors.Primary,
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
