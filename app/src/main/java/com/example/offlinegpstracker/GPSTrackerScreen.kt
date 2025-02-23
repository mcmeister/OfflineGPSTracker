package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LocationInfoChip(Icons.Filled.LocationOn, formatCoordinate(latitude), Color.Blue, Modifier.weight(1f))
        LocationInfoChip(Icons.Filled.LocationOn, formatCoordinate(longitude), Color.Blue, Modifier.weight(1f))
        LocationInfoChip(Icons.Filled.Terrain, "$altitude m", Color(139, 69, 19), Modifier.weight(1f))
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
        // border = BorderStroke(1.dp, Color.Black) // Added black border
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor, // Colorful icons
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                fontSize = 12.sp, // Adjusted text size
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Function to format latitude/longitude to 5 decimal places
fun formatCoordinate(value: String): String {
    return try {
        "%.5f".format(value.toDoubleOrNull() ?: 0.0)
    } catch (e: NumberFormatException) {
        value
    }
}

@Composable
fun GPSTrackerScreen(locationViewModel: LocationViewModel = viewModel()) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    val latitude by locationViewModel.latitude.observeAsState("")
    val longitude by locationViewModel.longitude.observeAsState("")
    val altitude by locationViewModel.altitude.observeAsState("")

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Makes screen scrollable
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Amazon Jungle",
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        CompassView(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .align(Alignment.CenterHorizontally)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Location Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        LocationInfoChipRow(latitude = latitude, longitude = longitude, altitude = altitude)

        Spacer(modifier = Modifier.height(16.dp))

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
