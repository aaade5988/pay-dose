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
    entities = [PaymentMethodEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PayDoseDatabase : RoomDatabase() {

    abstract fun paymentMethodDao(): PaymentMethodDao

    abstract fun transactionDao(): TransactionDao

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
                // 暂时不提供破坏性迁移：宁可启动失败也不静默清库
                .build()

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
        val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()
    }
}
