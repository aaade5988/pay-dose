package com.dose.pay.android.core

import androidx.compose.ui.graphics.Color
import com.dose.pay.android.data.MethodDto
import com.dose.pay.android.ui.theme.PayColors
import java.time.LocalDate

/** chip 配色语义 */
enum class ChipStyle { PRIMARY, ACCENT, GRAY, GREEN, RED }

/** 环形阈值状态 */
data class RingStatus(val key: String, val color: Color, val label: String)

/** 看板单卡行模型 */
data class DashRow(
    val id: Long,
    val name: String,
    val methodType: String,
    val initial: String,
    val color: Color,
    val hasLimit: Boolean,
    val currentUsed: String,
    val pct: Double?,
    val ringNone: Boolean,
    val ringP: Float,
    val ringColor: Color,
    val ringStatusKey: String,
    val pctText: String,
    val usedText: String,
    val availText: String,
    val availNegative: Boolean,
    val limitText: String,
    val moneyLine: String,
    val overText: String,
    val statusLabel: String,
    val statusChip: ChipStyle,
)

/** 聚合仪表盘模型（看板顶部环：本期消费 ÷ Σ已设额度） */
data class AggUsage(
    val count: Int,
    val limitedCount: Int,
    val hasLimited: Boolean,
    /** 占用率 = (本期消费 − 已还清) ÷ Σ已设额度 */
    val pct: Double?,
    val pctText: String,
    /** 主色弧长（占用部分） */
    val gaugeP: Float,
    /** 冻结弧长（已还清部分，另一种颜色） */
    val frozenP: Float,
    val usedText: String,
    /** 已还清金额（冻结，不计入占用） */
    val settledText: String,
    val hasSettled: Boolean,
    val limitText: String,
    val subText: String,
)

/**
 * 支付方式领域工具（移植自原小程序 utils/method.js）。
 * 真正的推荐算法在后端 /api/recommend，这里只做展示层的日期辅助与聚合。
 *
 * 注：原看板「单卡占用」区块已下线，[DashRow]/[toDashRow] 仅保留给
 * 「更新已用」弹层取当前已用与额度，不再驱动页面列表渲染。
 */
object Methods {

    const val CREDIT_CARD = "信用卡"
    const val DEBIT_CARD = "储蓄卡"
    const val E_WALLET = "电子钱包"
    const val OTHER = "其他"

    /** 契约枚举（后端 method_type 取值） */
    val METHOD_TYPES = listOf(CREDIT_CARD, DEBIT_CARD, E_WALLET, OTHER)

    /** 分段控件短标签（与 METHOD_TYPES 一一对应） */
    val METHOD_TYPE_LABELS = listOf("信用卡", "储蓄卡", "钱包", "其他")

    fun isCreditCard(method: MethodDto): Boolean = method.methodType == CREDIT_CARD

    /** 名称首字（用 codePoint 处理代理对） */
    fun nameInitial(name: String?): String {
        val text = (name ?: "").trim()
        if (text.isEmpty()) return "?"
        val cp = text.codePointAt(0)
        return String(Character.toChars(cp))
    }

    /** 按名称首字哈希循环取色，保证同一名称颜色稳定 */
    fun avatarColor(name: String?): Color {
        val initial = nameInitial(name)
        val code = initial.codePointAt(0)
        return PayColors.AvatarPalette[code % PayColors.AvatarPalette.size]
    }

    /** 类型 chip 样式：信用卡=主色 / 钱包=橙 / 其他=灰 */
    fun typeChipStyle(methodType: String): ChipStyle = when (methodType) {
        CREDIT_CARD -> ChipStyle.PRIMARY
        E_WALLET -> ChipStyle.ACCENT
        else -> ChipStyle.GRAY
    }

    /** 是否已设额度（'' / null 视为未设） */
    fun limitSet(method: MethodDto): Boolean = !method.creditLimit.isNullOrBlank()

    /** 账单 / 还款规则摘要 */
    fun ruleSummary(method: MethodDto): String {
        if (!isCreditCard(method)) return "即时扣款 · 不产生占用"
        val statement = if (method.statementDay != null) "账单日 " + method.statementDay + " 日" else "未设账单日"
        if (method.dueDay != null) return statement + " · 还款日 " + method.dueDay + " 日"
        if (method.dueDaysAfterStatement != null) return statement + " · 账单日后 " + method.dueDaysAfterStatement + " 天"
        return statement + " · 未设还款规则"
    }

