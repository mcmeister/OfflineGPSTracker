package com.example.offlinegpstracker

import kotlinx.coroutines.flow.Flow

class LocationRepository(private val locationDao: LocationDao) {

    fun getAllLocations(): Flow<List<Location>> = locationDao.getAllLocations()

    suspend fun insertLocation(location: Location) {
        locationDao.insertLocation(location)
    }

    fun getLocationById(locationId: Int): Flow<Location?> {
        return locationDao.getLocationById(locationId)
    }

    suspend fun updateLocation(location: Location) {
        locationDao.updateLocation(location)
    }

    suspend fun deleteLocation(locationId: Int) {
        locationDao.deleteLocation(locationId)
    }
}