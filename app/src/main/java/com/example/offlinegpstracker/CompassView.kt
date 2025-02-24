package com.example.offlinegpstracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun CompassView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
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

                    // Reverse azimuth direction to correct W and E flip
                    azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat() * -1
                    pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
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

    Box(
        modifier = modifier
            .size(250.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Direction Letters with Highlighting
        DirectionLetters(azimuth)

        // Rotating compass image
        Image(
            painter = painterResource(id = R.drawable.compass_ship),
            contentDescription = "Compass",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    rotationZ = azimuth.roundToInt().toFloat(),
                    rotationX = -pitch  // Use pitch to tilt the image
                )
        )
    }
}

@Composable
fun DirectionLetters(azimuth: Float) {
    val activeDirection = getActiveDirection(azimuth)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // N and S stay in the middle
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextDirection("N", "N" == activeDirection)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // W and E stay aligned in a row
                TextDirection("W", "W" == activeDirection)
                Spacer(modifier = Modifier.width(40.dp))
                TextDirection("E", "E" == activeDirection)
            }
            TextDirection("S", "S" == activeDirection)
        }

        // Diagonal letters (NE, NW, SE, SW)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            TextDirectionRotated("NW", "NW" == activeDirection, -45f, Modifier.offset(x = (-80).dp, y = (-80).dp))
            TextDirectionRotated("NE", "NE" == activeDirection, 45f, Modifier.offset(x = 80.dp, y = (-80).dp))
            TextDirectionRotated("SW", "SW" == activeDirection, 45f, Modifier.offset(x = (-80).dp, y = 80.dp))
            TextDirectionRotated("SE", "SE" == activeDirection, -45f, Modifier.offset(x = 80.dp, y = 80.dp))
        }
    }
}

// Standard (non-rotated) direction letters
@Composable
fun TextDirection(letter: String, isActive: Boolean) {
    Text(
        text = letter,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = if (isActive) Color.Red else Color.Black
    )
}

// Rotated direction letters (for NW, NE, SW, SE)
@Composable
fun TextDirectionRotated(letter: String, isActive: Boolean, rotation: Float, modifier: Modifier) {
    Text(
        text = letter,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = if (isActive) Color.Red else Color.Black,
        modifier = modifier.graphicsLayer(rotationZ = rotation)
    )
}

fun getActiveDirection(azimuth: Float): String {
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
