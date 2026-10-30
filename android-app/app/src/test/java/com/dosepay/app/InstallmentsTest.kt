package com.dosepay.app

import com.dosepay.app.core.Installments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 分期算法测试。
 *
 * 覆盖两种计价口径：
 * - 年利率口径（消费贷）：每期还款额固定、利息逐月递减；
 * - 总费率口径（信用卡账单分期）：每期本金与手续费都固定，与银行账单对齐。
 *
 * 断言全部走「分」级精确值：分期金额一旦差一分，用户对账就会对不上。
 */
class InstallmentsTest {

    /** 金额比较只看数值不看 scale（BigDecimal.equals 会因为尾随 0 不同而判不等） */
    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertEquals(expected, actual.toPlainString())
    }

    private fun d(v: String) = BigDecimal(v)

    /* ────────────── 年利率口径：等额本息 ────────────── */

    @Test
    fun `等额本息_每期还款额固定且利息逐月递减`() {
        val s = Installments.build(
            principal = d("1305.97"),
            periods = 12,
            mode = Installments.RateMode.ANNUAL_RATE,
            rate = d("0.0516"),
            firstDueDate = LocalDate.of(2026, 11, 21),
        )!!
        assertEquals(12, s.items.size)
        // 前 11 期还款额固定
        s.items.take(11).forEach { assertMoney("111.90", it.total) }
        // 利息逐月递减
        assertMoney("5.62", s.items[0].interestPart)
        assertMoney("5.16", s.items[1].interestPart)
        assertMoney("4.70", s.items[2].interestPart)
        assertMoney("0.96", s.items[10].interestPart)
        // 末期结清：吸收逐期取整尾差
        assertMoney("0.52", s.items[11].interestPart)
    }

    @Test
    fun `等额本息_本金合计严格等于分期本金`() {
        val s = Installments.build(
            principal = d("1305.97"),
            periods = 12,
            mode = Installments.RateMode.ANNUAL_RATE,
            rate = d("0.0516"),
            firstDueDate = LocalDate.of(2026, 11, 21),
        )!!
        val sum = s.items.fold(BigDecimal.ZERO) { a, p -> a.add(p.principalPart) }
        assertMoney("1305.97", sum)
        assertMoney("0.00", s.items.last().endBalance)
    }

    @Test
    fun `等额本息_总利息与银行账单存在分位尾差`() {
        val s = Installments.build(
            principal = d("1305.97"),
            periods = 12,
            mode = Installments.RateMode.ANNUAL_RATE,
            rate = d("0.0516"),
            firstDueDate = LocalDate.of(2026, 11, 21),
        )!!
        // 逐期取整到分后合计 36.83，银行账单 36.79，差 4 分属正常尾差
        assertMoney("36.83", s.totalInterest)
        assertMoney("1342.80", s.totalPayment)
    }

    /* ────────────── 总费率口径：等额本金 + 固定手续费 ────────────── */

    @Test
    fun `总费率口径_每期本金与手续费都固定`() {
        val s = Installments.build(
            principal = d("10000.00"),
            periods = 12,
            mode = Installments.RateMode.FEE_RATE,
            rate = d("0.072"),
            firstDueDate = LocalDate.of(2026, 11, 21),
        )!!
        assertMoney("720.00", s.totalInterest)
        assertMoney("10720.00", s.totalPayment)
        // 前 11 期：本金 833.33 + 手续费 60.00
        s.items.take(11).forEach {
            assertMoney("833.33", it.principalPart)
            assertMoney("60.00", it.interestPart)
            assertMoney("893.33", it.total)
        }
        // 末期本金补差，保证 Σ本金 = 10000
        assertMoney("833.37", s.items[11].principalPart)
        assertMoney("893.37", s.items[11].total)
    }

    @Test
    fun `总费率口径_折合年化约13个百分点`() {
        val s = Installments.build(
            principal = d("10000.00"),
            periods = 12,
            mode = Installments.RateMode.FEE_RATE,
            rate = d("0.072"),
            firstDueDate = LocalDate.of(2026, 11, 21),
        )!!
        val annual = s.effectiveAnnualRate.toDouble()
        // 7.2% 的总手续费率折合单利年化约 13%，这是分期最该被看见的数字
        assertEquals(0.130, annual, 0.002)
    }

    @Test
    fun `年利率口径_折合年化等于输入利率`() {
        val s = Installments.build(
            principal = d("1305.97"),
            periods = 12,
            mode = Installments.RateMode.ANNUAL_RATE,
            rate = d("0.0516"),
            firstDueDate = LocalDate.of(2026, 11, 21),
        )!!
        assertMoney("0.0516", s.effectiveAnnualRate)
    }

    /* ────────────── 看板口径 ────────────── */

    @Test
    fun `口径恒等式_本期待还加冻结额度等于净待还加首期利息`() {
        val s = Installments.build(
            principal = d("10000.00"),
            periods = 12,
            mode = Installments.RateMode.FEE_RATE,
            rate = d("0.072"),
            firstDueDate = LocalDate.of(2026, 11, 21),
        )!!
        val net = d("10000.00")
        // 挪到未来各期的本金 = 分期本金 − 首期本金
        val frozen = s.principal.subtract(s.items.first().principalPart)
        // 本期待还 = 净待还 − 冻结 + 首期利息
        val due = net.subtract(frozen).add(s.items.first().interestPart)
        // 两条口径必须能对上：待还 + 冻结 = 净待还 + 首期利息
        assertMoney("10060.00", due.add(frozen))
        assertMoney("10060.00", net.add(s.items.first().interestPart))
    }

    /* ────────────── IRR 反解 ────────────── */

    @Test
    fun `反解月利率_与已知年利率一致`() {
        val monthly = Installments.solveMonthlyRate(d("1305.97"), d("111.90"), 12)!!
        assertEquals(0.0043, monthly.toDouble(), 0.0001)
    }

    @Test
    fun `反解月利率_费率口径能还原出折合年化`() {
        val monthly = Installments.solveMonthlyRate(d("10000.00"), d("893.33"), 12)!!
        assertEquals(0.130, monthly.toDouble() * 12, 0.003)
    }

    @Test
    fun `反解月利率_参数非法返回空`() {
        assertNull(Installments.solveMonthlyRate(d("0"), d("100"), 12))
        assertNull(Installments.solveMonthlyRate(d("100"), d("0"), 12))
        assertNull(Installments.solveMonthlyRate(d("100"), d("10"), 0))
    }

    /* ────────────── 均分与日期 ────────────── */

    @Test
    fun `均分末位补差保证合计等于总额`() {
        val parts = Installments.splitEvenly(d("100.00"), 3)
        assertEquals(3, parts.size)
        assertMoney("33.33", parts[0])
        assertMoney("33.33", parts[1])
        assertMoney("33.34", parts[2])
        assertMoney("100.00", parts.fold(BigDecimal.ZERO) { a, b -> a.add(b) })
    }

    @Test
    fun `均分不足一分时全部落到末位`() {
        val parts = Installments.splitEvenly(d("0.01"), 3)
        assertMoney("0.00", parts[0])
        assertMoney("0.00", parts[1])
        assertMoney("0.01", parts[2])
    }

    @Test
    fun `每期还款日按月推进且月末钳制`() {
        val first = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 1, 31), Installments.dueDateOf(first, 1))
        assertEquals(LocalDate.of(2026, 2, 28), Installments.dueDateOf(first, 2))
        assertEquals(LocalDate.of(2026, 3, 31), Installments.dueDateOf(first, 3))
        // 跨年
        assertEquals(LocalDate.of(2027, 1, 31), Installments.dueDateOf(first, 13))
    }

    /* ────────────── 参数校验 ────────────── */

    @Test
    fun `参数非法时返回空`() {
        val due = LocalDate.of(2026, 11, 21)
        assertNull(Installments.build(d("0"), 12, Installments.RateMode.ANNUAL_RATE, d("0.05"), due))
        assertNull(Installments.build(d("-1"), 12, Installments.RateMode.ANNUAL_RATE, d("0.05"), due))
        assertNull(Installments.build(d("100"), 0, Installments.RateMode.ANNUAL_RATE, d("0.05"), due))
        assertNull(Installments.build(d("100"), 25, Installments.RateMode.ANNUAL_RATE, d("0.05"), due))
        assertNull(Installments.build(d("100"), 12, Installments.RateMode.ANNUAL_RATE, d("-0.01"), due))
        assertNull(Installments.build(d("100"), 12, "未知口径", d("0.05"), due))
    }
}
