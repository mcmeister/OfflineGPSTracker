package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val adjustedAzimuth = ((azimuth + 360) % 360).roundToInt()
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
    val centerOffset: Int = 0,
    val viewportWidth: Int = 0,
    val currentDirectionWidth: Int = 0
)

@SuppressLint("MutableCollectionMutableState")
@Composable
fun HorizontalCompassView(
    azimuth: Float,
    onDebugInfoUpdated: (CompassDebugInfo) -> Unit = {} // Callback for debug info
) {
    val adjustedAzimuth = ((azimuth + 360) % 360).roundToInt()
    val currentDirection = getHorizontalActiveDirection(azimuth)
    Log.d("CompassView", "Current Azimuth: $azimuth, Adjusted: $adjustedAzimuth, Direction: $currentDirection")

    // We no longer measure NW dynamically, so remove directionWidths
    // and rely on a fixed item width instead.
    val itemWidthDp = 25.dp
    // Convert our chosen DP to pixels for scrolling calculations:
    val itemWidthPx = with(LocalDensity.current) { itemWidthDp.roundToPx() }

    val lazyListState = rememberLazyListState()

    // Generate compass marks (major directions & separators)
    val extendedMarks = mutableListOf<CompassMark>()
    val majorDegrees = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
    val cycles = 3

    repeat(cycles) { cycle ->
        val cycleOffset = cycle * 360f

        majorDegrees.forEachIndexed { index, baseAngle ->
            // This is the main major direction
            val label = getHorizontalActiveDirection(baseAngle)
            // extendedAngle for scrolling, displayAngle for on-screen text
            extendedMarks.add(
                CompassMark(
                    extendedAngle = baseAngle + cycleOffset,
                    displayAngle = baseAngle,
                    label = label
                )
            )

            // Now insert 3 separators in between the major directions
            if (index < majorDegrees.size - 1) {
                // e.g. 45 / 4 = 11.25
                val step = 45f / 4f
                for (j in 1..3) {
                    val sepBaseAngle = baseAngle + j * step
                    extendedMarks.add(
                        CompassMark(
                            extendedAngle = sepBaseAngle + cycleOffset,
                            displayAngle = sepBaseAngle,
                            label = ""  // no text label for separators
                        )
                    )
                }
            }
        }
    }

    // Find the index of the current direction for precise centering
    val baseIndex = majorDegrees.indexOfFirst { degree ->
        getHorizontalActiveDirection(adjustedAzimuth.toFloat()) ==
                getHorizontalActiveDirection(degree)
    }.coerceAtLeast(0)
    val totalItemsPerCycle = majorDegrees.size + 3 * (majorDegrees.size - 1)
    val middleCycleOffset = (cycles / 2) * totalItemsPerCycle
    val currentIndex = middleCycleOffset + baseIndex * 4

    Log.d(
        "CompassView",
        "Base Index: $baseIndex, Total Items Per Cycle: $totalItemsPerCycle, " +
                "Middle Cycle Offset: $middleCycleOffset, Final Index: $currentIndex, " +
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
            )
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

                // Use a fixed width for spacing
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(
                            if (isMajorDirection) 40.dp
                            else if (isMiddleSeparator) 40.dp
                            else 20.dp
                        )
                        .padding(horizontal = 4.dp)
                ) {
                    // If it's a major direction or a separator angle, show text
                    val displayAngleInt = mark.displayAngle.roundToInt()

                    if (isMajorDirection) {
                        // For the "current" direction, show the real user azimuth
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
                        // It's a separator with no label, so just show "5°" etc. or maybe an empty Canvas
                        val sepAngleText = "${displayAngleInt}°"
                        // Example: show text or draw a line
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

        // Now that itemWidthPx is a known constant, we can do the auto-scroll
        LaunchedEffect(currentIndex, itemWidthPx) {
            if (itemWidthPx > 0) {
                val viewportWidthPx = lazyListState.layoutInfo.viewportSize.width
                val centerOffset = (viewportWidthPx - itemWidthPx) / 2

                // Callback to update debug info (for your external UI)
                onDebugInfoUpdated(
                    CompassDebugInfo(
                        currentIndex = currentIndex,
                        centerOffset = centerOffset,
                        viewportWidth = viewportWidthPx,
                        currentDirectionWidth = itemWidthPx
                    )
                )

                if (viewportWidthPx > 0) {
                    lazyListState.animateScrollToItem(currentIndex, centerOffset)
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