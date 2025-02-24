package com.example.offlinegpstracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
fun HorizontalCompassView() {
    val context = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val sensorEventListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    DisposableEffect(Unit) {
        sensorManager.registerListener(
            sensorEventListener,
            rotationVectorSensor,
            SensorManager.SENSOR_DELAY_UI
        )
        onDispose { sensorManager.unregisterListener(sensorEventListener) }
    }

    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    // Use getActiveDirection() to compute the active direction.
    val currentDirection = getHorizontalActiveDirection(azimuth)
    // Look up the base index for that direction.
    val baseIndex = directions.indexOf(currentDirection)
    val indexOffset = 8 // Controls how far to the right we start (middle of extended list)
    val currentIndex = baseIndex + indexOffset

    val extendedDirections = List(3) { directions }.flatten() // Duplicate for smooth looping
    val lazyListState = rememberLazyListState()
    var itemWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    LaunchedEffect(currentIndex) {
        // Observe changes to the viewport width
        snapshotFlow {
            lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
        }.collect { viewportWidth ->
            if (itemWidthPx > 0 && viewportWidth > 0) {
                // Calculate extra padding (20.dp on each side = 40.dp total)
                val extraPaddingPx = density.run { 40.dp.toPx() }
                val adjustedItemWidth = itemWidthPx - extraPaddingPx
                val centerOffset = (viewportWidth / 2) - (adjustedItemWidth / 2)
                lazyListState.animateScrollToItem(
                    index = currentIndex,
                    scrollOffset = centerOffset.toInt()
                )
            }
        }
    }

    LazyRow(
        state = lazyListState,
        userScrollEnabled = false, // disable manual scrolling
        modifier = Modifier.fillMaxWidth(), // span full width for proper centering
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(extendedDirections) { index, direction ->
            Row {
                Text(
                    text = direction,
                    fontSize = 32.sp,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .then(
                            if (index == currentIndex) Modifier.onGloballyPositioned { coordinates ->
                                val newWidth = coordinates.size.width
                                if (itemWidthPx != newWidth) {
                                    itemWidthPx = newWidth
                                }
                            } else Modifier
                        ),
                    color = if (index == currentIndex) Color.Red else Color.Black
                )
                if (index < extendedDirections.size - 1) {
                    SeparatorLines() // Separator remains unchanged
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
