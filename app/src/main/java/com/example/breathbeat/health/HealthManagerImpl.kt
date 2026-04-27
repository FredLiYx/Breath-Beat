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

    override suspend fun getAverageOxygenSaturation(startTime: Instant, endTime: Instant): Double? {
        // OxygenSaturationRecord does not support statistical aggregation directly in current Health Connect version.
        // We calculate it manually from raw records.
        val records = getOxygenSaturationData(startTime, endTime)
        if (records.isEmpty()) return null
        
        return records.map { it.percentage.value }.average()
    }
}
