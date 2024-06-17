package com.example.offlinegpstracker

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.navigation.compose.rememberNavController
import com.example.offlinegpstracker.ui.theme.OfflineGPSTrackerTheme
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.rememberPagerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPagerApi::class)
class MainActivity : AppCompatActivity() {

    private val locationViewModel: LocationViewModel by viewModels {
        LocationViewModelFactory(application, (application as MyApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OfflineGPSTrackerTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                CompositionLocalProvider(
                    LocalLifecycleOwner provides lifecycleOwner
                ) {
                    val navController = rememberNavController()
                    val scope = rememberCoroutineScope()

                    // Collecting locations in a Lifecycle-aware manner using helper function
                    val locations by locationViewModel.locations.collectAsStateWithLifecycle(lifecycleOwner.lifecycle)

                    // Initialize pagerState
                    val pagerState = rememberPagerState()

                    BackHandler {
                        if (pagerState != null && locations.isNotEmpty()) {
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
                            NavigationBar {
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
                                    label = { Text("GPS Tracker") },
                                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
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
                                    label = { Text("Locations") },
                                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
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
