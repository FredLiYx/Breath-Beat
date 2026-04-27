package com.example.breathbeat.health

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import java.time.Instant

data class MonthlyStats(
    val monthName: String,
    val avg: Double?,
    val min: Double?,
    val max: Double?
)

interface HealthManager {
    suspend fun hasAllPermissions(): Boolean
    fun getPermissionRequestIntent(): Set<String>
    
    suspend fun getHeartRateData(startTime: Instant, endTime: Instant): List<HeartRateRecord>
    suspend fun getOxygenSaturationData(startTime: Instant, endTime: Instant): List<OxygenSaturationRecord>
    
    suspend fun getAverageHeartRate(startTime: Instant, endTime: Instant): Long?
    suspend fun getMinMaxHeartRate(startTime: Instant, endTime: Instant): Pair<Long?, Long?>
    
    suspend fun getAverageOxygenSaturation(startTime: Instant, endTime: Instant): Double?
    suspend fun getMinMaxOxygenSaturation(startTime: Instant, endTime: Instant): Pair<Double?, Double?>

    suspend fun getYearlyHeartRateStats(): List<MonthlyStats>
    suspend fun getYearlyOxygenStats(): List<MonthlyStats>
}
