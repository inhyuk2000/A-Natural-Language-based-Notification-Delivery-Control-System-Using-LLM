package com.example.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceWindowTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private fun ms(date: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), seoul).toInstant().toEpochMilli()

    @Test
    fun daily_duringWindow_returnsToday() {
        val day = LocalDate.of(2026, 8, 4) // Tuesday
        val now = ms(day, 20, 30)
        val occ = RecurrenceWindow.computeOccurrenceWindow(
            nowMs = now,
            recurrence = RecurrenceWindow.DAILY,
            daysOfWeekCsv = null,
            windowStart = "20:00",
            windowEnd = "21:00",
        )
        assertEquals(ms(day, 20, 0), occ.deliveryMs)
        assertEquals(ms(day, 21, 0), occ.expiresMs)
    }

    @Test
    fun daily_afterWindow_rollsToTomorrow() {
        val day = LocalDate.of(2026, 8, 4)
        val now = ms(day, 21, 1)
        val occ = RecurrenceWindow.computeOccurrenceWindow(
            nowMs = now,
            recurrence = RecurrenceWindow.DAILY,
            daysOfWeekCsv = null,
            windowStart = "20:00",
            windowEnd = "21:00",
        )
        assertEquals(ms(day.plusDays(1), 20, 0), occ.deliveryMs)
        assertEquals(ms(day.plusDays(1), 21, 0), occ.expiresMs)
    }

    @Test
    fun weekly_monWedFri_skipsNonMatchingDays() {
        // 2026-08-04 is Tuesday → next is Wed 8/5
        val tuesday = LocalDate.of(2026, 8, 4)
        val now = ms(tuesday, 22, 0)
        val occ = RecurrenceWindow.computeOccurrenceWindow(
            nowMs = now,
            recurrence = RecurrenceWindow.WEEKLY,
            daysOfWeekCsv = "1,3,5",
            windowStart = "18:00",
            windowEnd = "22:00",
        )
        assertEquals(ms(LocalDate.of(2026, 8, 5), 18, 0), occ.deliveryMs)
        assertEquals(ms(LocalDate.of(2026, 8, 5), 22, 0), occ.expiresMs)
    }

    @Test
    fun overnight_window_spansMidnight() {
        val day = LocalDate.of(2026, 8, 4)
        val now = ms(day, 23, 30)
        val occ = RecurrenceWindow.computeOccurrenceWindow(
            nowMs = now,
            recurrence = RecurrenceWindow.DAILY,
            daysOfWeekCsv = null,
            windowStart = "22:00",
            windowEnd = "01:00",
        )
        assertEquals(ms(day, 22, 0), occ.deliveryMs)
        assertEquals(ms(day.plusDays(1), 1, 0), occ.expiresMs)
    }

    @Test
    fun overnight_afterMidnight_usesWindowStartedYesterday() {
        // 새벽 2시 → 어제 23:00 ~ 오늘 06:00 이 진행중
        val day = LocalDate.of(2026, 8, 4)
        val now = ms(day, 2, 0)
        val occ = RecurrenceWindow.computeOccurrenceWindow(
            nowMs = now,
            recurrence = RecurrenceWindow.DAILY,
            daysOfWeekCsv = null,
            windowStart = "23:00",
            windowEnd = "06:00",
        )
        assertEquals(ms(day.minusDays(1), 23, 0), occ.deliveryMs)
        assertEquals(ms(day, 6, 0), occ.expiresMs)
    }

    @Test
    fun daysLabel_formatsKorean() {
        assertEquals("월·수·금", RecurrenceWindow.daysOfWeekLabel("1,3,5"))
        assertEquals("매일", RecurrenceWindow.recurrenceLabel(RecurrenceWindow.DAILY, ""))
        assertTrue(RecurrenceWindow.isRecurring(RecurrenceWindow.WEEKLY))
    }
}
