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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("DefaultLocale")
@Composable
fun LocationInfoChipRow(
    latitude: String,
    longitude: String,
    altitude: String,
    textColor: Color, // passed from GPSTrackerScreen
    skin: Int         // one of UserPreferences.SKIN_CLASSIC, SKIN_NEON, SKIN_MINIMAL
) {
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

    // Select a modifier for the card based on the skin:
    val cardModifier = when (skin) {
        UserPreferences.SKIN_CLASSIC ->
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(Color.Transparent)
                .border(1.dp, Color(0xFF546E7A), RoundedCornerShape(4.dp))

        UserPreferences.SKIN_NEON ->
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                .border(1.dp, Color.Cyan, RoundedCornerShape(4.dp))

        UserPreferences.SKIN_MINIMAL ->
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(1.dp, Color.Black, RoundedCornerShape(4.dp))

        else -> // default for non-gauge screens
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
    }

    OutlinedCard(
        modifier = cardModifier,
        shape = RoundedCornerShape(4.dp),
        // For Classic the gradient is already applied via the background;
        // for Neon and Minimal the container remains transparent.
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Labels Above Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("Latitude", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text("Longitude", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text("Elevation", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
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
                        iconColor = Color.Blue,
                        textColor = textColor // pass the color
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LocationInfoChip(
                        icon = Icons.Filled.LocationOn,
                        value = displayedLongitude,
                        iconColor = Color.Blue,
                        textColor = textColor
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LocationInfoChip(
                        icon = Icons.Filled.Terrain,
                        value = if (altitude.isEmpty()) displayedAltitude else "$displayedAltitude m",
                        iconColor = Color(139, 69, 19),
                        textColor = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun LocationInfoChip(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    iconColor: Color,
    textColor: Color = Color.Unspecified // new parameter to style chip text
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
                fontWeight = FontWeight.Medium,
                color = textColor
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
    var name by remember { mutableStateOf("") }
    val latitude by locationViewModel.latitude.observeAsState("")
    val longitude by locationViewModel.longitude.observeAsState("")
    val altitude by locationViewModel.altitude.observeAsState("")

    val context = LocalContext.current

    // Sensor setup
    val sensorManager =
        remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val rotationVectorSensor =
        remember { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
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

    val userPreferences = remember { UserPreferences(context) }

    // Track whether DataStore has finished retrieving stored state
    var isCompassTypeLoaded by remember { mutableStateOf(false) }

    // Flow-backed state for persisted compass selection
    val storedCompassType by userPreferences.compassType.collectAsState(initial = -1)
    val storedCompassSkin by userPreferences.compassSkin.collectAsState(initial = UserPreferences.SKIN_CLASSIC)

    // Mutable state for UI tracking
    var compassViewType by remember { mutableIntStateOf(storedCompassType) }

    // Ensure stored value is applied only once after retrieval
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

    val coroutineScope = rememberCoroutineScope()

    val isGauge = (compassViewType == 2)
    val currentSkin = if (isGauge) storedCompassSkin else UserPreferences.NO_SKIN

    val screenModifier = Modifier
        .fillMaxSize()
        .then(
            when {
                isGauge && currentSkin == UserPreferences.SKIN_CLASSIC -> Modifier.background(Color.Transparent) // Handled by Box with Image
                isGauge && currentSkin == UserPreferences.SKIN_NEON -> Modifier.background(Color.Transparent) // Background handled by Image
                isGauge && currentSkin == UserPreferences.SKIN_MINIMAL -> Modifier.background(Color.Transparent)
                else -> Modifier.background(MaterialTheme.colorScheme.background)
            }
        )
        .verticalScroll(rememberScrollState())
        .padding(16.dp)

    val textColor = if (isGauge) {
        when (currentSkin) {
            UserPreferences.SKIN_CLASSIC -> Color.Black
            UserPreferences.SKIN_NEON -> Color.Cyan
            UserPreferences.SKIN_MINIMAL -> Color.White
            else -> Color.Black
        }
    } else MaterialTheme.colorScheme.onBackground

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = screenModifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Title
            Box(
                modifier = Modifier
                    .then(
                        if (isGauge && currentSkin == UserPreferences.SKIN_NEON) {
                            Modifier
                                .border(1.dp, Color.Cyan, RoundedCornerShape(12.dp)) // ✅ Border first!
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp) // ✅ Increased padding to match border size
                        } else {
                            Modifier
                                .border(1.dp, Color.Transparent, RoundedCornerShape(12.dp)) // ✅ Border first!
                                .background(Color.Transparent, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp) // ✅ Increased padding to match border size
                        }
                    )
            ) {
                Text(
                    text = "Amazon Jungle",
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor,
                    modifier = Modifier
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Compass block (the tap logic remains unchanged)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                compassViewType = (compassViewType + 1) % 3
                            },
                            onTap = {
                                if (compassViewType == 2) { // Only in Gauge view
                                    val newSkin = (storedCompassSkin + 1) % 3
                                    coroutineScope.launch {
                                        userPreferences.saveCompassSkin(newSkin)
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isCompassTypeLoaded) {
                    Crossfade(targetState = compassViewType, label = "CompassSwitch") { viewType ->
                        when (viewType) {
                            0 -> CompassView(modifier = Modifier.fillMaxSize())
                            1 -> CompassViewStatic(modifier = Modifier.fillMaxSize())
                            2 -> CompassViewGauge(azimuth = azimuth)
                        }
                    }
                } else {
                    Text(
                        "Loading compass...",
                        color = if (isGauge) textColor else Color.Unspecified
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Azimuth text
            Box(
                modifier = Modifier
                    .then(
                        if (isGauge && currentSkin == UserPreferences.SKIN_NEON) {
                            Modifier
                                .border(1.dp, Color.Cyan, RoundedCornerShape(12.dp)) // ✅ Border first!
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp) // ✅ Increased padding to match border size
                        } else {
                            Modifier
                                .border(1.dp, Color.Transparent, RoundedCornerShape(12.dp)) // ✅ Border first!
                                .background(Color.Transparent, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp) // ✅ Increased padding to match border size
                        }
                    )
            ) {
                Text(
                    text = "Azimuth: ${String.format("%.0f", azimuth)}°",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isGauge) textColor else MaterialTheme.typography.bodyLarge.color
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Pass skin and textColor to the chip row
            LocationInfoChipRow(
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                textColor = if (isGauge) textColor else Color.Black, // default black text
                skin = if (isGauge) currentSkin else -1 // default skin indicator for no border
            )
            Spacer(modifier = Modifier.height(8.dp))

            // --- Adjusted Location Name input ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (currentSkin == UserPreferences.SKIN_NEON) {
                            Modifier.background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        } else Modifier
                    )
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { newValue -> name = newValue },
                    label = {
                        Text(
                            "Enter Location Name",
                            color = if (isGauge) textColor else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    placeholder = {
                        Text(
                            "Leave blank to auto-generate",
                            fontSize = 12.sp, // Smaller font size for subtlety
                            color = (if (isGauge) textColor else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = when (currentSkin) {
                        UserPreferences.SKIN_CLASSIC -> TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            cursorColor = textColor,
                            focusedIndicatorColor = Color(0xFF546E7A),
                            unfocusedIndicatorColor = Color(0xFF546E7A),
                            focusedLabelColor = textColor,
                            unfocusedLabelColor = textColor
                        )

                        UserPreferences.SKIN_NEON -> TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            cursorColor = textColor,
                            focusedIndicatorColor = Color.Cyan,
                            unfocusedIndicatorColor = Color.Cyan,
                            focusedLabelColor = textColor,
                            unfocusedLabelColor = textColor
                        )

                        UserPreferences.SKIN_MINIMAL -> TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            cursorColor = textColor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedLabelColor = textColor,
                            unfocusedLabelColor = textColor
                        )

                        else -> TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor,
                            cursorColor = textColor,
                            focusedIndicatorColor = Color.Gray,
                            unfocusedIndicatorColor = Color.Gray,
                            focusedLabelColor = textColor,
                            unfocusedLabelColor = textColor
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // --- Adjusted buttons ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isGauge) {
                    when (currentSkin) {
                        UserPreferences.SKIN_CLASSIC -> {
                            Button(
                                onClick = { shareLocation(context, latitude, longitude) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = textColor
                                ),
                                border = BorderStroke(1.dp, Color(0xFF546E7A))
                            ) {
                                Text("Share")
                            }
                            Button(
                                onClick = {
                                    if (name.isEmpty()) {
                                        name = generateLocationName()
                                    }
                                    saveLocation(
                                        context,
                                        name,
                                        latitude,
                                        longitude,
                                        altitude,
                                        locationViewModel
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = textColor
                                ),
                                border = BorderStroke(1.dp, Color.Black)
                            ) {
                                Text("Save")
                            }
                        }

                        UserPreferences.SKIN_NEON -> {
                            OutlinedButton(
                                onClick = { shareLocation(context, latitude, longitude) },
                                border = BorderStroke(1.dp, Color.Cyan),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.3f),
                                    contentColor = Color.Cyan
                                )
                            ) {
                                Text("Share")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (name.isEmpty()) {
                                        name = generateLocationName()
                                    }
                                    saveLocation(
                                        context,
                                        name,
                                        latitude,
                                        longitude,
                                        altitude,
                                        locationViewModel
                                    )
                                },
                                border = BorderStroke(1.dp, Color.Cyan),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.3f),
                                    contentColor = Color.Cyan
                                )
                            ) {
                                Text("Save")
                            }
                        }

                        UserPreferences.SKIN_MINIMAL -> {
                            TextButton(
                                onClick = { shareLocation(context, latitude, longitude) },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Text("Share")
                            }
                            TextButton(
                                onClick = {
                                    if (name.isEmpty()) {
                                        name = generateLocationName()
                                    }
                                    saveLocation(
                                        context,
                                        name,
                                        latitude,
                                        longitude,
                                        altitude,
                                        locationViewModel
                                    )
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                } else {
                    Button(onClick = {
                        shareLocation(context, latitude, longitude)
                    }) {
                        Text("Share")
                    }
                    Button(onClick = {
                        if (name.isEmpty()) {
                            name = generateLocationName()
                        }
                        saveLocation(
                            context,
                            name,
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
    }
}

private fun generateLocationName(): String {
    val timestamp = System.currentTimeMillis()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return "Location at ${sdf.format(Date(timestamp))}"
}

fun saveLocation(
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
