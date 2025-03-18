package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RouteTrackerScreen(
    viewModel: RouteTrackerViewModel,
    modifier: Modifier = Modifier
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val currentRouteId by viewModel.currentRouteId.collectAsState()
    val savedRoutes by viewModel.savedRoutes.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()
    val scope = rememberCoroutineScope()
    val originalZoom = remember { mutableFloatStateOf(1f) } // Store original snapshot zoom
    val zoomLevel = remember { mutableFloatStateOf(1f) } // Current zoom level
    val lastInteractionTime = remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Ensure zoom initializes correctly based on the selected route or recording
    LaunchedEffect(selectedRoute, currentRouteId) {
        if (selectedRoute != null) {
            originalZoom.floatValue = selectedRoute!!.zoom.toFloat() // Store original zoom
            zoomLevel.floatValue = selectedRoute!!.zoom.toFloat() // Start at normal zoom
        }
    }

    val debugInfo = remember { mutableStateOf("Waiting for GPS data...") }

    Scaffold { padding ->

        LaunchedEffect(routePoints, isRecording) {
            while (isRecording) {  // Auto-zoom only when recording
                delay(5000)  // Every 5 seconds

                val timeSinceLastInteraction = System.currentTimeMillis() - lastInteractionTime.longValue
                if (timeSinceLastInteraction >= 5000) {
                    zoomLevel.floatValue = (0.5f * originalZoom.floatValue).coerceIn(0.5f * originalZoom.floatValue, 20f) // Auto-zoom to 50% of original
                }
            }
        }

        Box(modifier = modifier.padding(padding).fillMaxSize()) {
            when {
                isRecording && currentRouteId != null -> {
                    val route by produceState<Route?>(initialValue = null) {
                        value = viewModel.routeRepository.getRoute(currentRouteId!!)
                    }
                    route?.let { r ->
                        val bitmap = BitmapFactory.decodeFile(r.snapshotPath)?.asImageBitmap()
                        if (bitmap != null) {
                            val distance = calculateDistance(routePoints)  // ✅ Use distance properly

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, _, zoom, _ ->
                                            zoomLevel.floatValue = (zoomLevel.floatValue * zoom).coerceIn(0.5f * originalZoom.floatValue, 20f)
                                            lastInteractionTime.longValue = System.currentTimeMillis()
                                        }
                                    }
                            ) {
                                // ✅ Draw the map snapshot first (background)
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Map Snapshot",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(scaleX = zoomLevel.floatValue, scaleY = zoomLevel.floatValue),
                                    contentScale = ContentScale.Fit
                                )

                                // ✅ Draw the red route line AFTER the snapshot (on top)
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val path = Path()
                                    routePoints.forEachIndexed { index, point ->
                                        val (x, y) = latLonToPixel(
                                            point.latitude, point.longitude,
                                            r.centerLat, r.centerLon, zoomLevel.floatValue.toInt(), r.width, r.height
                                        )
                                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                    }
                                    drawPath(path, color = Color.Red, style = Stroke(width = 5f))
                                }

                                // **DEBUG INFO BLOCK**
                                LaunchedEffect(routePoints) {
                                    val lastPoint = routePoints.lastOrNull()
                                    if (lastPoint != null) {
                                        val (x, y) = latLonToPixel(
                                            lastPoint.latitude, lastPoint.longitude,
                                            r.centerLat, r.centerLon, r.zoom, r.width, r.height
                                        )
                                        debugInfo.value = """
                                            Route Points: ${routePoints.size}
                                            Last Lat: ${"%.6f".format(lastPoint.latitude)}, Lon: ${"%.6f".format(lastPoint.longitude)}
                                            Mapped X: ${x.toInt()}, Y: ${y.toInt()}
                                            Distance: %.2f km
                                        """.trimIndent()
                                    } else {
                                        debugInfo.value = "No GPS points received."
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(16.dp)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(8.dp)
                                ) {
                                    Text(text = debugInfo.value, color = Color.White, fontSize = 12.sp)
                                }

                                if (routePoints.size <= 1) {
                                    LaunchedEffect(Unit) {
                                        scope.launch {
                                            delay(1000)
                                            Log.i("RouteTracker", "Please start walking in the desired direction")
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp), // Keeps proper spacing from the bottom
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Total Distance: %.2f km".format(distance / 1000),  // ✅ Moved above buttons
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(8.dp),
                                    color = Color.White,
                                    fontSize = 14.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp)) // ✅ Adds space between distance text and buttons

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        if (isPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
                                    }) {
                                        Text(if (isPaused) "Resume" else "Pause")
                                    }
                                    Button(
                                        onClick = { viewModel.stopRecording() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                    ) {
                                        Text("Stop")
                                    }
                                }
                            }
                        }
                    }
                }

                !isRecording && selectedRoute != null -> {
                    val route = selectedRoute!!
                    val bitmap = BitmapFactory.decodeFile(route.snapshotPath)?.asImageBitmap()
                    if (bitmap != null) {
                        val distance = calculateDistance(routePoints)  // ✅ Use distance for saved routes too

                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Saved Route Snapshot",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val path = Path()
                                routePoints.forEachIndexed { index, point ->
                                    val (x, y) = latLonToPixel(
                                        point.latitude, point.longitude,
                                        route.centerLat, route.centerLon, route.zoom, route.width, route.height
                                    )
                                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, color = Color.Red, style = Stroke(width = 5f))
                            }

                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp)
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "Start: ${formatTime(route.startTime)}",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "End: ${route.endTime?.let { formatTime(it) } ?: "N/A"}",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Total Distance: %.2f km".format(distance / 1000),  // ✅ Fixed this
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                else -> {
                    Button(
                        onClick = { viewModel.startRecording() },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("Record")
                    }
                }
            }

            // **Debug Log to check if isRecording updates correctly**
            LaunchedEffect(isRecording) {
                Log.d("RouteTracker", "isRecording state changed: $isRecording")
            }

            // **Only show saved routes when NOT recording**
            if (!isRecording) {
                LazyColumn(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .width(200.dp)
                        .heightIn(max = 300.dp)
                ) {
                    items(savedRoutes) { savedRoute ->
                        Text(
                            text = "Route ${savedRoute.id} (${formatTime(savedRoute.startTime)})",
                            color = if (savedRoute == selectedRoute) Color.Yellow else Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable {
                                    viewModel.selectRoute(savedRoute.id)
                                }

                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SimpleDateFormat")
fun formatTime(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(date)
}

fun calculateDistance(points: List<RoutePoint>): Float {
    var distance = 0f
    for (i in 0 until points.size - 1) {
        val start = android.location.Location("").apply {
            latitude = points[i].latitude
            longitude = points[i].longitude
        }
        val end = android.location.Location("").apply {
            latitude = points[i + 1].latitude
            longitude = points[i + 1].longitude
        }
        distance += start.distanceTo(end)
    }
    return distance
}

fun latLonToPixel(
    lat: Double, lon: Double,
    centerLat: Double, centerLon: Double,
    zoom: Int, width: Int, height: Int
): Pair<Float, Float> {
    val metersPerPixel = 156543.03392 * cos(centerLat * PI / 180) / 2.0.pow(zoom.toDouble())
    val latSpan = height * metersPerPixel / 111000.0
    val lonSpan = width * metersPerPixel / (111000.0 * cos(centerLat * PI / 180))

    val north = centerLat + latSpan / 2
    val south = centerLat - latSpan / 2
    val west = centerLon - lonSpan / 2
    val east = centerLon + lonSpan / 2

    val x = ((lon - west) / (east - west) * width).toFloat()
    val y = ((north - lat) / (north - south) * height).toFloat()

    return Pair(x, y)
}