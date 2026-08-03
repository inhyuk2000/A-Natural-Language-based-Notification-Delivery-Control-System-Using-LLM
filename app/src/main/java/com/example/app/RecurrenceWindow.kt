package com.example.app

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 반복 규칙의 현재/다음 회차 [delivery, expires) 절대 시각을 계산한다.
 * 요일: ISO-8601 (월=1 … 일=7).
 */
object RecurrenceWindow {
    const val NONE = "none"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"

    private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    private val ISO_OFFSET: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    data class Occurrence(
        val deliveryIso: String,
        val expiresIso: String,
        val deliveryMs: Long,
        val expiresMs: Long,
    )

    fun normalizeRecurrence(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            DAILY -> DAILY
            WEEKLY -> WEEKLY
            else -> NONE
        }
    }

    fun isRecurring(recurrence: String?): Boolean {
        val r = normalizeRecurrence(recurrence)
        return r == DAILY || r == WEEKLY
    }

    fun parseDaysOfWeek(csv: String?): Set<Int> {
        if (csv.isNullOrBlank()) return emptySet()
        return csv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()
    }

    fun daysOfWeekLabel(csv: String?): String {
        val days = parseDaysOfWeek(csv).sorted()
        if (days.isEmpty()) return ""
        val names = mapOf(
            1 to "월", 2 to "화", 3 to "수", 4 to "목", 5 to "금", 6 to "토", 7 to "일",
        )
        return days.joinToString("·") { names[it] ?: it.toString() }
    }

    fun recurrenceLabel(recurrence: String?, daysOfWeek: String?): String {
        return when (normalizeRecurrence(recurrence)) {
            DAILY -> "매일"
            WEEKLY -> daysOfWeekLabel(daysOfWeek).ifBlank { "매주" }
            else -> ""
        }
    }

    fun parseHm(hm: String): Pair<Int, Int> {
        val parts = hm.trim().split(":")
        require(parts.size >= 2) { "invalid HH:mm: $hm" }
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        require(h in 0..23 && m in 0..59) { "invalid HH:mm: $hm" }
        return h to m
    }

    fun formatIso(zdt: ZonedDateTime): String = zdt.format(ISO_OFFSET)

    /**
     * @return 지금 진행 중인 회차, 없으면 아직 시작 전인 가장 가까운 회차
     */
    fun computeOccurrenceWindow(
        nowMs: Long,
        recurrence: String,
        daysOfWeekCsv: String?,
        windowStart: String,
        windowEnd: String,
        zoneId: ZoneId = SEOUL,
    ): Occurrence {
        val kind = normalizeRecurrence(recurrence)
        require(kind != NONE) { "recurrence must be daily or weekly" }

        val (startH, startM) = parseHm(windowStart)
        val (endH, endM) = parseHm(windowEnd)
        val allowedDays: Set<Int> = when (kind) {
            DAILY -> (1..7).toSet()
            WEEKLY -> {
                val days = parseDaysOfWeek(daysOfWeekCsv)
                require(days.isNotEmpty()) { "weekly requires days_of_week" }
                days
            }
            else -> emptySet()
        }

        val nowZdt = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val today = nowZdt.toLocalDate()
        val overnight = !LocalTime.of(endH, endM).isAfter(LocalTime.of(startH, startM))

        var active: Occurrence? = null
        var nextFuture: Occurrence? = null

        // 자정 넘김(23:00–06:00): 새벽에는 어제 시작한 회차가 진행 중일 수 있음 → offset -1부터
        val startOffset = if (overnight) -1L else 0L
        for (offset in startOffset..8L) {
            val day: LocalDate = today.plusDays(offset)
            if (day.dayOfWeek.value !in allowedDays) continue

            val start = ZonedDateTime.of(day, LocalTime.of(startH, startM), zoneId)
            var end = ZonedDateTime.of(day, LocalTime.of(endH, endM), zoneId)
            if (!end.isAfter(start)) {
                end = end.plusDays(1)
            }

            val startMs = start.toInstant().toEpochMilli()
            val endMs = end.toInstant().toEpochMilli()
            val occ = Occurrence(
                deliveryIso = formatIso(start),
                expiresIso = formatIso(end),
                deliveryMs = startMs,
                expiresMs = endMs,
            )

            if (nowMs in startMs until endMs) {
                active = occ
                break
            }
            if (nowMs < startMs && nextFuture == null) {
                nextFuture = occ
            }
        }

        return active ?: nextFuture
            ?: error("no occurrence window found for recurrence=$kind days=$daysOfWeekCsv")
    }

    /** delivery/expires ISO에서 HH:mm 추출 (Asia/Seoul) */
    fun hmFromIso(iso: String, zoneId: ZoneId = SEOUL): String {
        val ms = TimeUtils().parseIsoToMillis(iso)
        val zdt = Instant.ofEpochMilli(ms).atZone(zoneId)
        return "%02d:%02d".format(zdt.hour, zdt.minute)
    }
}
