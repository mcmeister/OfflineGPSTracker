package com.example.offlinegpstracker

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class RouteTrackerViewModelFactory(
    private val application: Application,
    private val routeRepository: RouteRepository,
    private val locationViewModel: LocationViewModel
) : ViewModelProvider.Factory {

    @RequiresApi(Build.VERSION_CODES.M)
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RouteTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RouteTrackerViewModel(
                routeRepository     = routeRepository,
                locationViewModel   = locationViewModel,
                application         = application          // pass last / in any order
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
