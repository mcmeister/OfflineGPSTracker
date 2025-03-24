package com.example.offlinegpstracker

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
                val snapshotFile = downloadMapSnapshot(centerLat, centerLon, zoom)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val routeId = _currentRouteId.value
            viewModelScope.launch {
                routeId?.let { id ->
                    val route = routeRepository.getRoute(id)
                    route?.let { routeToUpdate ->
                        tryUpdateSnapshotForRoute(routeToUpdate)
                    }
                }
            }
        }
    }

    fun stopRecording() {
        Intent(application, RouteRecordingService::class.java).also { application.stopService(it) }

        viewModelScope.launch {
            val routeId = _currentRouteId.value

            routeId?.let { id ->
                val route = routeRepository.getRoute(id)
                val points = routePoints.value

                if (route != null && points.size >= 2) {
                    val start = points.first().timestamp
                    val end = points.last().timestamp
                    val durationHours = (end - start) / (1000.0 * 60 * 60)
                    val distanceKm = calculateDistance(points) / 1000.0
                    val avgSpeed = if (durationHours > 0) distanceKm / durationHours else 0.0

                    routeRepository.routeDao.updateRouteWithSpeed(
                        routeId = id,
                        endTime = end,
                        snapshotPath = route.snapshotPath,
                        averageSpeed = avgSpeed
                    )
                } else {
                    routeRepository.updateRouteEndTime(id, System.currentTimeMillis())
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val latestRoute = routeRepository.getRoute(id)
                    latestRoute?.let { routeToUpdate ->
                        tryUpdateSnapshotForRoute(routeToUpdate)
                    }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && route != null) {
                tryUpdateSnapshotForRoute(route)
            }
            route?.let {
                routeRepository.getPointsForRoute(routeId).collect { points ->
                    _routePoints.value = points
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun tryUpdateSnapshotForRoute(route: Route) {
        viewModelScope.launch {
            if (isInternetAvailable() && route.snapshotPath!!.contains("default_map_placeholder")) {
                val newSnapshot = downloadMapSnapshot(route.centerLat, route.centerLon, route.zoom)
                routeRepository.updateRouteSnapshot(route.id, newSnapshot.path)
                if (route.id == currentRouteId.value) {
                    _selectedRoute.value = route.copy(snapshotPath = newSnapshot.path)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun tryUpdateSnapshotsForAllRoutes() {
        viewModelScope.launch {
            if (!isInternetAvailable()) return@launch
            savedRoutes.value.forEach { route ->
                if (route.snapshotPath!!.contains("default_map_placeholder")) {
                    tryUpdateSnapshotForRoute(route)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun isInternetAvailable(): Boolean {
        val connectivityManager = application.getSystemService(android.net.ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private suspend fun downloadMapSnapshot(
        centerLat: Double,
        centerLon: Double,
        zoom: Int
    ): File = withContext(Dispatchers.IO) {
        val snapshotFile = File(application.filesDir, "snapshot_${System.currentTimeMillis()}.png")
        val placeholderFile = File(application.filesDir, "default_map_placeholder.png")

        // Check network
        if (!isInternetAvailable()) {
            // Copy bundled placeholder drawable to file if not yet done
            if (!placeholderFile.exists()) {
                val inputStream = application.assets.open("default_map_placeholder.png")
                FileOutputStream(placeholderFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return@withContext placeholderFile
        }

        val token = "pk.eyJ1IjoibWNtZWlzdGVyIiwiYSI6ImNtOGF3d3YzdjBtcjUyaW9yNmFidndlbWsifQ.nlbq1LxHYM1jBBZUcXM0zw"
        val snapshotWidth = 640
        val snapshotHeight = 640
        val url =
            "https://api.mapbox.com/styles/v1/mapbox/streets-v11/static/$centerLon,$centerLat,$zoom/${snapshotWidth}x${snapshotHeight}@2x?access_token=$token"

        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val options = BitmapFactory.Options().apply { inScaled = false }
                val bitmap = BitmapFactory.decodeStream(response.body?.byteStream(), null, options)
                if (bitmap != null) {
                    FileOutputStream(snapshotFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    return@withContext snapshotFile
                }
            }
            Log.e("RouteTracker", "Download failed, using placeholder.")
            return@withContext placeholderFile
        } catch (e: Exception) {
            Log.e("RouteTracker", "Error downloading snapshot", e)
            return@withContext placeholderFile
        }
    }
}