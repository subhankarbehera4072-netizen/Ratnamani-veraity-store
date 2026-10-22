package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.ShopSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopSettingsDao {
    @Query("SELECT * FROM shop_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<ShopSettingsEntity?>

    @Query("SELECT * FROM shop_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): ShopSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: ShopSettingsEntity)

    @Update
    suspend fun update(settings: ShopSettingsEntity)

    @Query("UPDATE shop_settings SET lastBackupTimestamp = :timestamp, lastBackupStatus = :status WHERE id = 1")
    suspend fun updateBackupStatus(timestamp: Long, status: String)
}
