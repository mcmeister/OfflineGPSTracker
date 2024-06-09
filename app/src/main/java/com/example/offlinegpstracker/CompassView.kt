package com.example.offlinegpstracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext

@Composable
fun CompassView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }
    val isDarkTheme = isSystemInDarkTheme()

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

    Canvas(modifier = modifier) {
        rotate(-azimuth) {
            drawLine(Color.Red, start = center, end = center.copy(y = 0f), strokeWidth = 8f)
        }

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                color = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                textSize = 48f
                textAlign = android.graphics.Paint.Align.CENTER
            }

            val centerX = size.width / 2
            val centerY = size.height / 2

            canvas.nativeCanvas.drawText("N", centerX, centerY - centerY + 50, paint)
            canvas.nativeCanvas.drawText("S", centerX, centerY + centerY - 20, paint)
            canvas.nativeCanvas.drawText("E", centerX + centerX - 40, centerY + 20, paint)
            canvas.nativeCanvas.drawText("W", centerX - centerX + 40, centerY + 20, paint)
        }
    }
}
