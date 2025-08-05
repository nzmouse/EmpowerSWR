package com.empowerswr.test.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.empowerswr.test.EmpowerViewModel
import com.empowerswr.test.PrefsHelper
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class CheckInInfo(
    val statusText: String,
    val countdownText: String?,
    val isOpen: Boolean,
    val isClosed: Boolean
)

// Format date for subcard titles (EEEE, dd-MMM-yyyy)
private fun formatSubcardTitle(dateString: String?): String {
    if (dateString.isNullOrEmpty()) return "N/A"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("EEEE, dd-MMM-yyyy", Locale.getDefault())
        date?.let { outputFormat.format(it) } ?: "N/A"
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Title date format error for $dateString: ${e.message}")
        "N/A"
    }
}

// Format time for departure (h:mm a)
private fun formatTime(dateString: String?): String {
    if (dateString.isNullOrEmpty()) return "N/A"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        date?.let { outputFormat.format(it) } ?: "N/A"
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Time format error for $dateString: ${e.message}")
        "N/A"
    }
}

// Format arrival time (h:mm a if same day, E, h:mm a if different day)
private fun formatArrivalTime(arrDate: String?, depDate: String?): String {
    if (arrDate.isNullOrEmpty() || depDate.isNullOrEmpty()) return "N/A"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val arr = inputFormat.parse(arrDate)
        val dep = inputFormat.parse(depDate)
        val calArr = Calendar.getInstance().apply { time = arr }
        val calDep = Calendar.getInstance().apply { time = dep }
        val isSameDay = calArr.get(Calendar.YEAR) == calDep.get(Calendar.YEAR) &&
                calArr.get(Calendar.DAY_OF_YEAR) == calDep.get(Calendar.DAY_OF_YEAR)
        val outputFormat = if (isSameDay) {
            SimpleDateFormat("h:mm a", Locale.getDefault())
        } else {
            SimpleDateFormat("E, h:mm a", Locale.getDefault())
        }
        outputFormat.format(arr)
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Arrival time format error for $arrDate: ${e.message}")
        "N/A"
    }
}

// Format for PDB dates (yyyy-MM-dd, add at 8am)
private fun formatPdbDate(dateString: String?): String {
    if (dateString.isNullOrEmpty()) return "N/A"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("EEEE, dd-MMM-yyyy 'at 8am'", Locale.getDefault())
        date?.let { outputFormat.format(it) } ?: "N/A"
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Date format error for $dateString: ${e.message}")
        "N/A"
    }
}

// Calculate and format check-in time
private fun formatCheckInTime(depDate: String?, hoursBefore: Double): String {
    if (depDate.isNullOrEmpty()) return "N/A"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(depDate)
        val calendar = Calendar.getInstance().apply { time = date }
        calendar.add(Calendar.MINUTE, -(hoursBefore * 60).toInt())
        val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        outputFormat.format(calendar.time)
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Check-in time format error for $depDate: ${e.message}")
        "N/A"
    }
}

// Format countdown to a specific time (days, hours, minutes)
private fun formatCountdown(targetTime: Long): String {
    val now = System.currentTimeMillis()
    val diffMillis = targetTime - now
    if (diffMillis <= 0) return "Time passed"
    val days = diffMillis / (1000 * 60 * 60 * 24)
    val hours = (diffMillis % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
    val minutes = (diffMillis % (1000 * 60 * 60)) / (1000 * 60)
    return buildString {
        if (days > 0) append("$days day${if (days > 1) "s" else ""}, ")
        if (hours > 0 || days > 0) append("$hours hour${if (hours > 1) "s" else ""}, ")
        append("$minutes minute${if (minutes > 1) "s" else ""}")
    }
}

// Get check-in info
private fun getCheckInInfo(depDate: String?, hoursBefore: Double, isInternational: Boolean): CheckInInfo {
    if (depDate.isNullOrEmpty()) return CheckInInfo("N/A", null, false, false)
    try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(depDate)
        val calendar = Calendar.getInstance().apply { time = date }
        val checkInCalendar = calendar.clone() as Calendar
        checkInCalendar.add(Calendar.MINUTE, -(hoursBefore * 60).toInt())
        val closeCalendar = calendar.clone() as Calendar
        closeCalendar.add(Calendar.MINUTE, -60) // 1 hour before departure
        val now = Calendar.getInstance()
        val prefix = if (isInternational) "JEK IN" else "Jek in BIFO"
        if (now.before(checkInCalendar)) {
            val countdown = formatCountdown(checkInCalendar.timeInMillis)
            return CheckInInfo(
                statusText = "$prefix at ${formatCheckInTime(depDate, hoursBefore)}",
                countdownText = "Jek in open: $countdown",
                isOpen = false,
                isClosed = false
            )
        } else if (now.before(closeCalendar)) {
            val countdown = formatCountdown(closeCalendar.timeInMillis)
            return CheckInInfo(
                statusText = "$prefix OPEN",
                countdownText = "Jek in closes at: $countdown",
                isOpen = true,
                isClosed = false
            )
        } else {
            return CheckInInfo(
                statusText = "$prefix I KLOS FINIS",
                countdownText = null,
                isOpen = false,
                isClosed = true
            )
        }
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Check-in info error for $depDate: ${e.message}")
        return CheckInInfo("N/A", null, false, false)
    }
}

