package com.example.barandgrillpos.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY timestamp DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity)

    @Query("SELECT * FROM sales WHERE isSynced = 0")
    suspend fun getUnsyncedSales(): List<SaleEntity>

    @Query("UPDATE sales SET isSynced = 1 WHERE id = :saleId")
    suspend fun markAsSynced(saleId: String)

    // Sync Queue Methods
    @Insert
    suspend fun addToSyncQueue(entry: SyncQueueEntry)

    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    suspend fun getSyncQueue(): List<SyncQueueEntry>

    @Delete
    suspend fun removeFromSyncQueue(entry: SyncQueueEntry)
}

