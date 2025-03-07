package com.example.offlinegpstracker

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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

fun getNextDirection(azimuth: Float): String {
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N") // Circular wrap
    val currentIndex = directions.indexOf(getActiveDirectionRound(azimuth))
    return directions[(currentIndex + 1) % directions.size]
}

fun getPreviousDirection(azimuth: Float): String {
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N") // Circular wrap
    val currentIndex = directions.indexOf(getActiveDirectionRound(azimuth))
    return directions[(currentIndex - 1 + directions.size) % directions.size]
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun CompassViewRound(modifier: Modifier = Modifier, azimuth: Float) {
    var smoothedAzimuth by remember { mutableFloatStateOf(0f) }
    val alpha = 0.2f

    LaunchedEffect(azimuth) {
        smoothedAzimuth = (alpha * azimuth) + (1 - alpha) * smoothedAzimuth
    }

    val currentDirection = getActiveDirectionRound(smoothedAzimuth)
    val nextDirection = getNextDirection(smoothedAzimuth)
    val prevDirection = getPreviousDirection(smoothedAzimuth)

    val transition = rememberInfiniteTransition(label = "letter_slide")
    val offsetX by transition.animateFloat(
        initialValue = 40f, targetValue = -40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "letter_move"
    )

    Box(
        modifier = modifier
            .fillMaxWidth() // Ensure full width for proper centering
            .padding(horizontal = 16.dp) // Padding for safety
            .padding(vertical = 80.dp)
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(200.dp, 120.dp) // Enlarged for better centering
                .shadow(10.dp, RoundedCornerShape(50.dp))
                .background(
                    Brush.verticalGradient(listOf(Color.Black, Color.DarkGray)),
                    RoundedCornerShape(50.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Animated Direction Letters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                // Left Side Letter (Previous)
                Text(
                    text = prevDirection,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.offset(x = offsetX.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Center Letter (Highlighted in Red)
                Text(
                    text = currentDirection,
                    fontSize = 42.sp, // Larger size for emphasis
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Right Side Letter (Next)
                Text(
                    text = nextDirection,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.offset(x = -offsetX.dp)
                )
            }
        }
    }
}