package com.example.offlinegpstracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
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

    val userPreferences = remember { UserPreferences(context) }
    val compassTypeFlow = remember { userPreferences.compassType }
    val skinPrefFlow = remember { userPreferences.compassSkin }

    val compassType by compassTypeFlow.collectAsState(initial = 0)
    val skinPref by skinPrefFlow.collectAsState(initial = UserPreferences.SKIN_CLASSIC_GAUGE)

    val isGauge = (compassType == 2)

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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top
            ) {
                // Location Name Field
                StyledOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Location Name",
                    isGauge = isGauge,
                    skinPref = skinPref
                )

                // Latitude Field
                StyledOutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = "Latitude",
                    keyboardType = KeyboardType.Decimal,
                    isGauge = isGauge,
                    skinPref = skinPref
                )

                // Longitude Field
                StyledOutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = "Longitude",
                    keyboardType = KeyboardType.Decimal,
                    isGauge = isGauge,
                    skinPref = skinPref
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (photoPaths.isNotEmpty()) {
                    // Display First Image in Full Width
                    Image(
                        painter = rememberAsyncImagePainter(photoPaths[0]),
                        contentDescription = "Location Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(8.dp)
                    )

                    // Display Row of Thumbnails + Empty Slots for New Photos
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
                }

                // --- Buttons Always Displayed ---
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ThemedButton(
                        text = "Add Photo",
                        onClick = { imagePickerLauncher.launch("image/*") },
                        isGauge = isGauge,
                        skinPref = skinPref
                    )

                    ThemedButton(
                        text = "Download Photo",
                        onClick = {
                            if (photoPaths.isNotEmpty()) {
                                downloadImage(context, photoPaths[0])
                            } else {
                                Toast.makeText(
                                    context,
                                    "No images to download",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        isGauge = isGauge,
                        skinPref = skinPref
                    )

                    ThemedButton(
                        text = "Remove All Photos",
                        onClick = {
                            if (photoPaths.isNotEmpty()) {
                                photoPaths = emptyList()
                                locationViewModel.updateLocationPhoto(location.id, emptyList())
                                Toast.makeText(
                                    context,
                                    "All photos removed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        isGauge = isGauge,
                        skinPref = skinPref
                    )
                }

                // --- Remaining Buttons ---
                Spacer(modifier = Modifier.height(16.dp))

                ThemedButton(
                    text = "Save",
                    onClick = {
                        val updatedLocation = location.copy(
                            name = name.text,
                            latitude = latitude.text.toDoubleOrNull() ?: 0.0,
                            longitude = longitude.text.toDoubleOrNull() ?: 0.0,
                            altitude = 0.0, // You can add altitude handling
                            photoPaths = photoPaths
                        )
                        locationViewModel.updateLocation(updatedLocation)
                        Toast.makeText(context, "Location updated", Toast.LENGTH_SHORT).show()
                    },
                    isGauge = isGauge,
                    skinPref = skinPref
                )

                Spacer(modifier = Modifier.height(16.dp))

                ThemedButton(
                    text = "Delete",
                    onClick = {
                        locationViewModel.updateLocation(location.copy(status = "deleted"))
                        Toast.makeText(
                            context,
                            "Location flagged as deleted",
                            Toast.LENGTH_SHORT
                        ).show()
                        navController.popBackStack()
                    },
                    isGauge = isGauge,
                    skinPref = skinPref
                )

                Spacer(modifier = Modifier.height(16.dp))

                ThemedButton(
                    text = "Share",
                    onClick = { shareLocationDetails(context, latitude.text, longitude.text) },
                    isGauge = isGauge,
                    skinPref = skinPref
                )

                Spacer(modifier = Modifier.height(16.dp))

                ThemedButton(
                    text = "Navigate to Location",
                    onClick = { navController.navigate("navigator/${location.id}/${location.name}") },
                    isGauge = isGauge,
                    skinPref = skinPref
                )

                Spacer(modifier = Modifier.height(16.dp))

                ThemedButton(
                    text = "Back",
                    onClick = { navController.popBackStack() },
                    isGauge = isGauge,
                    skinPref = skinPref
                )
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

@Composable
fun StyledOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isGauge: Boolean,
    skinPref: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isGauge && skinPref == UserPreferences.SKIN_NEON_GAUGE) {
                    Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                } else Modifier
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(
                    label,
                    color = getLabelColor(skinPref, isGauge)
                )
            },
            placeholder = {
                Text(
                    "Enter $label",
                    fontSize = 12.sp,
                    color = getLabelColor(skinPref, isGauge).copy(alpha = 0.5f)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = keyboardType),
            colors = getTextFieldColors(skinPref, isGauge)
        )
    }
}

// Function to determine label color based on skin
@Composable
fun getLabelColor(skinPref: Int, isGauge: Boolean): Color {
    return if (!isGauge) {
        Color.Black // Always black if not gauge
    } else {
        when (skinPref) {
            UserPreferences.SKIN_NEON_GAUGE -> Color.Cyan
            UserPreferences.SKIN_MINIMAL_GAUGE -> Color.White
            else -> Color.Black // Classic or No skin
        }
    }
}

// Function to get the correct colors based on the selected skin
@Composable
fun getTextFieldColors(skinPref: Int, isGauge: Boolean): TextFieldColors {
    return if (!isGauge) {
        // Non-gauge: White background, black text, default Material UI
        TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            cursorColor = Color.Black,
            focusedIndicatorColor = Color.Gray,
            unfocusedIndicatorColor = Color.Gray,
            focusedLabelColor = Color.Black,
            unfocusedLabelColor = Color.Black
        )
    } else {
        // Gauge: Apply skin-based styles
        when (skinPref) {
            UserPreferences.SKIN_CLASSIC_GAUGE -> TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                cursorColor = Color.Black,
                focusedIndicatorColor = Color(0xFF546E7A),
                unfocusedIndicatorColor = Color(0xFF546E7A),
                focusedLabelColor = Color.Black,
                unfocusedLabelColor = Color.Black
            )

            UserPreferences.SKIN_NEON_GAUGE -> TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.Cyan,
                unfocusedTextColor = Color.Cyan,
                cursorColor = Color.Cyan,
                focusedIndicatorColor = Color.Cyan,
                unfocusedIndicatorColor = Color.Cyan,
                focusedLabelColor = Color.Cyan,
                unfocusedLabelColor = Color.Cyan
            )

            UserPreferences.SKIN_MINIMAL_GAUGE -> TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White
            )

            else -> TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                cursorColor = Color.Black,
                focusedIndicatorColor = Color.Gray,
                unfocusedIndicatorColor = Color.Gray,
                focusedLabelColor = Color.Black,
                unfocusedLabelColor = Color.Black
            )
        }
    }
}

@Composable
fun ThemedButton(
    text: String,
    onClick: () -> Unit,
    isGauge: Boolean,
    skinPref: Int
) {
    if (isGauge) {
        when (skinPref) {
            UserPreferences.SKIN_CLASSIC_GAUGE -> {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Black
                    ),
                    border = BorderStroke(1.dp, Color(0xFF546E7A))
                ) {
                    Text(text)
                }
            }

            UserPreferences.SKIN_NEON_GAUGE -> {
                OutlinedButton(
                    onClick = onClick,
                    border = BorderStroke(1.dp, Color.Cyan),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.Cyan
                    )
                ) {
                    Text(text)
                }
            }

            UserPreferences.SKIN_MINIMAL_GAUGE -> {
                OutlinedButton(
                    onClick = onClick,
                    border = BorderStroke(1.dp, Color.Transparent),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    )
                ) {
                    Text(text)
                }
            }
        }
    } else {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3F51B5), // Default Material Blue
                contentColor = Color.White
            )
        ) {
            Text(text)
        }
    }
}