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

data class CompassMark(val degrees: Int, val label: String)

val compassMarks = listOf(
    CompassMark(0, "N"),
    CompassMark(10, "|"),
    CompassMark(20, "|"),
    CompassMark(30, "|"),
    CompassMark(45, "NE"),
    CompassMark(60, "|"),
    CompassMark(70, "|"),
    CompassMark(80, "|"),
    CompassMark(90, "E"),
    CompassMark(100, "|"),
    CompassMark(110, "|"),
    CompassMark(120, "|"),
    CompassMark(135, "SE"),
    CompassMark(150, "|"),
    CompassMark(160, "|"),
    CompassMark(170, "|"),
    CompassMark(180, "S"),
    CompassMark(190, "|"),
    CompassMark(200, "|"),
    CompassMark(210, "|"),
    CompassMark(225, "SW"),
    CompassMark(240, "|"),
    CompassMark(250, "|"),
    CompassMark(260, "|"),
    CompassMark(270, "W"),
    CompassMark(280, "|"),
    CompassMark(290, "|"),
    CompassMark(300, "|"),
    CompassMark(315, "NW"),
    CompassMark(330, "|"),
    CompassMark(340, "|"),
    CompassMark(350, "|")
)

@Composable
fun HorizontalCompassView(azimuth: Float) {
    val adjustedAzimuth = ((azimuth + 360) % 360).roundToInt()
    val currentDirection = getHorizontalActiveDirection(azimuth)

    val lazyListState = rememberLazyListState()
    var itemWidthPx by remember { mutableIntStateOf(0) }

    // Find the index of the current direction or the closest match
    val currentIndex = compassMarks.indexOfFirst { it.label == currentDirection }.coerceAtLeast(0)

    // Auto-scroll to center the current direction
    LaunchedEffect(adjustedAzimuth, itemWidthPx) {
        if (itemWidthPx > 0) {
            val centerOffset = (lazyListState.layoutInfo.viewportEndOffset - itemWidthPx) / 2
            lazyListState.scrollToItem(currentIndex, -centerOffset)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(Color.Black)
    ) {
        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(compassMarks) { _, mark ->
                Text(
                    text = "${mark.degrees}° ${mark.label}",
                    fontSize = if (mark.label == currentDirection) 18.sp else 14.sp, // Highlight current direction
                    color = if (mark.label == currentDirection) Color.Red else Color.White,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .onGloballyPositioned { coordinates ->
                            if (itemWidthPx == 0) { // Capture only once
                                itemWidthPx = coordinates.size.width
                            }
                        }
                )
            }
        }

        // Draw indicator line at the center
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .height(30.dp)
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