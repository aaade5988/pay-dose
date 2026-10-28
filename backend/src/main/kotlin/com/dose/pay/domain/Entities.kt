package com.dose.pay.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 支付方式（信用卡 / 储蓄卡 / 电子钱包 / 其他）。
 *
 * 单用户应用，不设 user_id。
 * 注意：SQLite 没有原生 DECIMAL，JPA 层面仍声明 precision/scale，
 * 由 Hibernate 以 NUMERIC 存储；读取时统一 setScale(2) 归一，保证金额精度。
 */
@Entity
@Table(name = "payment_methods")
class PaymentMethodEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "name", nullable = false, length = 50)
    var name: String = "",

    @Column(name = "method_type", nullable = false, length = 20)
    var methodType: String = "",

    /** null = 不限额 */
    @Column(name = "credit_limit", precision = 14, scale = 2, nullable = true)
    var creditLimit: BigDecimal? = null,

    @Column(name = "current_used", precision = 14, scale = 2, nullable = false)
    var currentUsed: BigDecimal = BigDecimal.ZERO,

    @Column(name = "statement_day", nullable = true)
    var statementDay: Int? = null,

    @Column(name = "due_day", nullable = true)
    var dueDay: Int? = null,

    @Column(name = "due_days_after_statement", nullable = true)
    var dueDaysAfterStatement: Int? = null,

    @Column(name = "statement_day_inclusive", nullable = false)
    var statementDayInclusive: Boolean = false,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    /**
     * 「已还清」标记的有效期（= 标记时所在账单周期的还款日）。
     *
     * 这是**人工标记**，不是流水：标记后该卡本期消费在看板里显示为「已还清」，
     * 不再计入额度占用（仪表盘里那一段换成另一种颜色冻结）。
     *
     * 写成日期而不是布尔值，是为了让标记**随周期自动失效**：
     * 过了本期还款日进入下一期，标记自动过期，卡片恢复正常占用，无需人工清理。
     * 取消标记 = 置 NULL。
     */
    @Column(name = "settled_until", nullable = true)
    var settledUntil: LocalDate? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
) {
    /** 转成纯算法层模型（Calculator 不依赖 JPA） */
    fun toDomain(): PaymentMethod = PaymentMethod(
        id = id ?: 0L,
        name = name,
        methodType = methodType,
        creditLimit = creditLimit,
        currentUsed = currentUsed,
        statementDay = statementDay,
        dueDay = dueDay,
        dueDaysAfterStatement = dueDaysAfterStatement,
        statementDayInclusive = statementDayInclusive,
        active = active,
        settledUntil = settledUntil,
    )
}

/**
 * 消费 / 还款流水。
 *
 * amount 恒为正数，方向由 type 决定（消费 +、还款 −）。
 * current_used 由流水在**同一事务**内原子维护，界面不直接改。
 */
@Entity
@Table(name = "transactions")
class TransactionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "payment_method_id", nullable = false)
    var paymentMethodId: Long = 0,

    @Column(name = "type", nullable = false, length = 10)
    var type: String = Calculator.TXN_CONSUME,

    @Column(name = "amount", precision = 14, scale = 2, nullable = false)
    var amount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "txn_date", nullable = false)
    var txnDate: LocalDate = LocalDate.now(),

    @Column(name = "note", nullable = false, length = 50)
    var note: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
