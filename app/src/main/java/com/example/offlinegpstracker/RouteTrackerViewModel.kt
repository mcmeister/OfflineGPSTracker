package com.example.offlinegpstracker

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

@RequiresApi(Build.VERSION_CODES.M)
class RouteTrackerViewModel(
    application: Application,                    // ① first parameter
    val routeRepository: RouteRepository,
    private val locationViewModel: LocationViewModel
) : AndroidViewModel(application) {
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

    private val _debugInfoText = mutableStateOf("")
    val debugInfoText: State<String> get() = _debugInfoText

    @RequiresApi(Build.VERSION_CODES.M)
    private val connectivityManager =
        application.getSystemService(android.net.ConnectivityManager::class.java)

    private val activeRouteId = MutableStateFlow<Int?>(null)

    private val app = getApplication<Application>()

    init {
        /* ---------- live collectors ---------- */
        viewModelScope.launch {
            activeRouteId
                .collectLatest { id ->
                    if (id == null) {
                        _routePoints.value = emptyList()
                    } else {
                        routeRepository.getPointsForRoute(id)
                            .collect { pts -> _routePoints.value = pts }
                    }
                }
        }

        viewModelScope.launch {
            routeRepository.getAllRoutes()
                .collect { _savedRoutes.value = it }
        }

        /* ---------- connectivity watcher ---------- */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val request = android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(
                request,
                object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        // Internet became available → fetch any missing tiles
                        viewModelScope.launch { tryDownloadTilesForAllRoutes() }
                    }
                }
            )
        }

        // warm‑up at start‑up if we already have a connection
        if (isInternetAvailable()) {
            viewModelScope.launch { tryDownloadTilesForAllRoutes() }
        }

        /* ----------  🔄  process-restart recovery  ---------- */
        viewModelScope.launch {
            routeRepository.getLastUnfinishedRoute()?.let { unfinished ->
                _currentRouteId.value = unfinished.id
                activeRouteId.value = unfinished.id
                _isRecording.value = true
                _isPaused.value = false

                /* ensure the service is running (idempotent if it’s already alive) */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    app.startForegroundService(
                        Intent(app, RouteRecordingService::class.java)
                            .putExtra("routeId", unfinished.id)
                    )
                }
            }
        }
    }

    private fun resetDebugInfo() {
        Log.d("RouteTracker","resetDebugInfo() called – clearing old stats")
        _debugInfoText.value = ""
    }

    fun updateDebugInfo(info: String) {
        _debugInfoText.value = info
    }

    private suspend fun downloadTiles(
        centerLat : Double,
        centerLon : Double,
        zoom      : Int,
        tileRadius: Int
    ) = withContext(Dispatchers.IO) {

        val token     = "pk.eyJ1IjoibWNtZWlzdGVyIiwiYSI6ImNtOGF3d3YzdjBtcjUyaW9yNmFidndlbWsifQ.nlbq1LxHYM1jBBZUcXM0zw"
        val tileSize  = 512
        val z         = 1 shl zoom
        val sinLat    = sin(centerLat * PI/180).coerceIn(-.9999,.9999)
        val cx        = ((centerLon+180)/360*z).toInt()
        val cy        = ((0.5 - ln((1+sinLat)/(1-sinLat))/(4*PI))*z).toInt()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout   (15, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val cacheDir = File(app.filesDir, "tiles/$zoom")
        var downloaded = 0
        var skipped    = 0

        for (dx in -tileRadius..tileRadius) for (dy in -tileRadius..tileRadius) {
            val x = (cx+dx).coerceIn(0, z-1)
            val y = (cy+dy).coerceIn(0, z-1)
            val tileFile = File(cacheDir, "$x-$y.png")
            if (tileFile.exists()) { skipped++; continue }

            val url = "https://api.mapbox.com/styles/v1/" +
                    "mapbox/streets-v11/tiles/$tileSize/$zoom/$x/$y@2x?access_token=$token"

            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (resp.isSuccessful) {
                        tileFile.parentFile?.mkdirs()
                        resp.body?.byteStream()?.use { input ->
                            FileOutputStream(tileFile).use { out -> input.copyTo(out) }
                        }
                        downloaded++
                    } else {
                        Log.e("RouteTracker","[$zoom/$x/$y] HTTP ${resp.code}")
                    }
                }
            }.onFailure { e ->
                Log.e("RouteTracker","Tile download failed $zoom/$x/$y (${e.javaClass.simpleName})")
            }
        }

        Log.i("RouteTracker",
            "Tiles for z=$zoom  downloaded=$downloaded  skipped(existed)=$skipped")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startRecording() = viewModelScope.launch {
        resetDebugInfo()
        _selectedRoute.value = null

        // Clear old points NOW – the collector will refill when activeRouteId changes
        _routePoints.value   = emptyList()

        val loc = locationViewModel.locationFlow.value ?: return@launch
        val zoom       = 11
        val tileRadius = 2

        if (isInternetAvailable())
            downloadTiles(loc.latitude, loc.longitude, zoom, tileRadius)

        val id = routeRepository.insertRoute(
            Route(
                startTime    = System.currentTimeMillis(),
                snapshotPath = null,          // <-  IMPORTANT: no bogus path
                centerLat    = loc.latitude,
                centerLon    = loc.longitude,
                zoom         = zoom,
                width        = 640,
                height       = 640
            )
        ).toInt()

        _currentRouteId.value = id
        activeRouteId.value   = id
        _isRecording.value    = true
        _isPaused.value       = false

        app.startForegroundService(
            Intent(app, RouteRecordingService::class.java).putExtra("routeId", id)
        )
    }

    fun pauseRecording() {
        Intent(app, RouteRecordingService::class.java).apply {
            action = "PAUSE"
        }.also { app.startService(it) }
        _isPaused.value = true
    }

    fun resumeRecording() {
        Intent(app, RouteRecordingService::class.java).apply {
            action = "RESUME"
        }.also { app.startService(it) }

        _isPaused.value = false

        // while we’re at it, fetch the tiles that might have appeared
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            _currentRouteId.value?.let { id ->
                viewModelScope.launch {
                    routeRepository.getRoute(id)?.let { tryDownloadTilesForRoute(it) }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun continueRecordingFromSelectedRoute() {
        viewModelScope.launch {
            val route = _selectedRoute.value
            if (route != null) {
                _currentRouteId.value = route.id
                activeRouteId.value   = route.id
                _isRecording.value = true
                _isPaused.value = false
                Intent(app, RouteRecordingService::class.java).apply {
                    putExtra("routeId", route.id)
                }.also { app.startForegroundService(it) }
            }
        }
    }

    fun updateRouteName(routeId: Int, name: String) {
        viewModelScope.launch {
            routeRepository.routeDao.updateRouteName(routeId, name)
            // Force refresh
            val updated = routeRepository.getRoute(routeId)
            _selectedRoute.value = updated
        }
    }

    fun stopRecording() {
        // 1. halt foreground-service
        Intent(app, RouteRecordingService::class.java)
            .also { app.stopService(it) }

        viewModelScope.launch {
            _currentRouteId.value?.let { id ->

                val pts   = routePoints.value
                var route = routeRepository.getRoute(id)   // current copy – may not have endTime yet

                /* ── 1️⃣  bookkeeping ─────────────────────────── */
                if (route != null && pts.size >= 2) {
                    val start    = pts.first().timestamp
                    val end      = pts.last().timestamp
                    val hours    = (end - start) / 3_600_000.0
                    val km       = calculateDistance(pts) / 1000.0
                    val avgSpeed = if (hours > 0) km / hours else 0.0

                    routeRepository.routeDao.updateRouteWithSpeed(
                        routeId      = id,
                        endTime      = end,
                        snapshotPath = route.snapshotPath,   // keep whatever you stored earlier
                        averageSpeed = avgSpeed
                    )
                } else {
                    // fewer than 2 points → just stamp endTime
                    routeRepository.updateRouteEndTime(id, System.currentTimeMillis())
                }

                /* ── 2️⃣  refresh + tile download ───────────────── */
                route = routeRepository.getRoute(id)        // fetch fresh copy (has endTime/speed now)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    route?.let { tryDownloadTilesForRoute(it) }
                }

                /* ── 3️⃣  switch UI into “saved-route preview” ─── */
                if (route != null) {
                    _selectedRoute.value = route        // saved-route branch will render this
                    activeRouteId.value  = id           // keeps points flow alive
                }
            }

            /* ── 4️⃣  recording state flags ───────────────────── */
            _isRecording.value  = false
            _isPaused.value     = false
            _currentRouteId.value = null               // no active recording anymore
        }
    }

    fun selectRoute(routeId: Int) {
        viewModelScope.launch {
            val route = routeRepository.getRoute(routeId)
            _selectedRoute.value = route

            // download tiles as before
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                route?.let { tryDownloadTilesForRoute(it) }
            }

            activeRouteId.value = route?.id          // ← 🔑 this drives the points flow
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun tryDownloadTilesForRoute(route: Route, tileRadius: Int = 2) {
        if (!isInternetAvailable()) return
        viewModelScope.launch(Dispatchers.IO) {
            downloadTiles(route.centerLat, route.centerLon, route.zoom, tileRadius)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun tryDownloadTilesForAllRoutes(tileRadius: Int = 2) {
        if (!isInternetAvailable()) return
        savedRoutes.value.forEach { tryDownloadTilesForRoute(it, tileRadius) }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun isInternetAvailable(): Boolean {
        val connectivityManager = app.getSystemService(android.net.ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

}