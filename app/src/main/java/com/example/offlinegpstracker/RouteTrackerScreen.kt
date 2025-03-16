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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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

    Scaffold { padding ->
        Box(modifier = modifier.padding(padding).fillMaxSize()) {
            // Main content based on state
            when {
                isRecording && currentRouteId != null -> {
                    // Recording mode: real-time updates
                    val route by produceState<Route?>(initialValue = null) {
                        value = viewModel.routeRepository.getRoute(currentRouteId!!)
                    }
                    route?.let { r ->
                        val bitmap = BitmapFactory.decodeFile(r.snapshotPath)?.asImageBitmap()
                        if (bitmap != null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Map Snapshot",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val path = Path()
                                    routePoints.forEachIndexed { index, point ->
                                        val (x, y) = latLonToPixel(
                                            point.latitude, point.longitude,
                                            r.centerLat, r.centerLon, r.zoom, r.width, r.height
                                        )
                                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                    }
                                    drawPath(path, color = Color.Red, style = Stroke(width = 5f))
                                }
                                val distance = calculateDistance(routePoints)
                                Text(
                                    text = "Distance: %.2f km".format(distance / 1000),
                                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (routePoints.size <= 1) {
                                    LaunchedEffect(Unit) {
                                        scope.launch {
                                            delay(1000)
                                            Log.i("RouteTracker", "Please start walking in the desired direction")
                                        }
                                    }
                                }
                            }
                            Row(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                                Button(onClick = {
                                    if (isPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
                                }) {
                                    Text(if (isPaused) "Resume" else "Pause")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { viewModel.stopRecording() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                                    Text("Stop")
                                }
                            }
                        }
                    }
                }
                !isRecording && selectedRoute != null -> {
                    // Selected saved route mode
                    val route = selectedRoute!!
                    val bitmap = BitmapFactory.decodeFile(route.snapshotPath)?.asImageBitmap()
                    if (bitmap != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Saved Route Snapshot",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
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
                                    text = "Distance: %.2f km".format(calculateDistance(routePoints) / 1000),
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
                else -> {
                    // Idle mode: just the Record button
                    Button(
                        onClick = { viewModel.startRecording() },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("Record")
                    }
                }
            }

            // Saved Routes List (always visible)
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
                                if (!isRecording) viewModel.selectRoute(savedRoute.id)
                            }
                    )
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
    lat: Double,
    lon: Double,
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    width: Int,
    height: Int
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