// Check if dates are on different days
private fun isDifferentDay(date1: String?, date2: String?): Boolean {
    if (date1.isNullOrEmpty() || date2.isNullOrEmpty()) return false
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val d1 = inputFormat.parse(date1)
        val d2 = inputFormat.parse(date2)
        val cal1 = Calendar.getInstance().apply { time = d1 }
        val cal2 = Calendar.getInstance().apply { time = d2 }
        cal1.get(Calendar.YEAR) != cal2.get(Calendar.YEAR) ||
                cal1.get(Calendar.DAY_OF_YEAR) != cal2.get(Calendar.DAY_OF_YEAR)
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Date comparison error: ${e.message}")
        false
    }
}

// Check if today is the departure day
private fun isTodayDepDate(depDate: String?): Boolean {
    if (depDate.isNullOrEmpty()) return false
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dep = inputFormat.parse(depDate)
        val calDep = Calendar.getInstance().apply { time = dep }
        val calNow = Calendar.getInstance()
        calDep.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calDep.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Today check error for $depDate: ${e.message}")
        false
    }
}

// Check if today is the PDB date
private fun isTodayPdbDate(pdbDate: String?): Boolean {
    if (pdbDate.isNullOrEmpty()) return false
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = inputFormat.parse(pdbDate)
        val calDate = Calendar.getInstance().apply { time = date }
        val calNow = Calendar.getInstance()
        calDate.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calDate.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Today check error for $pdbDate: ${e.message}")
        false
    }
}

// Check if PDB is expired (after endDate or startDate if endDate is null)
private fun isPdbExpired(startDate: String?, endDate: String?): Boolean {
    if (startDate.isNullOrEmpty()) return false
    val dateToCheck = endDate ?: startDate
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = inputFormat.parse(dateToCheck)
        val calDate = Calendar.getInstance().apply { time = date }
        val calNow = Calendar.getInstance()
        calNow.after(calDate) && !(
                calNow.get(Calendar.YEAR) == calDate.get(Calendar.YEAR) &&
                        calNow.get(Calendar.DAY_OF_YEAR) == calDate.get(Calendar.DAY_OF_YEAR)
                )
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "PDB expiration check error for $dateToCheck: ${e.message}")
        false
    }
}

// Check if airlines are different
private fun isDifferentAirline(flightNo1: String?, flightNo2: String?): Boolean {
    if (flightNo1.isNullOrEmpty() || flightNo2.isNullOrEmpty()) return false
    return flightNo1.take(2) != flightNo2.take(2)
}

// Format for PDB internal dates (EEEE, dd-MMM, h:mm a)
private fun formatPdbInternalDate(dateString: String?): String {
    if (dateString.isNullOrEmpty()) return "N/A"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("EEEE, dd-MMM, h:mm a", Locale.getDefault())
        date?.let { outputFormat.format(it) } ?: "N/A"
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "PDB internal date format error for $dateString: ${e.message}")
        "N/A"
    }
}

