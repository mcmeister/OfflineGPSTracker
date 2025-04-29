package com.example.offlinegpstracker

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val context = LocalContext.current

    // Define permissions
    val readImagesPerm = if (Build.VERSION.SDK_INT >= 33)
        android.Manifest.permission.READ_MEDIA_IMAGES
    else
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    val accessMediaLocationPerm = if (Build.VERSION.SDK_INT >= 29) {
        android.Manifest.permission.ACCESS_MEDIA_LOCATION
    } else {
        ""
    }

    // Track permission states
    var hasGalleryPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readImagesPerm) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMediaLocationPerm by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 29) {
                ContextCompat.checkSelfPermission(context, accessMediaLocationPerm) ==
                        PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    // Permission launcher for both permissions
    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasGalleryPerm = permissions[readImagesPerm] ?: false
        if (Build.VERSION.SDK_INT >= 29) {
            hasMediaLocationPerm = permissions[accessMediaLocationPerm] ?: false
        }
    }

    // Request permissions when the screen first opens
    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(readImagesPerm)
        if (Build.VERSION.SDK_INT >= 29 && !hasMediaLocationPerm) {
            permissionsToRequest.add(accessMediaLocationPerm)
        }
        if (permissionsToRequest.size > 1 || !hasGalleryPerm) {
            requestPermissions.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Monitor permission changes
    LaunchedEffect(Unit) {
        snapshotFlow {
            Pair(
                ContextCompat.checkSelfPermission(context, readImagesPerm),
                if (Build.VERSION.SDK_INT >= 29) {
                    ContextCompat.checkSelfPermission(context, accessMediaLocationPerm)
                } else {
                    PackageManager.PERMISSION_GRANTED
                }
            )
        }.collect { (galleryPerm, mediaLocationPerm) ->
            hasGalleryPerm = galleryPerm == PackageManager.PERMISSION_GRANTED
            hasMediaLocationPerm = mediaLocationPerm == PackageManager.PERMISSION_GRANTED
        }
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
            val allRoutes by routeRepository.getAllRoutes()
                .collectAsState(initial = emptyList())

            val nearbyRoutes = remember(location.id to allRoutes) {
                allRoutes.filter {
                    haversine(
                        it.centerLat,
                        it.centerLon,
                        location.latitude,
                        location.longitude
                    ) <= 3_000.0
                }
            }
            var isEditing by remember { mutableStateOf(false) }
            var locationName by remember { mutableStateOf(TextFieldValue(location.name)) }
            val latitude by remember { mutableStateOf(TextFieldValue(location.latitude.toString())) }
            val longitude by remember { mutableStateOf(TextFieldValue(location.longitude.toString())) }
            var galleryImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

            LaunchedEffect(location.id, hasGalleryPerm, hasMediaLocationPerm) {
                if (!hasGalleryPerm) {
                    galleryImages = emptyList()
                    return@LaunchedEffect
                }
                if (Build.VERSION.SDK_INT >= 29 && !hasMediaLocationPerm) {
                    galleryImages = emptyList()
                    return@LaunchedEffect
                }

                galleryImages = fetchNearbyImages(
                    context,
                    location.latitude,
                    location.longitude,
                    1_000.0
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { focusManager.clearFocus() }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    /* ───────────────── ICON BAR (only when NOT editing) ───────────────── */
                    if (!isEditing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { isEditing = true }
                            )
                            Spacer(Modifier.width(12.dp))

                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        shareLocationDetails(context, latitude.text, longitude.text)
                                    }
                            )
                            Spacer(Modifier.width(12.dp))

                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = Color.Red,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        locationViewModel.updateLocation(location.copy(status = "deleted"))
                                        Toast.makeText(
                                            context,
                                            "Location deleted",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        navController.popBackStack()
                                    }
                            )
                        }
                    }

                    /* ───────────────── NAME LINE (always centred) ─────────────────────── */
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (isEditing) 0.dp else 16.dp),  // remove extra gap while editing
                        contentAlignment = Alignment.Center
                    ) {
                        if (isEditing) {
                            /* ---- text-field + save icon overlay ------------------------ */
                            Box(
                                modifier = Modifier.widthIn(max = 300.dp)   // keeps overall width predictable
                            ) {
                                OutlinedTextField(
                                    value = locationName,
                                    onValueChange = { locationName = it },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth(),                     // takes whole box width
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Black,
                                        unfocusedIndicatorColor = Color.Black,
                                        disabledIndicatorColor = Color.Black,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent
                                    )
                                )
                                /* ✓ save icon sits flush with the right edge */
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Save",
                                    modifier = Modifier
                                        .size(26.dp)
                                        .align(Alignment.CenterEnd)          // <— right on the field border
                                        .padding(end = 8.dp)                 // tweak if you need tighter fit
                                        .clickable {
                                            val updated = location.copy(name = locationName.text)
                                            locationViewModel.updateLocation(updated)
                                            isEditing = false
                                            focusManager.clearFocus()
                                        }
                                )
                            }
                        } else {
                            Text(
                                text = location.name,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp) // <-- ONE place to control left/right margins
                ) {
                    LatLonCard(
                    lat = latitude.text,
                    lon = longitude.text,
                    onNavigate = {
                        navController.navigate("navigator/${location.id}/${location.name}")
                    }
                )

                    Spacer(Modifier.height(16.dp))

                    PhotosSection(
                        galleryImages = galleryImages,
                        context       = context
                    )

                    Spacer(Modifier.height(16.dp))

                    // ─── SAVED ROUTES SECTION ───────────────────────────────────────────────
                    if (nearbyRoutes.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape  = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "Saved routes within 3 km radius of this Location (${nearbyRoutes.size})",
                                    fontSize   = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(Modifier.height(8.dp))

                                val listState = rememberLazyListState()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                ) {
                                    LazyColumn(
                                        state    = listState,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        items(nearbyRoutes) { r ->
                                            val routeName      = r.routeName?.takeIf { it.isNotBlank() } ?: "Route ${r.id}"
                                            val timeLabel = formatTime(r.startTime)
                                            AssistChip(
                                                onClick = { navController.navigate("view_route/${r.id}") },
                                                label   = { Text("$routeName  •  $timeLabel") },
                                                shape   = RoundedCornerShape(16.dp),
                                                colors  = AssistChipDefaults.assistChipColors(
                                                    containerColor = Color.Transparent,
                                                    labelColor     = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                            )
                                        }
                                    }

                                    ScrollThumbLocationDetails(
                                        listState = listState,
                                        modifier  = Modifier
                                            .fillMaxHeight()
                                            .width(4.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No saved routes available for this Location",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = { navController.popBackStack() }) {
                        Text("Back")
                    }
                }

                Spacer(Modifier.weight(1f))
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
private fun LatLonCard(
    lat: String,
    lon: String,
    onNavigate: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LatLonColumn("Latitude", lat)
                LatLonColumn("Longitude", lon)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Navigate to Location",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
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
@Suppress("DEPRECATION") // Suppress deprecation warnings for LATITUDE and LONGITUDE
private suspend fun fetchNearbyImages(
    context: Context,
    lat: Double,
    lon: Double,
    radiusM: Double
): List<Uri> = withContext(Dispatchers.IO) {
    val wanted = mutableListOf<Uri>()

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.LATITUDE,
        MediaStore.Images.Media.LONGITUDE
    )
    val cursor = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_TAKEN} DESC"
    )

    cursor?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val latCol = c.getColumnIndex(MediaStore.Images.Media.LATITUDE)
        val lonCol = c.getColumnIndex(MediaStore.Images.Media.LONGITUDE)

        var processedCount = 0 // Counter to limit processed photos
        while (c.moveToNext() && processedCount < 100) { // Limit to 100 photos
            processedCount++ // Increment counter

            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                c.getLong(idCol)
            )

            // Try MediaStore first
            var coords: Pair<Double, Double>? = null
            val mediaStoreLat = if (latCol != -1) c.getDouble(latCol) else null
            val mediaStoreLon = if (lonCol != -1) c.getDouble(lonCol) else null
            if (mediaStoreLat != null && mediaStoreLon != null && mediaStoreLat != 0.0 && mediaStoreLon != 0.0) {
                coords = Pair(mediaStoreLat, mediaStoreLon)
            }

            // If MediaStore fails, fall back to EXIF
            if (coords == null) {
                val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                    ?: continue
                coords = getLatLongFromExif(exif)
            }

            // Skip photos with no GPS data from either source
            if (coords == null) {
                continue
            }

            val (imgLat, imgLon) = coords
            val dist = haversine(imgLat, imgLon, lat, lon)

            if (dist <= radiusM) {
                wanted += uri
                if (wanted.size == 7) break
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
    // Log raw EXIF tags for debugging
    val latStr = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
    val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
    val lonStr = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
    val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
    println("Raw EXIF: lat=$latStr ($latRef), lon=$lonStr ($lonRef)")

    // Use the new getLatLong() method (returns DoubleArray?)
    val latLong = exif.latLong
    if (latLong != null) {
        println("getLatLong success: ${latLong[0]}, ${latLong[1]}")
        return Pair(latLong[0], latLong[1])
    }

    // Fallback to manual parsing
    if (latStr == null || latRef == null || lonStr == null || lonRef == null) {
        println("Manual parsing failed: Missing EXIF tags")
        return null
    }

    val lat = parseGpsValue(latStr, latRef)
    val lon = parseGpsValue(lonStr, lonRef)
    println("Manual parsing result: lat=$lat, lon=$lon")

    return if (lat != null && lon != null) Pair(lat, lon) else null
}

private fun parseGpsValue(raw: String, ref: String): Double? {
    // 1) remove any whitespace / wrappers like "(" and ")"
    val value = raw.trim().removePrefix("(").removeSuffix(")")

    // 2) fast-path: already plain decimal?
    value.toDoubleOrNull()?.let { dec ->
        return if (ref == "S" || ref == "W") -dec else dec
    }

    // 3) otherwise expect “d/m/s” rationals  e.g.  "37/1,25/1,1907/100"
    val parts = value.split(',').map { it.trim() }
    if (parts.size != 3) return null

    fun rat(r: String): Double? {
        val (num, den) = r.split('/').map { it.trim() }
        return num.toDoubleOrNull()?.div(den.toDoubleOrNull() ?: return null)
    }

    val deg = rat(parts[0]) ?: return null
    val min = rat(parts[1]) ?: return null
    val sec = rat(parts[2]) ?: return null

    val decimal = deg + min / 60 + sec / 3600
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

@Composable
fun PhotosSection(
    galleryImages: List<Uri>,
    context: Context
) {
    // state to track which image (if any) is in full-screen view
    var fullscreenUri by remember { mutableStateOf<Uri?>(null) }

    if (galleryImages.isNotEmpty()) {
        // full-screen preview dialog
        fullscreenUri?.let { uri ->
            Dialog(
                onDismissRequest = { fullscreenUri = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { fullscreenUri = null },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape  = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    text       = "Photos from gallery within 1 km radius of this Location",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> c.consume() } }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // only actual photos, up to 7
                        items(galleryImages.take(7)) { uri ->
                            Box(
                                Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                                    .clickable { fullscreenUri = uri },   // <-- open preview
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter      = rememberAsyncImagePainter(uri),
                                    contentDescription = null,
                                    modifier     = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop      // <-- fill box, crop edges
                                )
                            }
                        }

                        // “Go to Gallery” button always last
                        item {
                            TextButton(
                                onClick = {
                                    galleryImages.firstOrNull()?.let { uri ->
                                        Intent(Intent.ACTION_VIEW, uri)
                                            .apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                            .also(context::startActivity)
                                    } ?: Toast
                                        .makeText(context, "No photos to open", Toast.LENGTH_SHORT)
                                        .show()
                                },
                                modifier = Modifier.height(80.dp)
                            ) {
                                Text("Go\nto\nGallery", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Text(
            text       = "No photos available in gallery for this Location",
            fontSize   = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )
    }
}