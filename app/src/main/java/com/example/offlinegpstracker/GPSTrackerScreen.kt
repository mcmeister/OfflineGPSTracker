package com.example.offlinegpstracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.livedata.observeAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
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

        OutlinedTextField(
            value = TextFieldValue(latitude),
            onValueChange = {},
            label = { Text("Latitude") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal),
            readOnly = true
        )

        OutlinedTextField(
            value = TextFieldValue(longitude),
            onValueChange = {},
            label = { Text("Longitude") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal),
            readOnly = true
        )

        OutlinedTextField(
            value = TextFieldValue(altitude),
            onValueChange = {},
            label = { Text("Altitude") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal),
            readOnly = true
        )

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
                saveLocation(context, name.text, latitude, longitude, altitude, locationViewModel)
            }) {
                Text("Save")
            }
        }
    }
}

private fun generateLocationName(): String {
    val timestamp = System.currentTimeMillis()
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val formattedTimestamp = sdf.format(Date(timestamp))
    return "Location at $formattedTimestamp"
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
