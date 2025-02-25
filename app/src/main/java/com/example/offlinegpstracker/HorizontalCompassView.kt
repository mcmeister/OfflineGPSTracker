package com.example.offlinegpstracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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

val compassMarks = listOf(
    CompassMark(350, "N"), CompassMark(0, "N"), CompassMark(20, "NE"),
    CompassMark(40, "NE"), CompassMark(70, "E"), CompassMark(110, "E"),
    CompassMark(140, "SE"), CompassMark(180, "S"), CompassMark(220, "SW"),
    CompassMark(250, "W"), CompassMark(290, "NW"), CompassMark(330, "NW")
)

@Composable
fun HorizontalCompassView(azimuth: Float) {
    val adjustedAzimuth = ((azimuth + 360) % 360).roundToInt()
    val currentDirection = getHorizontalActiveDirection(azimuth)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            compassMarks.forEach { mark ->
                Text(
                    text = "${mark.degrees}°${mark.label?.let { " $it" } ?: ""}",
                    fontSize = 16.sp,
                    color = if (mark.label == currentDirection) Color.Red else Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        // Add indicator (arrows) at the current direction
        val centerIndex = compassMarks.indexOfFirst { it.degrees == adjustedAzimuth }
        if (centerIndex != -1) {
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
}