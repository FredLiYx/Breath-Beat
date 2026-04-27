package com.example.breathbeat.database

import kotlinx.coroutines.flow.Flow

class LungCapacityRepositoryImpl(
    private val lungCapacityDao: LungCapacityDao
) : LungCapacityRepository {
    override fun getAllRecords(): Flow<List<LungCapacityRecord>> {
        return lungCapacityDao.getAllRecords()
    }

    override fun getRecordsInRange(startTime: Long, endTime: Long): Flow<List<LungCapacityRecord>> {
        return lungCapacityDao.getRecordsInRange(startTime, endTime)
    }

    override suspend fun insertRecord(volumeML: Int, timestamp: Long) {
        val record = LungCapacityRecord(volumeML = volumeML, timestamp = timestamp)
        lungCapacityDao.insertRecord(record)
    }

    override suspend fun deleteAll() {
        lungCapacityDao.deleteAll()
    }
}
