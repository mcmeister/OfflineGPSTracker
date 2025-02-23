package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("DefaultLocale")
@Composable
fun LocationInfoCard(label: String, value: String, isAltitude: Boolean = false) {
    val displayValue = when {
        value.isEmpty() -> "Initializing..."
        isAltitude -> "${String.format("%.2f", value.toFloatOrNull() ?: 0.0)} m"
        else -> value
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = displayValue, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun GPSTrackerScreen(locationViewModel: LocationViewModel = viewModel()) {
    var name by remember { mutableStateOf(TextFieldValue("")) }
    val latitude by locationViewModel.latitude.observeAsState("")
    val longitude by locationViewModel.longitude.observeAsState("")
    val altitude by locationViewModel.altitude.observeAsState("")

    val context = LocalContext.current
    var photoPaths by remember { mutableStateOf<List<String>>(emptyList()) } // ✅ Now using List<String>

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri>? ->
        uris?.let {
            val newPaths = it.mapNotNull { uri -> saveImageToInternalStorage(context, uri) }
            photoPaths = (photoPaths + newPaths).distinct().take(4) // ✅ Store up to 4 photos as List<String>
        }
    }

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

        LocationInfoCard(label = "Latitude", value = latitude)
        LocationInfoCard(label = "Longitude", value = longitude)
        LocationInfoCard(label = "Altitude", value = altitude, isAltitude = true)

        Spacer(modifier = Modifier.height(16.dp))

        // Image Previews Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val placeholders = 4 - photoPaths.size
            photoPaths.forEach { path ->
                Image(
                    painter = rememberAsyncImagePainter(path),
                    contentDescription = "Selected Image",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { /* Optionally remove or replace image */ }
                )
            }
            repeat(placeholders) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_input_add),
                        contentDescription = "Add Photo",
                        tint = Color.Gray
                    )
                }
            }
        }

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
                    photoPaths, // ✅ Now passing List<String> directly
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
    photoPaths: List<String>, // ✅ Now using List<String>
    locationViewModel: LocationViewModel
) {
    if (latitude.isNotEmpty() && longitude.isNotEmpty()) {
        val location = Location(
            name = name,
            latitude = latitude.toDouble(),
            longitude = longitude.toDouble(),
            altitude = altitude.toDoubleOrNull() ?: 0.0,
            photoPaths = photoPaths // ✅ Room automatically converts this using Converters.kt
        )
        locationViewModel.saveLocation(location)
        Toast.makeText(context, "Location saved with ${photoPaths.size} photos", Toast.LENGTH_SHORT).show()
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
