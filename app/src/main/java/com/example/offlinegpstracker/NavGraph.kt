package com.example.offlinegpstracker

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NavGraph(
    navController: NavHostController,
    locationViewModel: LocationViewModel,
    routeTrackerVM: RouteTrackerViewModel,
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    locations: List<Location>
) {
    NavHost(navController = navController, startDestination = "main", modifier = modifier) {
        composable("main") {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> GPSTrackerScreen(              // ← needs skins
                        locationViewModel = locationViewModel
                    )

                    1 -> LocationsScreen(                // ← neutral UI
                        navController     = navController,
                        locationViewModel = locationViewModel,
                        locations         = locations
                    )

                    2 -> RouteTrackerScreen(viewModel = routeTrackerVM)
                }
            }
        }
        composable("location_details/{locationId}") { backStackEntry ->
            val locationId = backStackEntry.arguments
                ?.getString("locationId")
                ?.toIntOrNull() ?: 0
            LocationDetailsScreen(
                navController = navController,
                locationViewModel = locationViewModel,
                startIndex = locationId
            )
        }
        composable("navigator/{locationId}/{locationName}") { backStackEntry ->
            val locationId = backStackEntry.arguments
                ?.getString("locationId")
                ?.toIntOrNull() ?: 0
            val locationName = backStackEntry.arguments
                ?.getString("locationName") ?: ""
            val location = locationId.let {
                locationViewModel.getLocationById(it)
                    .collectAsState(initial = null)
                    .value
            }
            location?.let {
                NavigatorScreen(
                    navController = navController,
                    locationViewModel = locationViewModel,
                    savedLocation = it.toAndroidLocation(),
                    locationName = locationName
                )
            }
        }
        composable("view_route/{routeId}") { backStackEntry ->
            val routeId   = backStackEntry.arguments?.getString("routeId")?.toIntOrNull() ?: return@composable
            val context   = LocalContext.current
            val app       = context.applicationContext as MyApplication

            /* share the same LocationViewModel that NavGraph already owns */
            val routeVM = remember {
                RouteTrackerViewModel(
                    routeRepository = app.routeRepository,
                    application     = context.applicationContext as Application,
                    locationViewModel = locationViewModel
                )
            }

            LaunchedEffect(routeId) { routeVM.selectRoute(routeId) }

            RouteTrackerScreen(viewModel = routeVM)
        }
    }
}