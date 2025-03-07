package com.example.offlinegpstracker

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

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
    var previousAzimuth by remember { mutableFloatStateOf(azimuth) }
    var degreeOffset by remember { mutableFloatStateOf(0f) }
    val alpha = 0.2f

    LaunchedEffect(azimuth) {
        // Simple low-pass filter
        smoothedAzimuth = alpha * azimuth + (1 - alpha) * smoothedAzimuth
    }

    // 2) If you want an animation from old smoothedAzimuth to new smoothedAzimuth:
    val animatedAzimuth by animateFloatAsState(
        targetValue = smoothedAzimuth,
        animationSpec = tween(durationMillis = 300), label = "" // tune to taste
    )

    val currentDirection = getActiveDirectionRound(animatedAzimuth)
    val nextDirection = getNextDirection(animatedAzimuth)
    val prevDirection = getPreviousDirection(animatedAzimuth)

    val degreeScrollOffset = -animatedAzimuth

    val transition = rememberInfiniteTransition(label = "letter_slide")
    val sideOffset by transition.animateFloat(
        initialValue = 20f, targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "side_letter_move"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 80.dp)
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(300.dp, 120.dp)
                // .shadow(10.dp, RoundedCornerShape(60.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFFB0BEC5), Color(0xFF78909C)),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    ),
                    RoundedCornerShape(50.dp)
                )
                .border(8.dp, Color(0xFF546E7A), RoundedCornerShape(50.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.size(280.dp, 100.dp).align(Alignment.Center)) {
                    val tickLength = 10.dp.toPx()
                    val majorTickLength = 20.dp.toPx()
                    val tickColor = Color.White

                    for (i in 0 until 360 step 10) {
                        val angleRad = Math.toRadians(i.toDouble())

                        val startLength = if (i % 90 == 0) majorTickLength else tickLength

                        val startX =
                            (size.width / 2 + cos(angleRad) * (size.width / 2 - startLength)).toFloat()
                        val startY =
                            (size.height / 2 + sin(angleRad) * (size.height / 2 - startLength)).toFloat()
                        val endX = (size.width / 2 + cos(angleRad) * (size.width / 2)).toFloat()
                        val endY = (size.height / 2 + sin(angleRad) * (size.height / 2)).toFloat()

                        drawLine(
                            color = if (i % 90 == 0) Color.Red else tickColor,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = if (i % 90 == 0) 4f else 2f
                        )
                    }
                }

                // Glass reflection effect
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.15f)),
                                center = Offset(100f + smoothedAzimuth, 60f),
                                radius = 200f
                            ),
                            RoundedCornerShape(50.dp)
                        )
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Previous Direction
                    Text(
                        text = prevDirection,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF9C4),
                        modifier = Modifier.offset(x = -sideOffset.dp)
                    )

                    // Next Direction
                    Text(
                        text = nextDirection,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF9C4),
                        modifier = Modifier.offset(x = sideOffset.dp)
                    )

                    // Current Direction
                    Text(
                        text = currentDirection,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    InfiniteDegreeScroll(animatedAzimuth, degreeScrollOffset)
                }
            }
        }
    }
}

@Composable
fun InfiniteDegreeScroll(azimuth: Float, offsetDegrees: Float) {
    // If azimuth = 100°, offsetDegrees = -100, we want the "100°" to appear in the center.

    // Generate 360 labels, then we’ll shift them horizontally by offsetDegrees
    val degreesList = (0..359).toList()

    // We can animate the offset or just convert offsetDegrees to dp.
    // You can scale the offset if you prefer. For instance, 1 degree = 10.dp, etc.
    val pxPerDegree = 10.dp // tune to taste
    val finalOffset = offsetDegrees.dp * pxPerDegree.value / 1.dp.value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = finalOffset),
        horizontalArrangement = Arrangement.Center
    ) {
        degreesList.forEach { deg ->
            Text(
                text = "$deg°",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}