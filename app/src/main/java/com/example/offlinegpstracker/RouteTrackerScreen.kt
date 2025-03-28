package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

@OptIn(FlowPreview::class)
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
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val showRecordingChoiceDialog = remember { mutableStateOf(false) }

    // Global zoom states
    val originalZoom = remember { mutableFloatStateOf(1f) }
    val zoomLevel = remember { mutableFloatStateOf(1f) }
    val autoZoomApplied = remember { mutableStateOf(false) }
    val lastInteractionTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val density = LocalDensity.current
    val baseWidthDp = 0.5.dp / 3
    val baseStrokeWidth = with(density) { baseWidthDp.toPx() }  // original base
    val zoom = zoomLevel.floatValue

    val thicknessMultiplier = when {
        zoom <= 1.5f -> 5f
        zoom in 1.5f..20f -> {
            // Linearly interpolate between 5 and 1
            val t = (zoom - 1.5f) / (20f - 1.5f)
            5f - (4f * t) // From 5 → 1
        }
        else -> 1f
    }

    val strokeWidthPx = (baseStrokeWidth * thicknessMultiplier).coerceIn(0.1f, 10f)

    // Ensure zoom initializes correctly based on the selected route or recording
    LaunchedEffect(selectedRoute, currentRouteId, isRecording) {
        if (!isRecording && selectedRoute != null) {
            originalZoom.floatValue = selectedRoute!!.zoom.toFloat()
            zoomLevel.floatValue = selectedRoute!!.zoom.toFloat()
        }
    }

    LaunchedEffect(selectedRoute, isRecording) {
        if (selectedRoute != null && !isRecording) {
            zoomLevel.floatValue = 1.0f
            originalZoom.floatValue = 1.0f
            autoZoomApplied.value = false
            lastInteractionTime.longValue = System.currentTimeMillis()
        }
    }

    LaunchedEffect(isRecording) {
        snapshotFlow { lastInteractionTime.longValue }
            .debounce(10000)
            .collect {
                if (isRecording) {
                    zoomLevel.floatValue = (1.5f * originalZoom.floatValue).coerceIn(1f, 5f)
                }
            }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            zoomLevel.floatValue = 1.0f
            originalZoom.floatValue = 1.0f
            autoZoomApplied.value = false
        }
    }

    val debugInfo = viewModel.debugInfoText.value

    Scaffold { padding ->
        Box(modifier = modifier.padding(padding).fillMaxSize()) {
            when {
                isRecording && currentRouteId != null -> {
                    val route by produceState<Route?>(initialValue = null) {
                        value = viewModel.routeRepository.getRoute(currentRouteId!!)
                    }

                    if (route == null) {
                        // ⏳ Show temporary loading message while new route data is not yet ready
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        val r = route
                        val bitmap = BitmapFactory.decodeFile(r!!.snapshotPath)?.asImageBitmap()
                        if (bitmap != null) {
                            val distance = calculateDistance(routePoints)

                            // Shared transform state (zoom + offset)
                            val transformState = rememberTransformableState { zoomChange, _, _ ->
                                zoomLevel.floatValue = (zoomLevel.floatValue * zoomChange)
                                    .coerceIn(0.5f * originalZoom.floatValue, 20f)
                                lastInteractionTime.longValue = System.currentTimeMillis()
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                zoomLevel.floatValue = 1.0f
                                                // offset reset removed because we no longer update it
                                                lastInteractionTime.longValue =
                                                    System.currentTimeMillis()
                                            }
                                        )
                                    }
                                    .transformable(transformState)
                            ) {
                                // Centered container; note that we removed translationX/Y so the image always stays centered.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                        .align(Alignment.Center)
                                        .graphicsLayer(
                                            scaleX = zoomLevel.floatValue,
                                            scaleY = zoomLevel.floatValue
                                            // translationX and translationY have been removed
                                        )
                                ) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Map Snapshot",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )

                                    // Red route line
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        if (routePoints.isNotEmpty()) {
                                            val path = Path()
                                            routePoints.forEachIndexed { index, point ->
                                                val (x, y) = latLonToWebMercatorPixel(
                                                    point.latitude,  // ✅ fixed
                                                    point.longitude, // ✅ fixed
                                                    r.centerLat,
                                                    r.centerLon,
                                                    r.zoom.toFloat(),
                                                    size.width.toInt(),   // ✅ use Canvas size, not r.width
                                                    size.height.toInt()
                                                )

                                                if (index == 0) {
                                                    path.moveTo(x, y)
                                                } else {
                                                    path.lineTo(x, y)
                                                }
                                            }

                                            drawPath(
                                                path,
                                                color = Color.Red,
                                                style = Stroke(width = strokeWidthPx)
                                            )
                                        }
                                    }
                                }

                                // DEBUG INFO BLOCK
                                LaunchedEffect(routePoints, zoomLevel.floatValue) {
                                    if (routePoints.isNotEmpty()) {
                                        val lastPoint = routePoints.last()
                                        val firstPoint = routePoints.first()
                                        val distanceMeters = calculateDistance(routePoints)
                                        val durationMs = lastPoint.timestamp - firstPoint.timestamp
                                        val durationHours = durationMs / (1000.0 * 60 * 60)
                                        val avgSpeed = if (durationHours > 0) (distanceMeters / 1000.0) / durationHours else 0.0
                                        val durationMinutes = durationMs / (1000.0 * 60)
                                        val paceMinPerKm = if (distanceMeters >= 50)
                                            durationMinutes / (distanceMeters / 1000.0)
                                        else 0.0

                                        val paceDisplay = if (paceMinPerKm > 0)
                                            "${paceMinPerKm.toInt()}:${((paceMinPerKm % 1) * 60).toInt().toString().padStart(2, '0')} min/km"
                                        else
                                            "Walk at least 50m to see pace"

                                        val distanceDisplay = if (distanceMeters < 1000)
                                            "${distanceMeters.toInt()} m"
                                        else
                                            "%.2f km".format(distanceMeters / 1000.0)

                                        val nameLine = route?.routeName ?: "Route ${route?.id}"
                                        viewModel.updateDebugInfo("""
                                            $nameLine
                                            Route Points: ${routePoints.size}
                                            Zoom Level: ${"%.2f".format(zoomLevel.floatValue)}
                                            Distance: $distanceDisplay
                                            Avg Speed: ${"%.2f".format(avgSpeed)} km/h
                                            Pace: $paceDisplay
                                        """.trimIndent())
                                    } else {
                                        viewModel.updateDebugInfo("Loading...")
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(16.dp)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = debugInfo,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            val distanceDisplay = if (distance < 1000)
                                "${distance.toInt()} m"
                            else
                                "%.2f km".format(distance / 1000.0)

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Total Distance: $distanceDisplay",
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(8.dp),
                                    color = Color.White,
                                    fontSize = 14.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

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
                        val distance = calculateDistance(routePoints)

                        // Removed offset: only using zoom adjustments now.
                        val transformState = rememberTransformableState { zoomChange, _, _ ->
                            zoomLevel.floatValue = (zoomLevel.floatValue * zoomChange)
                                .coerceIn(0.5f * originalZoom.floatValue, 20f)
                            lastInteractionTime.longValue = System.currentTimeMillis()
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            zoomLevel.floatValue = 1.0f
                                            lastInteractionTime.longValue = System.currentTimeMillis()
                                        }
                                    )
                                }
                                .transformable(transformState)
                        ) {
                            // Map snapshot and route (zoomed)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                    .align(Alignment.Center)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(
                                            scaleX = zoomLevel.floatValue,
                                            scaleY = zoomLevel.floatValue
                                        )
                                ) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Saved Route Snapshot",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )

                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val path = Path()
                                        routePoints.forEachIndexed { index, point ->
                                            val (x, y) = latLonToWebMercatorPixel(
                                                point.latitude, point.longitude,
                                                route.centerLat, route.centerLon,
                                                route.zoom.toFloat(),
                                                size.width.toInt(),
                                                size.height.toInt()
                                            )
                                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                        }
                                        drawPath(
                                            path,
                                            color = Color.Red,
                                            style = Stroke(width = strokeWidthPx)
                                        )
                                    }
                                }

                                // Overlay inside map area (NOT zoomed)
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                        .widthIn(max = 200.dp)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(8.dp)
                                ) {
                                    val distanceDisplay = if (distance < 1000)
                                        "${distance.toInt()} m"
                                    else
                                        "%.2f km".format(distance / 1000.0)

                                    val durationMinutes = ((route.endTime ?: route.startTime) - route.startTime) / (1000.0 * 60)
                                    val paceMinPerKm = if (distance >= 50) durationMinutes / (distance / 1000.0) else 0.0
                                    val paceDisplay = if (paceMinPerKm > 0)
                                        "${paceMinPerKm.toInt()}:${((paceMinPerKm % 1) * 60).toInt().toString().padStart(2, '0')} min/km"
                                    else
                                        "Less than 50m walked"

                                    var isEditingName by remember { mutableStateOf(false) }
                                    var editedName by remember { mutableStateOf("") }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isEditingName) {
                                            TextField(
                                                value = editedName,
                                                onValueChange = { editedName = it },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .widthIn(min = 100.dp, max = 160.dp)
                                                    .height(28.dp),
                                                textStyle = LocalTextStyle.current.copy(
                                                    color = Color.White,
                                                    fontSize = 14.sp
                                                ),
                                                colors = TextFieldDefaults.colors(
                                                    focusedIndicatorColor = Color.White,
                                                    unfocusedIndicatorColor = Color.Gray,
                                                    cursorColor = Color.White,
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    disabledContainerColor = Color.Transparent
                                                )
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Save",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .padding(start = 8.dp)
                                                    .size(18.dp)
                                                    .align(Alignment.CenterVertically)
                                                    .clickable {
                                                        isEditingName = false
                                                        viewModel.updateRouteName(route.id, editedName)
                                                    }
                                            )
                                        } else {
                                            Text(
                                                text = route.routeName ?: "Route ${route.id}",
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .padding(start = 8.dp)
                                                    .size(18.dp)
                                                    .clickable {
                                                        editedName = route.routeName ?: ""
                                                        isEditingName = true
                                                    }
                                            )
                                        }
                                    }
                                    Text("Start: ${formatTime(route.startTime)}", color = Color.White, fontSize = 14.sp)
                                    Text("End: ${route.endTime?.let { formatTime(it) } ?: "N/A"}", color = Color.White, fontSize = 14.sp)
                                    Text("Total Distance: $distanceDisplay", color = Color.White, fontSize = 14.sp)
                                    route.averageSpeed?.let {
                                        Text("Avg Speed: %.2f km/h".format(it), color = Color.White, fontSize = 14.sp)
                                        Text("Pace: $paceDisplay", color = Color.White, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isRecording) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (selectedRoute != null) {
                                    showRecordingChoiceDialog.value = true
                                } else {
                                    viewModel.startRecording()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Record")
                        }

                        if (showRecordingChoiceDialog.value) {
                            AlertDialog(
                                onDismissRequest = { showRecordingChoiceDialog.value = false },
                                title = { Text("Recording Options") },
                                text = { Text("Do you want to continue recording for the selected route or start new recording?") },
                                confirmButton = {
                                    Button(onClick = {
                                        showRecordingChoiceDialog.value = false
                                        viewModel.continueRecordingFromSelectedRoute()
                                    }) {
                                        Text("Continue Recording")
                                    }
                                },
                                dismissButton = {
                                    Button(onClick = {
                                        showRecordingChoiceDialog.value = false
                                        viewModel.startRecording() // this will clear selectedRoute
                                    }) {
                                        Text("New Recording")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Only show saved routes when NOT recording
            if (!isRecording) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f))
                            .wrapContentWidth() // ✅ Shrink to content
                    ) {
                        // Dropdown toggle button
                        Text(
                            text = "Saved Routes",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .align(Alignment.End) // ✅ Match dropdown width and align right
                                .padding(8.dp)
                                .clickable {
                                    isDropdownExpanded = !isDropdownExpanded
                                }
                        )

                        // Expandable list
                        if (isDropdownExpanded) {
                            LazyColumn(
                                modifier = Modifier
                                    .heightIn(max = 290.dp)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                items(savedRoutes) { savedRoute ->
                                    Text(
                                        text = "${savedRoute.routeName ?: "Route ${savedRoute.id}"} (${formatTime(savedRoute.startTime)})",
                                        color = if (savedRoute == selectedRoute) Color.Yellow else Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier
                                            .padding(vertical = 2.dp)
                                            .clickable {
                                                viewModel.selectRoute(savedRoute.id)
                                                isDropdownExpanded = false
                                            }
                                    )
                                }
                            }
                        }
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

fun latLonToWebMercatorPixel(
    lat: Double,
    lon: Double,
    centerLat: Double,
    centerLon: Double,
    zoom: Float,
    width: Int,
    height: Int
): Pair<Float, Float> {
    val tileSize = 512  // Because you're using @2x in snapshot URL
    val scale = 2.0.pow(zoom.toDouble()) * tileSize

    fun project(lat: Double, lon: Double): Pair<Double, Double> {
        val x = (lon + 180.0) / 360.0 * scale
        val siny = sin(lat * PI / 180.0).coerceIn(-0.9999, 0.9999)
        val y = (0.5 - ln((1.0 + siny) / (1.0 - siny)) / (4.0 * PI)) * scale
        return Pair(x, y)
    }

    val (centerX, centerY) = project(centerLat, centerLon)
    val (pointX, pointY) = project(lat, lon)

    val dx = pointX - centerX
    val dy = pointY - centerY

    val pixelX = width / 2f + dx.toFloat()
    val pixelY = height / 2f + dy.toFloat()

    return pixelX to pixelY
}

