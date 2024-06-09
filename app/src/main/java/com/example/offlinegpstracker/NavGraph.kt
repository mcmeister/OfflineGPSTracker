package com.example.offlinegpstracker

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun NavGraph(navController: NavHostController, locationViewModel: LocationViewModel) {
    NavHost(
        navController = navController,
        startDestination = "locations_list"
    ) {
        composable("locations_list") {
            LocationsScreen(navController = navController, locationViewModel = locationViewModel)
        }
        composable("location_details_pager/{startIndex}") { backStackEntry ->
            val startIndex = backStackEntry.arguments?.getString("startIndex")?.toIntOrNull() ?: 0
            LocationDetailsPager(navController = navController, locationViewModel = locationViewModel, startIndex = startIndex)
        }
    }
}
