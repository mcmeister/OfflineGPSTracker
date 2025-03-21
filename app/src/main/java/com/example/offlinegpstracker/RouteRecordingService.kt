package com.example.offlinegpstracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RouteRecordingService : Service() {
    private var routeId: Int = -1
    private var isPaused: Boolean = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var locationViewModel: LocationViewModel

    override fun onCreate() {
        super.onCreate()
        val app = application as MyApplication
        locationViewModel = LocationViewModel(app, app.locationRepository)
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationViewModel.startLocationUpdates()
        }
        scope.launch {
            locationViewModel.locationFlow.collect { location ->
                if (location != null && !isPaused && routeId != -1) {
                    val point = RoutePoint(
                        routeId = routeId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = System.currentTimeMillis()
                    )
                    (application as MyApplication).routeRepository.insertPoint(point)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                "PAUSE" -> isPaused = true
                "RESUME" -> isPaused = false
                else -> {
                    routeId = intent.getIntExtra("routeId", -1)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
                        startForeground(
                            1,
                            createNotification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION // Specify type
                        )
                    } else {
                        startForeground(1, createNotification()) // Legacy behavior
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "recording_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Route Recording", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Route Recording")
            .setContentText("Recording in progress")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}