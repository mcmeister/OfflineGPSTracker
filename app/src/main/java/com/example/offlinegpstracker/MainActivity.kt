package com.example.offlinegpstracker

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.offlinegpstracker.ui.theme.OfflineGPSTrackerTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val locationViewModel: LocationViewModel by viewModels {
        LocationViewModelFactory(application, (application as MyApplication).repository)
    }
    private val routeTrackerViewModel: RouteTrackerViewModel by viewModels {
        RouteTrackerViewModelFactory(           // ← uses your existing factory
            application           = application,                 // ① order matches factory
            routeRepository       = (application as MyApplication).routeRepository,
            locationViewModel     = locationViewModel
        )
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
                CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides lifecycleOwner) {

                    /* ─── state ─── */
                    val navController = rememberNavController()
                    val scope         = rememberCoroutineScope()

                    // skin / type
                    val currentSkin  by userPreferences.compassSkin
                        .collectAsStateWithLifecycle(initialValue = UserPreferences.SKIN_CLASSIC_GAUGE)
                    val compassType  by userPreferences.compassType
                        .collectAsStateWithLifecycle(initialValue = 0)

                    // locations list
                    val locations    by locationViewModel.locations
                        .collectAsStateWithLifecycle(lifecycleOwner.lifecycle)

                    // pages (0 GPS, 1 Locations, 2 Route)
                    val pagerState   = rememberPagerState { 3 }

                    /* request location permission once */
                    RequestLocationPermission { locationViewModel.startLocationUpdates() }

                    /* pre-download map tiles (unchanged) */
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            routeTrackerViewModel.tryDownloadTilesForAllRoutes()
                        }
                    }

                    /* back-handler (unchanged) */
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
                                "navigator/{locationId}"       -> navController.navigate("main") {
                                    popUpTo("main") { inclusive = true }
                                }
                            }
                        }
                    }

                    /* is user **currently** on GPS tab + Gauge view ? */
                    val gaugeOnGpsTab = pagerState.currentPage == 0 && compassType == 2

                    /* nav-bar colours (skin only when above is true) */
                    val (unSel, _, sel) = if (gaugeOnGpsTab)
                        getNavBarColors(currentSkin, 2)
                    else
                        Triple(
                            MaterialTheme.colorScheme.onBackground,
                            MaterialTheme.colorScheme.onBackground,
                            MaterialTheme.colorScheme.primary
                        )

                    /* ─── UI  ─── */
                    Scaffold(
                        bottomBar = {
                            NavigationBar(
                                containerColor = if (gaugeOnGpsTab) Color.Transparent
                                else MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp
                            ) {
                                /* GPS Tracker */
                                NavigationBarItem(
                                    selected = pagerState.currentPage == 0,
                                    onClick  = {
                                        if (navController.currentDestination?.route == "main")
                                            scope.launch { pagerState.animateScrollToPage(0) }
                                        else {
                                            navController.navigate("main") { popUpTo("main") { inclusive = true } }
                                            scope.launch { pagerState.animateScrollToPage(0) }
                                        }
                                    },
                                    label = { Text("GPS Tracker",  color = if (pagerState.currentPage == 0) sel else unSel) },
                                    icon  = { Icon(Icons.Default.LocationOn, null,
                                        tint = if (pagerState.currentPage == 0) sel else unSel) }
                                )

                                /* Locations */
                                NavigationBarItem(
                                    selected = pagerState.currentPage == 1,
                                    onClick  = {
                                        if (navController.currentDestination?.route == "main")
                                            scope.launch { pagerState.animateScrollToPage(1) }
                                        else {
                                            navController.navigate("main") { popUpTo("main") { inclusive = true } }
                                            scope.launch { pagerState.animateScrollToPage(1) }
                                        }
                                    },
                                    label = { Text("Locations",   color = if (pagerState.currentPage == 1) sel else unSel) },
                                    icon  = { Icon(Icons.AutoMirrored.Filled.List, null,
                                        tint = if (pagerState.currentPage == 1) sel else unSel) }
                                )

                                /* Route Tracker */
                                NavigationBarItem(
                                    selected = pagerState.currentPage == 2,
                                    onClick  = {
                                        if (navController.currentDestination?.route == "main") {
                                            // already on Main → just flip the pager
                                            scope.launch {
                                                withContext(Dispatchers.Main.immediate) {
                                                    pagerState.animateScrollToPage(2)
                                                }
                                            }
                                        } else {
                                            // first pop back to Main, then flip the pager
                                            navController.navigate("main") { popUpTo("main") { inclusive = true } }
                                            scope.launch {
                                                withContext(Dispatchers.Main.immediate) {
                                                    pagerState.animateScrollToPage(2)
                                                }
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            "Route Tracker",
                                            color = if (pagerState.currentPage == 2) sel else unSel
                                        )
                                    },
                                    icon  = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.DirectionsWalk,
                                            contentDescription = null,
                                            tint = if (pagerState.currentPage == 2) sel else unSel
                                        )
                                    }
                                )
                            }
                        }
                    ) { innerPadding ->

                        /* root container so we can paint the backdrop underneath Scaffold content */
                        Box(Modifier.fillMaxSize()) {

                            /* full-screen skin only on GPS + Gauge */
                            if (gaugeOnGpsTab) {
                                SkinBackground(
                                    compassType = 2,
                                    skin        = currentSkin,
                                    modifier    = Modifier.fillMaxSize()
                                )
                            }

                            /* foreground screens */
                            NavGraph(
                                navController     = navController,
                                locationViewModel = locationViewModel,
                                routeTrackerVM    = routeTrackerViewModel,
                                pagerState        = pagerState,
                                locations         = locations,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (gaugeOnGpsTab) Modifier              // skin already painted
                                        else Modifier.background(MaterialTheme.colorScheme.background)
                                    )
                                    .padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
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

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun RequestLocationPermission(onPermissionGranted: () -> Unit) {
        val fineLocationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)

        LaunchedEffect(Unit) {
            if (!fineLocationPermission.status.isGranted) {
                fineLocationPermission.launchPermissionRequest()
            } else {
                onPermissionGranted()
            }
        }

        when (fineLocationPermission.status) {
            is PermissionStatus.Granted -> {
                onPermissionGranted()
            }
            is PermissionStatus.Denied -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text("Location permission is required for GPS tracking.", color = Color.Red)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationViewModel.stopLocationUpdates()
    }
}