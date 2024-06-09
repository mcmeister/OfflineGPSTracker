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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun LocationDetailsScreen(
    navController: NavHostController,
    locationId: Int,
    locationViewModel: LocationViewModel
) {
    val location by locationViewModel.getLocationById(locationId).collectAsState(initial = null)
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var latitude by remember { mutableStateOf(TextFieldValue("")) }
    var longitude by remember { mutableStateOf(TextFieldValue("")) }
    var altitude by remember { mutableStateOf(TextFieldValue("")) }

    val context = LocalContext.current

    LaunchedEffect(location) {
        location?.let {
            name = TextFieldValue(it.name)
            latitude = TextFieldValue(it.latitude.toString())
            longitude = TextFieldValue(it.longitude.toString())
            altitude = TextFieldValue(it.altitude.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Location Name") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = latitude,
            onValueChange = { latitude = it },
            label = { Text("Latitude") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal)
        )

        OutlinedTextField(
            value = longitude,
            onValueChange = { longitude = it },
            label = { Text("Longitude") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal)
        )

        OutlinedTextField(
            value = altitude,
            onValueChange = { altitude = it },
            label = { Text("Altitude") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Decimal)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val updatedLocation = location?.copy(
                name = name.text,
                latitude = latitude.text.toDoubleOrNull() ?: 0.0,
                longitude = longitude.text.toDoubleOrNull() ?: 0.0,
                altitude = altitude.text.toDoubleOrNull() ?: 0.0
            )
            if (updatedLocation != null) {
                locationViewModel.updateLocation(updatedLocation)
                Toast.makeText(context, "Location updated", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }) {
            Text("Save")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            location?.let {
                val updatedLocation = it.copy(status = "deleted")
                locationViewModel.updateLocation(updatedLocation)
                Toast.makeText(context, "Location flagged as deleted", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }) {
            Text("Delete")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            shareLocationDetails(context, latitude.text, longitude.text)
        }) {
            Text("Share")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            navController.popBackStack()
        }) {
            Text("Back")
        }
    }
}

private fun shareLocationDetails(context: Context, latitude: String, longitude: String) {
    try {
        if (latitude.isNotEmpty() && longitude.isNotEmpty()) {
            val latitudeDouble = latitude.toDoubleOrNull()
            val longitudeDouble = longitude.toDoubleOrNull()

            if (latitudeDouble != null && longitudeDouble != null) {
                val uri = Uri.parse("geo:0,0?q=$latitudeDouble,$longitudeDouble(Location)")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "Google Maps app is not installed", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Invalid latitude or longitude", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Location data is missing", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "An error occurred while sharing the location", Toast.LENGTH_SHORT).show()
    }
}
