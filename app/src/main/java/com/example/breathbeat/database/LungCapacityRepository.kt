package com.example.breathbeat.database

import kotlinx.coroutines.flow.Flow

interface LungCapacityRepository {
    fun getAllRecords(): Flow<List<LungCapacityRecord>>
    fun getRecordsInRange(startTime: Long, endTime: Long): Flow<List<LungCapacityRecord>>
    suspend fun insertRecord(volumeML: Int, timestamp: Long = System.currentTimeMillis())
    suspend fun deleteAll()
}
