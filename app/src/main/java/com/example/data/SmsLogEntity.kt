package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_logs")
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val sender: String,
    val message: String,
    val targetNumber: String,
    val status: String // "SUCCESS", "FAILED", "IGNORED"
)
