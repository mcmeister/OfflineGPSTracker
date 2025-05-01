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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var showDeleteDialog by remember { mutableStateOf(false) }

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
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(location.id, hasGalleryPerm, hasMediaLocationPerm) {
                isLoading = true
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
                isLoading = false
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Clear focus when tapping anywhere outside
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { focusManager.clearFocus() }
            ) {
                // ─── A) Scrollable header ───────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                ) {
                    Spacer(Modifier.height(16.dp))

                    // ── ICON BAR + DELETE DIALOG + NAME LINE ───────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        if (!isEditing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // ◀ Back
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { navController.popBackStack() }
                                )
                                // ✎ Edit, ⇪ Share, 🗑 Delete
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        "Edit",
                                        Modifier.size(20.dp).clickable { isEditing = true }
                                    )
                                    Icon(
                                        Icons.Filled.Share,
                                        "Share",
                                        Modifier.size(20.dp).clickable {
                                            shareLocationDetails(context, latitude.text, longitude.text)
                                        }
                                    )
                                    Icon(
                                        Icons.Filled.Delete,
                                        "Delete",
                                        tint = Color.Red,
                                        modifier = Modifier.size(20.dp).clickable { showDeleteDialog = true }
                                    )
                                }
                            }
                        }

                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("Delete ${location.name}?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        locationViewModel.updateLocation(location.copy(status = "deleted"))
                                        Toast.makeText(context, "Location deleted", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                        showDeleteDialog = false
                                    }) { Text("Yes") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) { Text("No") }
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (isEditing) 0.dp else 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isEditing) {
                                Row(
                                    modifier = Modifier.widthIn(max = 300.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = locationName,
                                        onValueChange = { locationName = it },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Filled.Check,
                                        "Save",
                                        Modifier.size(20.dp).clickable {
                                            locationViewModel.updateLocation(location.copy(name = locationName.text))
                                            isEditing = false
                                            focusManager.clearFocus()
                                        }
                                    )
                                }
                            } else {
                                Text(
                                    location.name,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── LAT/LON, PHOTOS, AND SAVED ROUTES ───────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        LatLonCard(
                            lat = latitude.text,
                            lon = longitude.text,
                            onNavigate = { navController.navigate("navigator/${location.id}/${location.name}") }
                        )
                        Spacer(Modifier.height(16.dp))
                        PhotosSection(galleryImages, isLoading, context)
                        Spacer(Modifier.height(16.dp))
                        // Saved Routes Drawer placed here within the scrollable Column
                        if (nearbyRoutes.isNotEmpty()) {
                            SavedRoutesDrawer(
                                nearbyRoutes = nearbyRoutes,
                                routeRepository = routeRepository,
                                navController = navController,
                                pullUpDistance = 220.dp
                            )
                        } else {
                            Text(
                                text = "No saved routes available for this Location",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(top = 16.dp)
                            )
                        }
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
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onNavigate() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment   = Alignment.CenterVertically
            ) {
                Text(
                    "Navigate to Location",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector        = Icons.Filled.Navigation,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.padding(start = 4.dp)
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
    isLoading: Boolean,
    context: Context
) {
    var fullscreenUri by remember { mutableStateOf<Uri?>(null) }

    when {
        // 1) Still loading:
        isLoading -> {
            AnimatedLoadingText(
                baseText = "Looking for photos in gallery",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        }

        // 2) Loaded & found images:
        galleryImages.isNotEmpty() -> {
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
                            painter      = rememberAsyncImagePainter(uri),
                            contentDescription = null,
                            modifier     = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            items(galleryImages.take(7)) { uri ->
                                Box(
                                    Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                                        .clickable { fullscreenUri = uri },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter      = rememberAsyncImagePainter(uri),
                                        contentDescription = null,
                                        modifier     = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

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
        }

        // 3) Loaded but empty:
        else -> {
            Text(
                text       = "No photos available in gallery for this Location",
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign  = TextAlign.Center
            )
        }
    }
}

@Composable
fun AnimatedLoadingText(
    modifier: Modifier = Modifier,
    baseText: String,
    maxDots: Int = 3,
    intervalMs: Long = 500L,
    textStyle: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    textAlign: TextAlign = TextAlign.Center
) {
    var dotCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(intervalMs)
            dotCount = (dotCount + 1) % (maxDots + 1)
        }
    }
    Text(
        text      = baseText + ".".repeat(dotCount),
        modifier  = modifier,
        style     = textStyle,
        textAlign = textAlign
    )
}

fun formatDistance(meters: Float): String =
    if (meters >= 1_000f) {
        // 1 km or more → show in km (two decimals)
        "%.2fkm".format(meters / 1_000f)
    } else {
        // less than 1 km → whole meters
        "${meters.toInt()}m"
    }

fun formatDuration(start: Long, end: Long?): String {
    val elapsedMs    = (end ?: System.currentTimeMillis()) - start
    val totalMins    = (elapsedMs / 1_000L / 60L).toInt()
    return if (totalMins < 60) {
        // less than 1 h → show minutes
        "$totalMins min"
    } else {
        // 1 h or more → show hours with two decimals
        val hours = elapsedMs.toDouble() / (1_000.0 * 60.0 * 60.0)
        "%.2f hr".format(hours)
    }
}

fun formatSpeed(meters: Float, start: Long, end: Long?): String {
    val seconds = ((end ?: System.currentTimeMillis()) - start) / 1_000f
    return if (seconds > 0f) {
        val kmh = (meters / seconds) * 3.6f
        "%.2fkm/hr".format(kmh)
    } else {
        "0km/hr"
    }
}

@Composable
fun NearbyRoutesList(
    nearbyRoutes: List<Route>,
    routeRepository: RouteRepository,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // Create a scroll state for both list + thumb
    val listState = rememberLazyListState()

    // Cache your points for distance calc
    val pointsMap = remember { mutableStateMapOf<Int, List<RoutePoint>>() }
    LaunchedEffect(nearbyRoutes) {
        nearbyRoutes.forEach { r ->
            if (!pointsMap.containsKey(r.id)) {
                scope.launch {
                    routeRepository.getPointsForRoute(r.id)
                        .collect { pts -> pointsMap[r.id] = pts }
                }
            }
        }
    }

    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(12.dp)
        ) {
            Text(
                "Saved routes within 3 km radius of this Location (${nearbyRoutes.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight() // Ensure the Row fills the remaining height
                    .weight(1f) // Take up all available space after the title and spacer
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f) // Ensure LazyColumn takes up all available space in the Row
                        .fillMaxHeight() // Fill the height of the Row
                        .padding(end = 8.dp) // Only padding on the end for the scrollbar
                ) {
                    items(nearbyRoutes) { r ->
                        val routeName = r.routeName?.takeIf { it.isNotBlank() } ?: "Route ${r.id}"
                        val timeLabel = formatTime(r.startTime)

                        val pts = pointsMap[r.id] ?: emptyList()
                        val totalDist = calculateDistance(pts)
                        val distanceText = formatDistance(totalDist)
                        val durationText = formatDuration(r.startTime, r.endTime)
                        val speedText = formatSpeed(totalDist, r.startTime, r.endTime)

                        AssistChip(
                            onClick = { navController.navigate("view_route/${r.id}") },
                            label = {
                                Column {
                                    // line 1
                                    Text("$routeName  •  $timeLabel", color = Color.Red.copy(alpha = 0.7f))
                                    // line 2
                                    Text("Distance: $distanceText\nDuration: $durationText\nSpeed: $speedText")
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Transparent,
                                labelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }

                // Your thumb, driven by the same listState
                ScrollThumbLocationDetails(
                    listState = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                )
            }
        }
    }
}

@Composable
fun SavedRoutesDrawer(
    nearbyRoutes: List<Route>,
    routeRepository: RouteRepository,
    navController: NavHostController,
    pullUpDistance: Dp
) {
    val density = LocalDensity.current
    val pullUpPx = with(density) { pullUpDistance.toPx() }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val navigationBarHeight = 56.dp // Approximate height of bottom navigation bar
    val headerHeight = 250.dp // Approximate height of content above (Location Name, Lat/Lon, Photos)
    val collapsedHeight = screenHeight - headerHeight - navigationBarHeight - 16.dp // Fill to 16dp above bottom nav bar
    val maxHeight = screenHeight - navigationBarHeight - 16.dp // Max height when expanded

    var isExpanded by remember { mutableStateOf(false) } // Initially collapsed
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    // Determine if the drawer can be expanded (more than 3 routes)
    val canExpand = nearbyRoutes.size > 3

    // Animate Y offset (only if expandable)
    val animatedOffsetPx by animateFloatAsState(
        targetValue = when {
            !canExpand -> 0f // No dragging if not expandable
            isDragging -> dragOffsetPx
            isExpanded -> -pullUpPx
            else -> 0f
        },
        animationSpec = tween(300), label = ""
    )

    // Animate height
    val targetH = if (isExpanded && canExpand) maxHeight else collapsedHeight
    val animatedH by animateDpAsState(targetValue = targetH, animationSpec = tween(300), label = "")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(animatedH)
            .offset { IntOffset(0, animatedOffsetPx.roundToInt()) }
            // Add tap gesture to toggle expand/collapse (only if expandable)
            .pointerInput(Unit) {
                if (canExpand) {
                    detectTapGestures(
                        onTap = {
                            isExpanded = !isExpanded // Toggle expand/collapse on tap
                        }
                    )
                }
            }
    ) {
        // Hide underlying content
        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background))

        // The draggable pill (only visible if expandable)
        if (canExpand) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(vertical = 8.dp)
                    .size(40.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.LightGray)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { isDragging = true },
                            onVerticalDrag = { ch, dy ->
                                ch.consume()
                                dragOffsetPx = (dragOffsetPx + dy).coerceIn(-pullUpPx, 0f)
                            },
                            onDragEnd = {
                                isDragging = false
                                isExpanded = dragOffsetPx < -pullUpPx / 2
                                dragOffsetPx = 0f
                            }
                        )
                    }
            )
        }

        // The list with matching width
        NearbyRoutesList(
            nearbyRoutes = nearbyRoutes,
            routeRepository = routeRepository,
            navController = navController,
            modifier = Modifier
                .fillMaxWidth() // Ensure width matches LatLonCard and PhotosSection
                .padding(top = if (canExpand) 16.dp else 0.dp) // Adjust padding based on pill visibility
                .fillMaxHeight() // Ensure the list fills the entire height of the drawer
        )
    }
}