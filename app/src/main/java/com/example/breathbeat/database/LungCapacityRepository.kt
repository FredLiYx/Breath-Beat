package com.example.breathbeat.database

import com.example.breathbeat.health.MonthlyStats
import kotlinx.coroutines.flow.Flow

interface LungCapacityRepository {
    fun getAllRecords(): Flow<List<LungCapacityRecord>>
    fun getRecordsInRange(startTime: Long, endTime: Long): Flow<List<LungCapacityRecord>>
    suspend fun insertRecord(volumeML: Int, timestamp: Long = System.currentTimeMillis())
    suspend fun deleteAll()

    suspend fun getAverageLungCapacity(startTime: Long, endTime: Long): Double?
    suspend fun getMinMaxLungCapacity(startTime: Long, endTime: Long): Pair<Double?, Double?>
    suspend fun getYearlyLungStats(): List<MonthlyStats>
}
