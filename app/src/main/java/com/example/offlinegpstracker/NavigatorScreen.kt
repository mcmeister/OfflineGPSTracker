package com.example.offlinegpstracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location as AndroidLocation
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable
fun NavigatorScreen(
    navController: NavHostController,
    locationViewModel: LocationViewModel,
    savedLocation: AndroidLocation,
    locationName: String
) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    var currentAzimuth by remember { mutableFloatStateOf(0f) }
    var currentLocation by remember { mutableStateOf<AndroidLocation?>(null) }
    var currentAltitude by remember { mutableDoubleStateOf(0.0) }
    var distance by remember { mutableDoubleStateOf(0.0) }
    var direction by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(Unit) {
        locationViewModel.startLocationUpdates()
        launch {
            locationViewModel.locationFlow.collect { location ->
                if (location != null) {
                    currentLocation = location
                    currentAltitude = location.altitude
                    distance = calculateDistance(
                        location.latitude,
                        location.longitude,
                        savedLocation.latitude,
                        savedLocation.longitude
                    )
                    direction = calculateBearing(
                        location.latitude,
                        location.longitude,
                        savedLocation.latitude,
                        savedLocation.longitude
                    )
                }
            }
        }
    }

    val sensorEventListener = remember {
        object : SensorEventListener {
            private val gravity = FloatArray(3)
            private val geomagnetic = FloatArray(3)
            private val R = FloatArray(9)
            private val I = FloatArray(9)

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, gravity, 0, event.values.size)
                    Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
                }
                if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(R, orientation)
                    currentAzimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
    }

    DisposableEffect(Unit) {
        sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = locationName,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))
        CompassArrow(direction, currentAzimuth)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Distance: ${distance.roundToInt()} meters",
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Current Altitude: ${currentAltitude.roundToInt()} meters",
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { navController.popBackStack() }) {
            Text("Back")
        }
    }
}

@Composable
fun CompassArrow(direction: Double, azimuth: Float) {
    val arrowColor = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = Modifier
            .size(200.dp)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)

            // Define the 3D arrow vertices
            val arrowTip = Offset(center.x, 20f)
            val leftBase = Offset(center.x - 50f, center.y + 50f)
            val rightBase = Offset(center.x + 50f, center.y + 50f)
            val leftShadowBase = Offset(center.x - 40f, center.y + 60f)
            val rightShadowBase = Offset(center.x + 40f, center.y + 60f)

            // Draw the shadow layer first
            rotate(degrees = azimuth - direction.toFloat(), pivot = center) {
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(arrowTip.x, arrowTip.y)
                        lineTo(leftShadowBase.x, leftShadowBase.y)
                        lineTo(rightShadowBase.x, rightShadowBase.y)
                        close()
                    },
                    color = arrowColor.copy(alpha = 0.3f) // lighter shadow
                )
            }

            // Draw the main arrow layer
            rotate(degrees = azimuth - direction.toFloat(), pivot = center) {
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(arrowTip.x, arrowTip.y)
                        lineTo(leftBase.x, leftBase.y)
                        lineTo(rightBase.x, rightBase.y)
                        close()
                    },
                    color = arrowColor
                )
            }
        }
    }
}

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}

fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(Math.toRadians(lat2))
    val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
            sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}
