package com.example.offlinegpstracker

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class RouteTrackerViewModelFactory(
    private val routeRepository: RouteRepository,
    private val application: Application,
    private val locationViewModel: LocationViewModel
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RouteTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RouteTrackerViewModel(routeRepository, application, locationViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
