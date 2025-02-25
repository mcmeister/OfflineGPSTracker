package com.example.offlinegpstracker

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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

fun getHorizontalActiveDirection(azimuth: Float): String {
    val adjustedAzimuth = ((azimuth + 360) % 360).roundToInt()
    return when (adjustedAzimuth) {
        in 337..360, in 0..22 -> "N"
        in 23..67 -> "NE"
        in 68..112 -> "E"
        in 113..157 -> "SE"
        in 158..202 -> "S"
        in 203..247 -> "SW"
        in 248..292 -> "W"
        in 293..336 -> "NW"
        else -> "N" // Default case
    }
}

data class CompassMark(val degrees: Int, val label: String? = null)

val compassMarks = (0..359).map { degrees ->
    CompassMark(degrees, getHorizontalActiveDirection(degrees.toFloat()).takeIf { it != "N" && degrees % 10 == 0 })
}

@Composable
fun HorizontalCompassView(azimuth: Float) {
    val adjustedAzimuth = ((azimuth + 360) % 360).roundToInt()
    val currentDirection = getHorizontalActiveDirection(azimuth)

    val lazyListState = rememberLazyListState()
    var itemWidthPx by remember { mutableIntStateOf(0) }

    // Find the index of the current direction or the closest match
    val currentIndex = compassMarks.indexOfFirst { it.degrees == adjustedAzimuth }

    // Auto-scroll to center the current direction
    LaunchedEffect(adjustedAzimuth) {
        snapshotFlow { lazyListState.layoutInfo }
            .collect { layoutInfo ->
                val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                if (itemWidthPx > 0 && viewportWidth > 0) {
                    val centerOffset = (viewportWidth - itemWidthPx) / 2
                    lazyListState.animateScrollToItem(currentIndex, -centerOffset)
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color.Black)
    ) {
        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(compassMarks) { _, mark ->
                Text(
                    text = "${mark.degrees}° ${mark.label ?: ""}".trim(),
                    fontSize = 14.sp,
                    color = if (mark.label == currentDirection) Color.Red else Color.White,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .onGloballyPositioned { coordinates ->
                            val newWidth = coordinates.size.width
                            if (itemWidthPx != newWidth) {
                                itemWidthPx = newWidth
                            }
                        }
                )
            }
        }

        // Move Canvas outside LazyRow but inside Box, ensuring it’s in a @Composable scope
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .width(10.dp)
                .height(20.dp)
        ) {
            val centerX = size.width / 2
            drawLine(
                color = Color.White,
                start = Offset(centerX, 0f),
                end = Offset(centerX, size.height),
                strokeWidth = 2f
            )
        }
    }
}