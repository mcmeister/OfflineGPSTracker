package com.example.offlinegpstracker

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt // Add this import if missing

fun getHorizontalActiveDirection(azimuth: Float): String {
    val adjustedAzimuth = ((azimuth + 360) % 360).roundToInt()
    Log.d("CompassDirection", "Azimuth: $azimuth, Adjusted Azimuth: $adjustedAzimuth")
    return when (adjustedAzimuth) {
        in 337..360, in 0..22 -> {
            Log.d("CompassDirection", "Returning N for $adjustedAzimuth")
            "N"
        }
        in 23..67 -> {
            Log.d("CompassDirection", "Returning NE for $adjustedAzimuth")
            "NE"
        }
        in 68..112 -> {
            Log.d("CompassDirection", "Returning E for $adjustedAzimuth")
            "E"
        }
        in 113..157 -> {
            Log.d("CompassDirection", "Returning SE for $adjustedAzimuth")
            "SE"
        }
        in 158..202 -> {
            Log.d("CompassDirection", "Returning S for $adjustedAzimuth")
            "S"
        }
        in 203..247 -> {
            Log.d("CompassDirection", "Returning SW for $adjustedAzimuth")
            "SW"
        }
        in 248..292 -> {
            Log.d("CompassDirection", "Returning W for $adjustedAzimuth")
            "W"
        }
        in 293..336 -> {
            Log.d("CompassDirection", "Returning NW for $adjustedAzimuth")
            "NW"
        }
        else -> {
            Log.d("CompassDirection", "Defaulting to N for $adjustedAzimuth")
            "N" // Default case
        }
    }
}

data class CompassMark(val degrees: Int, val label: String)

@Composable
fun HorizontalCompassView(azimuth: Float) {
    val adjustedAzimuth = ((azimuth + 360) % 360).roundToInt()
    val currentDirection = getHorizontalActiveDirection(azimuth)
    Log.d("CompassView", "Current Azimuth: $azimuth, Adjusted: $adjustedAzimuth, Direction: $currentDirection")

    val lazyListState = rememberLazyListState()
    var itemWidthPx by remember { mutableIntStateOf(0) }

    // Create a list of compass marks with major directions and 3 separators between them
    val extendedMarks = mutableListOf<CompassMark>()
    val majorDegrees = listOf(0, 45, 90, 135, 180, 225, 270, 315) // All major directions

    // Add major directions and 3 separators between each
    majorDegrees.forEachIndexed { index, degree ->
        // Add the direction
        val label = getHorizontalActiveDirection(degree.toFloat())
        extendedMarks.add(CompassMark(degree, label))
        Log.d("CompassMarks", "Adding major direction at $degree°: $label (from getHorizontalActiveDirection: $label)")

        // Add 3 separators after each direction, except after the last one
        if (index < majorDegrees.size - 1) {
            val nextMajorDegree = majorDegrees[index + 1]
            val step = (nextMajorDegree - degree) / 4.0 // Use 4.0 for Double division
            for (j in 1..3) {
                val separatorDegree = degree + (j * step)
                extendedMarks.add(CompassMark(separatorDegree.toInt(), "")) // Convert Double to Int directly
                Log.d("CompassMarks", "Adding separator at $separatorDegree° (rounded to ${separatorDegree.toInt()})")
            }
        }
    }

    // Find the index of the current direction or closest match
    val currentIndex = extendedMarks.indexOfFirst { it.label == currentDirection }.coerceAtLeast(0)
    Log.d("CompassView", "Current Index: $currentIndex, Current Direction: $currentDirection")

    // Auto-scroll to center the current direction
    LaunchedEffect(adjustedAzimuth, itemWidthPx) {
        if (itemWidthPx > 0) {
            val centerOffset = (lazyListState.layoutInfo.viewportEndOffset - itemWidthPx) / 2
            Log.d("CompassScroll", "Scrolling to index $currentIndex with offset $centerOffset")
            lazyListState.scrollToItem(currentIndex, -centerOffset)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp) // Increased height for visibility
            .background(Color.Black)
    ) {
        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(extendedMarks) { index, mark ->
                if (mark.label.isNotEmpty()) {
                    // Main Direction Labels (N, NE, E, SE, S, SW, W, NW)
                    Text(
                        text = "${mark.degrees}° ${mark.label}".trim(),
                        fontSize = if (mark.label == currentDirection) 18.sp else 14.sp,
                        color = if (mark.label == currentDirection) Color.Red else Color.White,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .onGloballyPositioned { coordinates ->
                                if (itemWidthPx == 0) {
                                    itemWidthPx = coordinates.size.width
                                    Log.d("CompassLayout", "Item width set to $itemWidthPx")
                                }
                            }
                    )
                } else {
                    // Separator Lines with different heights for middle vs. side separators
                    val isMiddleSeparator = (index % 4 == 2) // Middle separator (2nd of 3) is longer
                    Canvas(
                        modifier = Modifier
                            .width(4.dp) // Thin spacing for all separators
                            .height(if (isMiddleSeparator) 40.dp else 20.dp) // Longer for middle, shorter for sides
                    ) {
                        drawLine(
                            color = if (index % 4 == 0) Color.Gray else Color.DarkGray, // Major every 45°, minor otherwise
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = if (isMiddleSeparator) 4f else 2f // Thicker for middle, thinner for sides
                        )
                    }
                }
            }
        }

        // Indicator Line
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .height(40.dp) // Larger for better visibility
        ) {
            drawLine(
                color = Color.White,
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 3f
            )
        }
    }
}