package com.example.offlinegpstracker

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState

@OptIn(ExperimentalPagerApi::class)
@Composable
fun NavGraph(navController: NavHostController, locationViewModel: LocationViewModel, modifier: Modifier = Modifier, pagerState: com.google.accompanist.pager.PagerState, locations: List<Location>) {

    NavHost(
        navController = navController,
        startDestination = "main",
        modifier = modifier
    ) {
        composable("main") {
            HorizontalPager(
                count = 2,
                state = pagerState,
                modifier = Modifier.padding(16.dp)
            ) { page ->
                when (page) {
                    0 -> GPSTrackerScreen(locationViewModel)
                    1 -> LocationsScreen(navController = navController, locationViewModel = locationViewModel)
                }
            }
        }
        composable("location_details/{locationId}") { backStackEntry ->
            val locationId = backStackEntry.arguments?.getString("locationId")?.toIntOrNull() ?: 0
            LocationDetailsScreen(navController = navController, locationViewModel = locationViewModel, startIndex = locationId)
        }
        composable("navigator/{locationId}/{locationName}") { backStackEntry ->
            val locationId = backStackEntry.arguments?.getString("locationId")?.toIntOrNull() ?: 0
            val locationName = backStackEntry.arguments?.getString("locationName") ?: ""
            val location = locationId.let { locationViewModel.getLocationById(it).collectAsState(initial = null).value }
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
