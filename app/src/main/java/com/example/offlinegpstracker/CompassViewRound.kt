package com.example.offlinegpstracker

import android.annotation.SuppressLint
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun getActiveDirectionRound(azimuth: Float): String {
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

@SuppressLint("MutableCollectionMutableState")
@Composable
fun CompassViewRound(azimuth: Float) {
    var smoothedAzimuth by remember { mutableFloatStateOf(0f) }
    val alpha = 0.2f

    LaunchedEffect(azimuth) {
        smoothedAzimuth = (alpha * azimuth) + (1 - alpha) * smoothedAzimuth
    }

    val currentDirection = getActiveDirectionRound(smoothedAzimuth)
    var compassType by remember { mutableStateOf(CompassType.Round) }

    Crossfade(targetState = compassType, label = "CompassSwitch") { type ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    compassType = when (compassType) {
                        CompassType.Round -> CompassType.Horizontal
                        CompassType.Horizontal -> CompassType.Round
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            when (type) {
                CompassType.Round -> {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.Black, shape = androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentDirection,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    }
                }

                CompassType.Horizontal -> {
                    HorizontalCompassGauge(azimuth = smoothedAzimuth)
                }
            }
        }
    }
}

enum class CompassType {
    Round,
    Horizontal
}