package com.example.offlinegpstracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun HorizontalCompassGauge(azimuth: Float, modifier: Modifier = Modifier) {
    val gaugeWidthDp = 2500.dp
    val gaugeWidthPx = with(LocalDensity.current) { gaugeWidthDp.toPx() }

    // Normalize azimuth to [0,360)
    val normalizedAzimuth = (azimuth + 360) % 360
    val offsetX = -(normalizedAzimuth / 360f) * gaugeWidthPx

    Box(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .width(gaugeWidthDp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val directions = listOf(
                "N", "NE", "E", "SE", "S", "SW", "W", "NW",
                "N", "NE", "E", "SE", "S", "SW", "W", "NW",
                "N", "NE", "E", "SE", "S", "SW", "W", "NW"
            )

            directions.forEach { dir ->
                Text(
                    text = dir,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        // Center red arrow indicator
        Icon(
            Icons.Default.ArrowDropUp,
            contentDescription = "Current Direction",
            tint = Color.Red,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun CompassGaugeDirectionLetters() {
    val directions = listOf(
        "N", "NE", "E", "SE", "S", "SW", "W", "NW", "N", "NE", "E",
        "SE", "S", "SW", "W", "NW" // repeated to ensure seamless scrolling
    )

    directions.forEach { dir ->
        Text(
            text = dir,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
