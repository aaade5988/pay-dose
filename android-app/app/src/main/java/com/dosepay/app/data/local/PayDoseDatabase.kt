package com.dosepay.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 本地数据库（取代原来的 Spring Boot + SQLite 后端）。
 *
 * 数据存在 App 私有目录 `/data/data/com.dosepay.app/databases/pay-dose.db`，
 * **升级 APK 不会动这个文件**——这是「更新后数据不丢」的根本保证。
 *
 * 表结构变更时：版本号 +1，并在 [MIGRATIONS] 里补一条 Migration，
 * Room 会在打开库时自动执行，用户无感知、数据完整保留。
 */
@Database(
    entities = [
        PaymentMethodEntity::class,
        TransactionEntity::class,
        InstallmentPlanEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PayDoseDatabase : RoomDatabase() {

    abstract fun paymentMethodDao(): PaymentMethodDao

    abstract fun transactionDao(): TransactionDao

    abstract fun installmentPlanDao(): InstallmentPlanDao

    companion object {
        private const val DB_NAME = "pay-dose.db"

        @Volatile
        private var instance: PayDoseDatabase? = null

        fun get(context: Context): PayDoseDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): PayDoseDatabase =
            Room.databaseBuilder(context, PayDoseDatabase::class.java, DB_NAME)
                .addMigrations(*MIGRATIONS)
                // 暂时不提供破坏性迁移：宁可启动失败也不静默清库
                .build()

        /**
         * v1 → v2：新增账单分期计划表。
         *
         * 只是建表，不动已有数据：老用户的卡和流水一行不变。
         */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `installment_plans` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `payment_method_id` INTEGER NOT NULL,
                        `cycle_end` TEXT NOT NULL,
                        `principal` TEXT NOT NULL,
                        `periods` INTEGER NOT NULL,
                        `rate_mode` TEXT NOT NULL,
                        `rate` TEXT NOT NULL,
                        `created_at` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_installment_plans_payment_method_id_cycle_end`
                    ON `installment_plans` (`payment_method_id`, `cycle_end`)
                    """.trimIndent()
                )
            }
        }

        /**
         * 迁移登记表。
         *
         * 例：新增 note 字段时
         * ```
         * val MIGRATION_1_2 = object : Migration(1, 2) {
         *     override fun migrate(db: SupportSQLiteDatabase) {
         *         db.execSQL("ALTER TABLE transactions ADD COLUMN note2 TEXT NOT NULL DEFAULT ''")
         *     }
         * }
         * ```
         * 若不加 migration 就升版本号，Room 会抛 IllegalStateException 而不是清库——这是刻意的。
         */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf(MIGRATION_1_2)
    }
}
