package com.example.offlinegpstracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

@Composable
fun LocationsScreen(
    navController: NavHostController,
    locationViewModel: LocationViewModel = viewModel(),
    locations: List<Location>  // ✅ Accept locations as a parameter
) {
    val activeLocations = locations.filter { it.latitude != 0.0 && it.longitude != 0.0 && it.name.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), // Makes screen scrollable
        verticalArrangement = Arrangement.Top
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
