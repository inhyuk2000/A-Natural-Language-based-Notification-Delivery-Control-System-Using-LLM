package com.example.app

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.chat.ToolChoice
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.chat.chatMessage
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * LLM 프롬프트 → 알림 조건/대상 추출 → ContextManager 등록.
 *
 * Tools:
 * - extract_notification_condition : 시간·반복 (공통)
 * - extract_mute_target            : 받지마 default (블랙리스트)
 * - extract_allow_target           : 받아 default (화이트리스트)
 *
 * 한 명령에서 mute/allow target은 둘 중 하나만 사용.
 */
class PromptEngine(
    private val openAI: OpenAI,
    private val contextManager: ContextManager,
    private val chatMessages: MutableList<ChatMessage>,
) {
    companion object {
        private const val TOOL_CONDITION = "extract_notification_condition"
        private const val TOOL_MUTE = "extract_mute_target"
        private const val TOOL_ALLOW = "extract_allow_target"

        private val MUTE_HINTS = listOf("받지마", "차단", "뮤트", "mute", "끄", "조용", "dnd", "방해금지")
        private val ALLOW_HINTS = listOf("받아", "허용", "수신", "보내줘", "들려줘")
    }

    /** few-shot + 라우팅 규칙 (매 요청 시 messages 앞에 주입) */
    private fun systemFewShot(now: String): String = """
        |당신은 알림 규칙 추출기다. 사용자 한국어 명령을 tool call로만 구조화한다.
        |반드시 extract_notification_condition 과 (extract_mute_target | extract_allow_target) 중 하나를 함께 호출한다.
        |mute/allow target은 한 명령에 하나만. exceptions 필드는 없다.
        |
        |## 툴 선택
        |- 받지마/차단/뮤트/조용/끄 → extract_mute_target
        |- …만 받아/허용/수신 (받지마 없음) → extract_allow_target
        |- "카톡 빼고 다 받아" = 카톡 받지마 → extract_mute_target, name=["카카오톡"]
        |
        |## 시간 규칙 (현재=$now)
        |- 구간은 항상 delivery < expires. delivery==expires 절대 금지.
        |- "지금부터 N분(간) …" → delivery=현재, expires=현재+N분
        |- "N분 후부터 M분동안 …" → delivery=현재+N, expires=현재+N+M
        |- "N분 후부터 …까지" → delivery=현재+N, expires=끝 시각
        |- 반복(매일/요일) → recurrence + window_start/window_end (HH:mm)
        |
        |## Few-shot (현재=$now 가정)
        |
        |User: 카톡 5분동안 받지마
        |→ condition: delivery=$now, expires=($now+5분), recurrence=none
        |→ extract_mute_target: name=["카카오톡"], content=[]
        |
        |User: 지금부터 30분 모든 알림 받지마
        |→ condition: delivery=$now, expires=($now+30분)
        |→ extract_mute_target: name=[], content=[]
        |
        |User: 3분후부터 5분동안 카톡 받지마
        |→ condition: delivery=($now+3분), expires=($now+8분)
        |→ extract_mute_target: name=["카카오톡"], content=[]
        |
        |User: 카톡만 받아줘
        |→ condition: delivery=$now, expires=($now+12시간)  // 기간 미지정 시 12시간
        |→ extract_allow_target: name=["카카오톡"], content=[]
        |
        |User: 지금부터 1시간 카톡이랑 인스타만 받아
        |→ condition: delivery=$now, expires=($now+1시간)
        |→ extract_allow_target: name=["카카오톡","인스타그램"], content=[]
        |
        |User: 게임관련 카톡이랑 인스타만 받아줘
        |→ condition: delivery=$now, expires=($now+12시간)
        |→ extract_allow_target: name=["카카오톡","인스타그램"], content=["게임"]
        |
        |User: 광고 알림 받지마
        |→ extract_mute_target: name=[], content=["광고"]
        |
        |User: 매일 밤 11시부터 아침 6시까지 조용히
        |→ condition: recurrence=daily, window_start=23:00, window_end=06:00,
        |   delivery/expires는 해당 회차 구간으로 계산
        |→ extract_mute_target: name=[], content=[]
        |
        |User: 월수금 오후 2시부터 5시까지 카톡만 받아
        |→ condition: recurrence=weekly, days_of_week=[1,3,5], window_start=14:00, window_end=17:00
        |→ extract_allow_target: name=["카카오톡"], content=[]
        |
        |## 앱 이름 정규화
        |카톡→카카오톡, 인스타→인스타그램, 페북→페이스북, 유튜브→YouTube
    """.trimMargin()

    suspend fun getAiResponse(messages: List<ChatMessage>, currentTime: String): ChatCompletion {
        val now = currentTime
        Log.d("TEST_GPT", now)

        val withSystem = buildList {
            add(
                chatMessage {
                    role = ChatRole.System
                    content = systemFewShot(now)
                }
            )
            addAll(messages)
        }

        val request = chatCompletionRequest {
            model = ModelId("gpt-4o")
            this.messages = withSystem
            tools {
                function(
                    name = TOOL_CONDITION,
                    description = """
                        |알림 규칙의 시간·반복 조건을 추출한다. 현재 시각=$now.
                        |**절대 금지:** delivery와 expires를 같은 시각으로 두지 말 것.
                        |구간은 반드시 delivery < expires.
                        |
                        |Few-shot:
                        |- "카톡 5분동안 받지마" → delivery=현재($now), expires=현재+5분 (둘 다 현재+5분 아님!)
                        |- "지금부터 30분 조용히" → delivery=현재, expires=현재+30분
                        |- "3분후부터 5분동안 …" → delivery=현재+3분, expires=현재+8분
                        |- "5분 뒤부터 10분 뒤까지" → delivery=현재+5분, expires=현재+10분
                        |- 기간 미지정(…만 받아줘) → delivery=현재, expires=현재+12시간
                        |- 매일/요일 반복 → recurrence + window_start/window_end
                    """.trimMargin(),
                ) {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonObject("properties") {
                        putJsonObject("delivery") {
                            put("type", "object")
                            put("additionalProperties", false)
                            put(
                                "description",
                                "규칙 시작(ISO 8601, Asia/Seoul). 현재=$now. " +
                                    "'지금부터 … N분간' → delivery=현재. 'N분 뒤부터' → 현재+N분. " +
                                    "delivery=expires 금지.",
                            )
                            putJsonObject("properties") {
                                putJsonObject("absolute") {
                                    put("type", JsonPrimitive("string"))
                                    put("description", "예: '2025-08-25T18:00:00+09:00'. 현재: $now")
                                }
                            }
                            putJsonArray("required") { add("absolute") }
                        }
                        putJsonObject("activity") {
                            putJsonArray("type") {
                                add(JsonPrimitive("string"))
                                add(JsonPrimitive("null"))
                            }
                            put("description", "활동 조건(거의 미사용). 없으면 null.")
                        }
                        putJsonObject("location") {
                            putJsonArray("type") {
                                add(JsonPrimitive("string"))
                                add(JsonPrimitive("null"))
                            }
                            put("description", "위치 조건(거의 미사용). 없으면 null.")
                        }
                        putJsonObject("expires") {
                            put("type", "object")
                            put("additionalProperties", false)
                            put(
                                "description",
                                "규칙 종료. **delivery보다 반드시 이후**. 현재=$now. " +
                                    "'지금부터 5분간 받지마' → expires=현재+5분, delivery=현재. " +
                                    "delivery와 동일 시각 금지.",
                            )
                            putJsonObject("properties") {
                                putJsonObject("absolute") {
                                    put("type", JsonPrimitive("string"))
                                    put("description", "예: '2025-08-25T18:00:00+09:00'. 현재: $now")
                                }
                            }
                            putJsonArray("required") { add("absolute") }
                        }
                        putJsonObject("recurrence") {
                            put("type", JsonPrimitive("string"))
                            put("description", "none|daily|weekly. 매일→daily, 월수금/평일→weekly. 기본 none.")
                            putJsonArray("enum") {
                                add("none")
                                add("daily")
                                add("weekly")
                            }
                        }
                        putJsonObject("days_of_week") {
                            put("type", "array")
                            put("description", "weekly일 때 ISO 요일 월=1…일=7. 그 외 [].")
                            putJsonObject("items") {
                                put("type", JsonPrimitive("integer"))
                            }
                        }
                        putJsonObject("window_start") {
                            putJsonArray("type") {
                                add(JsonPrimitive("string"))
                                add(JsonPrimitive("null"))
                            }
                            put("description", "반복 벽시계 시작 HH:mm. none이면 null.")
                        }
                        putJsonObject("window_end") {
                            putJsonArray("type") {
                                add(JsonPrimitive("string"))
                                add(JsonPrimitive("null"))
                            }
                            put("description", "반복 벽시계 종료 HH:mm. none이면 null. overnight 가능(23:00–06:00).")
                        }
                    }
                    putJsonArray("required") {
                        add("delivery")
                        add("activity")
                        add("location")
                        add("expires")
                        add("recurrence")
                        add("days_of_week")
                        add("window_start")
                        add("window_end")
                    }
                }

                function(
                    name = TOOL_MUTE,
                    description = """
                        |받지마/차단/뮤트/조용/끄 명령 전용 (블랙리스트, mute default).
                        |'…만 받아'에는 이 툴을 쓰지 말 것 → extract_allow_target.
                        |exceptions 없음. "카톡 빼고 다 받아"도 이 툴로 name=["카카오톡"].
                        |
                        |Few-shot:
                        |(1) 모든 알림 받지마 → name=[], content=[]
                        |(2) 카톡 받지마 / 카톡 5분동안 받지마 → name=["카카오톡"], content=[]
                        |(3) 카톡이랑 인스타 받지마 → name=["카카오톡","인스타그램"], content=[]
                        |(4) 광고 알림 받지마 → name=[], content=["광고"]
                        |(5) 카톡 광고만 받지마 → name=["카카오톡"], content=["광고"]
                        |약어: 카톡→카카오톡, 인스타→인스타그램.
                    """.trimMargin(),
                ) {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonObject("properties") {
                        putJsonObject("name") {
                            put("type", "array")
                            put(
                                "description",
                                "묵음할 앱 정규화 이름 목록. 비우면([]) 모든 앱 묵음. " +
                                    "카톡→카카오톡.",
                            )
                            putJsonObject("items") {
                                putJsonArray("type") {
                                    add(JsonPrimitive("string"))
                                    add(JsonPrimitive("null"))
                                }
                            }
                        }
                        putJsonObject("content") {
                            put("type", "array")
                            put("description", "묵음할 키워드. 없으면 [].")
                            putJsonObject("items") {
                                putJsonArray("type") {
                                    add(JsonPrimitive("string"))
                                    add(JsonPrimitive("null"))
                                }
                            }
                        }
                    }
                    putJsonArray("required") {
                        add("name")
                        add("content")
                    }
                }

                function(
                    name = TOOL_ALLOW,
                    description = """
                        |'…만 받아/허용/수신' 명령 전용 (화이트리스트, allow default).
                        |받지마/차단/뮤트에는 이 툴 금지 → extract_mute_target.
                        |exceptions 없음. 허용 앱은 모두 name에.
                        |
                        |Few-shot:
                        |(1) 카톡만 받아줘 → name=["카카오톡"], content=[]
                        |(2) 카톡이랑 인스타만 받아 → name=["카카오톡","인스타그램"], content=[]
                        |(3) 게임관련 카톡이랑 인스타만 받아줘 → name=["카카오톡","인스타그램"], content=["게임"]
                        |(4) 엄마 카톡만 받아 → name=["카카오톡"], content=["엄마"]
                        |약어: 카톡→카카오톡, 인스타→인스타그램.
                    """.trimMargin(),
                ) {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonObject("properties") {
                        putJsonObject("name") {
                            put("type", "array")
                            put(
                                "description",
                                "수신 허용할 앱 정규화 이름 목록. 가능하면 비우지 말 것. " +
                                    "카톡→카카오톡.",
                            )
                            putJsonObject("items") {
                                putJsonArray("type") {
                                    add(JsonPrimitive("string"))
                                    add(JsonPrimitive("null"))
                                }
                            }
                        }
                        putJsonObject("content") {
                            put("type", "array")
                            put("description", "허용 키워드 필터. 없으면 [].")
                            putJsonObject("items") {
                                putJsonArray("type") {
                                    add(JsonPrimitive("string"))
                                    add(JsonPrimitive("null"))
                                }
                            }
                        }
                    }
                    putJsonArray("required") {
                        add("name")
                        add("content")
                    }
                }

                toolChoice = ToolChoice.Auto
            }
        }
        return openAI.chatCompletion(request)
    }

    suspend fun handle(prompt: String, currentTime: String) {
        chatMessages.appendMsg(
            chatMessage {
                role = ChatRole.User
                content = prompt
            }
        )

        var aiResponse = getAiResponse(chatMessages, currentTime)
        var aiMessage = aiResponse.choices.first().message

        var condition: JsonObject? = null
        var muteTarget: JsonObject? = null
        var allowTarget: JsonObject? = null
        val myToolCalls = aiMessage.toolCalls

        if (!myToolCalls.isNullOrEmpty()) {
            chatMessages.appendMsg(aiMessage)
            for (myToolCall in myToolCalls) {
                if (myToolCall is ToolCall.Function) {
                    val toolName = myToolCall.function.name
                    val argumentsJson = myToolCall.function.argumentsAsJson()
                    when (toolName) {
                        TOOL_CONDITION -> condition = argumentsJson
                        TOOL_MUTE -> muteTarget = argumentsJson
                        TOOL_ALLOW -> allowTarget = argumentsJson
                        // 구버전 호환
                        "extract_notification_target" -> {
                            val legacyMute = when (val el = argumentsJson["mute"]) {
                                is JsonPrimitive -> when {
                                    el.isString -> el.contentOrNull?.equals("true", ignoreCase = true) == true
                                    else -> runCatching { el.content.toBoolean() }.getOrDefault(true)
                                }
                                else -> true
                            }
                            if (legacyMute) muteTarget = argumentsJson else allowTarget = argumentsJson
                        }
                    }
                    chatMessages.appendMsg(
                        chatMessage {
                            role = ChatRole.Tool
                            name = toolName
                            toolCallId = myToolCall.id
                            content = argumentsJson.toString()
                        }
                    )
                }
            }
            chatMessages.appendMsg(
                chatMessage {
                    role = ChatRole.System
                    content = "추출된 tool 결과를 바탕으로 사용자에게 규칙이 어떻게 적용되는지 한두 문장으로 확인해줘."
                }
            )
            aiResponse = getAiResponse(chatMessages, currentTime)
            aiMessage = aiResponse.choices.first().message
        }

        val muteHint = MUTE_HINTS.any { prompt.contains(it, ignoreCase = true) }
        val allowHint = ALLOW_HINTS.any { prompt.contains(it, ignoreCase = true) }

        // mute/allow 둘 다 오면 프롬프트 힌트로 하나 선택 (받지마 우선)
        val (rawTarget, muteFlag) = when {
            muteTarget != null && allowTarget == null -> muteTarget to true
            allowTarget != null && muteTarget == null -> allowTarget to false
            muteTarget != null && allowTarget != null -> {
                if (muteHint || !allowHint) muteTarget to true
                else allowTarget to false
            }
            else -> null to false
        }

        if (rawTarget == null || condition == null || condition.isEmpty()) {
            chatMessages.appendMsg(
                chatMessage {
                    role = ChatRole.Assistant
                    content = aiMessage.content.orEmpty().ifBlank {
                        "알림 대상이나 조건이 제대로 추출되지 않았습니다. " +
                            "‘카톡 받지마’ 또는 ‘카톡만 받아줘’처럼 다시 말씀해주세요."
                    }
                }
            )
            return
        }

        // 힌트와 툴이 어긋나면 힌트 우선 보정
        val resolvedMute = when {
            muteHint && !allowHint -> true
            allowHint && !muteHint -> false
            else -> muteFlag
        }

        val names = rawTarget.stringList("name")
        val contents = rawTarget.stringList("content")

        if (!resolvedMute && names.isEmpty() && contents.isEmpty()) {
            chatMessages.appendMsg(
                chatMessage {
                    role = ChatRole.Assistant
                    content = "받을 앱이나 키워드가 없어요. ‘카톡만 받아줘’처럼 대상을 알려주세요."
                }
            )
            return
        }

        val targetFixed = buildJsonObject {
            put("mute", JsonPrimitive(resolvedMute))
            putJsonArray("name") {
                names.forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("content") {
                contents.forEach { add(JsonPrimitive(it)) }
            }
            // exceptions 제거 — 항상 빈 배열
            putJsonArray("exceptions") { }
        }

        Log.d("TEST_GPT", "target: $targetFixed mute=$resolvedMute")
        Log.d("TEST_GPT", "condition: $condition")

        fun JsonObject.optString(key: String): String? {
            val el = this[key] ?: return null
            if (el is JsonNull) return null
            return (el as? JsonPrimitive)?.contentOrNull
        }
        val recurrence = RecurrenceWindow.normalizeRecurrence(condition.optString("recurrence"))
        val daysCsv = when (val el = condition["days_of_week"]) {
            is JsonArray ->
                el.mapNotNull {
                    if (it is JsonNull) null
                    else (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                        ?: (it as? JsonPrimitive)?.let { p ->
                            runCatching { p.content.toInt() }.getOrNull()
                        }
                }
                    .filter { it in 1..7 }
                    .distinct()
                    .sorted()
                    .joinToString(",")
            else -> ""
        }
        val windowStart = condition.optString("window_start")?.trim().orEmpty()
        val windowEnd = condition.optString("window_end")?.trim().orEmpty()

        val title = deriveRuleTitleFromTarget(
            prompt = prompt,
            appNames = names,
            exceptions = emptyList(),
            mute = resolvedMute,
            recurrence = recurrence,
            daysOfWeek = daysCsv,
            windowStart = windowStart,
            windowEnd = windowEnd,
        )
        val success = contextManager.handleIncomingRule(targetFixed, condition, ruleTitle = title)
        if (!success) return

        chatMessages.appendMsg(aiMessage)
        Log.d("test", aiMessage.content.toString())
    }

    private fun JsonObject.stringList(key: String): List<String> {
        val el = this[key] ?: return emptyList()
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            if (item is JsonNull) null
            else item.jsonPrimitive.contentOrNull
        }.filter { it.isNotBlank() }
    }

    private fun MutableList<ChatMessage>.appendMsg(message: ChatMessage) {
        add(
            ChatMessage(
                role = message.role,
                content = message.content.orEmpty(),
                toolCalls = message.toolCalls,
                toolCallId = message.toolCallId,
                name = message.name,
            )
        )
    }
}
