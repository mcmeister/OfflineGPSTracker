package com.example.offlinegpstracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
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
import androidx.core.app.ActivityCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OfflineGPSTrackerTheme {
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                CompositionLocalProvider(
                    androidx.lifecycle.compose.LocalLifecycleOwner provides lifecycleOwner
                ) {
                    val navController = rememberNavController()
                    val scope = rememberCoroutineScope()

                    val currentSkin by userPreferences.compassSkin.collectAsStateWithLifecycle(initialValue = UserPreferences.SKIN_CLASSIC)
                    val compassType by userPreferences.compassType.collectAsStateWithLifecycle(initialValue = 0) // Track Compass Type

                    // Collecting locations in a Lifecycle-aware manner
                    val locations by locationViewModel.locations.collectAsStateWithLifecycle(lifecycleOwner.lifecycle)

                    // Ensure pagerState has a valid page count
                    val pagerState = rememberPagerState { 2 }

                    BackHandler {
                        if (locations.isNotEmpty()) {
                            when (navController.currentDestination?.route) {
                                "main" -> when (pagerState.currentPage) {
                                    0 -> finish() // Exit app when back pressed on GPS Tracker screen
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

                    Scaffold(
                        bottomBar = {
                            // Apply color logic only when CompassViewGauge is selected
                            val (navTextColor, navBackgroundColor, selectedItemColor) = getNavBarColors(currentSkin, compassType)

                            NavigationBar(containerColor = navBackgroundColor) {
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
                                    label = { Text("GPS Tracker", color = if (pagerState.currentPage == 0) selectedItemColor else navTextColor) },
                                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = if (pagerState.currentPage == 0) selectedItemColor else navTextColor) }
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
                                    label = { Text("Locations", color = if (pagerState.currentPage == 1) selectedItemColor else navTextColor) },
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = if (pagerState.currentPage == 1) selectedItemColor else navTextColor) }
                                )
                            }
                        }
                    ) { innerPadding ->
                        NavGraph(
                            navController = navController,
                            locationViewModel = locationViewModel,
                            modifier = Modifier.padding(innerPadding),
                            pagerState = pagerState,
                            locations = locations
                        )
                    }
                }
            }
        }
        requestLocationPermission()
    }

    @Composable
    fun getNavBarColors(currentSkin: Int, compassType: Int): Triple<Color, Color, Color> {
        return if (compassType == 2) { // Only change color if CompassViewGauge is selected
            when (currentSkin) {
                UserPreferences.SKIN_CLASSIC -> Triple(Color.Black, Color(0xFFB0BEC5), Color.DarkGray)
                UserPreferences.SKIN_NEON -> Triple(Color.Cyan, Color.Black, Color(0xFF007799))
                UserPreferences.SKIN_MINIMAL -> Triple(Color.Gray, Color.Black, Color.Gray)
                else -> Triple(MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primary)
            }
        } else {
            // Default colors when CompassViewGauge is NOT selected (Keep Material defaults)
            Triple(MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primary)
        }
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        } else {
            locationViewModel.startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            locationViewModel.startLocationUpdates()
        } else {
            Toast.makeText(this, "Location permissions are required to use this app", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationViewModel.stopLocationUpdates()
    }
}