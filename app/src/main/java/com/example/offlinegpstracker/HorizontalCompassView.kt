package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

fun getHorizontalActiveDirection(azimuth: Float): String {
    val adjustedAzimuth = ((azimuth + 360) % 360).toInt()
    // Log.d("CompassDirection", "Azimuth: $azimuth, Adjusted Azimuth: $adjustedAzimuth")
    return when (adjustedAzimuth) {
        in 337..360, in 0..22 -> {
            // Log.d("CompassDirection", "Returning N for $adjustedAzimuth")
            "N"
        }
        in 23..67 -> {
            // Log.d("CompassDirection", "Returning NE for $adjustedAzimuth")
            "NE"
        }
        in 68..112 -> {
            // Log.d("CompassDirection", "Returning E for $adjustedAzimuth")
            "E"
        }
        in 113..157 -> {
            // Log.d("CompassDirection", "Returning SE for $adjustedAzimuth")
            "SE"
        }
        in 158..202 -> {
            // Log.d("CompassDirection", "Returning S for $adjustedAzimuth")
            "S"
        }
        in 203..247 -> {
            // Log.d("CompassDirection", "Returning SW for $adjustedAzimuth")
            "SW"
        }
        in 248..292 -> {
            // Log.d("CompassDirection", "Returning W for $adjustedAzimuth")
            "W"
        }
        in 293..336 -> {
            // Log.d("CompassDirection", "Returning NW for $adjustedAzimuth")
            "NW"
        }
        else -> {
            // Log.d("CompassDirection", "Defaulting to N for $adjustedAzimuth")
            "N" // Default case
        }
    }
}

data class CompassMark(
    val extendedAngle: Float,  // includes cycle offset, e.g. 405°, 765°, etc.
    val displayAngle: Float,   // the base or separator angle (0°, 11.25°, 45°, etc.)
    val label: String = ""     // e.g. "N", "NE", "S", or empty for separators
)

data class CompassDebugInfo(
    val currentIndex: Int = 0,
    val centerOffset: Float = 0.0F,
    val viewportWidth: Int = 0,
    val currentDirectionWidth: Int = 0
)

