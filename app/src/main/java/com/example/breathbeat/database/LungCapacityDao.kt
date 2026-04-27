package com.example.breathbeat.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LungCapacityDao {
    @Query("SELECT * FROM lung_capacity_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<LungCapacityRecord>>

    @Query("SELECT * FROM lung_capacity_records WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getRecordsInRange(startTime: Long, endTime: Long): Flow<List<LungCapacityRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: LungCapacityRecord)

    @Query("DELETE FROM lung_capacity_records")
    suspend fun deleteAll()
}
