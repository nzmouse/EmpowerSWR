package com.empowerswr.luksave.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.empowerswr.luksave.DirectoryEntry
import com.empowerswr.luksave.EmpowerViewModel
import com.empowerswr.luksave.PrefsHelper
import com.empowerswr.luksave.NearbyPlace
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber

@Composable
fun InformationScreen(
    viewModel: EmpowerViewModel,
    navController: NavHostController,
    context: Context = LocalContext.current
) {
    val directoryEntries by viewModel.directoryEntries.collectAsState()
    val token by viewModel.token
    val workerId = PrefsHelper.getWorkerId(context) ?: ""
    val coroutineScope = rememberCoroutineScope()
    var fetchError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val localContext = LocalContext.current

    // Nearby services state
    val currentLocation by viewModel.currentLocation.collectAsState()
    var nearbyPlaces by remember { mutableStateOf<List<NearbyPlace>>(emptyList()) }
    var isNearbyLoading by remember { mutableStateOf(false) }

    // Log screen usage
    LaunchedEffect(Unit) {
        Timber.i("ScreenUsage: InformationScreen displayed, workerId=${PrefsHelper.getWorkerId(context) ?: "unknown"}, timestamp=${System.currentTimeMillis()}")
    }

    // Fetch directory (your original logic)
    LaunchedEffect(token, workerId) {
        if (token == null || workerId.isEmpty()) {
            navController.navigate("login") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            try {
                viewModel.fetchDirectory(localContext)
                fetchError = null
            } catch (e: HttpException) {
                val errorMessage = when (e.code()) {
                    404 -> "Directory endpoint not found (HTTP 404). Please check server configuration."
                    401 -> "Invalid or expired token. Please log in again."
                    else -> "Failed to load directory: ${e.message()}"
                }
                fetchError = errorMessage
                Timber.tag("InformationScreen").e(e, errorMessage)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(errorMessage)
                }
            } catch (e: Exception) {
                fetchError = "Failed to load directory: ${e.message}"
                Timber.tag("InformationScreen").e(e, "Fetch directory error")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to load directory: ${e.message}")
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (token == null || workerId.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Please log in to view directory",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = {
                        navController.navigate("login") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Go to Login")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
        // ==================== NEARBY SERVICES SECTION - FINAL VERSION ====================
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Nearby Services (within 25 km)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = {
                                    viewModel.refreshCurrentLocation(localContext)
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh location")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isNearbyLoading) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                                Text(
                                    text = "Finding nearby banks, supermarkets, churches & Western Union...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            } else if (nearbyPlaces.isEmpty()) {
                                Text(
                                    text = "Tap Refresh to find nearby services based on your current location.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 500.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(nearbyPlaces) { place ->
                                        NearbyServiceCard(place) { lat, lng ->
                                            val label = "${place.name} (${place.type})"
                                            val uri = "geo:$lat,$lng?q=$lat,$lng($label)&z=15".toUri()
                                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                setPackage("com.google.android.apps.maps")
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Timber.e(e, "Map open error")
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Failed to open Maps")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // ==================== END NEARBY SERVICES ====================
                // Your original grouped directory cards
                if (directoryEntries.isEmpty() && fetchError != null) {
                    item {
                        Text(
                            text = fetchError ?: "Directory Loading...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    val groupedEntries = directoryEntries.groupBy { it.dirCard }.entries.sortedBy { it.key }
                    groupedEntries.forEach { (dirCard, entries) ->
                        item {
                            Text(
                                text = dirCard,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(entries) { entry ->
                            DirectoryCard(entry) { lat, long ->
                                val label = entry.dirName
                                val uri = "geo:$lat,$long?q=$lat,$long($label)&z=15".toUri()
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Timber.tag("InformationScreen").e(e, "Map open error")
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Failed to open Maps: ${e.message}")
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // ✅ CORRECT POSITION: LaunchedEffect must be inside the else block, after LazyColumn
            LaunchedEffect(currentLocation) {
                val location = currentLocation
                if (location != null) {
                    Timber.i("📍 Location changed → lat=${location.latitude}, lng=${location.longitude}")
                    isNearbyLoading = true

                    coroutineScope.launch {
                        try {
                            Timber.i("🔄 Starting fetchNearbyServices...")
                            nearbyPlaces = viewModel.fetchNearbyServices(
                                lat = location.latitude,
                                lng = location.longitude,
                                radiusKm = 25
                            )
                            Timber.i("✅ fetchNearbyServices completed. Places: ${nearbyPlaces.size}")
                        } catch (e: Exception) {
                            Timber.e(e, "❌ fetchNearbyServices error")
                            nearbyPlaces = emptyList()
                        } finally {
                            isNearbyLoading = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NearbyServiceCard(place: NearbyPlace, onMapClick: (Double, Double) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Line 1: Name (Type)
                Text(
                    text = "${place.name} (${place.type})",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Line 2: Distance + Address
                Text(
                    text = "${"%.1f".format(place.distanceKm)} km • ${place.address}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Line 3: Phone (if available)
                place.phone?.let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Map button (top right, same as DirectoryCard)
            IconButton(
                onClick = { onMapClick(place.latLng.latitude, place.latLng.longitude) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "View on Google Maps",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// Your original DirectoryCard (unchanged - kept exactly as you provided)
@Composable
fun DirectoryCard(entry: DirectoryEntry, onMapClick: (Double, Double) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Line 1: dirName (dirType)
                Text(
                    text = "${entry.dirName} (${entry.dirType})",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Line 2: Phone numbers with icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Phone",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = buildString {
                            if (!entry.dirPhone.isNullOrEmpty()) append(entry.dirPhone)
                            if (!entry.dirPhone.isNullOrEmpty() && !entry.dirPhone2.isNullOrEmpty()) append(", ")
                            if (!entry.dirPhone2.isNullOrEmpty()) append(entry.dirPhone2)
                            if (entry.dirPhone.isNullOrEmpty() && entry.dirPhone2.isNullOrEmpty()) append("No phone numbers")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Line 3: Email with icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Email",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = entry.dirEmail ?: "No email",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (entry.dirLat != null && entry.dirLong != null && entry.dirLat != 0.0f && entry.dirLong != 0.0f) {
                IconButton(
                    onClick = { onMapClick(entry.dirLat.toDouble(), entry.dirLong.toDouble()) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "View on Google Maps",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}