@SuppressLint("MutableCollectionMutableState")
@Composable
fun HorizontalCompassView(
    azimuth: Float,
    onDebugInfoUpdated: (CompassDebugInfo) -> Unit = {} // Callback for debug info
) {
    // Smoothing factor (adjustable based on sensitivity)
    var smoothedAzimuth by remember { mutableFloatStateOf(0f) }
    val alpha = 0.2f

    LaunchedEffect(azimuth) {
        smoothedAzimuth = (alpha * azimuth) + (1 - alpha) * smoothedAzimuth
    }

    // Define the LazyListState
    val lazyListState = rememberLazyListState()

    // Store the last known valid viewport width
    var lastViewportWidth by remember { mutableFloatStateOf(0f) }

    val viewportWidthPx by remember {
        derivedStateOf {
            lazyListState.layoutInfo.viewportSize.width.takeIf { it > 0 }?.toFloat() ?: lastViewportWidth
        }
    }

    // Store the viewport width safely without unnecessary recompositions
    LaunchedEffect(viewportWidthPx) {
        if (viewportWidthPx > 0) {
            lastViewportWidth = viewportWidthPx
        }
    }

    // Prevent execution when viewport is unavailable
    // if (viewportWidthPx == 0f) return

    val totalAzimuthRange = 360f  // Full compass rotation

    val threshold = 1.5f  // Degrees (adjust as needed)

    val filteredAzimuth by remember {
        derivedStateOf {
            if (kotlin.math.abs(azimuth - smoothedAzimuth) > threshold) azimuth else smoothedAzimuth
        }
    }
    val adjustedAzimuth = ((filteredAzimuth + 360) % 360).roundToInt() - 90

    // Map azimuth to viewport width properly
    val scaledAzimuth = (adjustedAzimuth / totalAzimuthRange) * viewportWidthPx

    val currentDirection = getHorizontalActiveDirection(azimuth)

    // Prevent redundant scrolling by tracking last target index and time
    var lastTargetIndex by remember { mutableIntStateOf(-1) }
    val debounceTime = 300L  // 300ms debounce
    var lastScrollTime by remember { mutableLongStateOf(0L) }

    // Get the currently visible index to avoid unnecessary scrolling
    val currentVisibleIndex by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex }
    }

    // Prevent execution at launch only if we are at Index 0 AND azimuth is still 0
    if (adjustedAzimuth == 0 && currentVisibleIndex == 0) return

    Log.d("CompassView", "Azimuth: $azimuth, Adjusted: $adjustedAzimuth, Scaled: $scaledAzimuth, Direction: $currentDirection")

    // We no longer measure NW dynamically, so remove directionWidths
    // and rely on a fixed item width instead.
    val itemWidthDp = 25.dp
    // Convert our chosen DP to pixels for scrolling calculations:
    val itemWidthPx = with(LocalDensity.current) { itemWidthDp.roundToPx() }

    // Generate compass marks (major directions & separators)
    val extendedMarks = mutableListOf<CompassMark>()
    val majorDegrees = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
    val cycles = 3

    repeat(cycles) { cycle ->
        val cycleOffset = cycle * 360f

        // For each major interval, also generate 3 in-between ticks
        majorDegrees.forEach { baseAngle ->
            val steps = 4  // 0..3
            val stepAngle = 45f / steps

            for (j in 0 until steps) {
                val angle = baseAngle + j * stepAngle + cycleOffset
                // Always get the direction label for ANY angle
                val label = getHorizontalActiveDirection(angle)

                extendedMarks.add(
                    CompassMark(
                        extendedAngle = angle,
                        displayAngle = angle,
                        label = label
                    )
                )
            }
        }
    }

    // 1) Convert your real adjustedAzimuth into a 0–360 float
    val az = scaledAzimuth

    // 2) For debugging, log each item’s extendedAngle, label, difference from az
    for (i in extendedMarks.indices) {
        val rawAngle = extendedMarks[i].extendedAngle
        val label = extendedMarks[i].label
        // Convert to 0..360
        val angle360 = ((rawAngle % 360) + 360) % 360
        // Compute difference from user's az
        val diff = kotlin.math.abs(angle360 - az)

        // Log.d(
        //     "CompassDebug",
        //     "Item $i → extendedAngle=$rawAngle, angle360=$angle360, label='$label', diff=$diff"
        // )
    }

    // 3) Then pick the item with the smallest diff
    val targetIndex = extendedMarks.indices.minByOrNull { i ->
        val angle360 = ((extendedMarks[i].extendedAngle % 360) + 360) % 360
        val diff = kotlin.math.abs(angle360 - adjustedAzimuth)
        if (diff > 180) 360 - diff else diff  // Ensure closest wrap-around angle
    } ?: 0

    Log.d(
        "CompassView",
        "Selected Index: $targetIndex, " +
                "Angle: ${extendedMarks[targetIndex].extendedAngle}, " +
                "Label: '${extendedMarks[targetIndex].label}', " +
                "Azimuth: $az, " +
                "Current Direction: $currentDirection"
    )

    // Main container
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.DarkGray, Color.Black)
                ),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center // Added for consistent centering
    ) {
        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(extendedMarks) { index, mark ->
                val isMajorDirection = mark.label.isNotEmpty()
                val isMiddleSeparator = (index % 4 == 2) // optional style

                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(
                            if (isMajorDirection) 40.dp
                            else if (isMiddleSeparator) 40.dp
                            else 20.dp
                        )
                    // Removed .padding(horizontal = 4.dp) for perfect centering
                ) {
                    // Convert displayAngle to [0..359] range for UI
                    val modAngle = ((mark.displayAngle % 360) + 360) % 360
                    val displayAngleInt = modAngle.roundToInt()

                    if (isMajorDirection) {
                        val textToShow = if (mark.label == currentDirection) {
                            "$adjustedAzimuth° ${mark.label}"
                        } else {
                            "$displayAngleInt° ${mark.label}"
                        }

                        Text(
                            text = textToShow,
                            fontSize = if (mark.label == currentDirection) 18.sp else 14.sp,
                            color = if (mark.label == currentDirection) Color.Red else Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val sepAngleText = "${displayAngleInt}°"
                        Text(
                            text = sepAngleText,
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        val shouldScroll by remember {
            derivedStateOf { lastTargetIndex != targetIndex }
        }

        LaunchedEffect(targetIndex, itemWidthPx, viewportWidthPx) {
            if (viewportWidthPx > 0 && itemWidthPx > 0 &&
                targetIndex < extendedMarks.size &&
                lazyListState.layoutInfo.totalItemsCount > 0
            ) {
                val centerOffset = (viewportWidthPx - itemWidthPx) / 2 // ✅ Define centerOffset before use

                val currentTime = System.currentTimeMillis()
                val timeSinceLastScroll = currentTime - lastScrollTime

                if (shouldScroll && timeSinceLastScroll > debounceTime) {
                    lastScrollTime = currentTime // ✅ Update last scroll time
                    lastTargetIndex = targetIndex // ✅ Store last target index

                    onDebugInfoUpdated(
                        CompassDebugInfo(
                            currentIndex = targetIndex,
                            centerOffset = centerOffset,
                            viewportWidth = viewportWidthPx.toInt(),
                            currentDirectionWidth = itemWidthPx
                        )
                    )

                    // ✅ **Only scroll if we're not already at the target**
                    if (currentVisibleIndex != targetIndex) {
                        lazyListState.animateScrollToItem(targetIndex, centerOffset.toInt())
                    }
                }
            }
        }

        // Overlay the center arrow
        Image(
            painter = painterResource(id = R.drawable.red_arrow),
            contentDescription = "Compass Arrow",
            modifier = Modifier
                .align(Alignment.Center)
                .width(20.dp)
                .height(40.dp)
        )
    }
}