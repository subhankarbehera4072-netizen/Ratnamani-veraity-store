package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.entity.BillEntity
import com.example.data.entity.BillItemEntity
import com.example.data.entity.BillWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface BillDao {
    @Transaction
    @Query("SELECT * FROM bills ORDER BY timestamp DESC")
    fun getAllBillsWithItems(): Flow<List<BillWithItems>>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :billId LIMIT 1")
    suspend fun getBillWithItemsById(billId: Long): BillWithItems?

    @Transaction
    @Query("SELECT * FROM bills WHERE billNumber = :billNumber LIMIT 1")
    suspend fun getBillByNumber(billNumber: String): BillWithItems?

    @Transaction
    @Query("SELECT * FROM bills WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getBillsInRange(startTime: Long, endTime: Long): Flow<List<BillWithItems>>

    @Transaction
    @Query("SELECT * FROM bills WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getBillsInRangeList(startTime: Long, endTime: Long): List<BillWithItems>

    @Query("SELECT COALESCE(SUM(grandTotal), 0.0) FROM bills WHERE timestamp >= :startTime AND timestamp <= :endTime")
    fun getSalesTotalInRange(startTime: Long, endTime: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM bills WHERE timestamp >= :startTime AND timestamp <= :endTime")
    fun getBillsCountInRange(startTime: Long, endTime: Long): Flow<Int>

    @Query("SELECT * FROM bills ORDER BY id DESC LIMIT 1")
    suspend fun getLastBill(): BillEntity?

    @Query("SELECT * FROM bills")
    suspend fun getAllBillsList(): List<BillEntity>

    @Query("SELECT * FROM bill_items")
    suspend fun getAllBillItemsList(): List<BillItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBillItems(items: List<BillItemEntity>)

    @Query("DELETE FROM bills WHERE id = :billId")
    suspend fun deleteBillById(billId: Long)

    @Query("DELETE FROM bills")
    suspend fun deleteAllBills()

    @Query("DELETE FROM bill_items")
    suspend fun deleteAllBillItems()
}
