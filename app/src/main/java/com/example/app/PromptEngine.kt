package com.example.app

import android.util.Log
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 자연어 → 규칙 JSON 추출.
 *
 * LLM Function Calling 은 LangChain FastAPI 백엔드(`/v1/extract-rule`)에서 수행하고,
 * 이 클래스는 API 호출 후 [ContextManager.handleIncomingRule] 로 저장한다.
 *
 * ok=false(재질문) 이면 [pendingOriginal] 을 보관하고, 다음 요청에 pending=true 로 보낸다.
 * 서버 앞단 분류 LLM이 보충/새 명령을 가른다.
 */
class PromptEngine(
    private val contextManager: ContextManager,
    private val chatMessages: MutableList<ChatMessage>,
    private val apiBaseUrl: String = BuildConfig.EXTRACT_RULE_API_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 재질문 대기 중인 최초 사용자 명령. null 이면 pending=false. */
    private var pendingOriginal: String? = null

    suspend fun handle(prompt: String, currentTime: String) {
        chatMessages.add(
            ChatMessage(role = ChatRole.User, content = prompt),
        )

        val base = apiBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) {
            appendAssistant(
                "추출 API 주소가 비어 있습니다. local.properties 에 EXTRACT_RULE_API_BASE_URL 을 설정하세요.",
            )
            return
        }

        val isPending = pendingOriginal != null
        val responseBody = try {
            withContext(Dispatchers.IO) {
                postExtractRule(
                    baseUrl = base,
                    prompt = prompt,
                    currentTime = currentTime,
                    pending = isPending,
                    pendingOriginal = pendingOriginal,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "extract-rule API 실패", e)
            appendAssistant("서버 연결 실패: ${e.message ?: e.javaClass.simpleName}")
            return
        }

        val root = try {
            json.parseToJsonElement(responseBody).jsonObject
        } catch (e: Exception) {
            appendAssistant("서버 응답 파싱 실패")
            return
        }

        val ok = when (val el = root["ok"]) {
            is JsonPrimitive ->
                el.contentOrNull?.equals("true", ignoreCase = true) == true ||
                    runCatching { el.content.toBoolean() }.getOrDefault(false)
            else -> false
        }

        val assistantMessage = root["assistantMessage"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val classification = root["pendingClassification"]?.jsonPrimitive?.contentOrNull
        if (classification != null) {
            Log.d(TAG, "pendingClassification=$classification")
        }

        if (!ok) {
            // 정보 부족 재질문만 pending. chitchat/앱매핑실패 등은 pending 안 함.
            val needsSupplement = when (val el = root["needsSupplement"]) {
                is JsonPrimitive ->
                    el.contentOrNull?.equals("true", ignoreCase = true) == true ||
                        runCatching { el.content.toBoolean() }.getOrDefault(false)
                else -> false
            }
            if (needsSupplement && pendingOriginal == null) {
                pendingOriginal = prompt
            }
            val failReason = root["failReason"]?.jsonPrimitive?.contentOrNull
            if (!needsSupplement && (
                    failReason == "chitchat" ||
                        failReason == "tool_conflict" ||
                        failReason == "cancelled"
                    )
            ) {
                pendingOriginal = null
            } else if (!needsSupplement && !isPending) {
                pendingOriginal = null
            }
            appendAssistant(
                assistantMessage.ifBlank {
                    "알림 대상이나 조건이 제대로 추출되지 않았습니다. " +
                        "‘카톡 받지마’ 또는 ‘카톡만 받아줘’처럼 다시 말씀해주세요."
                },
            )
            return
        }

        val targetFixed = root["targetFixed"]?.jsonObject
        val condition = root["condition"]?.jsonObject
        if (targetFixed == null || condition == null || condition.isEmpty()) {
            if (pendingOriginal == null) {
                pendingOriginal = prompt
            }
            appendAssistant(
                assistantMessage.ifBlank {
                    "알림 대상이나 조건이 제대로 추출되지 않았습니다."
                },
            )
            return
        }

        val mute = when (val el = targetFixed["mute"]) {
            is JsonPrimitive -> when {
                el.isString -> el.contentOrNull?.equals("true", ignoreCase = true) == true
                else -> runCatching { el.content.toBoolean() }.getOrDefault(true)
            }
            else -> true
        }
        val names = targetFixed.stringList("name")
        val packages = targetFixed.stringList("packages")
        val contents = targetFixed.stringList("content")

        if (!mute && names.isEmpty() && packages.isEmpty() && contents.isEmpty()) {
            if (pendingOriginal == null) {
                pendingOriginal = prompt
            }
            appendAssistant(
                assistantMessage.ifBlank {
                    "받을 앱이나 키워드가 없어요. ‘카톡만 받아줘’처럼 대상을 알려주세요."
                },
            )
            return
        }

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

        val titleSource = pendingOriginal?.let { "$it $prompt" } ?: prompt
        val title = deriveRuleTitleFromTarget(
            prompt = titleSource,
            appNames = names,
            exceptions = emptyList(),
            mute = mute,
            recurrence = recurrence,
            daysOfWeek = daysCsv,
            windowStart = windowStart,
            windowEnd = windowEnd,
        )

        val targetForDb = buildJsonObject {
            put("mute", JsonPrimitive(mute))
            putJsonArray("name") { names.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("packages") { packages.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("content") { contents.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("exceptions") { }
        }

        val success = contextManager.handleIncomingRule(
            targetForDb,
            condition,
            ruleTitle = title,
        )
        if (!success) {
            appendAssistant(
                assistantMessage.ifBlank { "규칙 등록에 실패했습니다. 앱 이름을 확인해 주세요." },
            )
            return
        }

        pendingOriginal = null
        appendAssistant(
            assistantMessage.ifBlank { "규칙을 등록했습니다: $title" },
        )
        Log.d(TAG, "rule saved title=$title mute=$mute")
    }

    private fun appendAssistant(content: String) {
        chatMessages.add(ChatMessage(role = ChatRole.Assistant, content = content))
    }

    private fun JsonObject.stringList(key: String): List<String> {
        val el = this[key] ?: return emptyList()
        val arr = el as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            if (item is JsonNull) null
            else item.jsonPrimitive.contentOrNull
        }.filter { it.isNotBlank() }
    }

    private fun postExtractRule(
        baseUrl: String,
        prompt: String,
        currentTime: String,
        pending: Boolean,
        pendingOriginal: String?,
    ): String {
        val url = URL("$baseUrl/v1/extract-rule")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 60_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        val payload = buildJsonObject {
            put("prompt", prompt)
            put("currentTime", currentTime)
            put("installedApps", AppNameMapper.getInstalledAppsJsonArray())
            put("pending", pending)
            if (pending && !pendingOriginal.isNullOrBlank()) {
                put("pendingOriginal", pendingOriginal)
            }
        }.toString()
        payload.chunked(3000).forEachIndexed { i, part ->
            Log.d(TAG, "payload[$i]=$part")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: $body")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "PromptEngine"
    }
}
