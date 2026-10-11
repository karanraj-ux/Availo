package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subscriptions",
    indices = [Index(value = ["name"], unique = true)]
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val amount: String,
    val dateDetected: Long,
    val source: String, // "SMS" or "Email"
    val isNewsletter: Boolean = false,
    val originalMessage: String = ""
)
