package com.dosepay.app

import com.dosepay.app.core.Calculator
import com.dosepay.app.core.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 算法移植正确性测试。
 *
 * 用例来自 backend 的 CalculatorTest 语义，验证 App 侧实现与后端行为一致。
 * 这些是纯 JVM 单测，不需要 Android 设备。
 */
class CalculatorTest {

    private fun card(
        id: Long = 1,
        name: String = "测试卡",
        type: String = Calculator.CREDIT_CARD,
        limit: String? = "10000.00",
        used: String = "0.00",
        statementDay: Int? = 10,
        dueDay: Int? = 25,
        dueAfter: Int? = null,
        inclusive: Boolean = false,
    ) = PaymentMethod(
        id = id,
        name = name,
        methodType = type,
        creditLimit = limit?.let { BigDecimal(it) },
        currentUsed = BigDecimal(used),
        statementDay = statementDay,
        dueDay = dueDay,
        dueDaysAfterStatement = dueAfter,
        statementDayInclusive = inclusive,
    )

    /* ────────────── 基础：非信用卡 ────────────── */

    @Test
    fun `非信用卡即时扣款_还款日等于支付日`() {
        val c = card(type = Calculator.DEBIT_CARD, statementDay = null, dueDay = null)
        val pay = LocalDate.of(2026, 7, 29)
        assertEquals(pay, Calculator.calculateDueDate(pay, c))
    }

    @Test
    fun `非信用卡没有账单周期`() {
        val c = card(type = Calculator.E_WALLET, statementDay = null, dueDay = null)
        assertNull(Calculator.cycleWindow(LocalDate.of(2026, 7, 29), c))
    }

    /* ────────────── 账单日推算 ────────────── */

    @Test
    fun `支付日早于账单日_取本月账单日`() {
        val c = card(statementDay = 15, dueDay = 8)
        val pay = LocalDate.of(2026, 7, 10)
        // 账单日 15 → 本月 7/15；还款日 8 <= 15 顺延一月 → 8/8
        assertEquals(LocalDate.of(2026, 8, 8), Calculator.calculateDueDate(pay, c))
    }

    @Test
    fun `支付日晚于账单日_取次月账单日`() {
        val c = card(statementDay = 15, dueDay = 8)
        val pay = LocalDate.of(2026, 7, 20)
        // 下一账单日 8/15；还款日 8 <= 15 顺延 → 9/8
        assertEquals(LocalDate.of(2026, 9, 8), Calculator.calculateDueDate(pay, c))
    }

    @Test
    fun `账单日当天_inclusive为false算下一期`() {
        val c = card(statementDay = 10, dueDay = 25, inclusive = false)
        val pay = LocalDate.of(2026, 7, 10)
        // false → 下一账单日 8/10；还款日 25 > 10 → 8/25，占用 46 天
        val due = Calculator.calculateDueDate(pay, c)
        assertEquals(LocalDate.of(2026, 8, 25), due)
        assertEquals(46L, java.time.temporal.ChronoUnit.DAYS.between(pay, due))
    }

    @Test
    fun `账单日当天_inclusive为true算本期`() {
        val c = card(statementDay = 10, dueDay = 25, inclusive = true)
        val pay = LocalDate.of(2026, 7, 10)
        // true → 本期账单日 7/10；还款日 25 > 10 → 7/25，占用 15 天
        val due = Calculator.calculateDueDate(pay, c)
        assertEquals(LocalDate.of(2026, 7, 25), due)
        assertEquals(15L, java.time.temporal.ChronoUnit.DAYS.between(pay, due))
    }

    /* ────────────── 月末钳制 ────────────── */

    @Test
    fun `月末钳制_31日遇2月取28日`() {
        val c = card(statementDay = 31, dueDay = 15)
        val pay = LocalDate.of(2026, 1, 31)
        // 账单日 31 → 2 月钳制为 2/28；还款日 15 <= 28 顺延 → 3/15
        assertEquals(LocalDate.of(2026, 3, 15), Calculator.calculateDueDate(pay, c))
    }

    @Test
    fun `月末钳制_闰年2月取29日`() {
        val c = card(statementDay = 31, dueDay = 15)
        val pay = LocalDate.of(2028, 1, 31)
        // 2028 是闰年 → 2/29
        assertEquals(LocalDate.of(2028, 3, 15), Calculator.calculateDueDate(pay, c))
    }

    @Test
    fun `月末钳制_31日遇4月取30日`() {
        val c = card(statementDay = 31, dueDay = 15)
        val pay = LocalDate.of(2026, 4, 1)
        // 4 月只有 30 天 → 账单日 4/30；还款日 15 <= 30 顺延 → 5/15
        assertEquals(LocalDate.of(2026, 5, 15), Calculator.calculateDueDate(pay, c))
    }

    /* ────────────── 账单日后 N 天 ────────────── */

