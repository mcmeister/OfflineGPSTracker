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
import androidx.compose.foundation.gestures.detectTapGestures
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

    LaunchedEffect(selectedRoute) {
        if (selectedRoute != null) {
            zoomLevel.floatValue = 1.0f // ✅ Reset zoom to 100% (default zoom level)
        }
    }

    val debugInfo = remember { mutableStateOf("Waiting for GPS data...") }

    Scaffold { padding ->

        LaunchedEffect(routePoints, isRecording) {
            while (isRecording) {  // Auto-zoom only when recording
                delay(5000)  // Every 5 seconds

                val timeSinceLastInteraction = System.currentTimeMillis() - lastInteractionTime.longValue
                val targetZoom = 1.5f * originalZoom.floatValue  // ✅ Set 150% zoom as target

                if (timeSinceLastInteraction >= 5000 && zoomLevel.floatValue != targetZoom) {
                    zoomLevel.floatValue = targetZoom // ✅ Zoom in **only once** to 150% if needed
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
                                        detectTapGestures(
                                            onDoubleTap = { // ✅ Double-tap resets zoom
                                                zoomLevel.floatValue = 1.0f // ✅ Reset to default zoom
                                            }
                                        )
                                    }
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
                                    if (routePoints.isNotEmpty()) {
                                        val minLat = routePoints.minOf { it.latitude }
                                        val maxLat = routePoints.maxOf { it.latitude }
                                        val minLon = routePoints.minOf { it.longitude }
                                        val maxLon = routePoints.maxOf { it.longitude }
                                        val dynamicCenterLat = (minLat + maxLat) / 2
                                        val dynamicCenterLon = (minLon + maxLon) / 2

                                        // **1️⃣ Get pixel position of the dynamic center**
                                        val (dynamicCenterPx, dynamicCenterPy) = latLonToPixel(
                                            dynamicCenterLat, dynamicCenterLon,
                                            r.centerLat, r.centerLon,
                                            zoomLevel.floatValue, r.width, r.height
                                        )

                                        // **2️⃣ Compute the offset needed to shift the route to center**
                                        val offsetX = (size.width / 2f) - dynamicCenterPx
                                        val offsetY = (size.height / 2f) - dynamicCenterPy

                                        // **3️⃣ Build and draw the path using corrected offsets**
                                        val path = Path()
                                        routePoints.forEachIndexed { index, point ->
                                            val (origX, origY) = latLonToPixel(
                                                point.latitude, point.longitude,
                                                r.centerLat, r.centerLon,
                                                zoomLevel.floatValue, r.width, r.height
                                            )

                                            val adjustedX = origX + offsetX
                                            val adjustedY = origY + offsetY

                                            if (index == 0) {
                                                path.moveTo(adjustedX, adjustedY)
                                            } else {
                                                path.lineTo(adjustedX, adjustedY)
                                            }
                                        }

                                        // **Draw the corrected red-line route**
                                        drawPath(path, color = Color.Red, style = Stroke(width = 5f))
                                    }
                                }

                                // **DEBUG INFO BLOCK**
                                LaunchedEffect(routePoints) {
                                    val lastPoint = routePoints.lastOrNull()
                                    val distanceKm = calculateDistance(routePoints) / 1000.0

                                    if (lastPoint != null) {
                                        val (x, y) = latLonToPixel(
                                            lastPoint.latitude, lastPoint.longitude,
                                            r.centerLat, r.centerLon, zoomLevel.floatValue, r.width, r.height
                                        )

                                        // Debug information for real-time visualization
                                        val minLat = routePoints.minOfOrNull { it.latitude } ?: 0.0
                                        val maxLat = routePoints.maxOfOrNull { it.latitude } ?: 0.0
                                        val minLon = routePoints.minOfOrNull { it.longitude } ?: 0.0
                                        val maxLon = routePoints.maxOfOrNull { it.longitude } ?: 0.0

                                        val dynamicCenterLat = (minLat + maxLat) / 2
                                        val dynamicCenterLon = (minLon + maxLon) / 2

                                        val (dynamicCenterPx, dynamicCenterPy) = latLonToPixel(
                                            dynamicCenterLat, dynamicCenterLon,
                                            r.centerLat, r.centerLon, zoomLevel.floatValue, r.width, r.height
                                        )

                                        val offsetX = (r.width / 2f) - dynamicCenterPx
                                        val offsetY = (r.height / 2f) - dynamicCenterPy

                                        debugInfo.value = """
                                        Route Points: ${routePoints.size}
                                        Last Point: 
                                          Lat: ${"%.6f".format(lastPoint.latitude)}, Lon: ${"%.6f".format(lastPoint.longitude)}
                                          Mapped X: ${x.toInt()}, Y: ${y.toInt()}
                                        -----
                                        Bounding Box:
                                          Min Lat: ${"%.6f".format(minLat)}, Max Lat: ${"%.6f".format(maxLat)}
                                          Min Lon: ${"%.6f".format(minLon)}, Max Lon: ${"%.6f".format(maxLon)}
                                        -----
                                        Center Calculation:
                                          Dynamic Center Lat: ${"%.6f".format(dynamicCenterLat)}
                                          Dynamic Center Lon: ${"%.6f".format(dynamicCenterLon)}
                                          Mapped Center X: ${dynamicCenterPx.toInt()}, Y: ${dynamicCenterPy.toInt()}
                                        -----
                                        Offsets:
                                          OffsetX: ${offsetX.toInt()}, OffsetY: ${offsetY.toInt()}
                                        -----
                                        Zoom Level: ${"%.2f".format(zoomLevel.floatValue)}
                                        Distance: ${"%.2f".format(distanceKm)} km
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

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = { // ✅ Double-tap resets zoom
                                            zoomLevel.floatValue = 1.0f // ✅ Reset to default zoom
                                        }
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        zoomLevel.floatValue = (zoomLevel.floatValue * zoom).coerceIn(0.5f * originalZoom.floatValue, 20f)
                                        lastInteractionTime.longValue = System.currentTimeMillis()
                                    }
                                }
                        ) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Saved Route Snapshot",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(scaleX = zoomLevel.floatValue, scaleY = zoomLevel.floatValue), // ✅ Apply zoom
                                contentScale = ContentScale.Fit
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val path = Path()
                                routePoints.forEachIndexed { index, point ->
                                    val (x, y) = latLonToPixel(
                                        point.latitude, point.longitude,
                                        route.centerLat, route.centerLon,
                                        route.zoom.toFloat(), route.width, route.height
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
    zoom: Float, width: Int, height: Int
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