    /** 卡片列表一行规则文案（信用卡附带额度） */
    fun ruleLine(method: MethodDto): String {
        if (!isCreditCard(method)) return "即时扣款 · 不产生占用"
        var text = ruleSummary(method)
        if (limitSet(method)) text += " · 额度 " + Money.moneyCompact(method.creditLimit)
        return text
    }

    /** 该方式还款日摘要 */
    fun dueSummary(method: MethodDto): String {
        if (!isCreditCard(method)) return "即时扣款"
        if (method.dueDay != null) return "每月 " + method.dueDay + " 日"
        if (method.dueDaysAfterStatement != null) return "账单日后 " + method.dueDaysAfterStatement + " 天"
        return "未设置"
    }

    /**
     * 占用率 -> 阈值状态：<50% 安全(绿) / 50–79%+ 偏高(橙) / >=80% 吃紧(红)；null=未设额度。
     */
    fun usageStatus(pct: Double?): RingStatus {
        if (pct == null || !pct.isFinite()) return RingStatus("none", PayColors.RingTrack, "未设额度")
        return when {
            pct >= 80.0 -> RingStatus("bad", PayColors.RingBad, "吃紧")
            pct >= 50.0 -> RingStatus("warn", PayColors.RingWarn, "偏高")
            else -> RingStatus("ok", PayColors.RingOk, "安全")
        }
    }

    /**
     * 缺口环弧长参数：--p = 占用率 × 75%（整环只有 270°）；占用率钳到 0..100。
     */
    fun ringArc(pct: Double?): Float {
        if (pct == null || !pct.isFinite()) return 0f
        val p = pct.coerceIn(0.0, 100.0)
        return (p * 0.75).toFloat()
    }

    /** 看板单卡行模型 */
    fun toDashRow(method: MethodDto): DashRow {
        val hasLimit = limitSet(method)
        val usedText0 = method.currentUsed.ifBlank { "0" }
        val pct = if (hasLimit) Money.percentOf(usedText0, method.creditLimit) else null
        val none = pct == null
        val st = usageStatus(pct)
        val isDebit = method.methodType == DEBIT_CARD
        val usedC = Money.toCents(usedText0) ?: 0L
        val limitC = if (none) null else Money.toCents(method.creditLimit)
        val hasAvailable = !method.availableLimit.isNullOrBlank()
        val availStr: String? = when {
            hasAvailable -> method.availableLimit
            limitC == null -> null
            else -> Money.subAmount(method.creditLimit, usedText0)
        }
        val availNegative = availStr != null && (Money.toCents(availStr) ?: 0L) < 0L

        var overText = ""
        if (limitC != null && usedC > limitC) {
            overText = "超额度 " + Money.moneyCompact(Money.fromCents(usedC - limitC))
        }

        val moneyLine = if (none) {
            if (isDebit) "即时扣款 · 不计占用" else "不限额 · 不计入聚合 · 虚线灰环"
        } else {
            "已用 " + Money.moneyCompact(usedText0) +
                " · 可用 " + Money.moneyCompact(availStr) +
                " / 额度 " + Money.moneyCompact(method.creditLimit)
        }

        val statusLabel = if (none) (if (isDebit) "即时扣款" else "未设额度") else st.label
        val statusChip = if (none) ChipStyle.GRAY else when (st.key) {
            "ok" -> ChipStyle.GREEN
            "warn" -> ChipStyle.ACCENT
            else -> ChipStyle.RED
        }

        return DashRow(
            id = method.id,
            name = method.name,
            methodType = method.methodType,
            initial = nameInitial(method.name),
            color = avatarColor(method.name),
            hasLimit = hasLimit,
            currentUsed = usedText0,
            pct = pct,
            ringNone = none,
            ringP = ringArc(pct),
            ringColor = st.color,
            ringStatusKey = st.key,
            pctText = if (none) "—" else Math.round(pct).toInt().toString() + "%",
            usedText = Money.moneyCompact(usedText0),
            availText = if (availStr == null) "不限额" else Money.moneyCompact(availStr),
            availNegative = availNegative,
            limitText = if (none) "不限额" else Money.moneyCompact(method.creditLimit),
            moneyLine = moneyLine,
            overText = overText,
            statusLabel = statusLabel,
            statusChip = statusChip,
        )
    }

