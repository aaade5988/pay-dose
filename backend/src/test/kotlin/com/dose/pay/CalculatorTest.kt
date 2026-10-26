package com.dose.pay

import com.dose.pay.domain.Calculator
import com.dose.pay.domain.PaymentMethod
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 核心算法验证。
 *
 * 前 3 个用例是原 credit-payment-planner `tests/test_calculator.py` 的 1:1 翻译，
 * 其余覆盖月末钳制、两种还款模式、账单日归属、额度过滤、金额量化等边界。
 */
class CalculatorTest {

    private fun card(
        id: Long,
        name: String,
        limit: String? = "20000",
        used: String = "5000",
        statementDay: Int? = 10,
        dueDay: Int? = 25,
        dueDaysAfter: Int? = null,
        inclusive: Boolean = false,
        type: String = Calculator.CREDIT_CARD,
    ) = PaymentMethod(
        id = id,
        name = name,
        methodType = type,
        creditLimit = limit?.let { BigDecimal(it) },
        currentUsed = BigDecimal(used),
        statementDay = statementDay,
        dueDay = dueDay,
        dueDaysAfterStatement = dueDaysAfter,
        statementDayInclusive = inclusive,
    )

    // ---------- 原项目测试用例 1:1 翻译 ----------

    /** 账单日后消费走下一期 */
    @Test
    fun `payment after statement uses next cycle`() {
        val dueDate = Calculator.calculateDueDate(LocalDate.of(2026, 7, 11), card(1, "测试信用卡"))
        assertEquals(LocalDate.of(2026, 8, 25), dueDate)
    }

    /** 账单日前消费走本期 */
    @Test
    fun `payment before statement uses current cycle`() {
        val dueDate = Calculator.calculateDueDate(LocalDate.of(2026, 7, 9), card(1, "测试信用卡"))
        assertEquals(LocalDate.of(2026, 7, 25), dueDate)
    }

    /** 排序 + 超额过滤 */
    @Test
    fun `recommendations sorted and over limit excluded`() {
        val main = card(1, "测试信用卡", dueDay = 25)
        val shorter = card(2, "较短信用卡", used = "1000", statementDay = 20, dueDay = 15)
        val overLimit = card(3, "额度不足信用卡", limit = "10000", used = "9000", statementDay = 1, dueDay = 10)

        val results = Calculator.recommend(
            listOf(shorter, main, overLimit),
            BigDecimal("5000"),
            LocalDate.of(2026, 7, 11),
        )

        assertEquals(listOf("测试信用卡", "较短信用卡"), results.map { it.paymentMethod })
        assertTrue(results[0].occupiedDays >= results[1].occupiedDays)
    }

    // ---------- 边界用例 ----------

    /** 非信用卡即时扣款：占用 0 天 */
    @Test
    fun `non credit card settles immediately`() {
        val debit = card(9, "储蓄卡", type = Calculator.DEBIT_CARD, statementDay = null, dueDay = null)
        val date = LocalDate.of(2026, 7, 11)
        assertEquals(date, Calculator.calculateDueDate(date, debit))

        val results = Calculator.recommend(listOf(debit), BigDecimal("100"), date)
        assertEquals(1, results.size)
        assertEquals(0, results[0].occupiedDays)
        assertEquals("即时扣款，占用天数为 0 天", results[0].reason)
    }

    /** 月末钳制：账单日 31、2 月支付 → 账单日落在 2 月最后一天，还款日顺延到 3 月 31 */
    @Test
    fun `statement day clamps to month end`() {
        val method = card(1, "月末卡", statementDay = 31, dueDay = 31)
        assertEquals(
            LocalDate.of(2026, 2, 28),
            Calculator.nextStatementDate(LocalDate.of(2026, 2, 15), 31, false),
        )
        assertEquals(LocalDate.of(2026, 3, 31), Calculator.calculateDueDate(LocalDate.of(2026, 2, 15), method))
    }

    /** 闰年 2 月：2028 年 2 月 29 天 */
    @Test
    fun `leap year february clamps to 29`() {
        assertEquals(
            LocalDate.of(2028, 2, 29),
            Calculator.nextStatementDate(LocalDate.of(2028, 2, 15), 31, false),
        )
    }

    /** 账单日后 N 天模式 */
    @Test
    fun `due days after statement mode`() {
        val method = card(1, "N天卡", statementDay = 10, dueDay = null, dueDaysAfter = 20)
        // 支付 7/9 → 账单日 7/10 → 还款日 7/30
        assertEquals(LocalDate.of(2026, 7, 30), Calculator.calculateDueDate(LocalDate.of(2026, 7, 9), method))
        // 支付 7/11 → 账单日 8/10 → 还款日 8/30
        assertEquals(LocalDate.of(2026, 8, 30), Calculator.calculateDueDate(LocalDate.of(2026, 7, 11), method))
    }

    /** 账单日后 N 天可跨月/跨年 */
    @Test
    fun `due days after statement crosses year`() {
        val method = card(1, "跨年卡", statementDay = 20, dueDay = null, dueDaysAfter = 30)
        // 支付 12/21 → 账单日次年 1/20 → 还款日 2/19
        assertEquals(LocalDate.of(2027, 2, 19), Calculator.calculateDueDate(LocalDate.of(2026, 12, 21), method))
    }

