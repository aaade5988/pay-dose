package com.dosepay.app.data.local

import android.content.Context
import androidx.room.withTransaction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 种子数据文件结构 */
@Serializable
data class SeedData(
    val version: Int = 1,
    @SerialName("exported_from") val exportedFrom: String = "",
    @SerialName("exported_at") val exportedAt: String = "",
    val methods: List<SeedMethod> = emptyList(),
    val transactions: List<SeedTxn> = emptyList(),
)

@Serializable
data class SeedMethod(
    val id: Long,
    val name: String,
    @SerialName("method_type") val methodType: String,
    @SerialName("credit_limit") val creditLimit: String? = null,
    @SerialName("current_used") val currentUsed: String = "0.00",
    @SerialName("statement_day") val statementDay: Int? = null,
    @SerialName("due_day") val dueDay: Int? = null,
    @SerialName("due_days_after_statement") val dueDaysAfterStatement: Int? = null,
    @SerialName("statement_day_inclusive") val statementDayInclusive: Boolean = false,
    val active: Boolean = true,
    @SerialName("settled_until") val settledUntil: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class SeedTxn(
    val id: Long,
    @SerialName("payment_method_id") val paymentMethodId: Long,
    val type: String,
    val amount: String,
    @SerialName("txn_date") val txnDate: String,
    val note: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

/**
 * 首启数据导入器。
 *
 * 把 assets/seed-data.json（从原 Spring Boot 后端导出的真实数据）
 * 一次性写入本地数据库。
 *
 * **幂等**：库里已有任何支付方式就跳过，绝不覆盖用户后来的改动。
 * **保留原始 id**：卡与流水的 id 关系必须原样保留，否则归期会错。
 */
object SeedImporter {

    private const val ASSET_NAME = "seed-data.json"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    suspend fun importIfEmpty(context: Context, db: PayDoseDatabase) {
        val methodDao = db.paymentMethodDao()
        val txnDao = db.transactionDao()

        // 幂等：已有数据直接返回（用户可能已经手动加过卡）
        if (methodDao.count() > 0) return

        val seed = try {
            context.assets.open(ASSET_NAME).use { stream ->
                json.decodeFromString<SeedData>(stream.readBytes().decodeToString())
            }
        } catch (e: Exception) {
            // 没有种子文件或解析失败：当作全新用户，从空库开始
            return
        }

        if (seed.methods.isEmpty()) return

        db.withTransaction {
            // 保留原始 id：Room 允许显式指定主键
            for (m in seed.methods) {
                methodDao.insert(
                    PaymentMethodEntity(
                        id = m.id,
                        name = m.name,
                        methodType = m.methodType,
                        creditLimit = m.creditLimit,
                        currentUsed = m.currentUsed,
                        statementDay = m.statementDay,
                        dueDay = m.dueDay,
                        dueDaysAfterStatement = m.dueDaysAfterStatement,
                        statementDayInclusive = m.statementDayInclusive,
                        active = m.active,
                        settledUntil = m.settledUntil,
                        createdAt = m.createdAt.ifBlank { "2026-01-01T00:00:00" },
                    )
                )
            }
            for (t in seed.transactions) {
                txnDao.insert(
                    TransactionEntity(
                        id = t.id,
                        paymentMethodId = t.paymentMethodId,
                        type = t.type,
                        amount = t.amount,
                        txnDate = t.txnDate,
                        note = t.note,
                        createdAt = t.createdAt.ifBlank { "2026-01-01T00:00:00" },
                    )
                )
            }
        }
    }
}
