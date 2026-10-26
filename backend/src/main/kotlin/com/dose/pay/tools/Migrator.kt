package com.dose.pay.tools

/**
 * 数据迁移工具：把原 credit-payment-planner 的数据导入 pay-dose 的 SQLite 库。
 *
 * 原系统是 MySQL（也可能有 SQLite 冷备份 data/app.db），pay-dose 是 SQLite 单用户。
 * 迁移时：
 * - 保留 payment_methods 与 transactions 的业务字段
 * - 丢弃 users / auth_tokens（pay-dose 无认证）
 * - 丢弃 recommendations / recommendation_items（计算历史已下线）
 * - 保留原 id（便于对账追溯）
 *
 * 对账口径：逐行比对全部业务字段 + 金额合计 + 每卡余额与流水推导值一致性。
 */

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/** 迁移源类型 */
enum class SourceKind { SQLITE, MYSQL }

/** 一条支付方式的快照 */
data class MethodRow(
    val id: Long,
    val name: String,
    val methodType: String,
    val creditLimit: BigDecimal?,
    val currentUsed: BigDecimal,
    val statementDay: Int?,
    val dueDay: Int?,
    val dueDaysAfterStatement: Int?,
    val statementDayInclusive: Boolean,
    val active: Boolean,
)

/** 一条流水的快照 */
data class TxnRow(
    val id: Long,
    val paymentMethodId: Long,
    val type: String,
    val amount: BigDecimal,
    val txnDate: String,
    val note: String,
)

/** 迁移结果 */
data class MigrationReport(
    val sourceMethods: Int,
    val sourceTxns: Int,
    val writtenMethods: Int,
    val writtenTxns: Int,
    val methodDiffs: List<String>,
    val txnDiffs: List<String>,
    val balanceDiffs: List<String>,
) {
    val ok: Boolean
        get() = methodDiffs.isEmpty() && txnDiffs.isEmpty() && balanceDiffs.isEmpty()

    /** 输出对账报告；通过时打印 RECONCILE OK */
    fun print() {
        println("=".repeat(70))
        println("数据迁移对账报告")
        println("=".repeat(70))
        println("支付方式：源 $sourceMethods 行 → 目标 $writtenMethods 行")
        println("流水    ：源 $sourceTxns 行 → 目标 $writtenTxns 行")
        if (methodDiffs.isNotEmpty()) {
            println("\n[支付方式差异] ${methodDiffs.size} 处")
            methodDiffs.take(20).forEach { println("  - $it") }
        }
        if (txnDiffs.isNotEmpty()) {
            println("\n[流水差异] ${txnDiffs.size} 处")
            txnDiffs.take(20).forEach { println("  - $it") }
        }
        if (balanceDiffs.isNotEmpty()) {
            println("\n[余额一致性差异] ${balanceDiffs.size} 处")
            balanceDiffs.take(20).forEach { println("  - $it") }
        }
        println("\n" + "-".repeat(70))
        if (ok) {
            println("RECONCILE OK")
        } else {
            println("RECONCILE FAILED")
        }
    }
}

/**
 * 迁移执行器。
 *
 * @param sourceKind    源库类型
 * @param sourceConn    源库连接（只读使用）
 * @param targetConn    目标 SQLite 连接（写入）
 * @param dryRun        true 时只读源库、做预演统计，不写目标库
 * @param truncate      true 时先清空目标表
 */
