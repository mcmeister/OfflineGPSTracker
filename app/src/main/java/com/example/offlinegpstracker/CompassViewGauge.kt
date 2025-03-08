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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.cos
import kotlin.math.sin

fun getActiveDirectionGauge(azimuth: Float): String {
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
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val currentIndex = directions.indexOf(getActiveDirectionGauge(azimuth))
    return directions[(currentIndex + 1) % directions.size]
}

fun getPreviousDirection(azimuth: Float): String {
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val currentIndex = directions.indexOf(getActiveDirectionGauge(azimuth))
    return directions[(currentIndex - 1 + directions.size) % directions.size]
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun CompassViewGauge(modifier: Modifier = Modifier, azimuth: Float) {
    val viewModel = viewModel<CompassViewModel>()

    var smoothedAzimuth by remember { mutableFloatStateOf(0f) }
    val alpha = 0.2f

    LaunchedEffect(azimuth) {
        smoothedAzimuth = alpha * azimuth + (1 - alpha) * smoothedAzimuth
    }

    val animatedAzimuth by animateFloatAsState(
        targetValue = smoothedAzimuth,
        animationSpec = tween(durationMillis = 300),
        label = ""
    )

    val currentDirection = getActiveDirectionGauge(animatedAzimuth)
    val nextDirection = getNextDirection(animatedAzimuth)
    val prevDirection = getPreviousDirection(animatedAzimuth)

    val transition = rememberInfiniteTransition(label = "letter_slide")

    val sideOffset by transition.animateFloat(
        initialValue = 20f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "side_letter_move"
    )

    // Animate text size from initial size to 4sp
    val animatedTextSize by transition.animateFloat(
        initialValue = 32f, // Initial size in sp
        targetValue = 4f,  // Decreasing step by step
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "text_size_animation"
    )

    val lightX = viewModel.lightX
    val lightY = viewModel.lightY

    val context = LocalContext.current
    DisposableEffect(Unit) {
        viewModel.initialize(context)
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth(0.8f)
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(300.dp, 120.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFD3D3D3), Color(0xFFB0BEC5)),
                            start = Offset.Zero,
                            end = Offset.Infinite
                        ),
                        RoundedCornerShape(50.dp)
                    )
                    .border(8.dp, Color(0xFF546E7A), RoundedCornerShape(50.dp))
            )

            Box(
                modifier = Modifier
                    .size(300.dp, 120.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFD3D3D3),
                                Color(0xFFA0A0A0),
                                Color(0xFFD3D3D3)
                            ),
                            start = Offset.Zero,
                            end = Offset.Infinite
                        ),
                        RoundedCornerShape(50.dp)
                    )
                    .border(8.dp, Color(0xFF546E7A), RoundedCornerShape(50.dp))
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
                            center = Offset(lightX, lightY),
                            radius = 150f
                        ),
                        RoundedCornerShape(50.dp)
                    )
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.1f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(300f, 120f)
                        ),
                        RoundedCornerShape(50.dp)
                    )
            )

            // Letters behind ticks (zIndex 0f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f) // Ensure letters are behind ticks
            ) {
                Text(
                    text = prevDirection,
                    fontSize = animatedTextSize.sp, // Apply animated text size
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFF9C4),
                    modifier = Modifier.align(Alignment.Center).offset(x = -sideOffset.dp)
                )

                Text(
                    text = nextDirection,
                    fontSize = animatedTextSize.sp, // Apply animated text size
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFF9C4),
                    modifier = Modifier.align(Alignment.Center).offset(x = sideOffset.dp)
                )

                Text(
                    text = currentDirection,
                    fontSize = 42.sp, // Apply animated text size
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Ticks in front of letters (zIndex 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                Canvas(
                    modifier = Modifier
                        .size(240.dp, 100.dp)
                        .align(Alignment.Center)
                ) {
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
            }
        }
    }
}