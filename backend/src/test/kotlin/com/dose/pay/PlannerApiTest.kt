package com.dose.pay

import com.dose.pay.domain.PaymentMethodRepository
import com.dose.pay.domain.TransactionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 接口契约与业务规则集成测试（SQLite 真实文件库）。
 *
 * 覆盖：
 * - 卡片 CRUD 与各类错误码（400/404/409/422）
 * - 推荐计算端到端
 * - 流水与余额同事务原子维护、删除回滚、还款上限、允许超限
 * - 分页与排序
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlannerApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var methodRepo: PaymentMethodRepository

    @Autowired
    lateinit var txnRepo: TransactionRepository

    @BeforeEach
    fun clean() {
        txnRepo.deleteAll()
        methodRepo.deleteAll()
    }

    // -------------------- 辅助 --------------------

    private fun createCard(
        name: String,
        limit: String? = "10000.00",
        type: String = "信用卡",
        statementDay: Int? = 10,
        dueDay: Int? = 25,
        dueDaysAfter: Int? = null,
        inclusive: Boolean = false,
        expect: Int = 201,
    ): MvcResult {
        val limitJson = if (limit == null) "null" else "\"$limit\""
        val stmtJson = if (statementDay == null) "null" else statementDay.toString()
        val dueJson = if (dueDay == null) "null" else dueDay.toString()
        val afterJson = if (dueDaysAfter == null) "null" else dueDaysAfter.toString()
        val result = mockMvc.perform(
            post("/api/methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"$name","method_type":"$type","credit_limit":$limitJson,
                       "current_used":"0","statement_day":$stmtJson,"due_day":$dueJson,
                       "due_days_after_statement":$afterJson,"statement_day_inclusive":$inclusive}"""
                )
        ).andReturn()
        assertEquals(expect, result.response.status, result.response.getContentAsString(Charsets.UTF_8))
        return result
    }

    private fun createCardId(name: String, limit: String? = "10000.00", statementDay: Int = 10, dueDay: Int = 25): Long =
        objectMapper.readTree(createCard(name, limit, statementDay = statementDay, dueDay = dueDay).response.contentAsString)["method"]["id"].asLong()

    private fun postTxn(cardId: Long, type: String, amount: String, date: String = "2026-07-29", note: String = ""): MvcResult =
        mockMvc.perform(
            post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"payment_method_id":$cardId,"type":"$type","amount":"$amount",
                       "txn_date":"$date","note":"$note"}"""
                )
        ).andReturn()

    private fun currentUsedOf(cardId: Long): BigDecimal = methodRepo.findById(cardId).get().currentUsed

    // -------------------- 基础接口 --------------------

    @Test
    fun `health returns ok`() {
        val result = mockMvc.perform(get("/api/health")).andReturn()
        assertEquals(200, result.response.status)
        assertEquals("""{"status":"ok"}""", result.response.contentAsString)
    }

    @Test
    fun `empty methods returns empty list`() {
        val result = mockMvc.perform(get("/api/methods")).andReturn()
        assertEquals(200, result.response.status)
        assertEquals("""{"methods":[]}""", result.response.contentAsString)
    }

    // -------------------- 卡片 CRUD --------------------

    @Test
    fun `create and list method`() {
        val json = objectMapper.readTree(createCard("工商", "50000.00").response.contentAsString)
        assertEquals("工商", json["method"]["name"].asText())
        assertEquals("50000.00", json["method"]["credit_limit"].asText())
        assertEquals("0.00", json["method"]["current_used"].asText())
        assertEquals("50000.00", json["method"]["available_limit"].asText())
        assertTrue(json["method"]["active"].asBoolean())

        val list = objectMapper.readTree(mockMvc.perform(get("/api/methods")).andReturn().response.contentAsString)
        assertEquals(1, list["methods"].size())
    }

    @Test
    fun `duplicate name conflicts`() {
        createCard("工商")
        createCard("工商", expect = 409)
    }

    @Test
    fun `credit card requires limit`() {
        val r = createCard("缺额度", limit = null, expect = 400)
        assertTrue(r.response.getContentAsString(Charsets.UTF_8).contains("信用卡必须填写信用额度"))
    }

    @Test
    fun `credit card requires statement day`() {
        val r = createCard("缺账单日", statementDay = null, dueDay = 25, expect = 400)
        assertTrue(r.response.getContentAsString(Charsets.UTF_8).contains("信用卡必须填写账单日"))
    }

    @Test
    fun `credit card requires due rule`() {
        val r = createCard("缺还款规则", statementDay = 10, dueDay = null, expect = 400)
        assertTrue(r.response.getContentAsString(Charsets.UTF_8).contains("信用卡必须填写还款日或账单日后还款天数"))
    }

    @Test
    fun `non credit card needs no billing fields`() {
        createCard("储蓄卡", limit = null, type = "储蓄卡", statementDay = null, dueDay = null)
        val list = objectMapper.readTree(mockMvc.perform(get("/api/methods")).andReturn().response.contentAsString)
        assertEquals("储蓄卡", list["methods"][0]["method_type"].asText())
        assertTrue(list["methods"][0]["credit_limit"].isNull)
    }

    @Test
    fun `blank name rejected`() {
        val r = createCard("   ", expect = 400)
        assertTrue(r.response.getContentAsString(Charsets.UTF_8).contains("名称不能为空"))
    }

    @Test
    fun `invalid method type returns 422`() {
        val r = createCard("怪类型", type = "外币卡", expect = 422)
        val body = objectMapper.readTree(r.response.getContentAsString(Charsets.UTF_8))
        assertTrue(body["detail"].asText().contains("取值必须是"))
        assertEquals("literal_error", body["errors"][0]["type"].asText())
    }

    @Test
    fun `update method`() {
        val id = createCardId("工商", "10000.00")
        val result = mockMvc.perform(
            put("/api/methods/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"工商改名","method_type":"信用卡","credit_limit":"20000.00",
                       "current_used":"0","statement_day":12,"due_day":20,
                       "due_days_after_statement":null,"statement_day_inclusive":true}"""
                )
        ).andReturn()
        assertEquals(200, result.response.status)
        val body = objectMapper.readTree(result.response.contentAsString)
        assertEquals("工商改名", body["method"]["name"].asText())
        assertEquals(12, body["method"]["statement_day"].asInt())
        assertTrue(body["method"]["statement_day_inclusive"].asBoolean())
    }

    @Test
    fun `update nonexistent method 404`() {
        val result = mockMvc.perform(
            put("/api/methods/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"x","method_type":"信用卡","credit_limit":"1",
                       "current_used":"0","statement_day":1,"due_day":1}"""
                )
        ).andReturn()
        assertEquals(404, result.response.status)
        assertTrue(result.response.getContentAsString(Charsets.UTF_8).contains("支付方式 999 不存在"))
    }

    @Test
    fun `update to duplicate name conflicts`() {
        createCard("甲")
        val id = createCardId("乙")
        val result = mockMvc.perform(
            put("/api/methods/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"name":"甲","method_type":"信用卡","credit_limit":"1",
                       "current_used":"0","statement_day":1,"due_day":1}"""
                )
        ).andReturn()
        assertEquals(409, result.response.status)
        assertTrue(result.response.getContentAsString(Charsets.UTF_8).contains("已被其他记录使用"))
    }

    @Test
    fun `delete method and 404 afterwards`() {
        val id = createCardId("工商")
        assertEquals(200, mockMvc.perform(delete("/api/methods/$id")).andReturn().response.status)
        assertEquals(404, mockMvc.perform(delete("/api/methods/$id")).andReturn().response.status)
    }

    @Test
    fun `delete method with transactions conflicts`() {
        val id = createCardId("工商")
        postTxn(id, "消费", "50.00")
        val result = mockMvc.perform(delete("/api/methods/$id")).andReturn()
        assertEquals(409, result.response.status)
        assertEquals(
            "该卡还有 1 笔流水，请先删除流水",
            objectMapper.readTree(result.response.contentAsString)["detail"].asText(),
        )
    }

    // -------------------- 推荐 --------------------

    @Test
    fun `recommend sorted by occupied days desc`() {
        createCardId("长账期", "100000.00", statementDay = 28, dueDay = 17)
        createCardId("短账期", "100000.00", statementDay = 1, dueDay = 10)

        val result = mockMvc.perform(
            post("/api/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":"10000.00","payment_date":"2026-07-29"}""")
        ).andReturn()
        assertEquals(200, result.response.status)
        val results = objectMapper.readTree(result.response.contentAsString)["results"]
        assertEquals(2, results.size())
        assertTrue(results[0]["occupied_days"].asInt() >= results[1]["occupied_days"].asInt())
        assertEquals("长账期", results[0]["payment_method"].asText())
    }

    @Test
    fun `recommend filters over limit cards`() {
        createCardId("小额度", "100.00")
        createCardId("大额度", "100000.00")
        val result = mockMvc.perform(
            post("/api/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":"1000.00","payment_date":"2026-07-29"}""")
        ).andReturn()
        val results = objectMapper.readTree(result.response.contentAsString)["results"]
        assertEquals(1, results.size())
        assertEquals("大额度", results[0]["payment_method"].asText())
    }

    @Test
    fun `recommend zero amount rejected`() {
        val result = mockMvc.perform(
            post("/api/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":"0.00","payment_date":"2026-07-29"}""")
        ).andReturn()
        assertEquals(400, result.response.status)
        assertEquals(
            "支付金额必须大于 0",
            objectMapper.readTree(result.response.contentAsString)["detail"].asText(),
        )
    }

    @Test
    fun `recommend numeric amount returns 422`() {
        val result = mockMvc.perform(
            post("/api/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":100,"payment_date":"2026-07-29"}""")
        ).andReturn()
        assertEquals(422, result.response.status)
        val body = objectMapper.readTree(result.response.contentAsString)
        assertTrue(body["detail"].asText().contains("必须是字符串"))
        assertEquals(100, body["errors"][0]["input"].asInt())
        assertEquals("string_type", body["errors"][0]["type"].asText())
    }

    @Test
    fun `recommend top n out of range returns 422`() {
        val result = mockMvc.perform(
            post("/api/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":"100.00","payment_date":"2026-07-29","top_n":200}""")
        ).andReturn()
        assertEquals(422, result.response.status)
        assertTrue(result.response.getContentAsString(Charsets.UTF_8).contains("返回条数"))
    }

    @Test
    fun `recommend bad date returns 400`() {
        val result = mockMvc.perform(
            post("/api/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount":"100.00","payment_date":"2026/07/29"}""")
        ).andReturn()
        assertEquals(400, result.response.status)
        assertTrue(result.response.getContentAsString(Charsets.UTF_8).contains("格式不正确"))
    }

    // -------------------- 消费流水 --------------------

    @Test
    fun `consume increases balance`() {
        val id = createCardId("工商", "50000.00")
        val result = postTxn(id, "消费", "1200.00", note = "买手机")
        assertEquals(201, result.response.status)
        val txn = objectMapper.readTree(result.response.contentAsString)["transaction"]
        assertEquals("1200.00", txn["amount"].asText())
        assertEquals("工商", txn["payment_method_name"].asText())
        assertEquals(0, BigDecimal("1200.00").compareTo(currentUsedOf(id)))
    }

    @Test
    fun `repay decreases balance`() {
        val id = createCardId("工商")
        postTxn(id, "消费", "1200.00")
        assertEquals(201, postTxn(id, "还款", "200.00").response.status)
        assertEquals(0, BigDecimal("1000.00").compareTo(currentUsedOf(id)))
    }

    @Test
    fun `repay exceeding current used rolls back`() {
        val id = createCardId("工商")
        postTxn(id, "消费", "100.00")
        val result = postTxn(id, "还款", "999.00")
        assertEquals(400, result.response.status)
        assertEquals(
            "还款金额不能超过当前已用 ¥100.00",
            objectMapper.readTree(result.response.contentAsString)["detail"].asText(),
        )
        // 余额不变、流水未插入
        assertEquals(0, BigDecimal("100.00").compareTo(currentUsedOf(id)))
        assertEquals(1, txnRepo.countByPaymentMethodId(id))
    }

    @Test
    fun `consume may exceed limit`() {
        val id = createCardId("小额卡", "100.00")
        assertEquals(201, postTxn(id, "消费", "500.00").response.status)
        val list = objectMapper.readTree(mockMvc.perform(get("/api/methods")).andReturn().response.contentAsString)
        assertEquals("500.00", list["methods"][0]["current_used"].asText())
        assertEquals("-400.00", list["methods"][0]["available_limit"].asText())
    }

    @Test
    fun `txn amount must be positive`() {
        val id = createCardId("工商")
        val result = postTxn(id, "消费", "0.00")
        assertEquals(400, result.response.status)
        assertEquals("金额必须大于 0", objectMapper.readTree(result.response.contentAsString)["detail"].asText())
    }

    @Test
    fun `txn negative amount rejected`() {
        val id = createCardId("工商")
        val result = postTxn(id, "消费", "-5.00")
        assertEquals(400, result.response.status)
        assertTrue(result.response.getContentAsString(Charsets.UTF_8).contains("不能小于 0"))
    }

    @Test
    fun `txn note too long rejected`() {
        val id = createCardId("工商")
        val result = postTxn(id, "消费", "10.00", note = "x".repeat(51))
        assertEquals(400, result.response.status)
        assertEquals("备注不能超过 50 字", objectMapper.readTree(result.response.contentAsString)["detail"].asText())
    }

    @Test
    fun `txn invalid type returns 422`() {
        val id = createCardId("工商")
        val result = postTxn(id, "取现", "5.00")
        assertEquals(422, result.response.status)
        val body = objectMapper.readTree(result.response.contentAsString)
        assertTrue(body["detail"].asText().contains("取值必须是"))
        assertEquals("literal_error", body["errors"][0]["type"].asText())
    }

    @Test
    fun `txn on nonexistent card 404`() {
        val result = postTxn(999, "消费", "5.00")
        assertEquals(404, result.response.status)
        assertTrue(result.response.getContentAsString(Charsets.UTF_8).contains("支付方式 999 不存在"))
    }

    // -------------------- 流水列表与删除 --------------------

    @Test
    fun `transactions ordered by date then id desc`() {
        val id = createCardId("排序卡")
        postTxn(id, "消费", "10.00", note = "第一笔")
        postTxn(id, "消费", "20.00", note = "第二笔")
        postTxn(id, "消费", "30.00", note = "第三笔")

        val result = mockMvc.perform(get("/api/transactions?limit=50")).andReturn()
        val txns = objectMapper.readTree(result.response.contentAsString)["transactions"]
        assertEquals(listOf("第三笔", "第二笔", "第一笔"), txns.map { it["note"].asText() })
    }

    @Test
    fun `transactions pagination`() {
        val id = createCardId("分页卡")
        repeat(3) { postTxn(id, "消费", "${(it + 1) * 10}.00", note = "第${it + 1}笔") }

        val p1 = objectMapper.readTree(mockMvc.perform(get("/api/transactions?limit=1")).andReturn().response.contentAsString)
        assertEquals(1, p1["transactions"].size())
        val p2 = objectMapper.readTree(mockMvc.perform(get("/api/transactions?limit=1&offset=1")).andReturn().response.contentAsString)
        assertEquals(1, p2["transactions"].size())
        assertTrue(p1["transactions"][0]["id"].asLong() != p2["transactions"][0]["id"].asLong())
    }

    @Test
    fun `transactions filter by method`() {
        val a = createCardId("甲卡")
        val b = createCardId("乙卡")
        postTxn(a, "消费", "10.00")
        postTxn(b, "消费", "20.00")

        val result = mockMvc.perform(get("/api/transactions?method_id=$a&limit=50")).andReturn()
        val txns = objectMapper.readTree(result.response.contentAsString)["transactions"]
        assertEquals(1, txns.size())
        assertEquals(a, txns[0]["payment_method_id"].asLong())
    }

    @Test
    fun `bad limit and offset rejected`() {
        assertEquals(400, mockMvc.perform(get("/api/transactions?limit=0")).andReturn().response.status)
        assertEquals(400, mockMvc.perform(get("/api/transactions?limit=201")).andReturn().response.status)
        assertEquals(400, mockMvc.perform(get("/api/transactions?offset=-1")).andReturn().response.status)
        assertTrue(
            mockMvc.perform(get("/api/transactions?limit=0")).andReturn()
                .response.getContentAsString(Charsets.UTF_8).contains("limit 必须在 1-200 之间")
        )
    }

    @Test
    fun `delete consume rolls back balance`() {
        val id = createCardId("回滚卡")
        val txn = postTxn(id, "消费", "1200.00")
        val txnId = objectMapper.readTree(txn.response.contentAsString)["transaction"]["id"].asLong()
        assertEquals(0, BigDecimal("1200.00").compareTo(currentUsedOf(id)))

        val del = mockMvc.perform(delete("/api/transactions/$txnId")).andReturn()
        assertEquals(200, del.response.status)
        assertEquals("0.00", objectMapper.readTree(del.response.contentAsString)["card_current_used"].asText())
        assertEquals(0, BigDecimal.ZERO.compareTo(currentUsedOf(id)))
    }

    @Test
    fun `delete repay rolls back balance upward`() {
        val id = createCardId("还款回滚卡")
        postTxn(id, "消费", "1000.00")
        val repay = postTxn(id, "还款", "400.00")
        val repayId = objectMapper.readTree(repay.response.contentAsString)["transaction"]["id"].asLong()
        assertEquals(0, BigDecimal("600.00").compareTo(currentUsedOf(id)))

        val del = mockMvc.perform(delete("/api/transactions/$repayId")).andReturn()
        assertEquals("1000.00", objectMapper.readTree(del.response.contentAsString)["card_current_used"].asText())
        assertEquals(0, BigDecimal("1000.00").compareTo(currentUsedOf(id)))
    }

    @Test
    fun `delete nonexistent txn 404`() {
        val result = mockMvc.perform(delete("/api/transactions/999")).andReturn()
        assertEquals(404, result.response.status)
        assertEquals("流水 999 不存在", objectMapper.readTree(result.response.contentAsString)["detail"].asText())
    }

    // -------------------- 金额精度 --------------------

    @Test
    fun `amount precision preserved through round trip`() {
        val id = createCardId("精度卡", "99999.99")
        postTxn(id, "消费", "0.01")
        postTxn(id, "消费", "0.02")
        // 0.01 + 0.02 必须精确等于 0.03（浮点实现会得到 0.030000000000004）
        assertEquals(0, BigDecimal("0.03").compareTo(currentUsedOf(id)))
    }

    @Test
    fun `large amount within decimal range`() {
        val id = createCardId("大额卡", "999999999999.99")
        assertEquals(201, postTxn(id, "消费", "123456789012.34").response.status)
        assertEquals(0, BigDecimal("123456789012.34").compareTo(currentUsedOf(id)))
    }

    // -------------------- 看板：账单周期口径 --------------------

    @Test
    fun `cycle summary aggregates current period only`() {
        val id = createCardId("周期卡", "50000.00", statementDay = 10, dueDay = 25)
        val today = LocalDate.now()
        // 今天消费必然落在本期（还款日 = 本还款日）
        postTxn(id, "消费", "100.00", date = today.toString())
        postTxn(id, "消费", "50.50", date = today.toString())
        // 三个月前的消费属于更早的周期，不应计入本期
        postTxn(id, "消费", "999.00", date = today.minusMonths(3).toString())
        postTxn(id, "还款", "10.00", date = today.toString())

        val result = mockMvc.perform(get("/api/cycle")).andReturn()
        assertEquals(200, result.response.status, result.response.getContentAsString(Charsets.UTF_8))

        val cycle = objectMapper.readTree(result.response.contentAsString)["cycle"]
        assertEquals(today.toString(), cycle["as_of"].asText())
        // 本期消费 = 150.50，那笔 999 属于旧周期
        assertEquals("150.50", cycle["total_spend"].asText())
        assertEquals("10.00", cycle["total_repay"].asText())
        assertEquals("140.50", cycle["total_net"].asText())

        val card = cycle["cards"].first { it["payment_method_id"].asLong() == id }
        assertEquals("cycle", card["basis"].asText())
        assertEquals("150.50", card["spend_amount"].asText())
        assertEquals(2, card["spend_count"].asInt())
        assertEquals(1, card["repay_count"].asInt())
        // 周期区间必须完整且终点在今天之后（还款日当天 = 0）
        val end = LocalDate.parse(card["cycle_end"].asText())
        val start = LocalDate.parse(card["cycle_start"].asText())
        assertTrue(start.isBefore(end), "周期起点必须早于终点：$start -> $end")
        assertTrue(!end.isBefore(today), "本还款日不应早于今天：$end")
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(today, end).toInt(), card["days_to_due"].asInt())
    }

    @Test
    fun `cycle summary excludes non credit cards`() {
        val creditId = createCardId("周期信用卡", "50000.00", statementDay = 10, dueDay = 25)
        // 储蓄卡没有账单周期，不进看板
        createCard("储蓄卡", limit = null, type = "储蓄卡", statementDay = null, dueDay = null, expect = 201)
        postTxn(creditId, "消费", "20.00", date = LocalDate.now().toString())

        val cycle = objectMapper.readTree(mockMvc.perform(get("/api/cycle")).andReturn().response.contentAsString)["cycle"]
        val ids = cycle["cards"].map { it["payment_method_id"].asLong() }
        assertEquals(listOf(creditId), ids)
        assertEquals("20.00", cycle["total_spend"].asText())
    }

    @Test
    fun `cycle summary empty when no cards`() {
        val result = mockMvc.perform(get("/api/cycle")).andReturn()
        assertEquals(200, result.response.status)
        val cycle = objectMapper.readTree(result.response.contentAsString)["cycle"]
        assertEquals(0, cycle["cards"].size())
        assertEquals("0.00", cycle["total_spend"].asText())
    }
}
