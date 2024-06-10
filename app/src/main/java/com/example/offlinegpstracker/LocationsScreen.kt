package com.example.offlinegpstracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LocationsScreen(navController: NavHostController, locationViewModel: LocationViewModel = viewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val locations by locationViewModel.locations.collectAsStateWithLifecycle(lifecycle = lifecycleOwner.lifecycle)
    val activeLocations = locations.filter { it.latitude != 0.0 && it.longitude != 0.0 && it.name.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Amazon Jungle Locations (${activeLocations.size})",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        activeLocations.forEachIndexed { index, location ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable {
                        navController.navigate("location_details/$index")
                    },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${index + 1}. ${location.name}",
                    style = MaterialTheme.typography.bodyLarge
                )
                IconButton(onClick = {
                    locationViewModel.deleteLocation(location.id)
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
