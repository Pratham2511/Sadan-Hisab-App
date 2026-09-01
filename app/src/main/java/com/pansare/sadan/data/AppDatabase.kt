package com.pansare.sadan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RoomEntity::class,
        TenantEntity::class,
        PaymentEntity::class,
        MonthlyLedgerEntity::class,
        PaymentAllocationEntity::class,
        RentChangeEntity::class,
        SettingEntity::class,
        ImportValidationIssueEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun tenantDao(): TenantDao
    abstract fun paymentDao(): PaymentDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun allocationDao(): AllocationDao
    abstract fun rentChangeDao(): RentChangeDao
    abstract fun settingsDao(): SettingsDao
    abstract fun validationDao(): ValidationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pansare_sadan.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
