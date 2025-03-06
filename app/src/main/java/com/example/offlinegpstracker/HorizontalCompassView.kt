package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    return when (adjustedAzimuth) {
        in 337..360, in 0..22 -> "N"
        in 23..67 -> "NE"
        in 68..112 -> "E"
        in 113..157 -> "SE"
        in 158..202 -> "S"
        in 203..247 -> "SW"
        in 248..292 -> "W"
        in 293..336 -> "NW"
        else -> "N"
    }
}

data class CompassMark(
    val extendedAngle: Float,  // includes cycle offset, e.g. 405°, 765°, etc.
    val displayAngle: Float,   // the base or separator angle (0°, 11.25°, 45°, etc.)
    val label: String = ""     // e.g. "N", "NE", or empty for separators
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

    val totalAzimuthRange = 360f  // Full compass rotation
    val adjustedAzimuth = ((azimuth + 360) % 360).roundToInt() - 90

    // Map azimuth to viewport width properly
    val scaledAzimuth = (adjustedAzimuth / totalAzimuthRange) * viewportWidthPx

    val currentDirection = getHorizontalActiveDirection(azimuth)

    // Prevent redundant scrolling by tracking last target index and time
    var lastTargetIndex by remember { mutableIntStateOf(-1) }
    var lastScrollTime by remember { mutableLongStateOf(0L) }

    // Get the currently visible index to avoid unnecessary scrolling
    val currentVisibleIndex by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex }
    }

    // Prevent execution at launch only if we are at Index 0 AND azimuth is still 0
    if (adjustedAzimuth == 0 && currentVisibleIndex == 0) return

    Log.d("CompassView", "Azimuth: $azimuth, Adjusted: $adjustedAzimuth, Scaled: $scaledAzimuth, Direction: $currentDirection")

    // Use fixed item width of 25.dp for both labels and separators (for auto-scroll calculations)
    val itemWidthDp = 25.dp
    val itemWidthPx = with(LocalDensity.current) { itemWidthDp.roundToPx() }

    // Generate compass marks (major directions & separators)
    val extendedMarks = mutableListOf<CompassMark>()
    val majorDegrees = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)
    val cycles = 3

    repeat(cycles) { cycle ->
        val cycleOffset = cycle * 360f

        // For each major interval, generate 1 main label + 3 separator ticks.
        majorDegrees.forEach { baseAngle ->
            val steps = 4  // 0..3
            val stepAngle = 45f / steps

            for (j in 0 until steps) {
                val angle = baseAngle + j * stepAngle + cycleOffset
                // Only the first tick in each group gets a label; the rest are separators.
                val label = if (j == 0) getHorizontalActiveDirection(angle) else ""
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

    // Debug logging for each mark (unchanged)
    for (i in extendedMarks.indices) {
        val rawAngle = extendedMarks[i].extendedAngle
        val label = extendedMarks[i].label
        val angle360 = ((rawAngle % 360) + 360) % 360
        val diff = kotlin.math.abs(angle360 - az)
        // Debug log commented out
    }

    // 3) Pick the item with the smallest diff
    val targetIndex = extendedMarks.indices.minByOrNull { i ->
        val angle360 = ((extendedMarks[i].extendedAngle % 360) + 360) % 360
        val diff = kotlin.math.abs(angle360 - adjustedAzimuth)
        if (diff > 180) 360 - diff else diff
    } ?: 0

    Log.d(
        "CompassView",
        "Selected Index: $targetIndex, " +
                "Angle: ${extendedMarks[targetIndex].extendedAngle}, " +
                "Label: '${extendedMarks[targetIndex].label}', " +
                "Azimuth: $az, " +
                "Current Direction: $currentDirection"
    )

    // Main container with background and centering arrow overlay
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
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(extendedMarks) { index, mark ->
                // A mark is a major direction if its label is not empty; otherwise it's a separator.
                val isMajorDirection = mark.label.isNotEmpty()
                Box(
                    modifier = Modifier
                        .width(itemWidthDp) // Use fixed width of 25.dp for both labels and separators
                        .height(40.dp)      // Fixed container height for consistent alignment
                ) {
                    if (isMajorDirection) {
                        // For major directions, show a Column with the degree and the letter.
                        val modAngle = ((mark.displayAngle % 360) + 360) % 360
                        val displayAngleInt = modAngle.roundToInt()
                        val degreeText = if (mark.label == currentDirection) "$adjustedAzimuth°" else "$displayAngleInt°"
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = degreeText,
                                fontSize = 8.sp, // Reduced font size for degrees
                                color = Color.LightGray
                            )
                            Text(
                                text = mark.label,
                                fontSize = if (mark.label == currentDirection) 18.sp else 14.sp,
                                color = if (mark.label == currentDirection) Color.Red else Color.White
                            )
                        }
                    } else {
                        // For separators, draw a vertical line.
                        // Determine which separator based on index % 4:
                        //  - Type 2 (middle): longer line, white.
                        //  - Types 1 and 3: shorter line, grey.
                        val separatorType = index % 4
                        val lineHeight = if (separatorType == 2) 40.dp else 20.dp
                        val lineColor = if (separatorType == 2) Color.White else Color.Gray
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(2.dp) // Adjust thickness as needed
                                .height(lineHeight)
                                .background(lineColor)
                        )
                    }
                }
            }
        }

        // Auto-scroll logic (unchanged)
        LaunchedEffect(targetIndex, itemWidthPx, viewportWidthPx) {
            if (viewportWidthPx > 0 && itemWidthPx > 0 && targetIndex < extendedMarks.size && lazyListState.layoutInfo.totalItemsCount > 0) {
                val selectedAngle = extendedMarks[targetIndex].displayAngle
                val selectedAngle360 = ((selectedAngle % 360) + 360) % 360
                Log.d(
                    "CompassScroll",
                    "Viewport: $viewportWidthPx, Target Index: $targetIndex, Selected Angle: $selectedAngle360, Scaled: $scaledAzimuth"
                )
                val currentTime = System.currentTimeMillis()
                val timeSinceLastScroll = currentTime - lastScrollTime
                if (lastTargetIndex != targetIndex && timeSinceLastScroll > 400) {
                    lastTargetIndex = targetIndex
                    lastScrollTime = currentTime
                    val centerOffset = (viewportWidthPx - itemWidthPx) / 2
                    onDebugInfoUpdated(
                        CompassDebugInfo(
                            currentIndex = targetIndex,
                            centerOffset = centerOffset,
                            viewportWidth = viewportWidthPx.toInt(),
                            currentDirectionWidth = itemWidthPx
                        )
                    )
                    if (currentVisibleIndex != targetIndex) {
                        lazyListState.animateScrollToItem(targetIndex, centerOffset.toInt())
                    }
                }
            }
        }

        // Overlay the center arrow (unchanged)
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