    /** 账单日当天：inclusive=false 算下一期，true 算本期 */
    @Test
    fun `statement day boundary respects inclusive flag`() {
        val onStatement = LocalDate.of(2026, 7, 10)

        val exclusive = card(1, "不含当天", statementDay = 10, dueDay = 25, inclusive = false)
        assertEquals(LocalDate.of(2026, 8, 25), Calculator.calculateDueDate(onStatement, exclusive))

        val inclusive = card(1, "含当天", statementDay = 10, dueDay = 25, inclusive = true)
        assertEquals(LocalDate.of(2026, 7, 25), Calculator.calculateDueDate(onStatement, inclusive))
    }

    /** 不限额卡（creditLimit = null）永不被额度过滤 */
    @Test
    fun `unlimited card never filtered`() {
        val unlimited = card(1, "不限额卡", limit = null, used = "999999")
        val results = Calculator.recommend(listOf(unlimited), BigDecimal("999999999"), LocalDate.of(2026, 7, 11))
        assertEquals(1, results.size)
        // 999999 + 999999999 = 1000999998
        assertEquals(BigDecimal("1000999998"), results[0].afterUsed)
    }

    /** 已用 + 本次 = 额度（恰好等于）应通过，不被过滤 */
    @Test
    fun `exactly at limit is allowed`() {
        val method = card(1, "刚好满额", limit = "20000", used = "15000")
        val results = Calculator.recommend(listOf(method), BigDecimal("5000"), LocalDate.of(2026, 7, 11))
        assertEquals(1, results.size)
    }

    /** 已用 + 本次 超一分 → 被过滤 */
    @Test
    fun `one cent over limit is filtered`() {
        val method = card(1, "超一分", limit = "20000", used = "15000")
        val results = Calculator.recommend(listOf(method), BigDecimal("5000.01"), LocalDate.of(2026, 7, 11))
        assertTrue(results.isEmpty())
    }

    /** 金额必须大于 0 */
    @Test
    fun `amount must be positive`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            Calculator.recommend(emptyList(), BigDecimal.ZERO, LocalDate.of(2026, 7, 11))
        }
        assertEquals("支付金额必须大于 0", ex.message)
    }

    /** 信用卡缺账单日 → 报错 */
    @Test
    fun `credit card without statement day throws`() {
        val broken = card(1, "缺账单日", statementDay = null)
        val ex = assertFailsWith<IllegalArgumentException> {
            Calculator.calculateDueDate(LocalDate.of(2026, 7, 11), broken)
        }
        assertEquals("支付方式 缺账单日 缺少账单日", ex.message)
    }

    /** 信用卡有账单日但无还款规则 → 报错 */
    @Test
    fun `credit card without due rule throws`() {
        val broken = card(1, "缺还款规则", statementDay = 10, dueDay = null, dueDaysAfter = null)
        val ex = assertFailsWith<IllegalArgumentException> {
            Calculator.calculateDueDate(LocalDate.of(2026, 7, 11), broken)
        }
        assertEquals("支付方式 缺还款规则 缺少还款日", ex.message)
    }

    /** topN 截断 */
    @Test
    fun `top n truncates results`() {
        val methods = (1..5).map { card(it.toLong(), "卡$it", used = "0", statementDay = it * 2, dueDay = 28) }
        val date = LocalDate.of(2026, 7, 11)
        assertEquals(3, Calculator.recommend(methods, BigDecimal("100"), date).size)
        assertEquals(2, Calculator.recommend(methods, BigDecimal("100"), date, topN = 2).size)
        assertEquals(5, Calculator.recommend(methods, BigDecimal("100"), date, topN = 9).size)
    }

    /** 同占用天数时保持输入顺序（稳定排序，无次级排序） */
    @Test
    fun `equal occupied days keep input order`() {
        val a = card(1, "甲", used = "0", statementDay = 10, dueDay = 25)
        val b = card(2, "乙", used = "0", statementDay = 10, dueDay = 25)
        val results = Calculator.recommend(listOf(a, b), BigDecimal("100"), LocalDate.of(2026, 7, 1))
        assertEquals(listOf("甲", "乙"), results.map { it.paymentMethod })
    }

    /** 金额量化：银行家舍入（HALF_EVEN） */
    @Test
    fun `amount quantization uses half even`() {
        assertEquals("10.00", Calculator.formatAmount(BigDecimal("10")))
        assertEquals("10.12", Calculator.formatAmount(BigDecimal("10.125")))
        assertEquals("10.12", Calculator.formatAmount(BigDecimal("10.115")))
        assertEquals("10.14", Calculator.formatAmount(BigDecimal("10.135")))
        assertEquals("0.00", Calculator.formatAmount(BigDecimal.ZERO))
        assertEquals(null, Calculator.formatAmount(null))
    }

    /** 可用额度可为负（超限） */
    @Test
    fun `available limit can be negative`() {
        assertEquals(BigDecimal("-200.00"), Calculator.availableLimit(BigDecimal("1000"), BigDecimal("1200")))
        assertEquals(null, Calculator.availableLimit(null, BigDecimal("1200")))
    }
}
