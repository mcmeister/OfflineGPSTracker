package com.example.offlinegpstracker

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MyApplication : Application() {
    lateinit var locationRepository: LocationRepository

    val applicationScope = CoroutineScope(SupervisorJob())
    private val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { LocationRepository(database.locationDao()) }
    val routeRepository by lazy { RouteRepository(database.routeDao(), database.routePointDao()) }

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(this)
        locationRepository = LocationRepository(db.locationDao())
    }
}
