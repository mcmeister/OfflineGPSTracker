package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

@OptIn(FlowPreview::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RouteTrackerScreen(
    viewModel: RouteTrackerViewModel,
    modifier: Modifier = Modifier
) {
    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }
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
    val minZoom = 0.5f
    val maxZoom = 20f

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

    val tilesVersion by viewModel.tilesVersion.collectAsState()

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
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { Text("Loading…", color = Color.White, fontSize = 16.sp) }
                    } else {
                        val r = route!!
                        val distance = calculateDistance(routePoints)

                        /* ── pan & zoom state ──────────────────────── */
                        var offsetX by remember { mutableFloatStateOf(0f) }
                        var offsetY by remember { mutableFloatStateOf(0f) }


                        // ★ Visibility state for debug info
                        var showInfo by remember { mutableStateOf(true) }
                        var ignoreAutoShow by remember { mutableStateOf(false) }

                        // ★ Re-show 500ms after lastInteractionTime stops changing
                        LaunchedEffect(Unit) {
                            snapshotFlow { lastInteractionTime.longValue }
                                .debounce(500L)
                                .collect {
                                    if (!ignoreAutoShow) showInfo = true
                                }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            /* ── fixed-size clipped viewport ────────── */
                            val aspectRatio = r.width.toFloat() / r.height.toFloat()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspectRatio)
                                    .clipToBounds()
                                    .align(Alignment.Center)

                                    // ── SINGLE‐ & DOUBLE‐TAP ─────────────────────────────────────────────
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                // toggles info
                                                val willBeVisible = !showInfo
                                                showInfo = willBeVisible
                                                // if we just hid, suppress auto-re-show; if we just showed, allow it
                                                ignoreAutoShow = !willBeVisible
                                                lastInteractionTime.longValue = System.currentTimeMillis()
                                            },
                                            onDoubleTap = {
                                                // 1) suppress info block
                                                ignoreAutoShow = true
                                                lastInteractionTime.longValue =
                                                    System.currentTimeMillis()

                                                // 2) recenter the map
                                                offsetX = 0f
                                                offsetY = 0f

                                                // 3) cycle through your zoom levels
                                                val base = originalZoom.floatValue
                                                zoomLevel.floatValue = when {
                                                    zoomLevel.floatValue < base * 1.5f -> base * 1.5f
                                                    zoomLevel.floatValue < base * 3.0f -> base * 3.0f
                                                    zoomLevel.floatValue < base * 6.0f -> base * 6.0f
                                                    else -> base
                                                }
                                            }
                                        )
                                    }

                                    // ── PINCH-ZOOM & PAN ───────────────────────────────────────────────────
                                    .pointerInput(Unit) {
                                        detectTransformGestures { centroid, pan, zoomChange, _ ->
                                            // 1) Compute new zoom with non-linear scaling for smoother feel
                                            val oldZoom = zoomLevel.floatValue
                                            val zoomSensitivity =
                                                0.6f // Lower sensitivity for smoother zoom
                                            val newZoom =
                                                (oldZoom * (1f + (zoomChange - 1f) * zoomSensitivity))
                                                    .coerceIn(minZoom, maxZoom)
                                            val zoomDelta = newZoom - oldZoom

                                            // 2) Hide info if visible
                                            if (showInfo) showInfo = false

                                            // 3) Record interaction
                                            lastInteractionTime.longValue =
                                                System.currentTimeMillis()

                                            // 4) Pan with damping
                                            val panSensitivity =
                                                0.5f // Reduce pan speed for precision
                                            val panX = pan.x * panSensitivity
                                            val panY = pan.y * panSensitivity

                                            // 5) Calculate map bounds to constrain panning
                                            val mapWidth = r.width * zoomLevel.floatValue
                                            val mapHeight = r.height * zoomLevel.floatValue
                                            val maxOffsetX = mapWidth * 0.5f
                                            val maxOffsetY = mapHeight * 0.5f

                                            // Apply panning with bounds
                                            offsetX =
                                                (offsetX + panX).coerceIn(-maxOffsetX, maxOffsetX)
                                            offsetY =
                                                (offsetY + panY).coerceIn(-maxOffsetY, maxOffsetY)

                                            // 6) Adjust offset to keep centroid stable during zoom
                                            val centroidXRatio = centroid.x / size.width
                                            val centroidYRatio = centroid.y / size.height
                                            offsetX -= (centroidXRatio * size.width) * zoomDelta
                                            offsetY -= (centroidYRatio * size.height) * zoomDelta

                                            // 7) Apply zoom
                                            zoomLevel.floatValue = newZoom
                                        }
                                    }
                            ) {
                                /* ── apply scale + translation ───────── */
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                ) {
                                    TileMapOrPlaceholder(
                                        centerLat = route!!.centerLat,
                                        centerLon = route!!.centerLon,
                                        baseZoom = route!!.zoom,
                                        zoomLevel = zoomLevel.floatValue, // Use zoomLevel directly
                                        offsetX = offsetX,
                                        offsetY = offsetY,
                                        tilesVersion = tilesVersion,
                                        routePoints = routePoints
                                    )
                                }

                                LaunchedEffect(routePoints, zoomLevel.floatValue, isPaused) {
                                    if (routePoints.isNotEmpty()) {
                                        val first = routePoints.first()
                                        val last = routePoints.last()
                                        val distanceMeters = calculateDistance(routePoints)
                                        val durationMs = last.timestamp - first.timestamp
                                        val durationMin = durationMs / 60_000.0
                                        val durationHr = durationMs / 3_600_000.0
                                        val avgSpeedKmH =
                                            if (durationHr > 0) (distanceMeters / 1000.0) / durationHr else 0.0
                                        val paceMinPerKm = if (distanceMeters >= 50)
                                            durationMin / (distanceMeters / 1000.0) else -1.0

                                        val paceDisplay = if (paceMinPerKm > 0)
                                            "%d:%02d min/km".format(
                                                paceMinPerKm.toInt(),
                                                ((paceMinPerKm % 1) * 60).toInt()
                                            )
                                        else "Walk ≥ 50m to see pace"

                                        val distDisplay = if (distanceMeters < 1000)
                                            "${distanceMeters.toInt()} m"
                                        else "%.2f km".format(distanceMeters / 1000.0)

                                        // ← here’s the only real change:
                                        val nameLine = if (isPaused) "Paused" else "Recording…"

                                        viewModel.updateDebugInfo(
                                            listOf(
                                                nameLine,
                                                "Route points: ${routePoints.size}",
                                                "Zoom: %.2f".format(zoomLevel.floatValue),
                                                "Distance: $distDisplay",
                                                "Avg speed: %.2f km/h".format(avgSpeedKmH),
                                                "Pace: $paceDisplay"
                                            ).joinToString("\n")
                                        )
                                    } else {
                                        // also respect isPaused when there are no points yet
                                        viewModel.updateDebugInfo(
                                            if (isPaused) "Paused" else "Recording…"
                                        )
                                    }
                                }

                                /* ─────────── stylised overlay – debug info block with fade logic ─────────── */
                                val pillShape = RoundedCornerShape(4.dp)
                                val pillBackground = Color.Black.copy(alpha = 0.60f)
                                val chipModifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .background(pillBackground, pillShape)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)

                                AnimatedVisibility(
                                    visible = showInfo,
                                    enter = fadeIn(tween(300)),
                                    exit = fadeOut(tween(300)),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(16.dp)
                                ) {
                                    Column {
                                        debugInfo
                                            .lines()
                                            .filter { it.isNotBlank() }
                                            .forEach { line ->
                                                Text(
                                                    text = line.trim(),
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    modifier = chipModifier
                                                )
                                            }
                                    }
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
                                // ← define exactly the same chip modifier you use for debug-info
                                val pillShape = RoundedCornerShape(4.dp)
                                val pillBackground = Color.Black.copy(alpha = 0.60f)
                                val chipModifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .background(pillBackground, pillShape)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)

                                Text(
                                    text = "Total Distance: $distanceDisplay",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = chipModifier
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

                    /* ── helper numbers for the info chips ─────────────────────────────── */
                    val distanceMeters = calculateDistance(routePoints)                // meters
                    val durationMinutes =
                        ((route.endTime ?: route.startTime) - route.startTime) / (1000.0 * 60)

                    val paceMinPerKm = if (distanceMeters >= 50)
                        durationMinutes / (distanceMeters / 1000.0)
                    else 0.0

                    val paceDisplay = if (paceMinPerKm > 0)
                        "${paceMinPerKm.toInt()}:${
                            ((paceMinPerKm % 1) * 60)
                                .toInt().toString().padStart(2, '0')
                        } min/km"
                    else "Less than 50m walked"

                    val distanceDisplay = if (distanceMeters < 1000)
                        "${distanceMeters.toInt()} m"
                    else "%.2f km".format(distanceMeters / 1000.0)

                    // ─────────── new pan + zoom state ───────────
                    var offsetX by remember { mutableFloatStateOf(0f) }
                    var offsetY by remember { mutableFloatStateOf(0f) }


                    // ★ Visibility state
                    var showInfo by remember { mutableStateOf(true) }
                    var ignoreAutoShow by remember { mutableStateOf(false) }

                    // ★ Debounced re‑show 500ms after lastInteractionTime updates
                    LaunchedEffect(Unit) {
                        snapshotFlow { lastInteractionTime.longValue }
                            .debounce(500L)
                            .collect {
                                if (!ignoreAutoShow) {
                                    showInfo = true
                                }
                            }
                    }

                    // ─────────── merged gesture detector ───────────
                    Box(
                        modifier = modifier
                            .fillMaxSize()
                    ) {
                        // ─────────── fixed‑size, clipped viewport ───────────
                        val aspectRatio = route.width.toFloat() / route.height.toFloat()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio)
                                .clipToBounds()
                                .align(Alignment.Center)

                                // ── SINGLE‐ & DOUBLE‐TAP ─────────────────────────────────────────────
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            // toggles info
                                            val willBeVisible = !showInfo
                                            showInfo = willBeVisible
                                            // if we just hid, suppress auto-re-show; if we just showed, allow it
                                            ignoreAutoShow = !willBeVisible
                                            lastInteractionTime.longValue = System.currentTimeMillis()
                                        },
                                        onDoubleTap = {
                                            // 1) suppress info block
                                            ignoreAutoShow = true
                                            lastInteractionTime.longValue =
                                                System.currentTimeMillis()

                                            // 2) recenter the map
                                            offsetX = 0f
                                            offsetY = 0f

                                            // 3) cycle through your zoom levels
                                            val base = originalZoom.floatValue
                                            zoomLevel.floatValue = when {
                                                zoomLevel.floatValue < base * 1.5f -> base * 1.5f
                                                zoomLevel.floatValue < base * 3.0f -> base * 3.0f
                                                zoomLevel.floatValue < base * 6.0f -> base * 6.0f
                                                else -> base
                                            }
                                        }
                                    )
                                }

                                // ── PINCH-ZOOM & PAN ───────────────────────────────────────────────────
                                .pointerInput(Unit) {
                                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                                        // 1) Compute new zoom with non-linear scaling for smoother feel
                                        val oldZoom = zoomLevel.floatValue
                                        val zoomSensitivity =
                                            0.6f // Lower sensitivity for smoother zoom
                                        val newZoom =
                                            (oldZoom * (1f + (zoomChange - 1f) * zoomSensitivity))
                                                .coerceIn(minZoom, maxZoom)
                                        val zoomDelta = newZoom - oldZoom

                                        // 2) Hide info if visible
                                        if (showInfo) showInfo = false

                                        // 3) Record interaction
                                        lastInteractionTime.longValue = System.currentTimeMillis()

                                        // 4) Pan with damping
                                        val panSensitivity = 0.5f // Reduce pan speed for precision
                                        val panX = pan.x * panSensitivity
                                        val panY = pan.y * panSensitivity

                                        // 5) Calculate map bounds to constrain panning
                                        val mapWidth = route.width * zoomLevel.floatValue
                                        val mapHeight = route.height * zoomLevel.floatValue
                                        val maxOffsetX = mapWidth * 0.5f
                                        val maxOffsetY = mapHeight * 0.5f

                                        // Apply panning with bounds
                                        offsetX = (offsetX + panX).coerceIn(-maxOffsetX, maxOffsetX)
                                        offsetY = (offsetY + panY).coerceIn(-maxOffsetY, maxOffsetY)

                                        // 6) Adjust offset to keep centroid stable during zoom
                                        val centroidXRatio = centroid.x / size.width
                                        val centroidYRatio = centroid.y / size.height
                                        offsetX -= (centroidXRatio * size.width) * zoomDelta
                                        offsetY -= (centroidYRatio * size.height) * zoomDelta

                                        // 7) Apply zoom
                                        zoomLevel.floatValue = newZoom
                                    }
                                }
                        ) {
                            // ─────────── apply both scale & translation ───────────
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                TileMapOrPlaceholder(
                                    centerLat = route.centerLat,
                                    centerLon = route.centerLon,
                                    baseZoom = route.zoom,
                                    zoomLevel = zoomLevel.floatValue,  // your live zoom state
                                    offsetX = offsetX,              // your live pan state
                                    offsetY = offsetY,
                                    tilesVersion = tilesVersion,
                                    routePoints = routePoints
                                )
                            }

                            /* ─────────── overlay: inline‑chip info block ──────────────── */
                            AnimatedVisibility(
                                visible = showInfo,
                                enter = fadeIn(tween(300)),
                                exit = fadeOut(tween(300)),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                                    .widthIn(max = 200.dp)
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    val chipModifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .background(
                                            color = Color.Black.copy(alpha = 0.60f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 4.dp)

                                    val displayName = route.routeName ?: "Route ${route.id}"
                                    val densityRouteName = LocalDensity.current
                                    val textMeasurer = rememberTextMeasurer()

                                    /* Measure the text once per name‑change (and density change) */
                                    val minNameWidth by remember(displayName, densityRouteName) {
                                        mutableStateOf(
                                            with(densityRouteName) {
                                                textMeasurer
                                                    .measure(
                                                        text = displayName,
                                                        style = TextStyle(fontSize = 14.sp)
                                                    )
                                                    .size.width.toDp()
                                            } + 6.dp
                                        )
                                    }

                                    /* ───── name / edit chip ───── */
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = chipModifier
                                    ) {
                                        if (isEditingName) {
                                            BasicTextField(
                                                value = editedName,
                                                onValueChange = { editedName = it },
                                                singleLine = true,
                                                textStyle = LocalTextStyle.current.copy(
                                                    color = Color.White,
                                                    fontSize = 14.sp
                                                ),
                                                cursorBrush = SolidColor(Color.White),
                                                modifier = Modifier
                                                    .widthIn(min = minNameWidth, max = 160.dp)
                                                    .weight(1f, fill = false)
                                                    .padding(end = 6.dp)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Save",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clickable {
                                                        isEditingName = false
                                                        viewModel.updateRouteName(
                                                            route.id,
                                                            editedName.trim()
                                                        )
                                                    }
                                            )
                                        } else {
                                            Text(
                                                text = displayName,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                modifier = Modifier
                                                    .widthIn(min = minNameWidth)
                                                    .weight(1f, fill = false)
                                                    .padding(end = 6.dp)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clickable {
                                                        editedName = displayName
                                                        isEditingName = true
                                                    }
                                            )
                                        }
                                    }

                                    /* ───── single‑line info chips ───── */
                                    Text(
                                        text = "Start: ${formatTime(route.startTime)}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = chipModifier
                                    )

                                    Text(
                                        text = "End: ${route.endTime?.let { formatTime(it) } ?: "N/A"}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = chipModifier
                                    )

                                    Text(
                                        text = "Total Distance: $distanceDisplay",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = chipModifier
                                    )

                                    route.averageSpeed?.let {
                                        Text(
                                            text = "Avg Speed: %.2f km/h".format(it),
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = chipModifier
                                        )
                                        Text(
                                            text = "Pace: $paceDisplay",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = chipModifier
                                        )
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

            if (!isRecording) {

                /* visual constants */
                val pillShape = RoundedCornerShape(4.dp)
                val pillBackground = Color.Black.copy(alpha = 0.60f)

                /* composable helpers (✓) */
                val densitySavedRoutes = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()

                /* width of the pill text – measured once */
                val pillWidthDp by remember {
                    mutableStateOf(
                        with(densitySavedRoutes) {
                            val w = textMeasurer
                                .measure("Saved Routes", TextStyle(fontSize = 16.sp))
                                .size.width
                            (w + 16).toDp()           // 8dp start + 8dp end
                        }
                    )
                }

                /* width of the longest list item – recomputed only while open */
                val listWidthDp by remember(isDropdownExpanded, savedRoutes) {
                    derivedStateOf {
                        if (!isDropdownExpanded) 0.dp
                        else {
                            val longestPx = savedRoutes.maxOfOrNull { r ->
                                textMeasurer
                                    .measure(
                                        "${r.routeName ?: "Route ${r.id}"} (${formatTime(r.startTime)})",
                                        TextStyle(fontSize = 14.sp)
                                    ).size.width
                            } ?: 0
                            with(densitySavedRoutes) { (longestPx + 16).toDp() } // 8dp + 8dp padding
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {

                    /* ───────── toggle pill ───────── */
                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(pillBackground)
                            .clickable { isDropdownExpanded = !isDropdownExpanded }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Text("Saved Routes", color = Color.White, fontSize = 16.sp)
                    }

                    /* ───────── dropdown panel ───────── */
                    if (isDropdownExpanded) {

                        val fixedWidth = maxOf(listWidthDp, pillWidthDp)
                        val listState =
                            rememberLazyListState()          // we need this for the thumb

                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(y = 32.5.dp)                       // right below the pill
                                .width(fixedWidth)
                                .clip(pillShape)
                                .background(pillBackground)
                        ) {
                            Row {                                        // list + thumb side‑by‑side

                                /* ---------- list ---------- */
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .weight(1f)                      // take all remaining width
                                        .heightIn(max = 200.dp)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    items(savedRoutes) { savedRoute ->
                                        val selected = savedRoute == selectedRoute
                                        Text(
                                            text = "${savedRoute.routeName ?: "Route ${savedRoute.id}"} " +
                                                    "(${formatTime(savedRoute.startTime)})",
                                            color = if (selected) Color.Yellow else Color.White,
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

                                /* ---------- vertical thumb ---------- */
                                ScrollThumb(
                                    listState = listState,
                                    modifier = Modifier
                                        .heightIn(max = 200.dp)
                                        .width(4.dp)
                                        .padding(vertical = 4.dp)        // little air top/bottom
                                )
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

private data class ScrollMetrics(val progress: Float, val thumbHeight: Dp)

@Composable
private fun ScrollThumb(
    listState      : LazyListState,
    modifier       : Modifier = Modifier,
    thumbColor     : Color     = Color.LightGray.copy(alpha = .85f),
    trackColor     : Color     = Color.Black.copy(alpha = .25f),
    minThumbHeight : Dp        = 14.dp,
    thumbWidth     : Dp        = 4.dp
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {

        /* ---- whether we need a thumb at all (derived) ---- */
        val showThumb by remember {
            derivedStateOf {
                val total    = listState.layoutInfo.totalItemsCount
                val visible  = listState.layoutInfo.visibleItemsInfo.size
                total > 0 && visible < total
            }
        }
        if (!showThumb) return@BoxWithConstraints   // nothing to draw

        /* ---- scroll progress & thumb height (derived) ---- */
        val metrics by remember {
            derivedStateOf {

                val totalItems   = listState.layoutInfo.totalItemsCount
                val visibleItems = listState.layoutInfo.visibleItemsInfo.size

                /* progress 0f‑1f */
                val firstIndex  = listState.firstVisibleItemIndex.toFloat()
                val firstOffset = listState.firstVisibleItemScrollOffset
                val itemHeight  = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull()?.size ?: 1
                val exact   = (firstIndex + firstOffset / itemHeight) /
                        (totalItems - visibleItems).coerceAtLeast(1)
                val progress = exact.coerceIn(0f, 1f)

                /* thumb height */
                val ratio       = visibleItems.toFloat() / totalItems
                val rawHeightDp = maxHeight * ratio
                val thumbDp     = maxOf(rawHeightDp, minThumbHeight)

                ScrollMetrics(progress, thumbDp)
            }
        }

        /* px maths */
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeightPx     = with(density) { metrics.thumbHeight.toPx() }
        val yOffsetPx         = (containerHeightPx - thumbHeightPx) * metrics.progress

        /* background track (optional) */
        Box(
            Modifier
                .fillMaxSize()
                .background(trackColor)
        )

        /* thumb */
        Box(
            Modifier
                .offset { IntOffset(0, yOffsetPx.roundToInt()) }
                .width(thumbWidth)
                .height(metrics.thumbHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(thumbColor)
        )
    }
}

@Composable
fun TileMap(
    centerLat: Double,
    centerLon: Double,
    zoom: Int,
    zoomLevel: Float, // 1 ≤ zoomLevel < 2
    offsetX: Float,
    offsetY: Float,
    tileRoot: File,
    modifier: Modifier = Modifier,
    tileCache: MutableMap<String, Bitmap>
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val canvasW = with(density) { maxWidth.toPx() }
        val canvasH = with(density) { maxHeight.toPx() }

        val tileSize = 512f
        val worldSize = tileSize * (1 shl zoom)

        // Web Mercator projection to world pixels
        fun project(lat: Double, lon: Double): Pair<Double, Double> {
            val x = (lon + 180.0) / 360.0 * worldSize
            val siny = sin(lat * PI / 180).coerceIn(-0.9999, 0.9999)
            val y = (0.5 - ln((1 + siny) / (1 - siny)) / (4 * PI)) * worldSize
            return x to y
        }
        val (centerX, centerY) = project(centerLat, centerLon)

        // Compute viewport bounds in world pixels
        val halfW = (canvasW / zoomLevel) / 2f
        val halfH = (canvasH / zoomLevel) / 2f
        val minWX = centerX - halfW + offsetX / zoomLevel
        val maxWX = centerX + halfW + offsetX / zoomLevel
        val minWY = centerY - halfH + offsetY / zoomLevel
        val maxWY = centerY + halfH + offsetY / zoomLevel

        // Convert to tile indices
        val minTileX = floor(minWX / tileSize).toInt().coerceAtLeast(0)
        val maxTileX = floor(maxWX / tileSize).toInt().coerceAtMost((1 shl zoom) - 1)
        val minTileY = floor(minWY / tileSize).toInt().coerceAtLeast(0)
        val maxTileY = floor(maxWY / tileSize).toInt().coerceAtMost((1 shl zoom) - 1)

        // Generate tile coordinates
        val coords = remember(minTileX, maxTileX, minTileY, maxTileY) {
            (minTileX..maxTileX).flatMap { tx ->
                (minTileY..maxTileY).map { ty -> tx to ty }
            }
        }

        // Load tiles off UI thread
        val bitmaps by produceState(initialValue = emptyMap<String, ImageBitmap>(), coords, zoomLevel, tileRoot) {
            withContext(Dispatchers.IO) {
                val loaded = mutableMapOf<String, ImageBitmap>()
                coords.forEach { (tx, ty) ->
                    val key = "$zoom/$tx/$ty"
                    val file = File(tileRoot, "$tx-$ty.png")
                    val cachedBitmap = synchronized(tileCache) { tileCache[key] }
                    if (cachedBitmap != null) {
                        loaded[key] = cachedBitmap.asImageBitmap()
                    } else if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)
                            ?.also { bmp -> synchronized(tileCache) { tileCache[key] = bmp } }
                            ?.let { bmp -> loaded[key] = bmp.asImageBitmap() }
                    }
                }
                value = loaded
            }
        }

        // Render tiles
        Canvas(modifier = Modifier.fillMaxSize()) {
            coords.forEach { (tx, ty) ->
                val key = "$zoom/$tx/$ty"
                val img = bitmaps[key] ?: return@forEach

                val worldTileX = tx * tileSize
                val worldTileY = ty * tileSize
                val screenX = ((worldTileX - centerX) * zoomLevel + canvasW / 2 + offsetX)
                val screenY = ((worldTileY - centerY) * zoomLevel + canvasH / 2 + offsetY)
                val dst = (tileSize * zoomLevel).roundToInt()

                drawImage(
                    image = img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset(screenX.toInt(), screenY.toInt()),
                    dstSize = IntSize(dst, dst),
                    filterQuality = FilterQuality.High
                )
            }
        }
    }
}

private fun proj(lat: Double, lon: Double, zoom: Int): Pair<Float, Float> {
    val world = 256f * (1 shl zoom) // Mapbox uses 256 pixels per tile in world space
    val x = ((lon + 180) / 360 * world).toFloat()
    val siny = sin(lat * PI / 180).coerceIn(-0.9999, 0.9999)
    val y = ((0.5 - ln((1 + siny) / (1 - siny)) / (4 * PI)) * world).toFloat()
    return x to y
}

fun tileXY(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
    val n = 1 shl zoom
    val x = ((lon + 180) / 360 * n).toInt().coerceIn(0, n - 1)
    val latRad = lat * PI / 180
    val y = ((1 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2 * n)
        .toInt().coerceIn(0, n - 1)
    return x to y
}

@Composable
private fun TileMapOrPlaceholder(
    centerLat: Double,
    centerLon: Double,
    baseZoom: Int,
    zoomLevel: Float,
    offsetX: Float,
    offsetY: Float,
    tilesVersion: Int,
    routePoints: List<RoutePoint>
) {
    // Fallback to a default location if centerLat/centerLon are invalid (e.g., 0.0, 0.0)
    val validCenterLat = if (centerLat == 0.0 || centerLat.isNaN()) 37.7749 else centerLat // Default to San Francisco
    val validCenterLon = if (centerLon == 0.0 || centerLon.isNaN()) -122.4194 else centerLon

    Log.d("TileMapOrPlaceholder", "Center coordinates: lat=$validCenterLat, lon=$validCenterLon (original: lat=$centerLat, lon=$centerLon)")

    key(tilesVersion) {
        val context = LocalContext.current

        // 1) Debounce the zoom to decide when *folder* should switch
        val debouncedZoom by produceState(initialValue = zoomLevel, zoomLevel) {
            delay(100)
            value = zoomLevel
        }

        // 2) Pick which zoom folder actually has tiles
        val wantedZoom = (baseZoom + log2(debouncedZoom).roundToInt()).coerceIn(baseZoom, 22)
        Log.d("TileMapOrPlaceholder", "Wanted zoom: $wantedZoom, baseZoom: $baseZoom, debouncedZoom: $debouncedZoom")
        var z = wantedZoom
        var tileRoot: File? = null
        while (z >= baseZoom) {
            val folder = File(context.filesDir, "tiles/$z")
            if (folder.exists() && folder.isDirectory) {
                val tileFiles = folder.listFiles()
                if (!tileFiles.isNullOrEmpty()) {
                    tileRoot = folder
                    Log.d("TileMapOrPlaceholder", "Selected zoom level $z with ${tileFiles.size} tiles")
                    break
                } else {
                    Log.d("TileMapOrPlaceholder", "Zoom level $z folder exists but contains no tiles")
                }
            } else {
                Log.d("TileMapOrPlaceholder", "Zoom level $z folder does not exist")
            }
            z--
        }
        if (tileRoot == null) {
            Log.w("TileMapOrPlaceholder", "No tiles found for any zoom level from $wantedZoom down to $baseZoom")
        }

        // 3) Tile bitmap cache with LRU eviction
        val tileCache = remember {
            object : LinkedHashMap<String, Bitmap>(100, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, Bitmap>?): Boolean {
                    return size > 100 // Limit to 100 tiles (~25 MB for 512x512 ARGB_8888)
                }
            }
        }

        // 4) Prefetch adjacent tiles (using wantedZoom for consistency)
        LaunchedEffect(wantedZoom, validCenterLat, validCenterLon) {
            val prefetchZoomLevels = listOf(wantedZoom - 1, wantedZoom, wantedZoom + 1)
            prefetchZoomLevels.forEach { z ->
                if (z in baseZoom..22) {
                    val (tx, ty) = tileXY(validCenterLat, validCenterLon, z)
                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            val tileFile = File(context.filesDir, "tiles/$z/${tx + dx}-${ty + dy}.png")
                            if (tileFile.exists() && !tileCache.containsKey(tileFile.path)) {
                                try {
                                    val bitmap = BitmapFactory.decodeFile(tileFile.path)
                                    tileCache[tileFile.path] = bitmap
                                    Log.d("TileMapOrPlaceholder", "Prefetched tile: ${tileFile.path}")
                                } catch (e: Exception) {
                                    Log.e("TileMap", "Failed to load tile ${tileFile.path}", e)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5) Compute TileConfig only when tileRoot or z changes
        val tileConfig: TileConfig? = remember(tileRoot, z) {
            tileRoot?.let {
                TileConfig(it, z)
            }
        }

        Box(Modifier.fillMaxSize()) {
            // A) TileMap without graphicsLayer scaling (scaling is now handled in TileMap)
            Crossfade(
                targetState = tileConfig,
                animationSpec = tween(200),
                modifier = Modifier.fillMaxSize(),
                label = ""
            ) { cfg ->
                if (cfg != null) {
                    TileMap(
                        centerLat = validCenterLat,
                        centerLon = validCenterLon,
                        zoom = cfg.zoom,
                        tileRoot = cfg.tileRoot,
                        zoomLevel = zoomLevel, // Pass zoomLevel to TileMap
                        offsetX = offsetX,
                        offsetY = offsetY,
                        modifier = Modifier.fillMaxSize(),
                        tileCache = tileCache
                    )
                } else {
                    // No local tiles → placeholder
                    Log.w("TileMapOrPlaceholder", "No tile config available, showing placeholder")
                    Image(
                        painter = painterResource(R.drawable.default_map_placeholder),
                        contentDescription = "Map Placeholder",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // B) Draw the polyline with the same scaling logic
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Use tileConfig or fallback to baseZoom
                val zoomE = tileConfig?.zoom ?: baseZoom
                val worldTileSize = 256f
                val tileSize = 512
                val scale = tileSize / worldTileSize

                val (cX, cY) = proj(validCenterLat, validCenterLon, zoomE)
                val path = Path().apply {
                    routePoints.forEachIndexed { i, pt ->
                        val (xP, yP) = proj(pt.latitude, pt.longitude, zoomE)
                        // Apply the same transform as the map
                        val dx = (xP - cX) * scale * zoomLevel + (offsetX / zoomLevel)
                        val dy = (yP - cY) * scale * zoomLevel + (offsetY / zoomLevel)
                        val sx = (w / 2 + dx).roundToInt().toFloat()
                        val sy = (h / 2 + dy).roundToInt().toFloat()
                        if (i == 0) moveTo(sx, sy) else lineTo(sx, sy)
                    }
                }

                drawPath(
                    path = path,
                    color = Color.Red,
                    style = Stroke(width = 1.5f)
                )
            }
        }
    }
}

private data class TileConfig(
    val tileRoot: File,
    val zoom: Int
)