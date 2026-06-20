package com.example.applock.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locked_apps")
data class LockedApp(
    @PrimaryKey val packageName: String,
    val reLockOption: String = "Immediately",
    val profile: String = "Default" // Added for profiles
)
