package com.dose.pay

import com.dose.pay.tools.Migrator
import com.dose.pay.tools.SourceKind
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 数据迁移工具测试。
 *
 * 用临时 SQLite 文件模拟「源库」（结构对齐原 credit-payment-planner 的 SQLite 形态），
 * 迁移到另一个临时 SQLite 文件（pay-dose 目标库），验证：
 * - 业务字段逐行一致
 * - id 保留
 * - 对账通过（RECONCILE OK 语义）
 * - 幂等（重复运行结果一致）
 * - 脏数据能被对账检测出来（证明对账不是形同虚设）
 */
class MigratorTest {

    @TempDir
    lateinit var tmp: Path

    /** 造一个「源库」，表结构对齐原项目的 SQLite schema */
    private fun buildSourceDb(file: Path): Path {
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    CREATE TABLE payment_methods (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL DEFAULT 0,
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
                    CREATE TABLE transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        payment_method_id INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        amount TEXT NOT NULL,
                        txn_date TEXT NOT NULL,
                        note TEXT NOT NULL DEFAULT '',
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                // 3 张卡：信用卡带额度、信用卡不限额、储蓄卡
                st.executeUpdate(
                    """
                    INSERT INTO payment_methods
                        (id, name, method_type, credit_limit, current_used, statement_day, due_day,
                         due_days_after_statement, statement_day_inclusive, active, created_at)
                    VALUES
                        (1, '工商', '信用卡', '50000.00', '1200.00', 15, 8, NULL, 0, 1, '2026-07-01 10:00:00'),
                        (2, '花呗', '电子钱包', NULL, '300.50', 10, 20, NULL, 1, 1, '2026-07-02 11:00:00'),
                        (3, '招行储蓄', '储蓄卡', NULL, '0', NULL, NULL, NULL, 0, 1, '2026-07-03 12:00:00')
                    """.trimIndent()
                )

                // 3 条流水：消费 1000 + 消费 600 + 还款 400 = 净 1200（与卡 1 的 current_used 一致）
                st.executeUpdate(
                    """
                    INSERT INTO transactions
                        (id, payment_method_id, type, amount, txn_date, note, created_at)
                    VALUES
                        (1, 1, '消费', '1000.00', '2026-07-10', '买手机', '2026-07-10 09:00:00'),
                        (2, 1, '消费', '600.00', '2026-07-12', '吃饭', '2026-07-12 12:00:00'),
                        (3, 1, '还款', '400.00', '2026-07-15', '', '2026-07-15 18:00:00')
                    """.trimIndent()
                )
            }
        }
        return file
    }

    private fun runMigration(sourceDb: Path, targetDb: Path, truncate: Boolean = false) =
        DriverManager.getConnection("jdbc:sqlite:${sourceDb.toAbsolutePath()}").use { src ->
            DriverManager.getConnection("jdbc:sqlite:${targetDb.toAbsolutePath()}").use { dst ->
                Migrator(
                    sourceKind = SourceKind.SQLITE,
                    sourceConn = src,
                    targetConn = dst,
                    dryRun = false,
                    truncate = truncate,
                ).run()
            }
        }

    @Test
    fun `migration preserves all business fields`() {
        val src = buildSourceDb(tmp.resolve("src.db"))
        val dst = tmp.resolve("dst.db")

        val report = runMigration(src, dst)

        assertTrue(report.ok, "对账应通过，实际差异：${report.methodDiffs + report.txnDiffs + report.balanceDiffs}")
        assertEquals(3, report.writtenMethods)
        assertEquals(3, report.writtenTxns)

        // 回读目标库校验关键字段
        DriverManager.getConnection("jdbc:sqlite:${dst.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT * FROM payment_methods ORDER BY id").use { rs ->
                    assertTrue(rs.next())
                    assertEquals(1L, rs.getLong("id"))
                    assertEquals("工商", rs.getString("name"))
                    assertEquals("50000.00", rs.getString("credit_limit"))
                    assertEquals("1200.00", rs.getString("current_used"))
                    assertEquals(15, rs.getInt("statement_day"))
                    assertEquals(8, rs.getInt("due_day"))

                    assertTrue(rs.next())
                    assertEquals("花呗", rs.getString("name"))
                    assertEquals(null, rs.getString("credit_limit"))
                    assertEquals(1, rs.getInt("statement_day_inclusive"))
                }
            }
        }
    }

    @Test
    fun `migration is idempotent`() {
        val src = buildSourceDb(tmp.resolve("src2.db"))
        val dst = tmp.resolve("dst2.db")

        val first = runMigration(src, dst, truncate = true)
        assertTrue(first.ok)

        val second = runMigration(src, dst, truncate = true)
        assertTrue(second.ok, "第二次迁移也应通过")
        assertEquals(first.writtenMethods, second.writtenMethods)
        assertEquals(first.writtenTxns, second.writtenTxns)
    }

    @Test
    fun `dry run does not write target`() {
        val src = buildSourceDb(tmp.resolve("src3.db"))
        val dst = tmp.resolve("dst3.db")

        DriverManager.getConnection("jdbc:sqlite:${src.toAbsolutePath()}").use { srcConn ->
            DriverManager.getConnection("jdbc:sqlite:${dst.toAbsolutePath()}").use { dstConn ->
                val report = Migrator(
                    sourceKind = SourceKind.SQLITE,
                    sourceConn = srcConn,
                    targetConn = dstConn,
                    dryRun = true,
                    truncate = false,
                ).run()

                assertEquals(3, report.sourceMethods)
                assertEquals(3, report.sourceTxns)
                assertEquals(0, report.writtenMethods, "dry-run 不应写入目标库")
                assertEquals(0, report.writtenTxns)
            }
        }
    }

    @Test
    fun `reconcile detects dirty balance data`() {
        val src = buildSourceDb(tmp.resolve("src4.db"))
        // 追加一条「还款超过所有消费」的脏流水，模拟源库数据已损坏
        DriverManager.getConnection("jdbc:sqlite:${src.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    INSERT INTO transactions (id, payment_method_id, type, amount, txn_date, note, created_at)
                    VALUES (4, 1, '还款', '9999.00', '2026-07-16', '异常还款', '2026-07-16 10:00:00')
                    """.trimIndent()
                )
            }
        }

        val dst = tmp.resolve("dst4.db")
        val report = runMigration(src, dst)

        // 字段级对账仍应通过（数据原样搬运），但余额一致性检查必须报出问题
        assertTrue(report.balanceDiffs.isNotEmpty(), "对账应检测出还款超过消费的脏数据")
        assertTrue(!report.ok, "存在余额不一致时整体对账不应通过")
        assertTrue(
            report.balanceDiffs.any { it.contains("还款合计") && it.contains(">") },
            "差异说明应指出还款合计大于消费合计，实际：${report.balanceDiffs}",
        )
    }

    @Test
    fun `missing source table fails gracefully`() {
        val empty = tmp.resolve("empty.db")
        // 建一个不含业务表的空库
        DriverManager.getConnection("jdbc:sqlite:${empty.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { it.executeUpdate("CREATE TABLE dummy (id INTEGER)") }
        }
        val dst = tmp.resolve("dst5.db")

        var threw = false
        try {
            runMigration(empty, dst)
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw, "源库缺表时应抛出明确异常，而不是静默成功")
    }
}
