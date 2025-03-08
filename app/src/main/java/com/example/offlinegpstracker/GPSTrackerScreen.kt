package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("DefaultLocale")
@Composable
fun LocationInfoChipRow(latitude: String, longitude: String, altitude: String) {
    // If latitude/longitude are empty, show "Initializing...", otherwise format them
    val displayedLatitude = if (latitude.isEmpty()) "Initializing..." else formatCoordinate(latitude)
    val displayedLongitude = if (longitude.isEmpty()) "Initializing..." else formatCoordinate(longitude)
    val displayedAltitude = if (altitude.isEmpty()) {
        "Initializing..."
    } else {
        try {
            "%.2f".format(altitude.toDouble())
        } catch (e: Exception) {
            altitude
        }
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp) // Space around the card
            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp)), // Border to mimic input field
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent) // Transparent background
    ) {
        Column(
            modifier = Modifier.padding(12.dp), // Inner padding inside the frame
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Labels Above Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("Latitude", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Longitude", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Elevation", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Chip Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LocationInfoChip(
                        icon = Icons.Filled.LocationOn,
                        value = displayedLatitude,
                        iconColor = Color.Blue
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LocationInfoChip(
                        icon = Icons.Filled.LocationOn,
                        value = displayedLongitude,
                        iconColor = Color.Blue
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LocationInfoChip(
                        icon = Icons.Filled.Terrain,
                        value = if (altitude.isEmpty()) displayedAltitude else "$displayedAltitude m",
                        iconColor = Color(139, 69, 19)
                    )
                }
            }
        }
    }
}

@Composable
fun LocationInfoChip(
    icon: ImageVector,
    value: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                fontSize = if (value == "Initializing...") 9.sp else 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Function to format latitude/longitude to 5 decimal places
fun formatCoordinate(value: String): String {
    return try {
        "%.4f".format(value.toDoubleOrNull() ?: 0.0)
    } catch (e: NumberFormatException) {
        value
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun GPSTrackerScreen(locationViewModel: LocationViewModel = viewModel()) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    val latitude by locationViewModel.latitude.observeAsState("")
    val longitude by locationViewModel.longitude.observeAsState("")
    val altitude by locationViewModel.altitude.observeAsState("")

    val context = LocalContext.current

    // Sensor setup
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rotationVectorSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var azimuth by remember { mutableFloatStateOf(0f) }

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

    // Calculate current direction
    val currentDirection = getHorizontalActiveDirection(azimuth)

    val userPreferences = remember { UserPreferences(context) }

    // Track whether DataStore has finished retrieving stored state
    var isCompassTypeLoaded by remember { mutableStateOf(false) }

    // Flow-backed state for persisted compass selection
    val storedCompassType by userPreferences.compassType.collectAsState(initial = -1)

    // Mutable state for UI tracking
    var compassViewType by remember { mutableIntStateOf(0) }

    // Ensure stored value is applied **only once** after retrieval
    LaunchedEffect(storedCompassType) {
        if (storedCompassType != -1 && !isCompassTypeLoaded) {
            compassViewType = storedCompassType
            isCompassTypeLoaded = true
        }
    }

    // Track compass selection changes and save asynchronously
    LaunchedEffect(compassViewType) {
        if (isCompassTypeLoaded) {
            userPreferences.saveCompassType(compassViewType)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Amazon Jungle",
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Main Compass (Big Circular)
        if (isCompassTypeLoaded) { // Only show compass when DataStore has loaded
            Crossfade(targetState = compassViewType, label = "CompassSwitch") { viewType ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clickable(
                            onClick = { compassViewType = (compassViewType + 1) % 3 },
                            indication = null, // Disable ripple effect
                            interactionSource = remember { MutableInteractionSource() } // Required when setting indication
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (viewType) {
                        0 -> CompassView(modifier = Modifier.fillMaxSize())
                        1 -> CompassViewStatic(modifier = Modifier.fillMaxSize())
                        2 -> CompassViewGauge(azimuth = azimuth)
                    }
                }
            }
        } else {
            // Show loading indicator or placeholder until DataStore finishes loading
            Text("Loading compass...", modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Display Azimuth and Direction
        Text(
            text = "Azimuth: ${String.format("%.2f", azimuth)}°, Direction: $currentDirection",
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Location Info (Lat, Long, Alt)
        LocationInfoChipRow(latitude = latitude, longitude = longitude, altitude = altitude)

        Spacer(modifier = Modifier.height(24.dp))

        // Text Input Field
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Location Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons Row (Share & Save)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = {
                shareLocation(context, latitude, longitude)
            }) {
                Text("Share")
            }

            Button(onClick = {
                if (name.text.isEmpty()) {
                    name = TextFieldValue(generateLocationName())
                }
                saveLocation(
                    context,
                    name.text,
                    latitude,
                    longitude,
                    altitude,
                    locationViewModel
                )
            }) {
                Text("Save")
            }
        }
    }
}

private fun generateLocationName(): String {
    val timestamp = System.currentTimeMillis()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return "Location at ${sdf.format(Date(timestamp))}"
}

private fun saveLocation(
    context: Context,
    name: String,
    latitude: String,
    longitude: String,
    altitude: String,
    locationViewModel: LocationViewModel
) {
    if (latitude.isNotEmpty() && longitude.isNotEmpty()) {
        val location = Location(
            name = name,
            latitude = latitude.toDouble(),
            longitude = longitude.toDouble(),
            altitude = altitude.toDoubleOrNull() ?: 0.0
        )
        locationViewModel.saveLocation(location)
        Toast.makeText(context, "Location saved", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Latitude and Longitude are required", Toast.LENGTH_SHORT).show()
    }
}

private fun shareLocation(context: Context, latitude: String, longitude: String) {
    if (latitude.isNotEmpty() && longitude.isNotEmpty()) {
        val uri = Uri.parse("geo:0,0?q=$latitude,$longitude(Location)")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Google Maps app is not installed", Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, "Location data is missing", Toast.LENGTH_SHORT).show()
    }
}
