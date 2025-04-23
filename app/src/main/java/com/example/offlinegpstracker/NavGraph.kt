package com.example.offlinegpstracker

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NavGraph(
    navController: NavHostController,
    locationViewModel: LocationViewModel,
    routeTrackerVM: RouteTrackerViewModel,
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    locations: List<Location>
) {
    NavHost(navController = navController, startDestination = "main", modifier = modifier) {
        composable("main") {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> GPSTrackerScreen(locationViewModel)
                    1 -> LocationsScreen(
                        navController = navController,
                        locationViewModel = locationViewModel,
                        locations = locations,
                        userPreferences = userPreferences
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
    }
}