// Check if internal PDB date is expired
private fun isPdbInternalExpired(internalPdb: String?): Boolean {
    if (internalPdb.isNullOrEmpty()) return true
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val date = inputFormat.parse(internalPdb)
        val calDate = Calendar.getInstance().apply { time = date }
        val calNow = Calendar.getInstance()
        calNow.after(calDate)
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Internal PDB expiration check error for $internalPdb: ${e.message}")
        true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightsScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavHostController
) {
    Log.d("EmpowerSWR", "FlightsScreen composable called")
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val localContext = LocalContext.current
    val flightDetails by viewModel.flightDetails
    val pdbDetails by viewModel.pdbDetails
    var flightError by remember { mutableStateOf<String?>(null) }
    var pdbError by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val token by viewModel.token

    // Debug: Log token and workerId
    LaunchedEffect(token) {
        val workerId = PrefsHelper.getWorkerId(context)
        Log.d("EmpowerSWR", "FlightsScreen - Token: $token, WorkerId: $workerId")
        if (token == null || workerId == null) {
            Log.e("EmpowerSWR", "Token or WorkerId is null, navigating to login")
            navController.navigate("login") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Timer trigger for countdown refresh
    var timerTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60000) // Refresh every minute
            timerTrigger++
        }
    }

    LaunchedEffect(token, timerTrigger) {
        if (token == null) {
            Log.d("EmpowerSWR", "No token available, navigating to login")
            navController.navigate("login") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            val workerId = PrefsHelper.getWorkerId(context)
            if (workerId != null) {
                Log.d("EmpowerSWR", "Fetching flight and PDB details for workerId: $workerId")
                try {
                    viewModel.fetchFlightDetails(workerId) { error ->
                        if (error != null) {
                            Log.e("EmpowerSWR", "Flight fetch error: ${error.message}")
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Failed to load flight details: ${error.message}")
                            }
                            flightError = error.message ?: "Failed to load flight details"
                        } else {
                            flightError = null
                        }
                    }
                    viewModel.fetchPdbDetails(workerId) { error ->
                        if (error != null) {
                            Log.e("EmpowerSWR", "PDB fetch error: ${error.message}")
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Failed to load PDB details: ${error.message}")
                            }
                            pdbError = error.message ?: "Failed to load PDB details"
                        } else {
                            pdbError = null
                        }
                    }
                } catch (e: Exception) {
                    Log.e("EmpowerSWR", "Unexpected error in fetch: ${e.message}")
                    flightError = "Unexpected error: ${e.message}"
                    pdbError = "Unexpected error: ${e.message}"
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Unexpected error: ${e.message}")
                    }
                }
            } else {
                Log.e("EmpowerSWR", "No worker ID available")
                flightError = "No worker ID available"
                pdbError = "No worker ID available"
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("No worker ID available")
                }
                navController.navigate("login") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // Handle refresh
    LaunchedEffect(Unit) {
        navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("refresh_flights")
            ?.observe(navController.currentBackStackEntry!!) { refresh ->
                if (refresh) {
                    coroutineScope.launch {
                        delay(500)
                        val workerId = PrefsHelper.getWorkerId(context)
                        if (workerId != null && token != null) {
                            Log.d("EmpowerSWR", "Refreshing flight and PDB details for workerId: $workerId")
                            try {
                                viewModel.fetchFlightDetails(workerId) { error ->
                                    flightError = error?.message ?: "Failed to refresh flight details"
                                    if (error != null) {
                                        Log.e("EmpowerSWR", "Flight refresh error: ${error.message}")
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Failed to refresh flight details: ${error.message}")
                                        }
                                    } else {
                                        flightError = null
                                    }
                                }
                                viewModel.fetchPdbDetails(workerId) { error ->
                                    pdbError = error?.message ?: "Failed to refresh PDB details"
                                    if (error != null) {
                                        Log.e("EmpowerSWR", "PDB refresh error: ${error.message}")
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Failed to refresh PDB details: ${error.message}")
                                        }
                                    } else {
                                        pdbError = null
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("EmpowerSWR", "Refresh error: ${e.message}")
                                flightError = "Refresh failed: ${e.message}"
                                pdbError = "Refresh failed: ${e.message}"
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Refresh failed: ${e.message}")
                                }
                            }
                        } else {
                            Log.e("EmpowerSWR", "No worker ID or token available for refresh")
                            flightError = "No worker ID or token available"
                            pdbError = "No worker ID or token available"
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("No worker ID or token available")
                            }
                            navController.navigate("login") {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("refresh_flights")
                }
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                Log.d("EmpowerSWR", "Swipe-to-refresh triggered")
                coroutineScope.launch {
                    isRefreshing = true
                    try {
                        val workerId = PrefsHelper.getWorkerId(context)
                        if (workerId != null && token != null) {
                            Log.d("EmpowerSWR", "Refresh workerId: $workerId")
                            viewModel.fetchFlightDetails(workerId) { error ->
                                flightError = error?.message?.let { "Refresh failed: $it" } ?: "Refresh failed"
                                Log.e("EmpowerSWR", "Flight refresh error: $flightError")
                                if (error != null) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Failed to refresh flight details: ${error.message}")
                                    }
                                } else {
                                    flightError = null
                                }
                            }
                            viewModel.fetchPdbDetails(workerId) { error ->
                                pdbError = error?.message?.let { "Refresh failed: $it" } ?: "Refresh failed"
                                Log.e("EmpowerSWR", "PDB refresh error: $pdbError")
                                if (error != null) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Failed to refresh PDB details: ${error.message}")
                                    }
                                } else {
                                    pdbError = null
                                }
                            }
                            delay(1000)
                        } else {
                            Log.e("EmpowerSWR", "No worker ID or token available for refresh")
                            flightError = "No worker ID or token available"
                            pdbError = "No worker ID or token available"
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("No worker ID or token available")
                            }
                            navController.navigate("login") {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EmpowerSWR", "Refresh error: ${e.message}")
                        flightError = "Refresh failed: ${e.message}"
                        pdbError = "Refresh failed: ${e.message}"
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Refresh failed: ${e.message}")
                        }
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Flights and Pre-Departure",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Pre-Departure Details Card
                pdbDetails?.let { details ->
                    // Check if both PDB and Internal PDB are expired
                    val isPdbExpired = isPdbExpired(details.startDate, details.endDate)
                    val isInternalPdbExpired = isPdbInternalExpired(details.internalPdb)
                    if (!isPdbExpired || !isInternalPdbExpired) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "Pre-Departure Details",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // PDB Subcard
                                if (!isPdbExpired) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "PDB Details",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = formatPdbDate(details.startDate),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (details.schemes == "PALM" && details.endDate != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = formatPdbDate(details.endDate),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = details.pdbLocationLong ?: "N/A",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "PDB Status: ${details.pdbStatus ?: "N/A"}",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    val lat = details.pdbLat
                                                    val lng = details.pdbLong
                                                    Log.d("EmpowerSWR", "Opening Maps with pdbLat: $lat, pdbLong: $lng")
                                                    if (lat != null && lng != null) {
                                                        val label = details.pdbLocationLong ?: "PDB Location"
                                                        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)&z=15")
                                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                            setPackage("com.google.android.apps.maps")
                                                        }
                                                        try {
                                                            localContext.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Log.e("EmpowerSWR", "Map open error: ${e.message}")
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Failed to open Maps: ${e.message}")
                                                            }
                                                        }
                                                    } else {
                                                        Log.e("EmpowerSWR", "No location coordinates available")
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("No location coordinates available")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Open in Maps")
                                            }
                                            if (details.pdbStatus == "Messaged" || details.pdbStatus == "None") {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = {
                                                        val workerId = PrefsHelper.getWorkerId(localContext)
                                                        Log.d("EmpowerSWR", "Confirm PDB Status clicked, workerId: $workerId")
                                                        if (workerId == null) {
                                                            Log.e("EmpowerSWR", "No worker ID available for PDB status update")
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("No worker ID available")
                                                            }
                                                            return@Button
                                                        }
                                                        viewModel.updatePdbStatus(workerId) { success, message ->
                                                            Log.d("EmpowerSWR", "PDB status update result: success=$success, message=$message")
                                                            coroutineScope.launch {
                                                                if (success) {
                                                                    snackbarHostState.showSnackbar("PDB status updated to App OK")
                                                                } else {
                                                                    snackbarHostState.showSnackbar("Failed to update PDB status: ${message ?: "Unknown error"}")
                                                                }
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Confirm PDB Status")
                                                }
                                            }
                                            if (isTodayPdbDate(details.startDate) || isTodayPdbDate(details.endDate)) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Button(
                                                    onClick = {
                                                        val workerId = PrefsHelper.getWorkerId(localContext)
                                                        Log.d("EmpowerSWR", "Checked In clicked, workerId: $workerId")
                                                        if (workerId == null) {
                                                            Log.e("EmpowerSWR", "No worker ID available for check-in")
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("No worker ID available")
                                                            }
                                                            return@Button
                                                        }
                                                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(localContext)
                                                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                                            if (location != null) {
                                                                val lat = location.latitude
                                                                val lng = location.longitude
                                                                val calNow = Calendar.getInstance()
                                                                val amPm = if (calNow.get(Calendar.HOUR_OF_DAY) < 12) "am" else "pm"
                                                                val isStart = isTodayPdbDate(details.startDate)
                                                                val prefix = if (isStart) "PDB1" else "PDB2"
                                                                val action = "$prefix-$amPm"
                                                                viewModel.saveLocation(workerId, lat, lng, action)
                                                                Log.d("EmpowerSWR", "Location saved: lat=$lat, lng=$lng, action=$action")
                                                                coroutineScope.launch {
                                                                    snackbarHostState.showSnackbar("Location recorded for $action")
                                                                }
                                                            } else {
                                                                Log.e("EmpowerSWR", "Unable to get location for Checked In")
                                                                coroutineScope.launch {
                                                                    snackbarHostState.showSnackbar("Unable to get location")
                                                                }
                                                            }
                                                        }.addOnFailureListener { e ->
                                                            Log.e("EmpowerSWR", "Location fetch failed for Checked In: ${e.message}")
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Failed to get location: ${e.message}")
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("I am at PDB Now")
                                                }
                                            }
                                        }
                                    }
                                }

                                // Horizontal Divider if both subcards are visible
                                if (!isPdbExpired && !isInternalPdbExpired) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                // Internal PDB Subcard
                                if (!isInternalPdbExpired) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Internal PDB Details",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "${formatPdbInternalDate(details.internalPdb)} at our office",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Internal PDB Status: ${details.internalPdbStatus ?: "N/A"}",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (details.internalPdbStatus == "Messaged" || details.internalPdbStatus == "Unaware") {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Button(
                                                    onClick = {
                                                        val workerId = PrefsHelper.getWorkerId(localContext)
                                                        Log.d("EmpowerSWR", "Confirm Internal PDB Status clicked, workerId: $workerId")
                                                        if (workerId == null) {
                                                            Log.e("EmpowerSWR", "No worker ID available for internal PDB status update")
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("No worker ID available")
                                                            }
                                                            return@Button
                                                        }
                                                        viewModel.updatePdbInternalStatus(workerId) { success, message ->
                                                            Log.d("EmpowerSWR", "Internal PDB status update result: success=$success, message=$message")
                                                            coroutineScope.launch {
                                                                if (success) {
                                                                    snackbarHostState.showSnackbar("Internal PDB status updated to App OK")
                                                                } else {
                                                                    snackbarHostState.showSnackbar("Failed to update Internal PDB status: ${message ?: "Unknown error"}")
                                                                }
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Confirm Internal PDB Status")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } ?: run {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = pdbError ?: "Loading PDB details...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // Flight Details Card
                flightDetails?.let { details ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Flight Details",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (details.flightStatus == "Unaware" || details.flightStatus == "Messaged") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(0.5f),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Button(
                                        onClick = {
                                            val workerId = PrefsHelper.getWorkerId(localContext)
                                            Log.d("EmpowerSWR", "Confirm Flight Status clicked, workerId: $workerId")
                                            if (workerId == null) {
                                                Log.e("EmpowerSWR", "No worker ID available for flight status update")
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("No worker ID available")
                                                }
                                                return@Button
                                            }
                                            viewModel.updateFlightStatus(workerId) { success, message ->
                                                Log.d("EmpowerSWR", "Flight status update result: success=$success, message=$message")
                                                coroutineScope.launch {
                                                    if (success) {
                                                        snackbarHostState.showSnackbar("Flight status updated to Alerted(App)")
                                                    } else {
                                                        snackbarHostState.showSnackbar("Failed to update flight status: ${message ?: "Unknown error"}")
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Confirm Flight Status")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Flight Status: ${details.flightStatus ?: "N/A"}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // First Leg (International)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = formatSubcardTitle(details.intDepDate),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = getCheckInInfo(details.intDepDate, 2.5, true).statusText,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.ui.graphics.Color.Red
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = getCheckInInfo(details.intDepDate, 2.5, true).countdownText ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = "https://db.nougro.com/images/airlines/${details.intFlightNo.take(2)}.png",
                                            contentDescription = "Airline Logo",
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${details.intFlightNo} to ${details.intDest}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = formatTime(details.intDepDate),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.AirplanemodeActive,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.rotate(90f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formatArrivalTime(details.intArrDate, details.intDepDate),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (isTodayDepDate(details.intDepDate) && !getCheckInInfo(details.intDepDate, 2.5, true).isClosed) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                val workerId = PrefsHelper.getWorkerId(localContext)
                                                Log.d("EmpowerSWR", "Checked In clicked, workerId: $workerId")
                                                if (workerId == null) {
                                                    Log.e("EmpowerSWR", "No worker ID available for check-in")
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("No worker ID available")
                                                    }
                                                    return@Button
                                                }
                                                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(localContext)
                                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                                    if (location != null) {
                                                        val lat = location.latitude
                                                        val lng = location.longitude
                                                        val action = "Checked In"
                                                        viewModel.saveLocation(workerId, lat, lng, action)
                                                        Log.d("EmpowerSWR", "Location saved: lat=$lat, lng=$lng, action=$action")
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Location recorded for $action")
                                                        }
                                                    } else {
                                                        Log.e("EmpowerSWR", "Unable to get location for Checked In")
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Unable to get location")
                                                        }
                                                    }
                                                }.addOnFailureListener { e ->
                                                    Log.e("EmpowerSWR", "Location fetch failed for Checked In: ${e.message}")
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Failed to get location: ${e.message}")
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(0.5f)
                                        ) {
                                            Text("Checked In")
                                        }
                                    }
                                }
                            }

                            if (details.hotel1 != null && details.hotel1.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = details.hotel1,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            // Second Leg (Domestic)
                            if (details.domFlightNo != null && details.domFlightNo.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = formatSubcardTitle(details.domDepDate),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (isDifferentDay(details.intDepDate, details.domDepDate) ||
                                            isDifferentAirline(details.intFlightNo, details.domFlightNo)
                                        ) {
                                            Text(
                                                text = getCheckInInfo(details.domDepDate, 1.0, false).statusText,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = androidx.compose.ui.graphics.Color.Red
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = getCheckInInfo(details.domDepDate, 1.0, false).countdownText ?: "",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
                                                model = "https://db.nougro.com/images/airlines/${details.domFlightNo.take(2)}.png",
                                                contentDescription = "Airline Logo",
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${details.domFlightNo} to ${details.domDest ?: "N/A"}",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = formatTime(details.domDepDate),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.AirplanemodeActive,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.rotate(90f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = formatArrivalTime(details.domArrDate, details.domDepDate),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }

                            if (details.hotel2 != null && details.hotel2.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = details.hotel2,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            // Third Leg
                            if (details.dom2FlightNo != null && details.dom2FlightNo.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = formatSubcardTitle(details.dom2DepDate),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (isDifferentDay(details.domDepDate, details.dom2DepDate) ||
                                            isDifferentAirline(details.domFlightNo, details.dom2FlightNo)
                                        ) {
                                            Text(
                                                text = getCheckInInfo(details.dom2DepDate, 1.0, false).statusText,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = androidx.compose.ui.graphics.Color.Red
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = getCheckInInfo(details.dom2DepDate, 1.0, false).countdownText ?: "",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
                                                model = "https://db.nougro.com/images/airlines/${details.dom2FlightNo.take(2)}.png",
                                                contentDescription = "Airline Logo",
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${details.dom2FlightNo} to ${details.dom2Dest ?: "N/A"}",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = formatTime(details.dom2DepDate),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.AirplanemodeActive,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.rotate(90f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = formatArrivalTime(details.dom2ArrDate, details.dom2DepDate),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        val workerId = PrefsHelper.getWorkerId(localContext)
                                        Log.d("EmpowerSWR", "Mi Stap lo Ples ia clicked, workerId: $workerId")
                                        if (workerId == null) {
                                            Log.e("EmpowerSWR", "No worker ID available for travel location")
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("No worker ID available")
                                            }
                                            return@Button
                                        }
                                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(localContext)
                                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                            if (location != null) {
                                                val lat = location.latitude
                                                val lng = location.longitude
                                                val action = "Travel Location"
                                                viewModel.saveLocation(workerId, lat, lng, action)
                                                Log.d("EmpowerSWR", "Location saved: lat=$lat, lng=$lng, action=$action")
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Location recorded for $action")
                                                }
                                            } else {
                                                Log.e("EmpowerSWR", "Unable to get location for Mi Stap lo Ples ia")
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Unable to get location")
                                                }
                                            }
                                        }.addOnFailureListener { e ->
                                            Log.e("EmpowerSWR", "Location fetch failed for Mi Stap lo Ples ia: ${e.message}")
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Failed to get location: ${e.message}")
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Mi Stap lo Ples ia!")
                                }
                            }
                        }
                    }
                } ?: run {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = flightError ?: "Loading flight details...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}