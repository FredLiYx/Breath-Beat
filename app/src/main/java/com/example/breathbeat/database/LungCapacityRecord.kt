package com.example.breathbeat.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "lung_capacity_records")
data class LungCapacityRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val volumeML: Int,
    val timestamp: Long = System.currentTimeMillis()
)
