package com.dose.pay.domain

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType

interface PaymentMethodRepository : JpaRepository<PaymentMethodEntity, Long> {

    fun findAllByActiveTrueOrderByIdAsc(): List<PaymentMethodEntity>

    fun findAllByOrderByIdAsc(): List<PaymentMethodEntity>

    fun findByName(name: String): PaymentMethodEntity?

    /**
     * 加行锁读取卡片，用于「流水与余额同事务原子维护」。
     *
     * SQLite 是库级写锁（单写者），PESSIMISTIC_WRITE 在 SQLite 方言下不会生成
     * `FOR UPDATE`，而是依赖 SQLite 自身的写事务串行化——语义上同样保证了
     * 读-改-写不被并发插入打断。业务层仍用同一事务包裹，行为等价。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentMethodEntity p WHERE p.id = :id")
    fun lockById(@Param("id") id: Long): PaymentMethodEntity?
}

interface TransactionRepository : JpaRepository<TransactionEntity, Long> {

    fun countByPaymentMethodId(paymentMethodId: Long): Long

    /** 全部流水，按 txn_date DESC, id DESC（对应原 ORDER BY 语义） */
    @Query("SELECT t FROM TransactionEntity t ORDER BY t.txnDate DESC, t.id DESC")
    fun pageAll(pageable: Pageable): List<TransactionEntity>

    /** 按卡筛选的流水，排序同上 */
    @Query("SELECT t FROM TransactionEntity t WHERE t.paymentMethodId = :methodId ORDER BY t.txnDate DESC, t.id DESC")
    fun pageByMethod(@Param("methodId") methodId: Long, pageable: Pageable): List<TransactionEntity>

    fun findAllByPaymentMethodId(paymentMethodId: Long): List<TransactionEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransactionEntity t WHERE t.id = :id")
    fun lockById(@Param("id") id: Long): TransactionEntity?
}
