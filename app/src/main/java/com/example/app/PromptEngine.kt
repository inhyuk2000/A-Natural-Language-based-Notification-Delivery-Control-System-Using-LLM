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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** LLM 프롬프트 → 알림 조건/대상 추출 → ContextManager 등록 (홈·PromptActivity 공용) */
class PromptEngine(
    private val openAI: OpenAI,
    private val contextManager: ContextManager,
    private val chatMessages: MutableList<ChatMessage>,
) {
    suspend fun getAiResponse(messages: List<ChatMessage>, currentTime: String): ChatCompletion {
        val now = currentTime
        Log.d("TEST_GPT", now)
        val request = chatCompletionRequest {
            model = ModelId("gpt-4o")
            this.messages = messages
            tools {
                function(
                    name = "extract_notification_condition",
                    description = "알림 규칙의 시간·반복 조건을 추출한다. " +
                        "**절대 금지:** delivery와 expires를 같은 시각으로 두지 말 것(구버전 꼼수). " +
                        "구간은 반드시 delivery < expires. " +
                        "**지금부터 N분간 모든 알림 받지마** → delivery=현재(${now}), expires=현재+N분 " +
                        "(둘 다 현재+N분으로 두면 안 됨). " +
                        "'5분 뒤부터 10분 뒤까지' → delivery=현재+5분, expires=현재+10분. " +
                        "'3분후부터 5분동안' → delivery=현재+3분, expires=현재+8분. " +
                        "반복: 매일/월수금 → recurrence + window_*. 없으면 recurrence=none.",
                ) {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonObject("properties") {
                        putJsonObject("delivery") {
                            put("type", "object")
                            put("additionalProperties", false)
                            put(
                                "description",
                                "규칙 시작(ISO 8601). 현재=${now}. " +
                                    "'지금부터 … N분간' → delivery=현재(지금). expires만 현재+N분. " +
                                    "'N분 뒤부터' → 현재+N분. " +
                                    "절대 delivery=expires로 쓰지 말 것.",
                            )
                            putJsonObject("properties") {
                                putJsonObject("absolute") {
                                    put("type", JsonPrimitive("string"))
                                    put("description", "예: '2025-08-25T18:00:00+09:00'. 현재: ${now}.")
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
                                "규칙 종료. **delivery보다 반드시 이후**. 현재=${now}. " +
                                    "'지금부터 5분간 받지마' → expires=현재+5분, delivery=현재. " +
                                    "delivery와 동일한 시각 금지.",
                            )
                            putJsonObject("properties") {
                                putJsonObject("absolute") {
                                    put("type", JsonPrimitive("string"))
                                    put("description", "예: '2025-08-25T18:00:00+09:00'. 현재: ${now}.")
                                }
                            }
                            putJsonArray("required") { add("absolute") }
                        }
                        putJsonObject("recurrence") {
                            put("type", JsonPrimitive("string"))
                            put(
                                "description",
                                "none|daily|weekly. 매일→daily, 월수금/평일→weekly. 기본 none.",
                            )
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
                            put("description", "반복 벽시계 시작 HH:mm. '저녁'만 있으면 18:00. none이면 null.")
                        }
                        putJsonObject("window_end") {
                            putJsonArray("type") {
                                add(JsonPrimitive("string"))
                                add(JsonPrimitive("null"))
                            }
                            put("description", "반복 벽시계 종료 HH:mm. '저녁'만 있으면 22:00. none이면 null.")
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
                    name = "extract_notification_target",
                    description = "mute(true/false)와 name/content/exceptions를 추출한다. " +
                        "**mute:** 받지마/차단/뮤트 → true. 받아/허용만 있고 받지마 없으면 false. " +
                        "'…만 받아줘' → mute=false. " +
                        "(1) 모든 알림 받지마 → mute=true, name=[], content=[], exceptions=[]. " +
                        "(2) 카톡 받지마 → mute=true, name=['카카오톡'], exceptions=[]. " +
                        "(3) 카톡만 받아줘 → mute=false, name=['카카오톡'], content=[], exceptions=[]. " +
                        "(4) 게임관련 카톡이랑 인스타만 받아줘 → mute=false, name=['카카오톡','인스타그램'], " +
                        "content=['게임'], exceptions=[]. " +
                        "약어: 카톡→카카오톡.",
                ) {
                    put("type", "object")
                    put("additionalProperties", false)
                    putJsonObject("properties") {
                        putJsonObject("mute") {
                            put("type", JsonPrimitive("boolean"))
                            put(
                                "description",
                                "받지마/차단/뮤트/끄/조용 → true. " +
                                    "받아/허용만 → false. '…만 받아줘'는 false. 받지마 없으면 false.",
                            )
                        }
                        putJsonObject("name") {
                            put("type", "array")
                            put(
                                "description",
                                "mute=true: 묵음할 앱(비우면 전체 묵음). " +
                                    "mute=false: 수신할 앱. " +
                                    "게임관련 카톡·인스타만 받아 → ['카카오톡','인스타그램'].",
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
                            put(
                                "description",
                                "키워드. 게임관련 …만 받아 → ['게임']. 없으면 [].",
                            )
                            putJsonObject("items") {
                                putJsonArray("type") {
                                    add(JsonPrimitive("string"))
                                    add(JsonPrimitive("null"))
                                }
                            }
                        }
                        putJsonObject("exceptions") {
                            put("type", "array")
                            put(
                                "description",
                                "mute=true일 때만 드물게 사용(묵음 예외 앱). 보통 []. " +
                                    "mute=false에서는 반드시 []. 허용 앱은 name에.",
                            )
                            putJsonObject("items") {
                                putJsonArray("type") {
                                    add(JsonPrimitive("string"))
                                    add(JsonPrimitive("null"))
                                }
                            }
                        }
                    }
                    putJsonArray("required") {
                        add("mute")
                        add("name")
                        add("content")
                        add("exceptions")
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
        var target: JsonObject? = null
        val myToolCalls = aiMessage.toolCalls

        if (!myToolCalls.isNullOrEmpty()) {
            chatMessages.appendMsg(aiMessage)
            for (myToolCall in myToolCalls) {
                if (myToolCall is ToolCall.Function) {
                    val toolName = myToolCall.function.name
                    val argumentsJson = myToolCall.function.argumentsAsJson()
                    if (toolName == "extract_notification_condition") {
                        condition = argumentsJson
                    } else if (toolName == "extract_notification_target") {
                        target = argumentsJson
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
                    content = "이제 주어진 결과를 바탕으로 답변할 차례다."
                }
            )
            aiResponse = getAiResponse(chatMessages, currentTime)
            aiMessage = aiResponse.choices.first().message
        }

        if (target == null || condition == null || target.isEmpty() || condition.isEmpty()) {
            chatMessages.appendMsg(
                chatMessage {
                    role = ChatRole.Assistant
                    content = aiMessage.content.orEmpty().ifBlank {
                        "알림 대상이나 조건이 제대로 추출되지 않았습니다. 다시 시도해주세요."
                    }
                }
            )
            return
        }

        Log.d("TEST_GPT", "target: $target")
        Log.d("TEST_GPT", "condition: $condition")

        fun JsonObject.optString(key: String): String? {
            val el = this[key] ?: return null
            if (el is JsonNull) return null
            return (el as? JsonPrimitive)?.contentOrNull
        }
        val recurrence = RecurrenceWindow.normalizeRecurrence(condition.optString("recurrence"))
        val daysCsv = when (val el = condition["days_of_week"]) {
            is kotlinx.serialization.json.JsonArray ->
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
        val muteFromModel = when (val el = target["mute"]) {
            is JsonPrimitive -> when {
                el.isString -> el.contentOrNull?.equals("true", ignoreCase = true)
                else -> runCatching { el.content.toBoolean() }.getOrNull()
            }
            else -> null
        }
        // 받지마 계열 없으면 무조건 mute=false (「…만 받아줘」 등)
        val muteHint = listOf("받지마", "차단", "뮤트", "mute", "끄", "조용")
            .any { prompt.contains(it, ignoreCase = true) }
        val muteFlag = if (muteHint) (muteFromModel ?: true) else false
        // handleIncomingRule에 보정된 mute 전달
        val targetFixed = kotlinx.serialization.json.buildJsonObject {
            target.forEach { (k, v) ->
                if (k != "mute") put(k, v)
            }
            put("mute", kotlinx.serialization.json.JsonPrimitive(muteFlag))
        }
        val title = deriveRuleTitleFromTarget(
            prompt = prompt,
            appNames = target.stringList("name"),
            exceptions = target.stringList("exceptions"),
            mute = muteFlag,
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
        val arr = el as? kotlinx.serialization.json.JsonArray ?: return emptyList()
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
