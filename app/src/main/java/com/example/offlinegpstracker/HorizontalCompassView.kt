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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
    onDebugInfoUpdated: (CompassDebugInfo) -> Unit = {}
) {
    val density = LocalDensity.current
    val adjustedAzimuth = ((azimuth + 360) % 360).roundToInt()
    val currentDirection = getHorizontalActiveDirection(azimuth)
    Log.d("CompassView", "Current Azimuth: $azimuth, Adjusted: $adjustedAzimuth, Direction: $currentDirection")

    var fixedItemWidthPx by remember { mutableIntStateOf(0) } // Width in pixels
    var fixedItemWidthDp by remember { mutableStateOf<Dp?>(null) } // Width in dp
    val lazyListState = rememberLazyListState()
    val directionWidths by remember { mutableStateOf(mutableMapOf<String, Int>()) }

    // Generate compass marks (major directions & separators)
    val extendedMarks = mutableListOf<CompassMark>()
    val majorDegrees = listOf(0, 45, 90, 135, 180, 225, 270, 315)
    val cycles = 3 // Number of cycles for endless scrolling

    repeat(cycles) { cycle ->
        majorDegrees.forEachIndexed { index, degree ->
            val cycleOffset = cycle * 360
            val label = getHorizontalActiveDirection(degree.toFloat())
            extendedMarks.add(CompassMark(degree, label))

            if (index < majorDegrees.size - 1) {
                val step = 45 / 4.0 // Ensure floating-point precision
                for (j in 1..3) {
                    val separatorDegree = (degree + (j * step) + cycleOffset).roundToInt()
                    extendedMarks.add(CompassMark(separatorDegree, ""))
                }
            }
        }
    }

    // Find the index of the current direction for precise centering
    val baseIndex = majorDegrees.indexOfFirst { degree ->
        val direction = getHorizontalActiveDirection(adjustedAzimuth.toFloat())
        getHorizontalActiveDirection(degree.toFloat()) == direction
    }.coerceAtLeast(0)
    val totalItemsPerCycle = majorDegrees.size + 3 * (majorDegrees.size - 1)
    val middleCycleOffset = (cycles / 2) * totalItemsPerCycle
    val currentIndex = middleCycleOffset + baseIndex * 4

    Log.d("CompassView", "Base Index: $baseIndex, Total Items Per Cycle: $totalItemsPerCycle, Middle Cycle Offset: $middleCycleOffset, Final Index: $currentIndex, Current Direction: $currentDirection")

    // Ensure `fixedItemWidth` is properly assigned from NW
    LaunchedEffect(Unit) {
        while (fixedItemWidthPx == 0) {
            if (directionWidths["NW"] != null) {
                fixedItemWidthPx = directionWidths["NW"]!!
                fixedItemWidthDp = with(density) { fixedItemWidthPx.toDp() }
                Log.d("CompassView", "Set fixedItemWidthPx=$fixedItemWidthPx, fixedItemWidthDp=$fixedItemWidthDp")
            }
            delay(16.milliseconds)
        }
    }

    // Auto-scroll to center the current direction precisely above the arrow
    LaunchedEffect(currentIndex) {
        if (fixedItemWidthPx > 0) {
            snapshotFlow { lazyListState.layoutInfo }
                .filter { it.viewportSize.width > 0 }
                .first()
                .let { layoutInfo ->
                    val viewportWidthPx = layoutInfo.viewportSize.width
                    val centerOffset = (viewportWidthPx - fixedItemWidthPx) / 2
                    onDebugInfoUpdated(
                        CompassDebugInfo(
                            currentIndex = currentIndex,
                            centerOffset = centerOffset,
                            viewportWidth = viewportWidthPx,
                            currentDirectionWidth = fixedItemWidthPx
                        )
                    )
                    Log.d("CompassView", "Scrolling to index $currentIndex with offset $centerOffset, viewportWidthPx=$viewportWidthPx")
                    lazyListState.animateScrollToItem(currentIndex, centerOffset)
                }
        }
    }

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
                val isDirectionLabel = mark.label.isNotEmpty()
                val isMiddleSeparator = (index % 4 == 2)

                Box(
                    modifier = Modifier
                        .width(fixedItemWidthDp ?: Dp.Unspecified)
                        .height(if (isDirectionLabel) 40.dp else if (isMiddleSeparator) 40.dp else 20.dp)
                        .padding(horizontal = 4.dp)
                        .onGloballyPositioned { coordinates ->
                            val measuredWidth = coordinates.size.width
                            if (isDirectionLabel) {
                                directionWidths[mark.label] = measuredWidth
                            }
                        }
                ) {
                    if (isDirectionLabel) {
                        Text(
                            text = "${mark.degrees}° ${mark.label}".trim(),
                            fontSize = if (mark.label == currentDirection) 18.sp else 14.sp,
                            color = if (mark.label == currentDirection) Color.Red else Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
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

        // Indicator Arrow
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