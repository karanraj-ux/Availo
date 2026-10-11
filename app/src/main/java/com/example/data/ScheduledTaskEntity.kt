package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "SMS", "Call", "WhatsApp"
    val target: String,
    val message: String?,
    val timeMillis: Long,
    val completed: Boolean = false,
    val isRecurring: Boolean = false,
    val recurringIntervalMillis: Long = 0L
)
