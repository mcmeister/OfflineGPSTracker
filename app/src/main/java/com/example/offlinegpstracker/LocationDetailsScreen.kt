package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun LocationDetailsScreen(
    navController: NavHostController,
    locationViewModel: LocationViewModel,
    startIndex: Int = 0
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val locations by locationViewModel.locations.collectAsStateWithLifecycle(lifecycleOwner.lifecycle)
    val context  = LocalContext.current

    val readImagesPerm = if (Build.VERSION.SDK_INT >= 33)
        android.Manifest.permission.READ_MEDIA_IMAGES
    else
        android.Manifest.permission.READ_EXTERNAL_STORAGE

    var hasGalleryPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readImagesPerm) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val requestGalleryPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasGalleryPerm = granted }

    // ask once when the screen first opens
    LaunchedEffect(Unit) {
        if (!hasGalleryPerm) requestGalleryPerm.launch(readImagesPerm)
    }

    val pagerState = rememberPagerState { locations.size }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(startIndex) {
        if (locations.isNotEmpty())
            pagerState.scrollToPage(startIndex.coerceIn(0, locations.size - 1))
    }

    if (locations.isNotEmpty()) {
        HorizontalPager(state = pagerState) { page ->
            val location = locations[page]
            val routeRepository = (context.applicationContext as MyApplication).routeRepository
            val allRoutes      by routeRepository.getAllRoutes()
                .collectAsState(initial = emptyList())

            val nearbyRoutes = remember(location.id to allRoutes) {
                allRoutes.filter {
                    haversine(it.centerLat, it.centerLon, location.latitude, location.longitude) <= 3_000.0
                }
            }
            var name       by remember { mutableStateOf(TextFieldValue(location.name)) }
            val latitude   by remember { mutableStateOf(TextFieldValue(location.latitude.toString())) }
            val longitude  by remember { mutableStateOf(TextFieldValue(location.longitude.toString())) }
            var galleryImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

            LaunchedEffect(location.id, hasGalleryPerm) {
                galleryImages = if (hasGalleryPerm) {
                    fetchNearbyImages(
                        context  = context,
                        lat      = location.latitude,
                        lon      = location.longitude,
                        radiusM  = 1_000.0
                    ).take(3)
                } else {
                    emptyList()   // permission denied → show placeholders
                }
            }

            Column(                                     // top-level layout of the screen
                modifier = Modifier
                    .fillMaxSize()
                    // any tap not consumed by children will clear focus & hide keyboard
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { focusManager.clearFocus() }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
            ) {
                PlainTextField("Location Name", name, onValueChange = { name = it }, focusManager = focusManager)
                LatLonCard(lat = latitude.text, lon = longitude.text)

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            navController.navigate("navigator/${location.id}/${location.name}")
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Navigate to Location", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Filled.Navigation, null, Modifier.padding(start = 4.dp))
                }

                /* ─────── photo strip ─────── */
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { index ->
                        val uri = galleryImages.getOrNull(index)
                        Box(
                            Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color.Black, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(uri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    painterResource(R.drawable.ic_placeholder),
                                    null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            galleryImages.firstOrNull()?.let { uri ->
                                val view = Intent(Intent.ACTION_VIEW, uri).apply {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(view)
                            } ?: Toast.makeText(context, "No photos to open", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(80.dp)
                    ) {
                        Text("Go to\nGallery", fontSize = 12.sp)
                    }
                }
                /* ───────────────────────────────────────── */

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton("Delete") {
                        locationViewModel.updateLocation(location.copy(status = "deleted"))
                        Toast.makeText(context, "Location deleted", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                    ActionButton("Share") {
                        shareLocationDetails(context, latitude.text, longitude.text)
                    }
                }

                Spacer(Modifier.weight(1f))

                if (nearbyRoutes.isNotEmpty()) {

                    /* ─── styled card wrapper ───────────────────────────────────────── */
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        shape  = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.Black),                 // ← new black border
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent) // ← transparent bg
                    ) {

                        Column(Modifier.padding(12.dp)) {

                            /* title line */
                            Text(
                                "Saved routes within 3 km (${nearbyRoutes.size})",
                                fontSize   = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.height(8.dp))

                            /* list + thumb side-by-side */
                            val listState = rememberLazyListState()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)          // ← FIX: constrain viewport height
                            ) {

                                /* routes list */
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()     // take the full 200 dp
                                ) {
                                    items(nearbyRoutes) { r ->
                                        val displayName = r.routeName?.takeIf { it.isNotBlank() } ?: "Route ${r.id}"
                                        Text(
                                            "•  $displayName – ${formatTime(r.startTime)}",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clickable { navController.navigate("view_route/${r.id}") }
                                        )
                                    }
                                }

                                /* scroll thumb */
                                ScrollThumbLocationDetails(
                                    listState = listState,
                                    modifier  = Modifier
                                        .fillMaxHeight()     // same 200 dp height
                                        .width(4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ActionButton("Back") { navController.popBackStack() }
                    ActionButton("Save") {
                        val updated = location.copy(
                            name      = name.text,
                            latitude  = latitude.text.toDoubleOrNull() ?: 0.0,
                            longitude = longitude.text.toDoubleOrNull() ?: 0.0
                        )
                        locationViewModel.updateLocation(updated)
                        Toast.makeText(context, "Location updated", Toast.LENGTH_SHORT).show()
                    }
                }
            }
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

@Composable
private fun PlainTextField(
    @Suppress("SameParameterValue") label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusManager: FocusManager       // we’ll pass this in
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("Enter $label", fontSize = 12.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }   // hide keyboard when user presses “Done”
        ),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor   = Color.Black,      // or Black, whatever you use
            unfocusedIndicatorColor = Color.Black,
            disabledIndicatorColor  = Color.Black,
            focusedContainerColor   = Color.Transparent,   // ← make box clear
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor  = Color.Transparent
        )
    )
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick) { Text(text) }
}

