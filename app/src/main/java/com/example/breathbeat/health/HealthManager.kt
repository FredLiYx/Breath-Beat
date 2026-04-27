package com.example.breathbeat.health

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import java.time.Instant

interface HealthManager {
    suspend fun hasAllPermissions(): Boolean
    fun getPermissionRequestIntent(): Set<String>
    
    suspend fun getHeartRateData(startTime: Instant, endTime: Instant): List<HeartRateRecord>
    suspend fun getOxygenSaturationData(startTime: Instant, endTime: Instant): List<OxygenSaturationRecord>
    
    suspend fun getAverageHeartRate(startTime: Instant, endTime: Instant): Long?
    suspend fun getAverageOxygenSaturation(startTime: Instant, endTime: Instant): Double?
}
