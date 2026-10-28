package com.dose.pay.android

import com.dose.pay.android.core.Calculator
import com.dose.pay.android.core.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 黄金快照对账测试。
 *
 * 期望值全部来自 backend Spring Boot 的真实响应
 * （GET /api/cycle 与 POST /api/recommend），数据为迁移前导出的真实 10 张卡。
 *
 * 目的：证明 App 侧移植的算法与后端**逐字段一致**，
 * 这样本地化改造后用户看到的结果与改造前完全相同。
 */
class GoldenSnapshotTest {

    /** 真实的 10 张卡（来自 GET /api/methods 导出） */
    private val realCards: List<PaymentMethod> = listOf(
        pm(1, "工商", 0, 15, 8),
        pm(2, "建设", 4040, 23, 12),
        pm(3, "农行", 0, 27, 15),
        pm(4, "广发", 0, 28, 17),
        pm(5, "北农", 10100, 25, 19),
        pm(6, "花呗", 222, 10, 20, limit = "20500.00"),
        pm(7, "京东", 0, 11, 20),
        pm(8, "民生", 0, 1, 21),
        pm(9, "美团", 0, 15, 22),
        pm(10, "平安", 0, 6, 24),
    )

    private fun pm(
        id: Long, name: String, used: Int, stmtDay: Int, dueDay: Int, limit: String? = null,
    ) = PaymentMethod(
        id = id, name = name, methodType = Calculator.CREDIT_CARD,
        creditLimit = limit?.let { BigDecimal(it) },
        currentUsed = BigDecimal("$used"),
        statementDay = stmtDay, dueDay = dueDay,
        dueDaysAfterStatement = null, statementDayInclusive = false,
    )

    /* ────────── 推荐（POST /api/recommend, amount=5000, date=2026-10-28） ────────── */

    @Test
    fun `推荐结果与后端完全一致`() {
        val results = Calculator.recommend(
            realCards, BigDecimal("5000.00"), LocalDate.of(2026, 10, 28), 3
        )

        assertEquals(3, results.size)

        // 后端返回：北农 12-19 占用52天 / 广发 12-17 占用50天 / 农行 12-15 占用48天
        assertEquals("北农", results[0].paymentMethod)
        assertEquals(LocalDate.of(2026, 12, 19), results[0].dueDate)
        assertEquals(52, results[0].occupiedDays)
        assertEquals("10100.00", Calculator.formatAmount(results[0].currentUsed))
        assertEquals("15100.00", Calculator.formatAmount(results[0].afterUsed))

        assertEquals("广发", results[1].paymentMethod)
        assertEquals(LocalDate.of(2026, 12, 17), results[1].dueDate)
        assertEquals(50, results[1].occupiedDays)

        assertEquals("农行", results[2].paymentMethod)
        assertEquals(LocalDate.of(2026, 12, 15), results[2].dueDate)
        assertEquals(48, results[2].occupiedDays)
    }

    @Test
    fun `推荐reason文案与后端一致`() {
        val results = Calculator.recommend(
            realCards, BigDecimal("5000.00"), LocalDate.of(2026, 10, 28), 1
        )
        assertEquals("预计 2026-12-19 还款，占用 52 天", results[0].reason)
    }

    /* ────────── 看板（GET /api/cycle） ────────── */

    /** 后端返回的每张卡周期区间（as_of = 2026-10-28） */
    private val expectedWindows = mapOf(
        "工商" to (LocalDate.of(2026, 11, 8) to LocalDate.of(2026, 12, 8)),
        "建设" to (LocalDate.of(2026, 11, 12) to LocalDate.of(2026, 12, 12)),
        "农行" to (LocalDate.of(2026, 11, 15) to LocalDate.of(2026, 12, 15)),
        "广发" to (LocalDate.of(2026, 11, 17) to LocalDate.of(2026, 12, 17)),
        "北农" to (LocalDate.of(2026, 11, 19) to LocalDate.of(2026, 12, 19)),
        "花呗" to (LocalDate.of(2026, 10, 20) to LocalDate.of(2026, 11, 20)),
        "京东" to (LocalDate.of(2026, 10, 20) to LocalDate.of(2026, 11, 20)),
        "民生" to (LocalDate.of(2026, 10, 21) to LocalDate.of(2026, 11, 21)),
        "美团" to (LocalDate.of(2026, 10, 22) to LocalDate.of(2026, 11, 22)),
        "平安" to (LocalDate.of(2026, 10, 24) to LocalDate.of(2026, 11, 24)),
    )

    @Test
    fun `全部10张卡的周期区间与后端一致`() {
        val today = LocalDate.of(2026, 10, 28)
        for (card in realCards) {
            val window = Calculator.cycleWindow(today, card)
                ?: throw AssertionError("${card.name} 周期窗口为空")
            val expected = expectedWindows[card.name]
                ?: throw AssertionError("缺少 ${card.name} 的期望值")
            assertEquals("${card.name} 周期起点", expected.first, window.first)
            assertEquals("${card.name} 周期终点", expected.second, window.second)
        }
    }

    @Test
    fun `看板消费归期与后端一致`() {
        val today = LocalDate.of(2026, 10, 28)

        // 北农：2 笔 2026-10-28 的消费（100 + 10000），后端显示本期消费 10100.00、2 笔
        val beiNong = realCards.first { it.id == 5L }
        val beiNongEnd = Calculator.cycleWindow(today, beiNong)!!.second
        val txnDate = LocalDate.of(2026, 10, 28)
        assertEquals(
            "北农本期归期应为 12-19",
            beiNongEnd,
            Calculator.cycleEndFor(txnDate, beiNong)
        )

        // 建设：1 笔 2026-10-28 的消费 4040，后端显示本期消费 4040.00
        val jianShe = realCards.first { it.id == 2L }
        val jianSheEnd = Calculator.cycleWindow(today, jianShe)!!.second
        assertEquals(jianSheEnd, Calculator.cycleEndFor(txnDate, jianShe))

        // 花呗：1 笔 2026-09-02 的消费 222，后端显示本期消费 0.00（不在本期内）
        val huaBei = realCards.first { it.id == 6L }
        val huaBeiEnd = Calculator.cycleWindow(today, huaBei)!!.second
        val oldTxn = LocalDate.of(2026, 9, 2)
        assertEquals(
            "花呗 9-02 的消费不应归入本期",
            false,
            Calculator.cycleEndFor(oldTxn, huaBei) == huaBeiEnd
        )
    }

    @Test
    fun `剩余天数与后端一致`() {
        val today = LocalDate.of(2026, 10, 28)
        // 后端返回：工商41 建设45 农行48 广发50 北农52 花呗23 京东23 民生24 美团25 平安27
        val expected = mapOf(
            "工商" to 41, "建设" to 45, "农行" to 48, "广发" to 50, "北农" to 52,
            "花呗" to 23, "京东" to 23, "民生" to 24, "美团" to 25, "平安" to 27,
        )
        for (card in realCards) {
            val end = Calculator.cycleWindow(today, card)!!.second
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, end).toInt()
            assertEquals("${card.name} 剩余天数", expected[card.name], days)
        }
    }
}