@Composable
private fun LatLonCard(lat: String, lon: String) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape  = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, Color.Black),                                // ← black outline
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent) // ← transparent fill
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LatLonColumn("Latitude",  lat)
            LatLonColumn("Longitude", lon)
        }
    }
}

@Composable
private fun RowScope.LatLonColumn(label: String, value: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        LocationInfoChip(
            icon = Icons.Filled.LocationOn,
            value = if (value.isEmpty()) "Initializing…" else formatCoordinate(value),
            iconColor = Color.Blue,
            textColor = Color.Black
        )
    }
}

@SuppressLint("Recycle")
private suspend fun fetchNearbyImages(
    context: Context,
    lat: Double,
    lon: Double,
    radiusM: Double
): List<Uri> = withContext(Dispatchers.IO) {

    val wanted = mutableListOf<Uri>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_TAKEN
    )

    val cursor = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_TAKEN} DESC"
    )

    Log.d("GalleryTest", "query returned ${cursor?.count ?: -1} rows")   // ①

    cursor?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

        while (c.moveToNext()) {
            val id  = c.getLong(idCol)
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
            )

            val exif = context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it)
            } ?: continue

            val coords = getLatLongFromExif(exif)
            if (coords == null) {
                Log.d("GalleryTest", "   GPS tags missing for $uri")      // ②
                continue
            }

            val imgLat = coords.first
            val imgLon = coords.second
            val d = haversine(imgLat, imgLon, lat, lon)

            Log.d(                                                // ③
                "GalleryTest",
                "img=$imgLat,$imgLon  loc=$lat,$lon  dist=${"%.1f".format(d)} m"
            )

            if (d <= radiusM) {
                wanted += uri
                Log.d("GalleryTest", "   >>> accepted ($d m)")      // ④
                if (wanted.size == 3) break
            }
        }
    }
    wanted
}