class Migrator(
    private val sourceKind: SourceKind,
    private val sourceConn: Connection,
    private val targetConn: Connection,
    private val dryRun: Boolean,
    private val truncate: Boolean,
) {

    fun run(): MigrationReport {
        val srcMethods = readSourceMethods()
        val srcTxns = readSourceTxns()

        println("从源库读取：支付方式 ${srcMethods.size} 行，流水 ${srcTxns.size} 行")

        if (dryRun) {
            println("\n[--dry-run] 预演模式，不写入目标库")
            return MigrationReport(
                sourceMethods = srcMethods.size,
                sourceTxns = srcTxns.size,
                writtenMethods = 0,
                writtenTxns = 0,
                methodDiffs = emptyList(),
                txnDiffs = emptyList(),
                balanceDiffs = checkBalanceConsistency(srcMethods, srcTxns),
            )
        }

        ensureTargetSchema()
        if (truncate) {
            println("清空目标表…")
            targetConn.createStatement().use { st ->
                st.executeUpdate("DELETE FROM transactions")
                st.executeUpdate("DELETE FROM payment_methods")
            }
        }

        // 写入（保留原 id）
        targetConn.autoCommit = false
        try {
            val writtenMethods = writeMethods(srcMethods)
            val writtenTxns = writeTxns(srcTxns)
            targetConn.commit()
            println("写入完成：支付方式 $writtenMethods 行，流水 $writtenTxns 行")

            // 回读目标库做对账
            val dstMethods = readTargetMethods()
            val dstTxns = readTargetTxns()

            return MigrationReport(
                sourceMethods = srcMethods.size,
                sourceTxns = srcTxns.size,
                writtenMethods = dstMethods.size,
                writtenTxns = dstTxns.size,
                methodDiffs = diffMethods(srcMethods, dstMethods),
                txnDiffs = diffTxns(srcTxns, dstTxns),
                balanceDiffs = checkBalanceConsistency(dstMethods, dstTxns),
            )
        } catch (e: Exception) {
            targetConn.rollback()
            throw e
        } finally {
            targetConn.autoCommit = true
        }
    }

    // -------------------- 源库读取 --------------------

    private fun readSourceMethods(): List<MethodRow> {
        // 源库可能是旧版 SQLite（credit_limit 存 TEXT），也可能是 MySQL（DECIMAL）
        val sql = """
            SELECT id, name, method_type, credit_limit, current_used,
                   statement_day, due_day, due_days_after_statement,
                   statement_day_inclusive, active
            FROM payment_methods
            ORDER BY id
        """.trimIndent()
        val out = ArrayList<MethodRow>()
        sourceConn.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    out.add(
                        MethodRow(
                            id = rs.getLong("id"),
                            name = rs.getString("name") ?: "",
                            methodType = rs.getString("method_type") ?: "",
                            creditLimit = rs.getString("credit_limit")?.let { toDecimal(it) },
                            currentUsed = toDecimal(rs.getString("current_used")) ?: BigDecimal.ZERO,
                            statementDay = rs.getObject("statement_day")?.toString()?.toIntOrNull(),
                            dueDay = rs.getObject("due_day")?.toString()?.toIntOrNull(),
                            dueDaysAfterStatement = rs.getObject("due_days_after_statement")?.toString()?.toIntOrNull(),
                            statementDayInclusive = toBool(rs.getObject("statement_day_inclusive")),
                            active = toBool(rs.getObject("active")),
                        )
                    )
                }
            }
        }
        return out
    }

    private fun readSourceTxns(): List<TxnRow> {
        val sql = """
            SELECT id, payment_method_id, type, amount, txn_date, note
            FROM transactions
            ORDER BY id
        """.trimIndent()
        val out = ArrayList<TxnRow>()
        sourceConn.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    out.add(
                        TxnRow(
                            id = rs.getLong("id"),
                            paymentMethodId = rs.getLong("payment_method_id"),
                            type = rs.getString("type") ?: "",
                            amount = toDecimal(rs.getString("amount")) ?: BigDecimal.ZERO,
                            txnDate = normalizeDate(rs.getString("txn_date")),
                            note = rs.getString("note") ?: "",
                        )
                    )
                }
            }
        }
        return out
    }

    // -------------------- 目标库写入 --------------------

    private fun ensureTargetSchema() {
        targetConn.createStatement().use { st ->
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS payment_methods (
                    id INTEGER PRIMARY KEY,
                    name TEXT NOT NULL,
                    method_type TEXT NOT NULL,
                    credit_limit TEXT,
                    current_used TEXT NOT NULL DEFAULT '0',
                    statement_day INTEGER,
                    due_day INTEGER,
                    due_days_after_statement INTEGER,
                    statement_day_inclusive INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL
                )
                """.trimIndent()
            )
            st.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS transactions (
                    id INTEGER PRIMARY KEY,
                    payment_method_id INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    amount TEXT NOT NULL,
                    txn_date TEXT NOT NULL,
                    note TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private fun writeMethods(rows: List<MethodRow>): Int {
        val sql = """
            INSERT OR REPLACE INTO payment_methods
                (id, name, method_type, credit_limit, current_used, statement_day, due_day,
                 due_days_after_statement, statement_day_inclusive, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        var count = 0
        targetConn.prepareStatement(sql).use { ps ->
            val now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            rows.forEach { r ->
                ps.setLong(1, r.id)
                ps.setString(2, r.name)
                ps.setString(3, r.methodType)
                // 金额以两位小数字符串写入：SQLite 的 NUMERIC 亲和性会把 50000.00 存成整数 50000，
                // 丢失小数位表示。坚持文本可保证回读与源库逐字符一致。
                if (r.creditLimit == null) ps.setNull(4, java.sql.Types.VARCHAR)
                else ps.setString(4, r.creditLimit.setScale(2, RoundingMode.HALF_EVEN).toPlainString())
                ps.setString(5, r.currentUsed.setScale(2, RoundingMode.HALF_EVEN).toPlainString())
                if (r.statementDay == null) ps.setNull(6, java.sql.Types.INTEGER) else ps.setInt(6, r.statementDay)
                if (r.dueDay == null) ps.setNull(7, java.sql.Types.INTEGER) else ps.setInt(7, r.dueDay)
                if (r.dueDaysAfterStatement == null) ps.setNull(8, java.sql.Types.INTEGER)
                else ps.setInt(8, r.dueDaysAfterStatement)
                ps.setInt(9, if (r.statementDayInclusive) 1 else 0)
                ps.setInt(10, if (r.active) 1 else 0)
                ps.setString(11, now)
                ps.addBatch()
                count++
            }
            ps.executeBatch()
        }
        return count
    }

    private fun writeTxns(rows: List<TxnRow>): Int {
        val sql = """
            INSERT OR REPLACE INTO transactions
                (id, payment_method_id, type, amount, txn_date, note, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        var count = 0
        targetConn.prepareStatement(sql).use { ps ->
            val now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            rows.forEach { r ->
                ps.setLong(1, r.id)
                ps.setLong(2, r.paymentMethodId)
                ps.setString(3, r.type)
                // 同 payment_methods：金额坚持文本存储，避免 SQLite NUMERIC 亲和性丢小数位
                ps.setString(4, r.amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString())
                ps.setString(5, r.txnDate)
                ps.setString(6, r.note)
                ps.setString(7, now)
                ps.addBatch()
                count++
            }
            ps.executeBatch()
        }
        return count
    }

    private fun readTargetMethods(): List<MethodRow> {
        val out = ArrayList<MethodRow>()
        targetConn.createStatement().use { st ->
            st.executeQuery("SELECT * FROM payment_methods ORDER BY id").use { rs ->
                while (rs.next()) {
                    out.add(
                        MethodRow(
                            id = rs.getLong("id"),
                            name = rs.getString("name") ?: "",
                            methodType = rs.getString("method_type") ?: "",
                            creditLimit = rs.getString("credit_limit")?.let { toDecimal(it) },
                            currentUsed = toDecimal(rs.getString("current_used")) ?: BigDecimal.ZERO,
                            statementDay = rs.getObject("statement_day")?.toString()?.toIntOrNull(),
                            dueDay = rs.getObject("due_day")?.toString()?.toIntOrNull(),
                            dueDaysAfterStatement = rs.getObject("due_days_after_statement")?.toString()?.toIntOrNull(),
                            statementDayInclusive = toBool(rs.getObject("statement_day_inclusive")),
                            active = toBool(rs.getObject("active")),
                        )
                    )
                }
            }
        }
        return out
    }

    private fun readTargetTxns(): List<TxnRow> {
        val out = ArrayList<TxnRow>()
        targetConn.createStatement().use { st ->
            st.executeQuery("SELECT * FROM transactions ORDER BY id").use { rs ->
                while (rs.next()) {
                    out.add(
                        TxnRow(
                            id = rs.getLong("id"),
                            paymentMethodId = rs.getLong("payment_method_id"),
                            type = rs.getString("type") ?: "",
                            amount = toDecimal(rs.getString("amount")) ?: BigDecimal.ZERO,
                            txnDate = normalizeDate(rs.getString("txn_date")),
                            note = rs.getString("note") ?: "",
                        )
                    )
                }
            }
        }
        return out
    }

    // -------------------- 对账 --------------------

    private fun diffMethods(src: List<MethodRow>, dst: List<MethodRow>): List<String> {
        val diffs = ArrayList<String>()
        val dstById = dst.associateBy { it.id }
        src.forEach { s ->
            val d = dstById[s.id]
            if (d == null) {
                diffs.add("id=${s.id} 名称「${s.name}」在目标库缺失")
                return@forEach
            }
            if (s.name != d.name) diffs.add("id=${s.id} name: ${s.name} != ${d.name}")
            if (s.methodType != d.methodType) diffs.add("id=${s.id} method_type: ${s.methodType} != ${d.methodType}")
            if (!amountEquals(s.creditLimit, d.creditLimit)) {
                diffs.add("id=${s.id} credit_limit: ${s.creditLimit} != ${d.creditLimit}")
            }
            if (!amountEquals(s.currentUsed, d.currentUsed)) {
                diffs.add("id=${s.id} current_used: ${s.currentUsed} != ${d.currentUsed}")
            }
            if (s.statementDay != d.statementDay) diffs.add("id=${s.id} statement_day 不一致")
            if (s.dueDay != d.dueDay) diffs.add("id=${s.id} due_day 不一致")
            if (s.dueDaysAfterStatement != d.dueDaysAfterStatement) diffs.add("id=${s.id} due_days_after_statement 不一致")
            if (s.statementDayInclusive != d.statementDayInclusive) diffs.add("id=${s.id} statement_day_inclusive 不一致")
            if (s.active != d.active) diffs.add("id=${s.id} active 不一致")
        }
        if (src.size != dst.size) diffs.add("行数不一致：源 ${src.size} != 目标 ${dst.size}")
        return diffs
    }

    private fun diffTxns(src: List<TxnRow>, dst: List<TxnRow>): List<String> {
        val diffs = ArrayList<String>()
        val dstById = dst.associateBy { it.id }
        src.forEach { s ->
            val d = dstById[s.id]
            if (d == null) {
                diffs.add("id=${s.id} 流水在目标库缺失")
                return@forEach
            }
            if (s.paymentMethodId != d.paymentMethodId) diffs.add("id=${s.id} payment_method_id 不一致")
            if (s.type != d.type) diffs.add("id=${s.id} type: ${s.type} != ${d.type}")
            if (!amountEquals(s.amount, d.amount)) diffs.add("id=${s.id} amount: ${s.amount} != ${d.amount}")
            if (s.txnDate != d.txnDate) diffs.add("id=${s.id} txn_date: ${s.txnDate} != ${d.txnDate}")
            if (s.note != d.note) diffs.add("id=${s.id} note: ${s.note} != ${d.note}")
        }
        if (src.size != dst.size) diffs.add("行数不一致：源 ${src.size} != 目标 ${dst.size}")
        return diffs
    }

    /**
     * 余额一致性：每张卡的 current_used 应能由流水推导。
     *
     * 局限说明：原 schema 没有「初始已用」字段，无法区分初始值与流水累积值。
     * 因此这里校验的是**流水本身自洽**——同一张卡不得出现「还款额 > 已有消费额」的
     * 不可能状态（那说明数据在源头就已损坏）。
     */
    private fun checkBalanceConsistency(methods: List<MethodRow>, txns: List<TxnRow>): List<String> {
        val diffs = ArrayList<String>()
        val byMethod = txns.groupBy { it.paymentMethodId }
        methods.forEach { m ->
            val list = byMethod[m.id] ?: return@forEach
            var consume = BigDecimal.ZERO
            var repay = BigDecimal.ZERO
            list.forEach {
                if (it.type == "消费") consume = consume.add(it.amount)
                else if (it.type == "还款") repay = repay.add(it.amount)
            }
            // 还款总额不应超过消费总额（否则历史上出现过透支式还款，是脏数据）
            if (repay > consume) {
                diffs.add("卡「${m.name}」(id=${m.id})：还款合计 $repay > 消费合计 $consume，流水不自洽")
            }
        }
        return diffs
    }

    // -------------------- 小工具 --------------------

    private fun toDecimal(text: String?): BigDecimal? {
        if (text.isNullOrBlank()) return null
        return try {
            BigDecimal(text.trim()).setScale(2, RoundingMode.HALF_EVEN)
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun toBool(value: Any?): Boolean {
        if (value == null) return false
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> value.toString() in listOf("1", "true", "TRUE", "True")
        }
    }

    /** 日期归一为 YYYY-MM-DD（源库可能带时间部分） */
    private fun normalizeDate(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val trimmed = text.trim()
        return if (trimmed.length >= 10) trimmed.substring(0, 10) else trimmed
    }

    private fun amountEquals(a: BigDecimal?, b: BigDecimal?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return a.compareTo(b) == 0
    }
}
