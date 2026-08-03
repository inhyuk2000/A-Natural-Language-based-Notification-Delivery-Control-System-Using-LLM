package com.example.app

import java.text.SimpleDateFormat
import java.util.*

class TimeUtils {

    fun getTodayDatetime(): String {
        val now = Calendar.getInstance().time
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        return sdf.format(now)
    }

    fun parseIsoToMillis(isoTime: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        val date = sdf.parse(isoTime)
        return date?.time ?: 0L
    }

    fun formatMillisToIso(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        return sdf.format(Date(millis))
    }
}