/** Simple Haversine distance (metres) */
private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) *
            cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return r * c
}

private fun getLatLongFromExif(exif: ExifInterface): Pair<Double, Double>? {
    // Retrieve GPS data from EXIF tags
    val latStr = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
    val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
    val lonStr = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
    val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)

    // Check if any data is missing
    if (latStr == null || latRef == null || lonStr == null || lonRef == null) {
        return null
    }

    // Parse latitude and longitude
    val lat = parseGpsValue(latStr, latRef)
    val lon = parseGpsValue(lonStr, lonRef)

    // Return the coordinates as a Pair, or null if parsing fails
    return if (lat != null && lon != null) Pair(lat, lon) else null
}

private fun parseGpsValue(value: String, ref: String): Double? {
    // New fast path – already decimal?
    value.toDoubleOrNull()?.let { dec ->
        return if (ref == "S" || ref == "W") -dec else dec
    }

    // … otherwise fall back to “d/m/s” parsing
    val parts = value.split(',').map { it.trim() }
    if (parts.size != 3) return null

    fun parseRational(rational: String): Double? {
        val fraction = rational.split('/').map { it.trim() }
        if (fraction.size != 2) return null
        val num = fraction[0].toDoubleOrNull() ?: return null
        val denom = fraction[1].toDoubleOrNull() ?: return null
        if (denom == 0.0) return null
        return num / denom
    }

    val degrees = parseRational(parts[0]) ?: return null
    val minutes = parseRational(parts[1]) ?: return null
    val seconds = parseRational(parts[2]) ?: return null

    val decimal = degrees + (minutes / 60.0) + (seconds / 3600.0)
    return if (ref == "S" || ref == "W") -decimal else decimal
}

@Composable
fun ScrollThumbLocationDetails(
    listState      : LazyListState,
    modifier       : Modifier = Modifier,
    thumbColor     : Color     = Color.Black.copy(alpha = 0.3f),
    trackColor     : Color     = Color.LightGray.copy(alpha = .25f),
    minThumbHeight : Dp = 14.dp,
    thumbWidth     : Dp        = 4.dp
) {
    BoxWithConstraints(
        modifier               // ←– your width / height
            .zIndex(1f)        // ✱ keep thumb above list
    ) {

        /* show thumb only when list is longer than viewport */
        val showThumb by remember {
            derivedStateOf {
                val total   = listState.layoutInfo.totalItemsCount
                val visible = listState.layoutInfo.visibleItemsInfo.size
                total > 0 && visible < total
            }
        }

        /* progress & thumb height */
        val metrics by remember {
            derivedStateOf {
                val total   = listState.layoutInfo.totalItemsCount
                val visible = listState.layoutInfo.visibleItemsInfo.size

                val firstIndex  = listState.firstVisibleItemIndex.toFloat()
                val firstOffset = listState.firstVisibleItemScrollOffset
                val itemHeight  = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull()?.size ?: 1
                val progress = ((firstIndex + firstOffset / itemHeight) /
                        (total - visible).coerceAtLeast(1)).coerceIn(0f, 1f)

                val ratio   = visible.toFloat() / total
                val rawH    = maxHeight * ratio
                val thumbH  = maxOf(rawH, minThumbHeight)

                progress to thumbH
            }
        }

        /* drawing */
        val density        = LocalDensity.current
        val trackHeightPx  = with(density) { maxHeight.toPx() }
        val thumbHeightPx  = with(density) { metrics.second.toPx() }
        val yOffsetPx      = (trackHeightPx - thumbHeightPx) * metrics.first

        Box(
            Modifier
                .fillMaxSize()
                .background(trackColor)
        )

        /* draw thumb only when needed */
        if (showThumb) {
            Box(
                Modifier
                    .offset { IntOffset(0, yOffsetPx.roundToInt()) }
                    .width(thumbWidth)
                    .height(metrics.second)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(thumbColor)
            )
        }
    }
}