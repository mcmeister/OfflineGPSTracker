package com.example.offlinegpstracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.io.FileOutputStream

@Composable
fun LocationDetailsScreen(
    navController: NavHostController,
    locationViewModel: LocationViewModel,
    startIndex: Int = 0
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val locations by locationViewModel.locations.collectAsStateWithLifecycle(lifecycleOwner.lifecycle)
    val context = LocalContext.current

    // Ensure rememberPagerState is correctly defined
    val pagerState = rememberPagerState { locations.size }

    LaunchedEffect(startIndex) {
        if (locations.isNotEmpty()) {
            pagerState.scrollToPage(startIndex.coerceIn(0, locations.size - 1))
        }
    }

    if (locations.isNotEmpty()) {
        HorizontalPager(
            state = pagerState
        ) { page ->
            val location = locations[page]
            var name by remember { mutableStateOf(TextFieldValue(location.name)) }
            var latitude by remember { mutableStateOf(TextFieldValue(location.latitude.toString())) }
            var longitude by remember { mutableStateOf(TextFieldValue(location.longitude.toString())) }
            var altitude by remember { mutableStateOf(TextFieldValue(location.altitude.toString())) }
            var photoPaths by remember { mutableStateOf(location.photoPaths) }
            rememberCoroutineScope()

            val imagePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetMultipleContents()
            ) { uris: List<Uri>? ->
                uris?.let {
                    val newPaths = it.mapNotNull { uri -> saveImageToInternalStorage(context, uri) }
                    photoPaths = (photoPaths + newPaths).distinct().take(4) // Store up to 4 photos
                    locationViewModel.updateLocationPhoto(location.id, photoPaths) // ✅ No Gson() needed!
                }
            }

            LaunchedEffect(location.id) { // Trigger update when location ID changes
                name = TextFieldValue(location.name)
                latitude = TextFieldValue(location.latitude.toString())
                longitude = TextFieldValue(location.longitude.toString())
                altitude = TextFieldValue(location.altitude.toString())
                photoPaths = location.photoPaths.toList() // Ensure it's a fresh list copy
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()), // Makes screen scrollable
                verticalArrangement = Arrangement.Top
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

                if (photoPaths.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(photoPaths[0]),
                        contentDescription = "Location Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val placeholders = 4 - photoPaths.size
                        photoPaths.forEach { path ->
                            Image(
                                painter = rememberAsyncImagePainter(path),
                                contentDescription = "Location Photo",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { /* Optionally open full-size view */ }
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
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Text("Add Photo")
                        }

                        Button(onClick = {
                            if (photoPaths.isNotEmpty()) {
                                downloadImage(context, photoPaths[0]) // Download the first image
                            } else {
                                Toast.makeText(context, "No images to download", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Download Photo")
                        }

                        Button(onClick = {
                            if (photoPaths.isNotEmpty()) {
                                photoPaths = emptyList()
                                locationViewModel.updateLocationPhoto(location.id, emptyList())
                                Toast.makeText(context, "All photos removed", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Remove All Photos")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    val updatedLocation = location.copy(
                        name = name.text,
                        latitude = latitude.text.toDoubleOrNull() ?: 0.0,
                        longitude = longitude.text.toDoubleOrNull() ?: 0.0,
                        altitude = altitude.text.toDoubleOrNull() ?: 0.0,
                        photoPaths = photoPaths // Updated to list of photos
                    )
                    locationViewModel.updateLocation(updatedLocation)
                    Toast.makeText(context, "Location updated", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Save")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    locationViewModel.updateLocation(location.copy(status = "deleted"))
                    Toast.makeText(context, "Location flagged as deleted", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
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
                    navController.navigate("navigator/${location.id}/${location.name}")
                }) {
                    Text("Navigate to Location")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    navController.popBackStack()
                }) {
                    Text("Back")
                }
            }
        }
    }
}

fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "location_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        return file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun downloadImage(context: Context, filePath: String) {
    try {
        val file = File(filePath)
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val newFile = File(downloadDir, file.name)

        file.copyTo(newFile, overwrite = true)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", newFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)

        Toast.makeText(context, "Photo downloaded to Downloads folder", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to download photo", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
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
