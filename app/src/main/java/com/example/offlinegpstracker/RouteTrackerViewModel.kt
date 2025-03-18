package com.example.offlinegpstracker

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class RouteTrackerViewModel(
    val routeRepository: RouteRepository,
    private val application: Application,
    private val locationViewModel: LocationViewModel
) : ViewModel() {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _currentRouteId = MutableStateFlow<Int?>(null)
    val currentRouteId: StateFlow<Int?> = _currentRouteId

    private val _routePoints = MutableStateFlow<List<RoutePoint>>(emptyList())
    val routePoints: StateFlow<List<RoutePoint>> = _routePoints

    private val _savedRoutes = MutableStateFlow<List<Route>>(emptyList())
    val savedRoutes: StateFlow<List<Route>> = _savedRoutes

    private val _selectedRoute = MutableStateFlow<Route?>(null) // New state for selected route
    val selectedRoute: StateFlow<Route?> = _selectedRoute

    init {
        viewModelScope.launch {
            _currentRouteId.collect { routeId ->
                if (routeId != null) {
                    routeRepository.getPointsForRoute(routeId).collect { points ->
                        _routePoints.value = points
                    }
                } else {
                    _routePoints.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            routeRepository.getAllRoutes().collect { routes ->
                _savedRoutes.value = routes
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startRecording() {
        viewModelScope.launch {
            _selectedRoute.value = null // Clear selected route when starting new recording
            val currentLocation = locationViewModel.locationFlow.value
            if (currentLocation != null) {
                val centerLat = currentLocation.latitude
                val centerLon = currentLocation.longitude
                val zoom = 14
                val width = 640
                val height = 640
                val snapshotFile = downloadMapSnapshot(centerLat, centerLon, zoom, width, height)
                if (snapshotFile != null) {
                    val route = Route(
                        startTime = System.currentTimeMillis(),
                        snapshotPath = snapshotFile.path,
                        centerLat = centerLat,
                        centerLon = centerLon,
                        zoom = zoom,
                        width = width,
                        height = height
                    )
                    val routeId = routeRepository.insertRoute(route).toInt()
                    _currentRouteId.value = routeId
                    _isRecording.value = true
                    _isPaused.value = false
                    Intent(application, RouteRecordingService::class.java).apply {
                        putExtra("routeId", routeId)
                    }.also { application.startForegroundService(it) }
                } else {
                    Log.e("RouteTracker", "Failed to download map snapshot")
                }
            } else {
                Log.e("RouteTracker", "Location not available")
            }
        }
    }

    fun pauseRecording() {
        Intent(application, RouteRecordingService::class.java).apply {
            action = "PAUSE"
        }.also { application.startService(it) }
        _isPaused.value = true
    }

    fun resumeRecording() {
        Intent(application, RouteRecordingService::class.java).apply {
            action = "RESUME"
        }.also { application.startService(it) }
        _isPaused.value = false
    }

    fun stopRecording() {
        Intent(application, RouteRecordingService::class.java).also { application.stopService(it) }
        viewModelScope.launch {
            _currentRouteId.value?.let { routeId ->
                val route = routeRepository.getRoute(routeId)
                val points = routePoints.value
                if (route != null && points.isNotEmpty()) {
                    val finalSnapshotPath = saveRouteWithRedLine(route, points)
                    routeRepository.updateRouteEndTimeAndSnapshot(
                        routeId,
                        System.currentTimeMillis(),
                        finalSnapshotPath
                    )
                } else {
                    routeRepository.updateRouteEndTime(routeId, System.currentTimeMillis())
                }
            }
            _isRecording.value = false
            _currentRouteId.value = null
        }
    }

    fun selectRoute(routeId: Int) {
        viewModelScope.launch {
            val route = routeRepository.getRoute(routeId)
            _selectedRoute.value = route
            route?.let {
                routeRepository.getPointsForRoute(routeId).collect { points ->
                    _routePoints.value = points
                }
            }
        }
    }

    private suspend fun saveRouteWithRedLine(route: Route, points: List<RoutePoint>): String? = withContext(Dispatchers.IO) {
        try {
            val baseBitmap = BitmapFactory.decodeFile(route.snapshotPath)
            val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint().apply {
                color = Color.RED
                strokeWidth = 5f
                style = Paint.Style.STROKE
            }
            for (i in 0 until points.size - 1) {
                val (x1, y1) = latLonToPixel(points[i].latitude, points[i].longitude, route.centerLat, route.centerLon,
                    route.zoom.toFloat(), route.width, route.height)
                val (x2, y2) = latLonToPixel(points[i + 1].latitude, points[i + 1].longitude, route.centerLat, route.centerLon,
                    route.zoom.toFloat(), route.width, route.height)
                canvas.drawLine(x1, y1, x2, y2, paint)
            }
            val file = File(application.filesDir, "route_${route.id}_final.png")
            FileOutputStream(file).use { out ->
                mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.path
        } catch (e: Exception) {
            Log.e("RouteTracker", "Error saving route snapshot", e)
            null
        }
    }

    private suspend fun downloadMapSnapshot(
        centerLat: Double,
        centerLon: Double,
        zoom: Int,
        width: Int,
        height: Int
    ): File? = withContext(Dispatchers.IO) {
        val token = "pk.eyJ1IjoibWNtZWlzdGVyIiwiYSI6ImNtOGF3d3YzdjBtcjUyaW9yNmFidndlbWsifQ.nlbq1LxHYM1jBBZUcXM0zw"
        val url = "https://api.mapbox.com/styles/v1/mapbox/streets-v11/static/$centerLon,$centerLat,$zoom/${width}x$height?access_token=$token"
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val options = BitmapFactory.Options().apply { inScaled = false }
                val bitmap = BitmapFactory.decodeStream(response.body?.byteStream(), null, options)
                val file = File(application.filesDir, "snapshot_${System.currentTimeMillis()}.png")
                if (bitmap != null) {
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
                file
            } else null
        } catch (e: Exception) {
            Log.e("RouteTracker", "Error downloading snapshot", e)
            null
        }
    }
}