package com.example.barandgrillpos.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'Cash'")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN soldBy TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales ADD COLUMN branchId TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS menu_items_cache (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                price REAL NOT NULL,
                category TEXT NOT NULL,
                subcategory TEXT NOT NULL,
                branchId TEXT,
                isActive INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_menu_items_cache_branchId ON menu_items_cache(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_menu_items_cache_isActive ON menu_items_cache(isActive)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS employees_cache (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                role TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_cache_isActive ON employees_cache(isActive)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_cache (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                stock_quantity REAL NOT NULL,
                min_threshold REAL NOT NULL,
                branchId TEXT,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_cache_branchId ON inventory_cache(branchId)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS categories_cache (
                name TEXT NOT NULL,
                parentName TEXT,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(name)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE menu_items_cache ADD COLUMN ingredientsJson TEXT")
    }
}

// Normalize stale mixed-case subcategory/category values to UPPERCASE so that
// case-sensitive grouping in the POS UI does not produce duplicate filter tabs.
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE menu_items_cache SET subcategory = UPPER(subcategory), category = UPPER(category)")
    }
}

// Migration 9→10: Wipe stale categories cache and inactive menu item rows.
// This forces a clean re-fetch from Supabase so any out-of-date "COCKTAILS as main category"
// or inactive items are removed from the local cache immediately.
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM menu_items_cache WHERE isActive = 0")
        db.execSQL("DELETE FROM categories_cache")
    }
}

// Migration 10→11: Add expenses table (kept for backward compatibility, no expense features in POS).
// Now stripped in version 12.
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS expenses (
                id TEXT NOT NULL,
                amount REAL NOT NULL,
                category TEXT NOT NULL,
                description TEXT NOT NULL,
                branchId TEXT,
                timestamp INTEGER NOT NULL,
                isSynced INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_branchId ON expenses(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_category ON expenses(category)")
    }
}

// Migration 11→12: Remove expenses table from POS (expenses are owner-level only).
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS expenses")
    }
}

// Migration 12→13: Add status column to inventory_cache for asset lifecycle.
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inventory_cache ADD COLUMN status TEXT NOT NULL DEFAULT 'AVAILABLE'")
    }
}

// Migration 13→14: Add customers_cache table for membership management.
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS customers_cache (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                email TEXT,
                address TEXT,
                idType TEXT,
                idNumber TEXT,
                profileImageUrl TEXT,
                membershipStatus TEXT NOT NULL DEFAULT 'ACTIVE',
                membershipExpiry TEXT,
                notes TEXT,
                branchId TEXT,
                createdAt TEXT,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_cache_branchId ON customers_cache(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_cache_membershipStatus ON customers_cache(membershipStatus)")
    }
}

@Database(
    entities = [
        SaleEntity::class, 
        SyncQueueEntry::class, 
        MenuItemEntity::class, 
        EmployeeEntity::class, 
        InventoryItemEntity::class, 
        CategoryEntity::class,
        CustomerEntity::class
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun saleDao(): SaleDao
    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bar_and_grill_db"
                )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, 
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, 
                    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    MIGRATION_13_14
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
