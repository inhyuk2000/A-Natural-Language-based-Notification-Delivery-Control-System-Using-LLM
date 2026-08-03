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
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.example.app.AppNameMapper
import com.example.app.BuildConfig
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
    val openAI = remember {
        OpenAI(
            OpenAIConfig(
                token = BuildConfig.OPENAI_API_KEY
            )
        )
    }
    val promptEngine = remember(openAI, contextManager, viewModel) {
        PromptEngine(openAI, contextManager, viewModel.chatMessages)
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
                        너는 사용자의 자연어 명령을 보고, **알림을 받지 않을 조건**과 **알림의 대상**을 추출하는 역할을 한다.
                        너는 사용자의 명령에서 앱 이름을 정확히 매핑해야 한다.
                        아래 JSON은 사용자의 휴대폰에 현재 설치되어 있는 외부 앱들의 패키지 명과 한글 이름입니다.
                        이를 참고해서 사용자로부터 입력된 자연어 명령을 보고, 해당하는 한글 이름(Names)로 변환하라.
                        만약 Names 목록이 여러 개인 경우, 제일 첫 번째 항목의 이름을 선택하라.
                        '모든 알림' / '전부' 는 앱 이름이 아니다. name 배열에는 넣지 말고 빈 배열 [] 로 둔다.
                        '지금부터 N분간 모든 알림 받지마' 는 'N분 뒤부터 모든 알림 전부 수신'으로 해석한다.
                        이때 delivery와 expires는 둘 다 현재+N분이고 name은 [] 이다. delivery를 현재로 두면 안 된다.

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
