package com.example.offlinegpstracker

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
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
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

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

    private val _selectedRoute = MutableStateFlow<Route?>(null)
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
            _selectedRoute.value = null
            val currentLocation = locationViewModel.locationFlow.value
            if (currentLocation != null) {
                val route = Route(
                    startTime = System.currentTimeMillis(),
                    snapshotPath = "",
                    centerLat = currentLocation.latitude,
                    centerLon = currentLocation.longitude,
                    zoom = 14,
                    width = 1080,
                    height = 1920
                )
                val routeId = routeRepository.insertRoute(route).toInt()
                _currentRouteId.value = routeId
                _isRecording.value = true
                _isPaused.value = false
                Intent(application, RouteRecordingService::class.java).apply {
                    putExtra("routeId", routeId)
                }.also { application.startForegroundService(it) }
            } else {
                // No debug message here since UI handles it
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
                ?: throw IllegalStateException("Snapshot file not found at ${route.snapshotPath}")
            val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint().apply {
                color = Color.RED
                strokeWidth = 5f
                style = Paint.Style.STROKE
            }
            for (i in 0 until points.size - 1) {
                val (x1, y1) = latLonToPixel(points[i].latitude, points[i].longitude, route.centerLat, route.centerLon, route.zoom, route.width, route.height)
                val (x2, y2) = latLonToPixel(points[i + 1].latitude, points[i + 1].longitude, route.centerLat, route.centerLon, route.zoom, route.width, route.height)
                canvas.drawLine(x1, y1, x2, y2, paint)
            }
            val file = File(application.filesDir, "route_${route.id}_final.png")
            FileOutputStream(file).use { out ->
                mutableBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.path
        } catch (e: Exception) {
            null
        }
    }

    suspend fun generateMapSnapshot(
        centerLat: Double,
        centerLon: Double,
        bearing: Double,
        width: Int,
        height: Int,
        distanceKm: Double
    ): File? = withContext(Dispatchers.IO) {
        try {
            val earthRadius = 6371.0 // km
            val distance = distanceKm / 2 // Center is halfway up the 10km map
            val bearingRad = Math.toRadians(bearing)

            val latRad = Math.toRadians(centerLat)
            val lonRad = Math.toRadians(centerLon)

            val newLatRad = asin(
                sin(latRad) * cos(distance / earthRadius) +
                        cos(latRad) * sin(distance / earthRadius) * cos(bearingRad)
            )
            val newLonRad = lonRad + atan2(
                sin(bearingRad) * sin(distance / earthRadius) * cos(latRad),
                cos(distance / earthRadius) - sin(latRad) * sin(newLatRad)
            )

            val mapCenterLat = Math.toDegrees(newLatRad)
            val mapCenterLon = Math.toDegrees(newLonRad)

            val zoom = calculateZoomLevel(distanceKm, height)

            val token = "pk.eyJ1IjoibWNtZWlzdGVyIiwiYSI6ImNtOGF3d3YzdjBtcjUyaW9yNmFidndlbWsifQ.nlbq1LxHYM1jBBZUcXM0zw"
            val url = "https://api.mapbox.com/styles/v1/mapbox/streets-v11/static/" +
                    "$mapCenterLon,$mapCenterLat,$zoom,$bearing/${width}x$height?access_token=$token"

            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bitmap = BitmapFactory.decodeStream(response.body?.byteStream())
                    ?: throw IllegalStateException("Failed to decode map bitmap")
                val file = File(application.filesDir, "snapshot_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                _currentRouteId.value?.let { routeId ->
                    routeRepository.updateRouteSnapshot(
                        routeId,
                        file.path,
                        mapCenterLat,
                        mapCenterLon,
                        zoom
                    )
                } ?: throw IllegalStateException("No current route ID")

                file
            } else {
                throw IllegalStateException("Mapbox request failed with code: ${response.code}")
            }
        } catch (e: Exception) {
            null // Snapshot failure will be caught and displayed in UI
        }
    }

    private fun calculateZoomLevel(distanceKm: Double, heightPx: Int): Int {
        val meters = distanceKm * 1000
        val zoom = (ln(156543.03392 * heightPx / meters) / ln(2.0)).toInt()
        return zoom.coerceIn(0, 22) // Mapbox zoom levels range from 0 to 22
    }
}