    @Test
    fun `账单日后N天模式`() {
        val c = card(statementDay = 10, dueDay = null, dueAfter = 20)
        val pay = LocalDate.of(2026, 7, 5)
        // 账单日 7/10 + 20 天 = 7/30
        assertEquals(LocalDate.of(2026, 7, 30), Calculator.calculateDueDate(pay, c))
    }

    /* ────────────── 推荐 ────────────── */

    @Test
    fun `推荐按占用天数降序`() {
        // A: 账单日 1 日、还款日 25 日 → 支付日 7/5 下一账单日 8/1，还款 8/25，占用 51 天
        // B: 账单日 20 日、还款日 5 日 → 支付日 7/5 账单日 7/20，还款 8/5，占用 31 天
        val a = card(id = 1, name = "A", statementDay = 1, dueDay = 25)
        val b = card(id = 2, name = "B", statementDay = 20, dueDay = 5)
        val pay = LocalDate.of(2026, 7, 5)
        val results = Calculator.recommend(listOf(a, b), BigDecimal("100.00"), pay, 3)

        assertEquals(2, results.size)
        assertTrue(results[0].occupiedDays >= results[1].occupiedDays)
        assertEquals("A", results[0].paymentMethod)
    }

    @Test
    fun `超额卡被过滤`() {
        val small = card(id = 1, name = "小额", limit = "1000.00", used = "900.00")
        val big = card(id = 2, name = "大额", limit = "50000.00", used = "0.00")
        val pay = LocalDate.of(2026, 7, 5)
        val results = Calculator.recommend(listOf(small, big), BigDecimal("200.00"), pay, 3)

        assertEquals(1, results.size)
        assertEquals("大额", results[0].paymentMethod)
    }

    @Test
    fun `不限额卡不过滤`() {
        val c = card(id = 1, limit = null, used = "999999.00")
        val results = Calculator.recommend(listOf(c), BigDecimal("100.00"), LocalDate.of(2026, 7, 5), 3)
        assertEquals(1, results.size)
    }

    @Test
    fun `TopN 截断`() {
        val list = (1..5).map { card(id = it.toLong(), name = "卡$it", limit = null) }
        val results = Calculator.recommend(list, BigDecimal("100.00"), LocalDate.of(2026, 7, 5), 3)
        assertEquals(3, results.size)
    }

    /* ────────────── 金额工具 ────────────── */

    @Test
    fun `银行家舍入_10点125归为10点12`() {
        // HALF_EVEN：10.125 → 10.12（不是 10.13）
        assertEquals("10.12", Calculator.formatAmount(BigDecimal("10.125")))
    }

    @Test
    fun `银行家舍入_10点135归为10点14`() {
        assertEquals("10.14", Calculator.formatAmount(BigDecimal("10.135")))
    }

    @Test
    fun `可用额度计算`() {
        assertEquals(
            "6000.00",
            Calculator.formatAmount(Calculator.availableLimit(BigDecimal("10000"), BigDecimal("4000")))
        )
        // 不限额返回 null
        assertNull(Calculator.availableLimit(null, BigDecimal("100")))
    }

    /* ────────────── 账单周期窗口 ────────────── */

    @Test
    fun `周期窗口_本还款日晚于今天`() {
        val c = card(statementDay = 25, dueDay = 19)
        val today = LocalDate.of(2026, 10, 28)
        val window = Calculator.cycleWindow(today, c)
        assertTrue(window != null)
        val (start, end) = window!!
        assertTrue("起点应早于终点", start.isBefore(end))
        assertTrue("终点不应早于今天", !end.isBefore(today))
    }

    @Test
    fun `周期窗口_边界_左开右闭`() {
        // 账单日 6 日、还款日 24 日，today = 2026-10-28
        // → 窗口 = (10-24, 11-24]
        val c = card(statementDay = 6, dueDay = 24)
        val today = LocalDate.of(2026, 10, 28)
        val (start, end) = Calculator.cycleWindow(today, c)!!

        assertEquals(LocalDate.of(2026, 10, 24), start)
        assertEquals(LocalDate.of(2026, 11, 24), end)

        // 左开：上一还款日当天刷的，还款日仍是本期终点 → 算本期
        assertEquals(end, Calculator.cycleEndFor(start, c))

        // 右闭：本还款日当天刷的，还款日已滚到下一期 → 不算本期
        assertEquals(LocalDate.of(2026, 12, 24), Calculator.cycleEndFor(end, c))

        // 今天刷的落在本期
        assertEquals(end, Calculator.cycleEndFor(today, c))
    }

    @Test
    fun `缺账单日的信用卡_抛异常`() {
        val c = card(statementDay = null, dueDay = null)
        var thrown = false
        try {
            Calculator.calculateDueDate(LocalDate.of(2026, 7, 5), c)
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("缺账单日应抛异常", thrown)
    }
}
