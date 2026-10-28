package com.dosepay.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 格式化与数值工具（移植自原小程序 utils/format.js 的金额与日期部分）。
 *
 * 约定：
 * - 金额一律先转「分」整数再运算，全程不出现浮点直加；
 * - 百分比是比例而非金额，沿用规格书的 Double 口径；
 * - 日期统一 "YYYY-MM-DD" 字符串与 [LocalDate] 互转。
 */
object Money {

    private val NON_DIGIT = Regex("[^\\d.]")
    private val GROUP_SEPARATOR = Regex("\\B(?=(\\d{3})+(?!\\d))")
    private val LEADING_ZERO = Regex("^0+(?=\\d)")
    private val CLEAN_SYMBOL = Regex("[,\\s¥￥]")
    private val AMOUNT_SHAPE = Regex("^-?\\d*(\\.\\d*)?$")
    private val INPUT_SHAPE = Regex("^\\d{0,12}(\\.\\d{0,2})?$")
    private val CENTS_SHAPE = Regex("^(-?)(\\d+)(?:\\.(\\d{1,2}))?$")

    /** 千分位格式化（只做字符串分组，不改变数值） */
    fun formatAmount(value: String?, decimals: Int = 2, withSign: Boolean = false): String {
        var raw = (value ?: "").trim()
        if (raw.isEmpty()) raw = "0"
        raw = raw.replace(CLEAN_SYMBOL, "")
        if (raw.isEmpty() || raw == "-" || raw == "." || raw == "-.") return padZero(decimals, withSign)

        var negative = false
        if (raw.startsWith("-")) {
            negative = true
            raw = raw.substring(1)
        }
        if (!AMOUNT_SHAPE.matches(raw) || raw.isEmpty()) return padZero(decimals, withSign)

        val parts = raw.split(".")
        var intPart = parts[0].ifEmpty { "0" }
        var fracPart = if (parts.size > 1) parts[1] else ""
        // 小数位截断（不做四舍五入，避免精度争议；后端已保证两位）
        if (fracPart.length > decimals) fracPart = fracPart.substring(0, decimals)
        while (fracPart.length < decimals) fracPart += "0"
        if (decimals == 0) fracPart = ""

        intPart = intPart.replace(LEADING_ZERO, "")
        val grouped = intPart.replace(GROUP_SEPARATOR, ",")
        val body = if (decimals > 0) "$grouped.$fracPart" else grouped
        if (negative) return (if (withSign) "- " else "-") + body
        return if (withSign) "+ $body" else body
    }

    private fun padZero(decimals: Int, withSign: Boolean): String {
        val prefix = if (withSign) "+ " else ""
        return if (decimals > 0) prefix + "0." + "0".repeat(decimals) else prefix + "0"
    }

    /** 金额展示：12345.60 -> ¥12,345.60；负数使用 U+2212 减号 */
    fun money(value: String?): String {
        val raw = (value ?: "").trim().replace(CLEAN_SYMBOL, "")
        return if (raw.startsWith("-")) "−¥" + formatAmount(raw.substring(1)) else "¥" + formatAmount(raw)
    }

    /** 省略末尾 .00 的展示：10000.00 -> ¥10,000 */
    fun moneyCompact(value: String?): String {
        val raw = (value ?: "").trim().replace(CLEAN_SYMBOL, "")
        val negative = raw.startsWith("-")
        val full = formatAmount(if (negative) raw.substring(1) else raw)
        val body = if (full.endsWith(".00")) full.substring(0, full.length - 3) else full
        return (if (negative) "−¥" else "¥") + body
    }

    /** 额度展示：null / 空串视为不限额 */
    fun limitText(value: String?): String {
        if (value == null || value.trim().isEmpty()) return "不限额"
        return money(value)
    }

    /** 逐键清洗：只留数字与第一个小数点，整数最多 12 位、小数最多 2 位，去前导零 */
    fun sanitizeAmountInput(value: String?): String {
        var raw = (value ?: "").replace(NON_DIGIT, "")
        val firstDot = raw.indexOf('.')
        if (firstDot >= 0) {
            raw = raw.substring(0, firstDot + 1) + raw.substring(firstDot + 1).replace(".", "")
        }
        val parts = raw.split(".")
        var intPart = parts[0].take(12).replace(LEADING_ZERO, "")
        if (parts.size > 1 && intPart.isEmpty()) intPart = "0"
        if (parts.size == 1) return intPart
        return intPart + "." + parts[1].take(2)
    }

    /** 校验并规范化金额输入；非法返回 null，空串表示未填 */
    fun normalizeAmountInput(value: String?): String? {
        if (value == null) return ""
        val raw = value.trim().replace(CLEAN_SYMBOL, "")
        if (raw.isEmpty() || raw == ".") return ""
        if (!INPUT_SHAPE.matches(raw)) return null
        val parts = raw.split(".")
        val intPart = (parts[0].ifEmpty { "0" }).replace(LEADING_ZERO, "")
        return if (parts.size > 1) intPart + "." + parts[1] else intPart
    }

    /** 定稿补全："12." -> "12.00"，"7.5" -> "7.50" */
    fun finalizeAmountInput(value: String?): String {
        val norm = normalizeAmountInput(value) ?: return ""
        val parts = norm.split(".")
        if (parts.size == 1) return parts[0]
        val frac = parts[1]
        if (frac.isEmpty()) return parts[0] + ".00"
        if (frac.length == 1) return parts[0] + "." + frac + "0"
        return parts[0] + "." + frac
    }

    /** 字符串金额 -> 分（整数）；非法返回 null */
    fun toCents(value: String?): Long? {
        if (value == null) return null
        val raw = value.trim().replace(CLEAN_SYMBOL, "")
        if (raw.isEmpty() || raw == "-") return null
        val m = CENTS_SHAPE.find(raw) ?: return null
        val sign = if (m.groupValues[1] == "-") -1L else 1L
        val frac = m.groupValues[3].padEnd(2, '0')
        // 超出 Long 范围的数字串按非法处理，避免溢出
        val intValue = m.groupValues[2].toLongOrNull() ?: return null
        val fracValue = frac.toLongOrNull() ?: return null
        return sign * (intValue * 100 + fracValue)
    }

    /** 分 -> 两位小数字符串 */
    fun fromCents(cents: Long): String {
        val negative = cents < 0
        val abs = if (negative) -cents else cents
        val intPart = abs / 100
        val frac = abs % 100
        val fracText = if (frac < 10) "0$frac" else "$frac"
        return (if (negative) "-" else "") + intPart + "." + fracText
    }

    /** 两金额相减（字符串入、字符串出；可为负） */
    fun subAmount(a: String?, b: String?): String = fromCents((toCents(a) ?: 0L) - (toCents(b) ?: 0L))

    /**
     * 占用百分比（分 -> 分，整数运算，一位小数，不钳制——超限卡可 >100）；
     * 分母 <= 0 或不限额返回 null。
     */
    fun percentOfCents(usedCents: Long, limitCents: Long?): Double? {
        if (limitCents == null || limitCents <= 0) return null
        return Math.round(usedCents * 1000.0 / limitCents) / 10.0
    }

    /** 占用百分比（字符串入） */
    fun percentOf(used: String?, limit: String?): Double? {
        val usedC = toCents(used) ?: return null
        return percentOfCents(usedC, toCents(limit))
    }
}

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
    fun daysInMonth(year: Int, month: Int): Int = YearMonth.of(year, month).lengthOfMonth()

    /** LocalDate -> UTC 毫秒（DatePicker 选中值口径） */
    fun toUtcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** UTC 毫秒 -> LocalDate */
    fun fromUtcMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
