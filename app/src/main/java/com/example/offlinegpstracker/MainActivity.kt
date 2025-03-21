package com.example.offlinegpstracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.offlinegpstracker.ui.theme.OfflineGPSTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val locationViewModel: LocationViewModel by viewModels {
        LocationViewModelFactory(application, (application as MyApplication).repository)
    }
    private val userPreferences by lazy { UserPreferences(application) }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge drawing and set system nav bar transparent.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            OfflineGPSTrackerTheme {
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                CompositionLocalProvider(
                    androidx.lifecycle.compose.LocalLifecycleOwner provides lifecycleOwner
                ) {
                    val navController = rememberNavController()
                    val scope = rememberCoroutineScope()

                    // Observe skin & compass type
                    val currentSkin by userPreferences.compassSkin
                        .collectAsStateWithLifecycle(initialValue = UserPreferences.SKIN_CLASSIC_GAUGE)
                    val compassType by userPreferences.compassType
                        .collectAsStateWithLifecycle(initialValue = 0)

                    // Observe locations
                    val locations by locationViewModel.locations
                        .collectAsStateWithLifecycle(lifecycleOwner.lifecycle)

                    // Create/remember pagerState (2 pages: GPSTrackerScreen + LocationsScreen)
                    val pagerState = rememberPagerState { 3 }

                    // Handle Back
                    BackHandler {
                        if (locations.isNotEmpty()) {
                            when (navController.currentDestination?.route) {
                                "main" -> when (pagerState.currentPage) {
                                    0 -> finish()
                                    1 -> scope.launch {
                                        withContext(Dispatchers.Main.immediate) {
                                            pagerState.animateScrollToPage(0)
                                        }
                                    }
                                }
                                "location_details/{locationId}" -> navController.navigate("main") {
                                    popUpTo("main") { inclusive = true }
                                }
                                "navigator/{locationId}" -> navController.navigate("main") {
                                    popUpTo("main") { inclusive = true }
                                }
                            }
                        }
                    }

                    // Scaffold with a transparent NavigationBar
                    Scaffold(
                        bottomBar = {
                            val (unselectedColor, _, selectedItemColor) = getNavBarColors(currentSkin, compassType)
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = pagerState.currentPage == 0,
                                    onClick = {
                                        if (navController.currentDestination?.route == "main") {
                                            scope.launch {
                                                withContext(Dispatchers.Main.immediate) {
                                                    pagerState.animateScrollToPage(0)
                                                }
                                            }
                                        } else {
                                            navController.navigate("main") {
                                                popUpTo("main") { inclusive = true }
                                            }
                                            scope.launch {
                                                withContext(Dispatchers.Main.immediate) {
                                                    pagerState.animateScrollToPage(0)
                                                }
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            "GPS Tracker",
                                            color = if (pagerState.currentPage == 0)
                                                selectedItemColor
                                            else
                                                unselectedColor
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = if (pagerState.currentPage == 0)
                                                selectedItemColor
                                            else
                                                unselectedColor
                                        )
                                    }
                                )
                                NavigationBarItem(
                                    selected = pagerState.currentPage == 1,
                                    onClick = {
                                        if (navController.currentDestination?.route == "main") {
                                            scope.launch {
                                                withContext(Dispatchers.Main.immediate) {
                                                    pagerState.animateScrollToPage(1)
                                                }
                                            }
                                        } else {
                                            navController.navigate("main") {
                                                popUpTo("main") { inclusive = true }
                                            }
                                            scope.launch {
                                                withContext(Dispatchers.Main.immediate) {
                                                    pagerState.animateScrollToPage(1)
                                                }
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            "Locations",
                                            color = if (pagerState.currentPage == 1)
                                                selectedItemColor
                                            else
                                                unselectedColor
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.List,
                                            contentDescription = null,
                                            tint = if (pagerState.currentPage == 1)
                                                selectedItemColor
                                            else
                                                unselectedColor
                                        )
                                    }
                                )
                                NavigationBarItem(
                                    selected = pagerState.currentPage == 2,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                                    label = { Text("Route Tracker", color = if (pagerState.currentPage == 2) selectedItemColor else unselectedColor) },
                                    icon = { Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = if (pagerState.currentPage == 2) selectedItemColor else unselectedColor) }
                                )
                            }
                        }
                    ) { innerPadding ->
                        // The background is drawn based on the selected skin.
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (compassType == 2) { // Only show a background image if Gauge view is active.
                                when (currentSkin) {
                                    UserPreferences.SKIN_CLASSIC_GAUGE -> {
                                        Image(
                                            painter = painterResource(id = R.drawable.metallic_background),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    UserPreferences.SKIN_NEON_GAUGE -> {
                                        Image(
                                            painter = painterResource(id = R.drawable.neon_background),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    UserPreferences.SKIN_MINIMAL_GAUGE -> {
                                        Box(modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black)
                                        )
                                    }
                                    else -> {
                                        Box(modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
                                        )
                                    }
                                }
                            } else {
                                // Not gauge mode: fallback to default background.
                                Box(modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                )
                            }

                            // Foreground content (NavGraph) with innerPadding so that it is above the nav bar.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                NavGraph(
                                    navController = navController,
                                    locationViewModel = locationViewModel,
                                    modifier = Modifier.fillMaxSize(),
                                    pagerState = pagerState,
                                    locations = locations,
                                    userPreferences = userPreferences
                                )
                            }
                        }
                    }
                }
            }
        }
        requestPermissions()
    }

    @Composable
    fun getNavBarColors(currentSkin: Int, compassType: Int): Triple<Color, Color, Color> {
        return if (compassType == 2) {
            when (currentSkin) {
                UserPreferences.SKIN_CLASSIC_GAUGE ->
                    //   first       second           third
                    Triple(Color.Black, Color.Black, Color.DarkGray)
                UserPreferences.SKIN_NEON_GAUGE ->
                    //   first    second      third
                    Triple(Color.White, Color.White, Color(0xFF007799))
                UserPreferences.SKIN_MINIMAL_GAUGE ->
                    //   first    second     third
                    Triple(Color.White, Color.White, Color.Gray)
                else ->
                    Triple(Color.LightGray, MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.primary)
            }
        } else {
            // NOT gauge mode
            Triple(
                MaterialTheme.colorScheme.primary,                          // ignored
                MaterialTheme.colorScheme.onBackground,   // unselected
                MaterialTheme.colorScheme.primary         // selected
            )
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) { // Only request storage permissions on older versions
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val deniedPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isNotEmpty()) {
            if (shouldShowRationale(deniedPermissions)) {
                showRationaleDialog(deniedPermissions)
            } else {
                ActivityCompat.requestPermissions(this, deniedPermissions.toTypedArray(), 1)
            }
        } else {
            onPermissionsGranted()
        }
    }

    private fun shouldShowRationale(permissions: List<String>): Boolean {
        return permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
    }

    private fun showRationaleDialog(permissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs location, camera, and storage permissions to function correctly. Please grant them.")
            .setPositiveButton("OK") { _, _ ->
                ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Permissions are required for full functionality.", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun onPermissionsGranted() {
        locationViewModel.startLocationUpdates()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                onPermissionsGranted()
            } else {
                val permanentlyDenied = permissions.any { permission ->
                    ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
                }

                if (permanentlyDenied) {
                    showSettingsDialog()
                } else {
                    Toast.makeText(this, "Permissions are required for full functionality.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Denied")
            .setMessage("Some permissions are permanently denied. Open app settings to grant them manually.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationViewModel.stopLocationUpdates()
    }
}