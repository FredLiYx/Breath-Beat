package com.example.breathbeat.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class HealthManagerImpl(private val context: Context) : HealthManager {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    private val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class)
    )

    override suspend fun hasAllPermissions(): Boolean {
        val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
        return grantedPermissions.containsAll(permissions)
    }

    override fun getPermissionRequestIntent(): Set<String> {
        return permissions
    }

    override suspend fun getHeartRateData(startTime: Instant, endTime: Instant): List<HeartRateRecord> {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records
    }

    override suspend fun getOxygenSaturationData(startTime: Instant, endTime: Instant): List<OxygenSaturationRecord> {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records
    }

    override suspend fun getAverageHeartRate(startTime: Instant, endTime: Instant): Long? {
        val response = healthConnectClient.aggregate(
            AggregateRequest(
                metrics = setOf(HeartRateRecord.BPM_AVG),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response[HeartRateRecord.BPM_AVG]
    }

    override suspend fun getMinMaxHeartRate(startTime: Instant, endTime: Instant): Pair<Long?, Long?> {
        val response = healthConnectClient.aggregate(
            AggregateRequest(
                metrics = setOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return Pair(response[HeartRateRecord.BPM_MIN], response[HeartRateRecord.BPM_MAX])
    }

    override suspend fun getAverageOxygenSaturation(startTime: Instant, endTime: Instant): Double? {
        val records = getOxygenSaturationData(startTime, endTime)
        if (records.isEmpty()) return null
        
        return records.map { it.percentage.value }.average()
    }

    override suspend fun getMinMaxOxygenSaturation(startTime: Instant, endTime: Instant): Pair<Double?, Double?> {
        val records = getOxygenSaturationData(startTime, endTime)
        if (records.isEmpty()) return Pair(null, null)
        
        val percentages = records.map { it.percentage.value }
        return Pair(percentages.minOrNull(), percentages.maxOrNull())
    }

    override suspend fun getYearlyHeartRateStats(): List<MonthlyStats> {
        val now = Instant.now()
        val stats = mutableListOf<MonthlyStats>()
        val zoneId = ZoneId.systemDefault()
        
        for (i in 11 downTo 0) {
            val monthStart = now.atZone(zoneId).minusMonths(i.toLong()).withDayOfMonth(1).toInstant()
            val monthEnd = now.atZone(zoneId).minusMonths(i.toLong()).withDayOfMonth(now.atZone(zoneId).minusMonths(i.toLong()).toLocalDate().lengthOfMonth()).toInstant()
            
            val avg = getAverageHeartRate(monthStart, monthEnd)
            val (min, max) = getMinMaxHeartRate(monthStart, monthEnd)
            
            val monthName = monthStart.atZone(zoneId).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            stats.add(MonthlyStats(monthName, avg?.toDouble(), min?.toDouble(), max?.toDouble()))
        }
        return stats
    }

    override suspend fun getYearlyOxygenStats(): List<MonthlyStats> {
        val now = Instant.now()
        val stats = mutableListOf<MonthlyStats>()
        val zoneId = ZoneId.systemDefault()
        
        for (i in 11 downTo 0) {
            val monthStart = now.atZone(zoneId).minusMonths(i.toLong()).withDayOfMonth(1).toInstant()
            val monthEnd = now.atZone(zoneId).minusMonths(i.toLong()).withDayOfMonth(now.atZone(zoneId).minusMonths(i.toLong()).toLocalDate().lengthOfMonth()).toInstant()
            
            val avg = getAverageOxygenSaturation(monthStart, monthEnd)
            val (min, max) = getMinMaxOxygenSaturation(monthStart, monthEnd)
            
            val monthName = monthStart.atZone(zoneId).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            stats.add(MonthlyStats(monthName, avg, min, max))
        }
        return stats
    }
}
