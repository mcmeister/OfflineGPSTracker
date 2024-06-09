package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.app.Application
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocationViewModel(application: Application, private val repository: LocationRepository) : AndroidViewModel(application) {

    val locations = repository.getAllLocations().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _latitude = MutableLiveData<String>()
    val latitude: LiveData<String> = _latitude

    private val _longitude = MutableLiveData<String>()
    val longitude: LiveData<String> = _longitude

    private val _altitude = MutableLiveData<String>()
    val altitude: LiveData<String> = _altitude

    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)
    private var locationCallback: LocationCallback? = null

    init {
        startLocationUpdates()
    }

    fun saveLocation(location: Location) {
        viewModelScope.launch {
            repository.insertLocation(location)
        }
    }

    fun getLocationById(locationId: Int): kotlinx.coroutines.flow.Flow<Location?> {
        return repository.getLocationById(locationId)
    }

    fun updateLocation(location: Location) {
        viewModelScope.launch {
            repository.updateLocation(location)
        }
    }

    fun deleteLocation(locationId: Int) {
        viewModelScope.launch {
            repository.deleteLocation(locationId)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).apply {
            setMinUpdateIntervalMillis(5000L)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    _latitude.value = location.latitude.toString()
                    _longitude.value = location.longitude.toString()
                    _altitude.value = location.altitude.toString()
                }
            }
        }

        locationCallback?.let {
            fusedLocationClient.requestLocationUpdates(locationRequest, it, Looper.getMainLooper())
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }

    fun updateCurrentLocation(latitude: String, longitude: String, altitude: String) {
        _latitude.value = latitude
        _longitude.value = longitude
        _altitude.value = altitude
    }
}
