package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

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

data class CompassMark(val degrees: Int, val label: String)

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

    var maxLabelWidth by remember { mutableIntStateOf(0) }
    var nwMeasuredWidth by remember { mutableIntStateOf(0) }
    val lazyListState = rememberLazyListState()
    var itemWidthPx by remember { mutableIntStateOf(0) }
    val directionWidths by remember { mutableStateOf(mutableMapOf<String, Int>()) } // Changed to mutableStateOf with MutableMap

    // Create a list of compass marks with major directions and 3 separators between them
    val extendedMarks = mutableListOf<CompassMark>()
    val majorDegrees = listOf(0, 45, 90, 135, 180, 225, 270, 315) // All major directions
    val cycles = 3 // Number of cycles for endless scrolling

    // Create a repeating list of directions and separators for endless scrolling
    repeat(cycles) { cycle ->
        majorDegrees.forEachIndexed { index, degree ->
            // Add the direction with an offset for each cycle
            val cycleOffset = cycle * 360
            val label = getHorizontalActiveDirection(degree.toFloat())
            extendedMarks.add(CompassMark(degree, label))
            Log.d("CompassMarks",
                "Adding major direction at $degree° (cycle $cycle): $label (from getHorizontalActiveDirection: $label)")

            // Add 3 separators after each direction, except after the last one in each cycle
            if (index < majorDegrees.size - 1) {
                val nextMajorDegree = majorDegrees[index + 1]
                val step = (nextMajorDegree - degree) / 4.0 // Use 4.0 for Double division
                for (j in 1..3) {
                    val separatorDegree = degree + (j * step)
                    extendedMarks.add(CompassMark(separatorDegree.toInt() + cycleOffset, ""))
                    Log.d("CompassMarks", "Adding separator at ${separatorDegree.toInt() + cycleOffset}° (cycle $cycle, rounded to ${separatorDegree.toInt()})")
                }
            }
        }
    }

    // Find the index of the current direction for precise centering across cycles
    val baseIndex = majorDegrees.indexOfFirst { degree ->
        val direction = getHorizontalActiveDirection(adjustedAzimuth.toFloat())
        getHorizontalActiveDirection(degree.toFloat()) == direction
    }.coerceAtLeast(0)
    val totalItemsPerCycle = majorDegrees.size + 3 * (majorDegrees.size - 1) // Directions + separators per cycle
    val middleCycleOffset = (cycles / 2) * totalItemsPerCycle // Offset to center in the middle cycle
    val currentDirectionWidth = (directionWidths[currentDirection] ?: 200).coerceAtLeast(200)
    val viewportWidth by remember { derivedStateOf { lazyListState.layoutInfo.viewportSize.width } } // Fixed viewportSize reference
    val halfViewportWidth = viewportWidth / 2
    val targetPosition = halfViewportWidth - (currentDirectionWidth / 2) // Center the current direction letter
    val currentIndex = ((baseIndex + middleCycleOffset + (totalItemsPerCycle / 2)) % extendedMarks.size + extendedMarks.size) % extendedMarks.size // Ensure positive modulo for centering
    Log.d("CompassView", "Base Index: $baseIndex, Total Items Per Cycle: $totalItemsPerCycle, Middle Cycle Offset: $middleCycleOffset, Final Index: $currentIndex, Current Direction: $currentDirection, Viewport Width: $viewportWidth, Target Position: $targetPosition")

    // Update debug info for display
    LaunchedEffect(currentIndex, currentDirectionWidth, viewportWidth) {
        onDebugInfoUpdated(CompassDebugInfo(
            currentIndex = currentIndex,
            centerOffset = (viewportWidth - currentDirectionWidth) / 2,
            viewportWidth = viewportWidth,
            currentDirectionWidth = currentDirectionWidth
        ))
    }

    // Auto-scroll to center the current direction precisely above the arrow, with a delay to ensure widths are updated
    LaunchedEffect(adjustedAzimuth) {
        // Wait until directionWidths has a valid width for the current direction or a reasonable default
        while (directionWidths[currentDirection] == 0 && itemWidthPx == 0) {
            delay(16.milliseconds) // Use milliseconds extension for better clarity
            Log.d("CompassScrollDebug", "Waiting for widths: directionWidths[$currentDirection]=${directionWidths[currentDirection]}, itemWidthPx=$itemWidthPx")
        }
        val viewportWidthPx = lazyListState.layoutInfo.viewportSize.width
        val directionWidth = directionWidths[currentDirection] ?: maxOf(itemWidthPx, 200) // Use current direction’s width, fallback to itemWidthPx or 200px
        val centerOffset = maxOf((viewportWidthPx - directionWidth) / 2, 0) // Center the current direction, ensure non-negative
        Log.d("CompassScroll", "Animating scroll to index $currentIndex with offset $centerOffset, directionWidth=$directionWidth, viewportWidth=$viewportWidthPx")
        lazyListState.animateScrollToItem(currentIndex, -centerOffset)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.DarkGray, Color.Black) // Darker at edges, lighter in middle
                ),
                shape = RoundedCornerShape(8.dp) // Rounded ends
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
                val isDirectionLabel = mark.label.isNotEmpty()

                Box(
                    modifier = Modifier
                        .width(if (nwMeasuredWidth > 0) nwMeasuredWidth.dp else Dp.Unspecified)
                        .padding(horizontal = 8.dp)
                        .onGloballyPositioned { coordinates ->
                            val measuredWidth = coordinates.size.width
                            if (isDirectionLabel) {
                                directionWidths[mark.label] = maxOf(measuredWidth, directionWidths[mark.label] ?: 0) // Update width for this direction
                                if (mark.label == currentDirection) {
                                    itemWidthPx = maxOf(itemWidthPx, measuredWidth) // Update itemWidthPx for current direction
                                    Log.d("CompassLayout", "Item width for ${mark.label} set to $itemWidthPx")
                                }
                                // Update maxLabelWidth and nwMeasuredWidth for "NW"
                                maxLabelWidth = maxOf(maxLabelWidth, measuredWidth)
                                if (mark.label == "NW") {
                                    nwMeasuredWidth = maxOf(measuredWidth, nwMeasuredWidth)
                                }
                            }
                        }
                ) {
                    if (isDirectionLabel) {
                        Text(
                            text = "${mark.degrees}° ${mark.label}".trim(),
                            fontSize = if (mark.label == currentDirection) 18.sp else 14.sp,
                            color = if (mark.label == currentDirection) Color.Red else Color.White
                        )
                    } else {
                        // Separator Lines
                        val isMiddleSeparator = (index % 4 == 2)
                        Canvas(
                            modifier = Modifier
                                .width(if (nwMeasuredWidth > 0) nwMeasuredWidth.dp else Dp.Unspecified)
                                .height(if (isMiddleSeparator) 40.dp else 20.dp)
                        ) {
                            drawLine(
                                color = if (index % 4 == 0) Color.Gray else Color.DarkGray,
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = if (isMiddleSeparator) 4f else 2f
                            )
                        }
                    }
                }
            }
        }

        // Indicator Arrow (replace the Canvas with Image)
        Image(
            painter = painterResource(id = R.drawable.red_arrow), // Use your drawable resource
            contentDescription = "Compass Arrow",
            modifier = Modifier
                .align(Alignment.Center)
                .width(20.dp) // Adjust width to match the arrow size (tune as needed)
                .height(40.dp) // Adjust height to match the arrow size (tune as needed)
        )
    }
}