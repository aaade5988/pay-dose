package com.dosepay.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * 支付方式 DAO（对应 backend 的 PaymentMethodRepository）。
 *
 * 所有查询都限定 active = 1，与后端 `findAllByActiveTrueOrderByIdAsc` 一致。
 */
@Dao
interface PaymentMethodDao {

    /** 有效卡列表，按 id 升序（推荐与看板都依赖这个顺序） */
    @Query("SELECT * FROM payment_methods WHERE active = 1 ORDER BY id ASC")
    suspend fun findAllActive(): List<PaymentMethodEntity>

    @Query("SELECT * FROM payment_methods WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): PaymentMethodEntity?

    /** 重名检查（后端 createMethod 的 409 判据） */
    @Query("SELECT * FROM payment_methods WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): PaymentMethodEntity?

    @Insert
    suspend fun insert(entity: PaymentMethodEntity): Long

    @Update
    suspend fun update(entity: PaymentMethodEntity)

    @Query("DELETE FROM payment_methods WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM payment_methods")
    suspend fun count(): Int
}

/**
 * 流水 DAO（对应 backend 的 TransactionRepository）。
 *
 * 排序与后端的 `pageAll` / `pageByMethod` 一致：txn_date DESC, id DESC。
 */
@Dao
interface TransactionDao {

    /** 全量流水（看板与切负债对账用） */
    @Query("SELECT * FROM transactions ORDER BY txn_date DESC, id DESC")
    suspend fun findAll(): List<TransactionEntity>

    /** 分页：无卡筛选 */
    @Query("SELECT * FROM transactions ORDER BY txn_date DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun pageAll(limit: Int, offset: Int): List<TransactionEntity>

    /** 分页：按卡筛选 */
    @Query(
        "SELECT * FROM transactions WHERE payment_method_id = :methodId " +
            "ORDER BY txn_date DESC, id DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun pageByMethod(methodId: Long, limit: Int, offset: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TransactionEntity?

    /** 某卡流水数（删卡前的 409 判据） */
    @Query("SELECT COUNT(*) FROM transactions WHERE payment_method_id = :methodId")
    suspend fun countByMethodId(methodId: Long): Int

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Delete
    suspend fun delete(entity: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
