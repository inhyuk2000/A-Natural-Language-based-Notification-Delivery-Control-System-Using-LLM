package com.example.app.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.TextContent
import com.example.app.presentation.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

data class UiChatMessage(val isUser: Boolean, val content: String)

private sealed class ChatListItem {
    data class Bubble(
        val isUser: Boolean,
        val content: String,
        val showAvatar: Boolean,
    ) : ChatListItem()

    data class TipCard(
        val examples: List<String>,
    ) : ChatListItem()
}

private val TipExamples = listOf(
    "지금부터 10분동안 게임 관련 카톡이랑 인스타 알림들만 받아줘",
    "업무 시간에는 슬랙 알림만 허용해줘",
    "밤 11시 이후에는 모든 알림 차단해줘",
)

private val WelcomeItems = listOf(
    ChatListItem.Bubble(
        isUser = false,
        content = "안녕하세요! 👋 알림 제어 챗봇입니다.",
        showAvatar = true,
    ),
    ChatListItem.Bubble(
        isUser = false,
        content = "저에게 자연어로 알림 수신 조건을 말씀해 주시면, AI가 자동으로 분석하여 규칙을 설정해 드려요.",
        showAvatar = false,
    ),
    ChatListItem.TipCard(examples = TipExamples),
    ChatListItem.Bubble(
        isUser = false,
        content = "어떤 알림을 제어하고 싶으신가요? 아래에 입력해 주세요!",
        showAvatar = false,
    ),
)

fun ChatMessage.toUiMessage(): UiChatMessage? {
    if (role == ChatRole.System || role == ChatRole.Tool) return null
    val content = (messageContent as? TextContent)?.content ?: content.orEmpty()
    if (content.isBlank()) return null
    return UiChatMessage(isUser = role == ChatRole.User, content = content)
}

@Composable
fun ChatScreen(
    messages: List<UiChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onOpenTutorial: () -> Unit,
) {
    val listState = rememberLazyListState()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // 화면 세션 동안 한 번만 재생 (스크롤 재활용 시 재재생 방지). 재진입 시 remember 초기화되어 전부 다시 애니.
    val revealedKeys = remember { mutableSetOf<String>() }
    var revealedCount by remember { mutableIntStateOf(0) }

    val conversationItems = remember(messages) { messages.toChatListItems() }
    val listItems = remember(conversationItems) { WelcomeItems + conversationItems }
    val lastBubbleContent = (listItems.lastOrNull() as? ChatListItem.Bubble)?.content

    // 메시지 증가·내용 갱신·등장 애니·키보드 시 맨 아래로
    LaunchedEffect(listItems.size, lastBubbleContent, imeVisible, revealedCount) {
        if (listItems.isEmpty()) return@LaunchedEffect
        delay(48)
        listState.animateScrollToItem(listItems.lastIndex)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .filter { it > 0 }
            .collect {
                listState.animateScrollToItem(it - 1)
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
                .offset(x = 40.dp, y = 140.dp)
                .size(280.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x445F33E1),
                            Color(0x225F33E1),
                            Color.Transparent,
                        )
                    ),
                    shape = CircleShape,
                )
        )
        Box(
            modifier = ComposeModifier
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = (-120).dp)
                .size(240.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x440087FF),
                            Color(0x180087FF),
                            Color.Transparent,
                        )
                    ),
                    shape = CircleShape,
                )
        )

        Column(modifier = ComposeModifier.fillMaxSize()) {
            ChatHeader(onBack = onBack, onOpenTutorial = onOpenTutorial)

            Column(
                modifier = ComposeModifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding(),
            ) {
                // 메시지 영역만 스크롤 — 입력바는 하단 고정
                LazyColumn(
                    state = listState,
                    modifier = ComposeModifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        items = listItems,
                        key = { index, item -> itemStableKey(index, item) },
                    ) { index, item ->
                        val itemKey = itemStableKey(index, item)
                        ChatItemEntrance(
                            itemKey = itemKey,
                            index = index,
                            listItems = listItems,
                            revealedKeys = revealedKeys,
                            onRevealed = { revealedCount = revealedKeys.size },
                        ) {
                            when (item) {
                                is ChatListItem.Bubble -> MessageBubble(
                                    item = item,
                                    onExampleClick = null,
                                )
                                is ChatListItem.TipCard -> TipExamplesBubble(
                                    examples = item.examples,
                                    onExampleClick = { onInputChange(it) },
                                )
                            }
                        }
                    }
                }

                ChatComposer(
                    input = input,
                    onInputChange = onInputChange,
                    onSend = onSend,
                )
            }
        }
    }
}

private fun itemStableKey(index: Int, item: ChatListItem): String = when (item) {
    is ChatListItem.TipCard -> "welcome-tip"
    is ChatListItem.Bubble ->
        if (index < WelcomeItems.size) {
            "welcome-$index"
        } else {
            "msg-$index-${if (item.isUser) "u" else "a"}-${item.content.hashCode()}"
        }
}

@Composable
private fun ChatItemEntrance(
    itemKey: String,
    index: Int,
    listItems: List<ChatListItem>,
    revealedKeys: MutableSet<String>,
    onRevealed: () -> Unit,
    content: @Composable () -> Unit,
) {
    var visible by remember(itemKey) { mutableStateOf(itemKey in revealedKeys) }

    LaunchedEffect(itemKey) {
        if (itemKey in revealedKeys) {
            visible = true
            return@LaunchedEffect
        }
        // 아직 안 나온 메시지 기준 스태거 → 재진입 시 전체, 전송 시 신규만 짧게
        val pendingBefore = (0 until index).count { i ->
            itemStableKey(i, listItems[i]) !in revealedKeys
        }
        delay(40L + pendingBefore.coerceAtMost(16) * 55L)
        revealedKeys.add(itemKey)
        visible = true
        onRevealed()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 },
    ) {
        content()
    }
}

