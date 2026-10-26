package com.dose.pay

import com.dose.pay.tools.Migrator
import com.dose.pay.tools.SourceKind
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 迁移后兼容性测试。
 *
 * 场景：用户用迁移工具从旧系统导入数据（金额以 TEXT 存储），
 * 然后正常启动 pay-dose 读取。验证 JPA 能正确读出 TEXT 金额并转成 BigDecimal。
 *
 * 这是真实使用路径，必须保证——否则迁移完就报错。
 *
 * 隔离：通过 properties 覆盖 datasource URL，使本类拥有独立的 ApplicationContext
 * 与独立库文件；否则会与 PlannerApiTest 共用上下文，迁移写入的旧格式数据
 * 会让 JPA 读取失败。
 */
@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:sqlite:\${java.io.tmpdir}/pay-dose-migrated-test.db",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MigratedDataCompatTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var dataSource: javax.sql.DataSource

    @TempDir
    lateinit var tmp: Path

    @Test
    fun `jpa reads amounts stored as text by migration tool`() {
        // 1. 造源库
        val src = tmp.resolve("legacy.db")
        DriverManager.getConnection("jdbc:sqlite:${src.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    CREATE TABLE payment_methods (
                        id INTEGER PRIMARY KEY, name TEXT NOT NULL,
                        method_type TEXT NOT NULL, credit_limit TEXT, current_used TEXT NOT NULL DEFAULT '0',
                        statement_day INTEGER, due_day INTEGER, due_days_after_statement INTEGER,
                        statement_day_inclusive INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1,
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    CREATE TABLE transactions (
                        id INTEGER PRIMARY KEY, payment_method_id INTEGER NOT NULL, type TEXT NOT NULL,
                        amount TEXT NOT NULL, txn_date TEXT NOT NULL, note TEXT NOT NULL DEFAULT '',
                        created_at TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    INSERT INTO payment_methods VALUES
                        (1, '旧工商', '信用卡', '50000.00', '1200.55', 15, 8, NULL, 0, 1, '2026-07-01 10:00:00'),
                        (2, '旧花呗', '电子钱包', NULL, '300.50', 10, 20, NULL, 1, 1, '2026-07-02 11:00:00')
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    INSERT INTO transactions VALUES
                        (1, 1, '消费', '1000.55', '2026-07-10', '旧数据', '2026-07-10 09:00:00')
                    """.trimIndent()
                )
            }
        }

        // 2. 迁移到 Spring 实际使用的那个 SQLite 库（从注入的 DataSource 取连接）
        DriverManager.getConnection("jdbc:sqlite:${src.toAbsolutePath()}").use { srcConn ->
            dataSource.connection.use { targetConn ->
                val report = Migrator(
                    sourceKind = SourceKind.SQLITE,
                    sourceConn = srcConn,
                    targetConn = targetConn,
                    dryRun = false,
                    truncate = true,
                ).run()
                assertTrue(report.ok, "迁移对账应通过：${report.methodDiffs + report.txnDiffs}")
            }
        }

        // 3. 通过 HTTP 接口读取（走 JPA → BigDecimal 的完整链路）
        val result = mockMvc.perform(get("/api/methods")).andReturn()
        assertEquals(200, result.response.status, result.response.getContentAsString(Charsets.UTF_8))
        val body = objectMapper.readTree(result.response.contentAsString)
        val methods = body["methods"]
        assertEquals(2, methods.size())

        // 信用卡：金额必须精确读出，不得因 TEXT 存储而报错
        assertEquals("旧工商", methods[0]["name"].asText())
        assertEquals("50000.00", methods[0]["credit_limit"].asText())
        assertEquals("1200.55", methods[0]["current_used"].asText())
        assertEquals("48799.45", methods[0]["available_limit"].asText())

        // 不限额卡：credit_limit 必须为 null 而非字符串 "null"
        assertEquals("旧花呗", methods[1]["name"].asText())
        assertTrue(methods[1]["credit_limit"].isNull, "不限额卡的 credit_limit 应为 null")
        assertEquals("300.50", methods[1]["current_used"].asText())
        assertTrue(methods[1]["available_limit"].isNull)
    }
}
