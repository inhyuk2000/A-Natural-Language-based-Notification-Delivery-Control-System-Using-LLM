package com.example.app.presentation.navigation

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.example.app.AppNameMapper
import com.example.app.ContextManager
import com.example.app.PromptEngine
import com.example.app.PromptViewModel
import com.example.app.TimeUtils
import com.example.app.presentation.screens.ChatScreen
import com.example.app.presentation.screens.ChatTutorialScreen
import com.example.app.presentation.screens.UiChatMessage
import com.example.app.presentation.screens.toUiMessage
import kotlinx.coroutines.launch

private const val ChatPrefs = "chat_prefs"
private const val KeyTutorialDone = "chat_tutorial_done"
private const val CrossfadeMs = 280

@Composable
fun ChatRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val chatPrefs = remember {
        context.getSharedPreferences(ChatPrefs, Context.MODE_PRIVATE)
    }
    val tutorialAlreadyDone = remember {
        chatPrefs.getBoolean(KeyTutorialDone, false)
    }
    var showTutorial by remember {
        mutableStateOf(!tutorialAlreadyDone)
    }
    // 첫 자동 진입 vs 사용법 버튼으로 다시 열기
    var tutorialIsFirstTime by remember {
        mutableStateOf(!tutorialAlreadyDone)
    }

    // Activity 스코프 — 채팅 route를 pop해도 대화 기록 유지
    val viewModel: PromptViewModel = viewModel(viewModelStoreOwner = activity)
    val scope = rememberCoroutineScope()
    val contextManager = remember { ContextManager(context) }
    val promptEngine = remember(contextManager, viewModel) {
        PromptEngine(contextManager, viewModel.chatMessages)
    }

    var uiMessages by remember {
        mutableStateOf(viewModel.chatMessages.mapNotNull { it.toUiMessage() })
    }
    var inputText by remember { mutableStateOf("") }

    fun refreshUi() {
        uiMessages = viewModel.chatMessages.mapNotNull { it.toUiMessage() }
    }

    LaunchedEffect(Unit) {
        AppNameMapper.loadInstalledApps(context)
        val systemV2 = chatPrefs.getBoolean("system_message_v2", false)
        if (!systemV2 || viewModel.chatMessages.none { it.role == ChatRole.System }) {
            val appMappingJson = AppNameMapper.getMappingAsJson()
            viewModel.chatMessages.removeAll { it.role == ChatRole.System }
            viewModel.chatMessages.add(
                0,
                ChatMessage(
                    role = ChatRole.System,
                    content = """
                        너는 사용자의 자연어 명령을 보고 알림 규칙의 시간·대상을 이해하는 도우미다.
                        실제 규칙 추출·패키지 매핑은 서버가 수행한다.
                        아래는 기기에 설치된 앱의 패키지→라벨(자동 수집) 참고용이다.
                        '모든 알림' / '전부' 는 앱 이름이 아니다.

                        앱 목록:
                        $appMappingJson
                    """.trimIndent()
                )
            )
            viewModel.systemMessageAdded = true
            chatPrefs.edit().putBoolean("system_message_v2", true).apply()
        }
        refreshUi()
    }

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                val message = intent?.getStringExtra("error_message") ?: return
                viewModel.chatMessages.add(
                    ChatMessage(role = ChatRole.Assistant, content = message)
                )
                refreshUi()
            }
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver,
            android.content.IntentFilter("com.example.APP_NAME_MAPPING_ERROR")
        )
        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
    }

    // 같은 destination 안에서 튜토리얼→채팅만 교체 (별도 Activity/route 전환 느낌 제거)
    AnimatedContent(
        targetState = showTutorial,
        transitionSpec = {
            (
                fadeIn(tween(CrossfadeMs)) +
                    slideInHorizontally(tween(CrossfadeMs)) { it / 12 }
                ) togetherWith (
                fadeOut(tween(CrossfadeMs - 40)) +
                    slideOutHorizontally(tween(CrossfadeMs - 40)) { -it / 12 }
                )
        },
        label = "chatTutorialCrossfade",
    ) { tutorial ->
        if (tutorial) {
            ChatTutorialScreen(
                isFirstTime = tutorialIsFirstTime,
                onContinue = {
                    if (tutorialIsFirstTime) {
                        chatPrefs.edit().putBoolean(KeyTutorialDone, true).apply()
                    }
                    showTutorial = false
                }
            )
        } else {
            ChatScreen(
                messages = uiMessages,
                input = inputText,
                onInputChange = { inputText = it },
                onBack = onBack,
                onOpenTutorial = {
                    tutorialIsFirstTime = false
                    showTutorial = true
                },
                onSend = {
                    val prompt = inputText.trim()
                    if (prompt.isBlank()) return@ChatScreen
                    val currentTime = TimeUtils().getTodayDatetime()
                    inputText = ""
                    uiMessages = uiMessages +
                        UiChatMessage(isUser = true, content = prompt) +
                        UiChatMessage(isUser = false, content = "GPT 응답을 가져오는 중...")
                    scope.launch {
                        try {
                            promptEngine.handle(prompt, currentTime)
                            context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("last_prompt_preview", prompt)
                                .apply()
                        } catch (e: Exception) {
                            Log.e("ChatRoute", "프롬프트 처리 실패", e)
                            viewModel.chatMessages.add(
                                ChatMessage(
                                    role = ChatRole.Assistant,
                                    content = "오류: ${e.message ?: e.javaClass.simpleName}"
                                )
                            )
                        }
                        refreshUi()
                    }
                },
            )
        }
    }
}