private fun List<UiChatMessage>.toChatListItems(): List<ChatListItem> {
    var prevWasAssistant = false
    return map { msg ->
        if (msg.isUser) {
            prevWasAssistant = false
            ChatListItem.Bubble(isUser = true, content = msg.content, showAvatar = false)
        } else {
            val showAvatar = !prevWasAssistant
            prevWasAssistant = true
            ChatListItem.Bubble(isUser = false, content = msg.content, showAvatar = showAvatar)
        }
    }
}

@Composable
private fun ChatHeader(
    onBack: () -> Unit,
    onOpenTutorial: () -> Unit,
) {
    Row(
        modifier = ComposeModifier
            .fillMaxWidth()
            .background(Color(0xB3FFFFFF))
            .border(
                width = 1.dp,
                color = AppColors.Border,
                shape = RoundedCornerShape(0.dp),
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = ComposeModifier.weight(1f),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = AppColors.Black,
                    modifier = ComposeModifier.size(24.dp),
                )
            }
            Spacer(modifier = ComposeModifier.width(4.dp))
            Box(
                modifier = ComposeModifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.Badge),
                contentAlignment = Alignment.Center,
            ) {
                Text("🤖", fontSize = 16.sp)
            }
            Spacer(modifier = ComposeModifier.width(8.dp))
            Column {
                Text(
                    text = "알림 제어 챗봇",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = AppColors.Black,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = ComposeModifier
                            .size(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(AppColors.Blue)
                    )
                    Spacer(modifier = ComposeModifier.width(4.dp))
                    Text(
                        text = "AI Assistant",
                        fontSize = 10.sp,
                        color = AppColors.Secondary,
                    )
                }
            }
        }
        Box(
            modifier = ComposeModifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF0E8FF))
                .clickable(onClick = onOpenTutorial)
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Text(
                text = "사용법",
                color = AppColors.Primary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    item: ChatListItem.Bubble,
    onExampleClick: ((String) -> Unit)?,
) {
    if (item.isUser) {
        Row(
            modifier = ComposeModifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = item.content,
                color = AppColors.White,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = ComposeModifier
                    .widthIn(max = 268.dp)
                    .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color(0x145F33E1))
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 4.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp,
                        )
                    )
                    .background(AppColors.Primary)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        return
    }

    Row(
        modifier = ComposeModifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = ComposeModifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (item.showAvatar) AppColors.Badge else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (item.showAvatar) {
                Text("🤖", fontSize = 14.sp)
            }
        }
        Spacer(modifier = ComposeModifier.width(8.dp))
        Text(
            text = item.content,
            color = AppColors.Black,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = ComposeModifier
                .widthIn(max = 268.dp)
                .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color(0x0824252C))
                .border(
                    1.dp,
                    AppColors.Border,
                    RoundedCornerShape(
                        topStart = if (item.showAvatar) 4.dp else 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    ),
                )
                .background(
                    AppColors.White,
                    RoundedCornerShape(
                        topStart = if (item.showAvatar) 4.dp else 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .then(
                    if (onExampleClick != null) {
                        ComposeModifier.clickable { onExampleClick(item.content) }
                    } else {
                        ComposeModifier
                    }
                ),
        )
    }
}

@Composable
private fun TipExamplesBubble(
    examples: List<String>,
    onExampleClick: (String) -> Unit,
) {
    Row(modifier = ComposeModifier.fillMaxWidth()) {
        Spacer(modifier = ComposeModifier.size(28.dp))
        Spacer(modifier = ComposeModifier.width(8.dp))
        Column(
            modifier = ComposeModifier
                .widthIn(max = 268.dp)
                .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color(0x0A5F33E1))
                .border(1.dp, Color(0x1F5F33E1), RoundedCornerShape(16.dp))
                .background(Color(0xFFF8F6FF), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = "💡 이렇게 말해보세요:",
                color = AppColors.Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = ComposeModifier.height(8.dp))
            examples.forEachIndexed { index, example ->
                if (index > 0) Spacer(modifier = ComposeModifier.height(6.dp))
                Text(
                    text = "💬 \"$example\"",
                    color = AppColors.Black,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = ComposeModifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onExampleClick(example) }
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatComposer(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = ComposeModifier
            .fillMaxWidth()
            .background(AppColors.White)
            .border(
                width = 1.dp,
                color = AppColors.Border,
                shape = RoundedCornerShape(0.dp),
            )
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = ComposeModifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.Nav),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = AppColors.Primary,
                modifier = ComposeModifier.size(22.dp),
            )
        }

        Row(
            modifier = ComposeModifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.InputBg)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = ComposeModifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    color = AppColors.Black,
                    fontSize = 13.sp,
                ),
                cursorBrush = SolidColor(AppColors.Primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { if (input.isNotBlank()) onSend() }
                ),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) {
                            Text(
                                text = "메시지를 입력하세요",
                                color = AppColors.Secondary,
                                fontSize = 13.sp,
                            )
                        }
                        inner()
                    }
                },
            )
            Text("☺", fontSize = 14.sp, color = AppColors.Secondary)
        }

        IconButton(
            onClick = onSend,
            modifier = ComposeModifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.Primary),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "전송",
                tint = AppColors.White,
                modifier = ComposeModifier.size(16.dp),
            )
        }
    }
}
