package com.example.offlinegpstracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
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

@Composable
fun HorizontalCompassView(azimuth: Float) {
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val extendedDirections = List(3) { directions }.flatten()
    val lazyListState = rememberLazyListState()
    var itemWidthPx by remember { mutableIntStateOf(0) }

    // 1) Instead of returning a single direction, compute a float index in [0..8)
    val rawIndex = (azimuth + 360f) % 360f / 45f
    // e.g. azimuth=70 => 70/45=1.55 => halfway between NE(index=1) and E(index=2)
    val floatIndex = 8 + rawIndex // shift by 8 to land in middle chunk

    // 2) Separate integer + fraction
    val mainIndex = floatIndex.toInt() // e.g. 9
    val fraction = floatIndex - mainIndex // e.g. 0.55

    // 3) Force item `mainIndex` on screen so it’s measured
    LaunchedEffect(mainIndex) {
        // Just scrollToItem so we can measure it
        lazyListState.scrollToItem(mainIndex, 0)
    }

    // 4) Use snapshotFlow to keep centering with partial offsets
    LaunchedEffect(mainIndex, fraction) {
        snapshotFlow {
            lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
        }.collect { viewportWidth ->
            if (itemWidthPx > 0 && viewportWidth > 0) {
                val centerOffset = (viewportWidth - itemWidthPx) / 2

                // Shift our offset by fraction of 1 item,
                // so we "slide" between directions
                val partialShift = fraction * itemWidthPx

                // negative offset to center + partial shift
                val finalOffset = -(centerOffset + partialShift)
                lazyListState.scrollToItem(mainIndex, finalOffset.toInt())
            }
        }
    }

    // 5) Build the row
    LazyRow(
        state = lazyListState,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(extendedDirections) { index, direction ->
            Row(
                modifier = if (index == mainIndex) {
                    Modifier.onGloballyPositioned { coordinates ->
                        val newWidth = coordinates.size.width
                        if (itemWidthPx != newWidth) {
                            itemWidthPx = newWidth
                        }
                    }
                } else Modifier
            ) {
                Text(
                    text = direction,
                    fontSize = 32.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    // highlight whichever is the "nearest" integer direction
                    color = if (index == floatIndex.roundToInt()) Color.Red else Color.Black
                )
                if (index < extendedDirections.size - 1) {
                    SeparatorLines()
                }
            }
        }
    }
}

@Composable
fun SeparatorLines() {
    Canvas(
        modifier = Modifier
            .height(30.dp)
            .width(10.dp)
    ) {
        val startX = size.width / 2
        val startY = 0f
        val endY = size.height
        drawLine(Color.Black, Offset(startX, startY), Offset(startX, endY), strokeWidth = 3f)
    }
}
