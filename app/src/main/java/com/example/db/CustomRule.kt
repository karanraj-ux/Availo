package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "custom_rules")
data class CustomRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val trigger: String,
    val action: String
)
