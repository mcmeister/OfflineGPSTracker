package com.example.offlinegpstracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location")
data class Location(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val status: String = "active"
) {
    val deleted: Boolean
        get() = status == "deleted"
}
