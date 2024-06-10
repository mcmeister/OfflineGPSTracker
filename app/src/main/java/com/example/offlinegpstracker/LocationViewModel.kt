package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location as AndroidLocation
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.lifecycle.*
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LocationViewModel(application: Application, private val repository: LocationRepository) : AndroidViewModel(application) {

    // Change locations to StateFlow
    val locations: StateFlow<List<Location>> = repository.getAllLocations().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _latitude = MutableLiveData<String>()
    val latitude: LiveData<String> = _latitude

    private val _longitude = MutableLiveData<String>()
    val longitude: LiveData<String> = _longitude

    private val _altitude = MutableLiveData<String>()
    val altitude: LiveData<String> = _altitude

    private val _locationFlow = MutableStateFlow<AndroidLocation?>(null)
    val locationFlow: StateFlow<AndroidLocation?> = _locationFlow

    private val _selectedLocationId = MutableLiveData<Int?>()
    val selectedLocation: LiveData<Location?> = _selectedLocationId.switchMap { id ->
        id?.let { repository.getLocationById(it).asLiveData() } ?: MutableLiveData(null)
    }

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

    fun getLocationById(locationId: Int) = repository.getLocationById(locationId)

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

    fun selectLocation(locationId: Int) {
        _selectedLocationId.value = locationId
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(getApplication(), android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(getApplication(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request permissions if not granted
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L).apply {
            setMinUpdateIntervalMillis(5000L)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    _latitude.value = location.latitude.toString()
                    _longitude.value = location.longitude.toString()
                    _altitude.value = location.altitude.toString()
                    _locationFlow.value = location
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
}

// Extension function to convert custom Location to AndroidLocation
fun Location.toAndroidLocation(): AndroidLocation {
    val androidLocation = AndroidLocation("")
    androidLocation.latitude = this.latitude
    androidLocation.longitude = this.longitude
    androidLocation.altitude = this.altitude
    return androidLocation
}

// Extension function to convert AndroidLocation to custom Location
fun AndroidLocation.toCustomLocation(): Location {
    return Location(
        name = "Location at ${System.currentTimeMillis()}", // Provide appropriate name if needed
        latitude = this.latitude,
        longitude = this.longitude,
        altitude = this.altitude
    )
}
