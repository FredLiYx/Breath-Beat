package com.example.breathbeat.database

import com.example.breathbeat.health.MonthlyStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

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

    override suspend fun getAverageLungCapacity(startTime: Long, endTime: Long): Double? {
        val records = lungCapacityDao.getRecordsInRange(startTime, endTime).first()
        if (records.isEmpty()) return null
        return records.map { it.volumeML }.average()
    }

    override suspend fun getMinMaxLungCapacity(startTime: Long, endTime: Long): Pair<Double?, Double?> {
        val records = lungCapacityDao.getRecordsInRange(startTime, endTime).first()
        if (records.isEmpty()) return Pair(null, null)
        val volumes = records.map { it.volumeML.toDouble() }
        return Pair(volumes.minOrNull(), volumes.maxOrNull())
    }

    override suspend fun getYearlyLungStats(): List<MonthlyStats> {
        val now = Instant.now()
        val stats = mutableListOf<MonthlyStats>()
        val zoneId = ZoneId.systemDefault()
        
        for (i in 11 downTo 0) {
            val dateTime = now.atZone(zoneId).minusMonths(i.toLong())
            val monthStart = dateTime.withDayOfMonth(1).with(LocalTime.MIN).toInstant().toEpochMilli()
            val monthEnd = dateTime.withDayOfMonth(dateTime.toLocalDate().lengthOfMonth()).with(LocalTime.MAX).toInstant().toEpochMilli()
            
            val records = lungCapacityDao.getRecordsInRange(monthStart, monthEnd).first()
            val monthName = Instant.ofEpochMilli(monthStart).atZone(zoneId).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            
            if (records.isEmpty()) {
                stats.add(MonthlyStats(monthName, null, null, null))
            } else {
                val volumes = records.map { it.volumeML.toDouble() }
                stats.add(MonthlyStats(monthName, volumes.average(), volumes.minOrNull(), volumes.maxOrNull()))
            }
        }
        return stats
    }
}
