package com.example.campusmate.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Small date helpers for repository queries and demo data. */
object DateTimeUtils {
    private const val DATE_PATTERN = "yyyy-MM-dd"

    fun nowMillis(): Long = System.currentTimeMillis()

    fun todayDate(): String = formatDate(nowMillis())

    fun formatDate(timeMillis: Long): String {
        return SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date(timeMillis))
    }

    fun parseDateMillis(date: String): Long? {
        return runCatching {
            SimpleDateFormat(DATE_PATTERN, Locale.US).apply { isLenient = false }
                .parse(date)
                ?.time
        }.getOrNull()
    }

    fun formatDateTime(timeMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timeMillis))
    }

    fun currentWeekday(): Int {
        return androidDayOfWeekToCourseWeekday(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
    }

    fun weekdayForDate(date: String): Int {
        val millis = parseDateMillis(date) ?: return currentWeekday()
        return weekdayForMillis(millis)
    }

    fun weekdayForMillis(timeMillis: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        return androidDayOfWeekToCourseWeekday(calendar.get(Calendar.DAY_OF_WEEK))
    }

    fun startOfTodayMillis(): Long {
        return startOfDay(Calendar.getInstance()).timeInMillis
    }

    fun endOfTodayMillis(): Long {
        val calendar = startOfDay(Calendar.getInstance())
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.timeInMillis - 1L
    }

    fun startOfDayMillis(timeMillis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        return startOfDay(calendar).timeInMillis
    }

    fun endOfDayMillis(timeMillis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        startOfDay(calendar)
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.timeInMillis - 1L
    }

    fun datePlusDays(date: String, days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = parseDateMillis(date) ?: nowMillis()
        startOfDay(calendar)
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return formatDate(calendar.timeInMillis)
    }

    fun startOfWeekDate(): String {
        val calendar = startOfDay(Calendar.getInstance())
        val weekday = currentWeekday()
        calendar.add(Calendar.DAY_OF_MONTH, -(weekday - 1))
        return formatDate(calendar.timeInMillis)
    }

    private fun startOfDay(calendar: Calendar): Calendar {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar
    }

    private fun androidDayOfWeekToCourseWeekday(androidDayOfWeek: Int): Int {
        return when (androidDayOfWeek) {
            Calendar.SUNDAY -> 7
            else -> androidDayOfWeek - 1
        }
    }
}
