package com.example.offlinegpstracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun LocationsScreen(navController: NavHostController, locationViewModel: LocationViewModel) {
    val locations by locationViewModel.locations.collectAsState(initial = emptyList())
    val activeLocations = locations.filter { !it.deleted } // Filter out deleted locations

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Amazon Jungle Locations (${activeLocations.size})",
            style = MaterialTheme.typography.titleLarge
        ) // Display count of locations
        Spacer(modifier = Modifier.height(16.dp))

        activeLocations.forEachIndexed { index, location -> // Use activeLocations and index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable {
                        navController.navigate("location_details_pager/$index")
                    }
            ) {
                Text(
                    "${index + 1}. ${location.name}",
                    style = MaterialTheme.typography.bodyLarge
                ) // Display location number before name
            }
        }
    }
}
