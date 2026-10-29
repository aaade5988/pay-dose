package com.dosepay.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dosepay.app.core.PaymentMethod
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 支付方式表（对应 backend 的 payment_methods）。
 *
 * 字段名与后端实体完全一致，便于对账与数据迁移。
 * 金额列以 TEXT 存两位小数字符串：SQLite 无原生 DECIMAL，
 * 存字符串可避免 REAL 的浮点误差，与「金额全程 BigDecimal」的约定一致。
 */
@Entity(tableName = "payment_methods")
data class PaymentMethodEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "method_type")
    val methodType: String,

    /** null = 不限额 */
    @ColumnInfo(name = "credit_limit")
    val creditLimit: String? = null,

    @ColumnInfo(name = "current_used")
    val currentUsed: String = "0.00",

    @ColumnInfo(name = "statement_day")
    val statementDay: Int? = null,

    @ColumnInfo(name = "due_day")
    val dueDay: Int? = null,

    @ColumnInfo(name = "due_days_after_statement")
    val dueDaysAfterStatement: Int? = null,

    @ColumnInfo(name = "statement_day_inclusive")
    val statementDayInclusive: Boolean = false,

    @ColumnInfo(name = "active")
    val active: Boolean = true,

    /**
     * 「已还清」标记的有效期（= 标记时所在账单周期的还款日），ISO 字符串。
     *
     * 这是**人工标记**，不是流水：标记后该卡本期消费在看板里显示为「已还清」，
     * 不再计入额度占用。写成日期而不是布尔值，是为了让标记**随周期自动失效**。
     */
    @ColumnInfo(name = "settled_until")
    val settledUntil: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: String = LocalDateTime.now().toString(),
) {
    /** 转成纯算法层模型（Calculator 不依赖 Room） */
    fun toDomain(): PaymentMethod = PaymentMethod(
        id = id,
        name = name,
        methodType = methodType,
        creditLimit = creditLimit?.let { BigDecimal(it) },
        currentUsed = BigDecimal(currentUsed),
        statementDay = statementDay,
        dueDay = dueDay,
        dueDaysAfterStatement = dueDaysAfterStatement,
        statementDayInclusive = statementDayInclusive,
        active = active,
        settledUntil = settledUntil?.let { LocalDate.parse(it) },
    )
}

/**
 * 消费 / 还款流水表（对应 backend 的 transactions）。
 *
 * amount 恒为正数，方向由 type 决定（消费 +、还款 −）。
 * current_used 由流水在**同一事务**内原子维护，界面不直接改。
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "payment_method_id")
    val paymentMethodId: Long,

    /** 消费 / 还款 */
    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "amount")
    val amount: String,

    /** ISO 日期 "YYYY-MM-DD" */
    @ColumnInfo(name = "txn_date")
    val txnDate: String,

    @ColumnInfo(name = "note")
    val note: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: String = LocalDateTime.now().toString(),
) {
    val amountDecimal: BigDecimal get() = BigDecimal(amount)
    val txnLocalDate: LocalDate get() = LocalDate.parse(txnDate)
}
