package com.example.offlinegpstracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM location WHERE status = 'active'")
    fun getAllLocations(): Flow<List<Location>>

    @Insert
    suspend fun insertLocation(location: Location) // Changed from String to Location

    @Query("SELECT * FROM location WHERE id = :locationId")
    fun getLocationById(locationId: Int): Flow<Location?>

    @Update
    suspend fun updateLocation(location: Location)

    @Query("UPDATE location SET status = 'deleted' WHERE id = :locationId")
    suspend fun deleteLocation(locationId: Int)

    @Query("UPDATE location SET photoPaths = :photoPath WHERE id = :locationId")
    suspend fun updateLocationPhoto(locationId: Int, photoPath: String?)
}
