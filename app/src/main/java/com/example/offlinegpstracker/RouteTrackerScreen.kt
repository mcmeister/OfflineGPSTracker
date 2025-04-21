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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

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

                                LaunchedEffect(routePoints, zoomLevel.floatValue) {
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
                                            "%d:%02d min/km".format(
                                                paceMinPerKm.toInt(),
                                                ((paceMinPerKm % 1) * 60).toInt()
                                            )
                                        else "Walk ≥ 50 m to see pace"

                                        val distDisplay = if (distanceMeters < 1000)
                                            "${distanceMeters.toInt()} m"
                                        else "%.2f km".format(distanceMeters / 1000.0)

                                        val nameLine = route?.routeName ?: "Recording…"

                                        viewModel.updateDebugInfo(
                                            listOf(
                                                nameLine,
                                                "Route points: ${routePoints.size}",
                                                "Zoom: %.2f".format(zoomLevel.floatValue),
                                                "Distance: $distDisplay",
                                                "Avg speed: %.2f km/h".format(avgSpeedKmH),
                                                "Pace: $paceDisplay"
                                            ).joinToString("\n")
                                        )
                                    } else {
                                        viewModel.updateDebugInfo("Recording…")
                                    }
                                }

                                /* ─────────── stylised overlay – same pill style as saved‑route block ─────────── */
                                val pillShape = RoundedCornerShape(4.dp)
                                val pillBackground = Color.Black.copy(alpha = 0.60f)
                                val chipModifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .background(pillBackground, pillShape)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(16.dp)
                                ) {
                                    debugInfo              // → State<String> you already show elsewhere
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
                        /* ───────────────────────────────────────────────────────────────────── */

                        /* zoom / pan logic — unchanged except for offset removal */
                        val transformState = rememberTransformableState { zoomChange, _, _ ->
                            zoomLevel.floatValue =
                                (zoomLevel.floatValue * zoomChange)
                                    .coerceIn(0.5f * originalZoom.floatValue, 20f)
                            lastInteractionTime.longValue = System.currentTimeMillis()
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            zoomLevel.floatValue = 1f
                                            lastInteractionTime.longValue =
                                                System.currentTimeMillis()
                                        }
                                    )
                                }
                                .transformable(transformState)
                        ) {

                            /* ─────────── map snapshot + red route line (zoomed) ───────────── */
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
                                            path = path,
                                            color = Color.Red,
                                            style = Stroke(width = strokeWidthPx)
                                        )
                                    }
                                }

                                /* ─────────── overlay: inline‑chip info block ──────────────── */
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                        .widthIn(max = 200.dp)          // keep a ceiling, not a floor
                                ) {
                                    /* ---------- shared pill background + padding ---------- */
                                    val chipModifier = Modifier
                                        .padding(vertical = 2.dp)
                                        .background(
                                            color = Color.Black.copy(alpha = 0.60f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 4.dp)

                                    val displayName = route.routeName ?: "Route ${route.id}"
                                    val densityRouteName =
                                        LocalDensity.current        // ← composable read here
                                    val textMeasurer = rememberTextMeasurer()

                                    /*  Measure the text once per name‑change (and density change) */
                                    val minNameWidth by remember(displayName, densityRouteName) {
                                        mutableStateOf(
                                            with(densityRouteName) {
                                                textMeasurer
                                                    .measure(
                                                        text = displayName,
                                                        style = TextStyle(fontSize = 14.sp)
                                                    )
                                                    .size.width.toDp()
                                            } + 6.dp           // 6 dp = same trailing padding you add below
                                        )
                                    }

                                    /* ───── name / edit chip ───── */
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = chipModifier            // dark pill background
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
                                                    .widthIn(
                                                        min = minNameWidth,
                                                        max = 160.dp
                                                    ) // lock min width
                                                    .weight(
                                                        1f,
                                                        fill = false
                                                    )                  // push icon only when needed
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
                                                    .widthIn(min = minNameWidth)              // same min width
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
                            (w + 16).toDp()           // 8 dp start + 8 dp end
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
                            with(densitySavedRoutes) { (longestPx + 16).toDp() } // 8 dp + 8 dp padding
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
