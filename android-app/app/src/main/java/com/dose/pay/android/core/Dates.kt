package com.dose.pay.android.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 日期工具（移植自原小程序 utils/format.js 的日期部分） */
object Dates {

    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val WEEKDAYS = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    /** LocalDate -> "YYYY-MM-DD" */
    fun toStr(date: LocalDate): String = date.format(ISO)

    /** "YYYY-MM-DD" -> LocalDate；非法返回 null */
    fun parse(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDate.parse(value.trim())
        } catch (e: Exception) {
            null
        }
    }

    fun today(): LocalDate = LocalDate.now()

    fun addDays(date: LocalDate, days: Long): LocalDate = date.plusDays(days)

    /** 星期文案（周日 / 周一 …） */
    fun weekdayLabel(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value % 7]

    /** "YYYY-MM-DD" -> "MM-DD" */
    fun shortDate(value: String?): String {
        val s = value ?: ""
        return if (s.length >= 10) s.substring(5, 10) else s
    }

    /** 某月天数（month 为 1..12） */
    fun daysInMonth(year: Int, month: Int): Int = java.time.YearMonth.of(year, month).lengthOfMonth()

    /** LocalDate -> UTC 毫秒（DatePicker 选中值口径） */
    fun toUtcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** UTC 毫秒 -> LocalDate */
    fun fromUtcMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    /** 本周末：最近的周六；若今天已经是周六或周日则取今天 */
    fun weekendFrom(today: LocalDate): LocalDate {
        val dow = today.dayOfWeek.value // 1=周一 … 7=周日
        if (dow == 6 || dow == 7) return today
        return addDays(today, (6 - dow).toLong())
    }
}
