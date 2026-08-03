package com.example.app

import android.util.Log
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI

/** 최근 알림 폴더용 LLM 요약 (OpenAI Chat Completions) */
object NotifAiSummarizer {

    suspend fun summarizeAppNotifications(
        openAI: OpenAI,
        appDisplayName: String,
        items: List<NotifLogEntry>,
    ): String {
        if (items.isEmpty()) return "요약할 알림이 없습니다."

        // 요약은 목록에 보이는 개수와 무관하게 전체 알림 내용을 사용
        val bullet = items.joinToString("\n") { e ->
            val statusKo = when (e.status) {
                "completed", "delivered" -> "전송"
                else -> "지연"
            }
            val title = e.title.ifBlank { "(제목 없음)" }
            val text = e.text.ifBlank { "(내용 없음)" }
            "- [$statusKo] $title / $text"
        }

        val request = chatCompletionRequest {
            model = ModelId("gpt-4o-mini")
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = """
                        너는 모바일 알림을 자연스럽게 풀어 쓰는 요약기다. 앱($appDisplayName)에서 온 알림 전체를 읽고 한국어로 요약한다.

                        문체 (반드시 지킬 것):
                        - "~가 ~ 내용의 알림을 보냈어요.", "~에 대한 요청이 왔어요.", "~ 광고는 차단했어요." 처럼 주어+내용을 한 문장으로 풀어 쓴다.
                        - 여러 알림은 주제·의도별로 묶어서 한두 문장으로 압축한다. 전체 약 2줄.
                        - 전송/차단 등 결과는 필요할 때만 자연스럽게 덧붙인다.

                        금지:
                        - 제목·메시지를 쉼표로 나열하지 말 것. (예: "A, B, C와 같은 메시지를 보냈습니다" 금지)
                        - "다음과 같습니다", "알림 목록", 불릿, 번호, 따옴표, 머리말 금지.
                        - 원문 제목을 그대로 복붙하지 말고, 무슨 내용인지 해석해서 쓸 것.
                    """.trimIndent(),
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = """
                        앱: $appDisplayName
                        알림 ${items.size}건 (전체):
                        $bullet

                        위 전체를 주제별로 생각해서 2문장 이내로 풀어 요약해줘.
                    """.trimIndent(),
                ),
            )
        }

        return try {
            val completion = openAI.chatCompletion(request)
            completion.choices.firstOrNull()?.message?.content?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "요약을 생성하지 못했습니다."
        } catch (e: Exception) {
            Log.e("NotifAiSummarizer", "요약 실패: ${e.message}", e)
            "요약 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
        }
    }
}
