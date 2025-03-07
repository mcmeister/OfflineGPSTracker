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
import androidx.compose.ui.draw.drawWithContent
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
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val currentIndex = directions.indexOf(getActiveDirectionRound(azimuth))
    return directions[(currentIndex + 1) % directions.size]
}

fun getPreviousDirection(azimuth: Float): String {
    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val currentIndex = directions.indexOf(getActiveDirectionRound(azimuth))
    return directions[(currentIndex - 1 + directions.size) % directions.size]
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun CompassViewRound(modifier: Modifier = Modifier, azimuth: Float) {
    // Get the CompassViewModel (ensure CompassViewModel extends ViewModel)
    val viewModel = viewModel<CompassViewModel>()

    var smoothedAzimuth by remember { mutableFloatStateOf(0f) }
    val alpha = 0.2f

    // Smooth azimuth transition
    LaunchedEffect(azimuth) {
        smoothedAzimuth = alpha * azimuth + (1 - alpha) * smoothedAzimuth
    }

    val animatedAzimuth by animateFloatAsState(
        targetValue = smoothedAzimuth,
        animationSpec = tween(durationMillis = 300),
        label = ""
    )

    val currentDirection = getActiveDirectionRound(animatedAzimuth)
    val nextDirection = getNextDirection(animatedAzimuth)
    val prevDirection = getPreviousDirection(animatedAzimuth)

    val transition = rememberInfiniteTransition(label = "letter_slide")
    val sideOffset by transition.animateFloat(
        initialValue = 20f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "side_letter_move"
    )

    // Directly assign values (they are observable mutable state)
    val lightX = viewModel.lightX
    val lightY = viewModel.lightY

    // Get the context outside of DisposableEffect to avoid calling composable functions in a non-composable lambda.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        viewModel.initialize(context)
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    // 1) The outer Box fills the entire screen and centers the gauge box.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // 2) The gauge box: fill some fraction of width or a fixed width, plus a fixed height.
        //    **Add contentAlignment = Alignment.Center** here so children are centered.
        Box(
            modifier = modifier
                .fillMaxWidth(0.8f)
                .height(120.dp),
            contentAlignment = Alignment.Center // <-- CRITICAL CHANGE
        ) {
            // --- GAUGE BACKGROUND LAYER ---
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
                    .drawWithContent {
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.05f),
                            radius = 10.dp.toPx(),
                            center = Offset(50f, 50f)
                        )
                    }
            )

            // --- METALLIC OVERLAY LAYER ---
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
                    .drawWithContent {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.03f),
                            start = Offset(30f, 20f),
                            end = Offset(50f, 40f),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color.Black.copy(alpha = 0.03f),
                            start = Offset(250f, 80f),
                            end = Offset(270f, 100f),
                            strokeWidth = 1f
                        )
                    }
            )

            // --- GLASS COVER (TRANSPARENT) ---
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
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

            // --- GAUGE ELEMENTS (TICKS, LETTERS) ON TOP ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                Canvas(
                    modifier = Modifier
                        .size(280.dp, 100.dp)
                        .align(Alignment.Center)
                ) {
                    val tickLength = 10.dp.toPx()
                    val majorTickLength = 20.dp.toPx()
                    val tickColor = Color.White

                    for (i in 0 until 360 step 10) {
                        val angleRad = Math.toRadians(i.toDouble())
                        val startLength = if (i % 90 == 0) majorTickLength else tickLength

                        val startX = (size.width / 2 + cos(angleRad) * (size.width / 2 - startLength)).toFloat()
                        val startY = (size.height / 2 + sin(angleRad) * (size.height / 2 - startLength)).toFloat()
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

                // Centered text for previous, next, and current directions.
                Box(modifier = Modifier.fillMaxSize()) {
                    // Previous direction, moving left from the center
                    Text(
                        text = prevDirection,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF9C4),
                        modifier = Modifier.align(Alignment.Center).offset(x = -sideOffset.dp)
                    )
                    // Next direction, moving right from the center
                    Text(
                        text = nextDirection,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF9C4),
                        modifier = Modifier.align(Alignment.Center).offset(x = sideOffset.dp)
                    )
                    // Current direction (centered both horizontally and vertically)
                    Text(
                        text = currentDirection,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}