    /**
     * 聚合仪表盘口径：**本账单周期消费合计 / Σ已设额度**（分整数）。
     *
     * 分母只算「已设额度且额度 > 0」的卡。分子用本期消费而不是 Σcurrent_used：
     * 后者跨周期累积，会出现「本期一笔没刷、环里却显示有钱」的错读
     * （2026-10-28 用户反馈：环 222 与下方本期 0 打架）。
     *
     * 「已还清」是人工标记：[settledCents] 是被标记卡的本期消费，
     * 它们**冻结**在环上（另一种颜色），不再计入占用率：
     * 占用率 = (本期消费 − 已还清) ÷ Σ额度。
     */
    fun cycleGauge(methods: List<MethodDto>, spendCents: Long, settledCents: Long = 0L): AggUsage {
        var limitC = 0L
        var limitedCount = 0
        for (m in methods) {
            if (!limitSet(m)) continue
            val l = Money.toCents(m.creditLimit) ?: continue
            if (l <= 0L) continue
            limitC += l
            limitedCount++
        }
        val spendC = spendCents.coerceAtLeast(0L)
        val settledC = settledCents.coerceIn(0L, spendC)
        val usedC = spendC - settledC
        val pct = if (limitC > 0L) Math.round(usedC * 1000.0 / limitC) / 10.0 else null
        val frozenPct = if (limitC > 0L) Math.round(settledC * 1000.0 / limitC) / 10.0 else null
        val settledText = Money.moneyCompact(Money.fromCents(settledC))
        return AggUsage(
            count = methods.size,
            limitedCount = limitedCount,
            hasLimited = limitedCount > 0,
            pct = pct,
            pctText = if (pct == null) "—" else Math.round(pct).toInt().toString() + "%",
            gaugeP = ringArc(pct),
            frozenP = ringArc(frozenPct),
            usedText = Money.moneyCompact(Money.fromCents(usedC)),
            settledText = settledText,
            hasSettled = settledC > 0L,
            limitText = Money.moneyCompact(Money.fromCents(limitC)),
            subText = "本期消费 ÷ Σ已设额度 · 仅计入 " + limitedCount + "/" + methods.size + " 张设额度卡" +
                if (settledC > 0L) " · 已还清 " + settledText + " 已冻结（不计入占用）" else "",
        )
    }

    /** 月末钳制：day 超过当月天数时取当月最后一天 */
    fun clampDay(year: Int, month: Int, day: Int): Int = minOf(day, Dates.daysInMonth(year, month))

    /**
     * 下一个账单日（>= from），与后端 Calculator.nextStatementDate 保持一致。
     */
    fun nextStatementDate(from: LocalDate, statementDay: Int, inclusive: Boolean): LocalDate {
        val day = if (statementDay > 0) statementDay else 1
        var year = from.year
        var month = from.monthValue
        var candidate = LocalDate.of(year, month, clampDay(year, month, day))
        val accept = if (inclusive) !from.isAfter(candidate) else from.isBefore(candidate)
        if (accept) return candidate
        month += 1
        if (month > 12) {
            month = 1
            year += 1
        }
        return LocalDate.of(year, month, clampDay(year, month, day))
    }

    /**
     * 「下账单日前」快捷日期：所有信用卡中最近一个账单日的前一天（至少是今天）。
     * 返回 null 表示没有可用信用卡。
     */
    fun dayBeforeNearestStatement(methods: List<MethodDto>, from: LocalDate): Pair<LocalDate, String>? {
        val cards = methods.filter { isCreditCard(it) && it.statementDay != null && it.statementDay > 0 }
        if (cards.isEmpty()) return null
        var bestDate: LocalDate? = null
        var bestName = ""
        for (card in cards) {
            val next = nextStatementDate(from, card.statementDay!!, card.statementDayInclusive)
            if (bestDate == null || next.isBefore(bestDate)) {
                bestDate = next
                bestName = card.name
            }
        }
        var target = Dates.addDays(bestDate!!, -1)
        if (target.isBefore(from)) target = from
        return target to bestName
    }
}
