package com.example.offlinegpstracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

@Composable
fun LocationsScreen(
    navController: NavHostController,
    locationViewModel: LocationViewModel = viewModel(),
    locations: List<Location>,
    userPreferences: UserPreferences
) {
    val activeLocations = locations.filter { it.latitude != 0.0 && it.longitude != 0.0 && it.name.isNotEmpty() }

    val compassType by userPreferences.compassType.collectAsState(initial = 0)
    val compassSkin by userPreferences.compassSkin.collectAsState(initial = UserPreferences.SKIN_CLASSIC_GAUGE)

    val textColor = when {
        compassType == 2 && compassSkin == UserPreferences.SKIN_NEON_GAUGE -> Color.Cyan
        compassType == 2 && compassSkin == UserPreferences.SKIN_CLASSIC_GAUGE -> Color.Black
        compassType == 2 && compassSkin == UserPreferences.SKIN_MINIMAL_GAUGE -> Color.White
        else -> Color.Black
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            "My Locations (${activeLocations.size})",
            style = MaterialTheme.typography.titleLarge,
            color = textColor,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        activeLocations.forEachIndexed { index, location ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                elevation = CardDefaults.elevatedCardElevation(4.dp),
                colors = CardDefaults.cardColors( // ✅ Ensures white background
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .clickable {
                            navController.navigate("location_details/$index")
                        },
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${index + 1}. ${location.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { locationViewModel.deleteLocation(location.id) },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}