package com.example.offlinegpstracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
        sensorManager.registerListener(sensorEventListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val extendedDirections = List(3) { directions }.flatten() // Duplicate the list for smooth looping
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Calculate the index for the centered position based on azimuth
    val indexOffset = 8 // This controls how far to the right we start (middle of extended list)
    val currentIndex = (azimuth / 45).roundToInt().mod(8) + indexOffset

    LaunchedEffect(azimuth) {
        coroutineScope.launch {
            delay(50) // Slight delay to ensure smooth scrolling
            lazyListState.animateScrollToItem(currentIndex)
        }
    }

    LazyRow(
        state = lazyListState,
        modifier = Modifier
    ) {
        itemsIndexed(extendedDirections) { index, direction ->
            Row {
                Text(
                    text = direction,
                    fontSize = 32.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = if (index == currentIndex) Color.Red else Color.Black
                )
                if (index < extendedDirections.size - 1) {
                    SeparatorLines() // Add separator between items
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
