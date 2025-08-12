package com.empowerswr.luksave.ui.screens

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.empowerswr.luksave.EmpowerViewModel
import com.empowerswr.luksave.PrefsHelper
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
data class CheckInInfo(
    val statusText: String,
    val countdownText: String?,
    val isOpen: Boolean,
    val isClosed: Boolean
)

// Check network availability
private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

// Format date for subcard titles (EEEE, dd-MMM-yyyy)
private fun formatSubcardTitle(dateString: String?): String {
    if (dateString.isNullOrEmpty()) return "N/A"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("EEEE, dd-MMM-yyyy", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
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
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
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
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
        val arr = inputFormat.parse(arrDate)
        val dep = inputFormat.parse(depDate)
        val calArr = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate")).apply { time = arr }
        val calDep = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate")).apply { time = dep }
        val isSameDay = calArr.get(Calendar.YEAR) == calDep.get(Calendar.YEAR) &&
                calArr.get(Calendar.DAY_OF_YEAR) == calDep.get(Calendar.DAY_OF_YEAR)
        val outputFormat = if (isSameDay) {
            SimpleDateFormat("h:mm a", Locale.getDefault())
        } else {
            SimpleDateFormat("E, h:mm a", Locale.getDefault())
        }.apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
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
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("EEEE, dd-MMM-yyyy 'at 8am'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
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
        val vutZone = java.time.ZoneId.of("Pacific/Efate")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(vutZone)
        val departureTime = ZonedDateTime.parse(depDate, formatter)
        val checkInTime = departureTime.minusHours(hoursBefore.toLong())
            .minusMinutes(((hoursBefore % 1.0) * 60).toLong())
        val outputFormat = DateTimeFormatter.ofPattern("h:mm a 'VUT'").withZone(vutZone)
        outputFormat.format(checkInTime)
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Check-in time format error for $depDate: ${e.message}")
        "N/A"
    }
}

// Format countdown to a specific time (days, hours, minutes)
private fun formatCountdown(targetTime: Long): String {
    val deviceTimeZone = TimeZone.getDefault()
    val nowCalendar = Calendar.getInstance(deviceTimeZone)
    val now = nowCalendar.timeInMillis
    val diffMillis = targetTime - now + 60000 // Add 1 minute (60,000 ms)
    if (diffMillis <= 0) return "Time passed"
    val days = diffMillis / (1000 * 60 * 60 * 24)
    val hours = (diffMillis % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
    val minutes = (diffMillis % (1000 * 60 * 60)) / (1000 * 60)
    Log.d("EmpowerSWR", "formatCountdown: now=$now (device TZ: ${deviceTimeZone.id}), targetTime=$targetTime, diffMillis=$diffMillis")
    return buildString {
        if (days > 0) append("$days day${if (days > 1) "s" else ""}, ")
        if (hours > 0 || days > 0) append("$hours hour${if (hours > 1) "s" else ""}, ")
        append("$minutes minute${if (minutes > 1) "s" else ""}")
    }
}

// Get check-in info
private fun getCheckInInfo(depDate: String?, hoursBefore: Double, isInternational: Boolean): CheckInInfo {
    val prefix = if (isInternational) "JEK IN" else "Jek in BIFO"
    if (depDate.isNullOrEmpty()) return CheckInInfo("N/A", null, false, false)
    try {
        val vutZone = java.time.ZoneId.of("Pacific/Efate")
        val deviceZone = java.time.ZoneId.systemDefault()
        Log.d("EmpowerSWR", "Device timezone: ${deviceZone.id}")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(vutZone)
        val departureTime = ZonedDateTime.parse(depDate, formatter)
        val checkInTime = departureTime.minusHours(hoursBefore.toLong())
            .minusMinutes(((hoursBefore % 1.0) * 60).toLong())
        val closeTime = departureTime.minusHours(1)
        val now = ZonedDateTime.now(deviceZone)
        val outputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(vutZone)
        Log.d(
            "EmpowerSWR",
            "getCheckInInfo: depDate=$depDate (VUT, epoch=${departureTime.toInstant().toEpochMilli()}), " +
                    "checkInTime=${outputFormat.format(checkInTime)} (epoch=${checkInTime.toInstant().toEpochMilli()}), " +
                    "closeTime=${outputFormat.format(closeTime)} (epoch=${closeTime.toInstant().toEpochMilli()}), " +
                    "now=${outputFormat.format(now)} (device TZ: ${deviceZone.id}, epoch=${now.toInstant().toEpochMilli()})"
        )
        if (now.isBefore(checkInTime)) {
            val countdown = formatCountdown(checkInTime.toInstant().toEpochMilli())
            return CheckInInfo(
                statusText = "$prefix at ${formatCheckInTime(depDate, hoursBefore)}",
                countdownText = "Jek in open: $countdown",
                isOpen = false,
                isClosed = false
            )
        } else if (now.isBefore(closeTime)) {
            val countdown = formatCountdown(closeTime.toInstant().toEpochMilli())
            return CheckInInfo(
                statusText = "$prefix OPEN",
                countdownText = "Jek in closes in: $countdown",
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
        return CheckInInfo("$prefix Error: ${e.message}", null, false, false)
    }
}

// Check if dates are on different days
private fun isDifferentDay(date1: String?, date2: String?): Boolean {
    if (date1.isNullOrEmpty() || date2.isNullOrEmpty()) return false
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
        val d1 = inputFormat.parse(date1)
        val d2 = inputFormat.parse(date2)
        val cal1 = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate")).apply { time = d1 }
        val cal2 = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate")).apply { time = d2 }
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
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
        val dep = inputFormat.parse(depDate)
        val calDep = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate")).apply { time = dep }
        val calNow = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate"))
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
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
        val date = inputFormat.parse(pdbDate)
        val calDate = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate")).apply { time = date }
        val calNow = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate"))
        Log.d("EmpowerSWR", "isTodayPdbDate: pdbDate=$pdbDate, calDate=${calDate.time}, calNow=${calNow.time}, isToday=${calDate.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) && calDate.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)}")
        calDate.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calDate.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Today check error for $pdbDate: ${e.message}")
        false
    }
}

// Check if PDB date is today or in the future
private fun isValidPdbDate(pdbDate: String?): Boolean {
    if (pdbDate.isNullOrEmpty()) return false
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
        val date = inputFormat.parse(pdbDate)
        val calDate = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate")).apply { time = date }
        val calNow = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate"))
        Log.d("EmpowerSWR", "isValidPdbDate: pdbDate=$pdbDate, calDate=${calDate.time}, calNow=${calNow.time}, isValid=${calDate.get(Calendar.YEAR) > calNow.get(Calendar.YEAR) || (calDate.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) && calDate.get(Calendar.DAY_OF_YEAR) >= calNow.get(Calendar.DAY_OF_YEAR))}")
        calDate.get(Calendar.YEAR) > calNow.get(Calendar.YEAR) ||
                (calDate.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                        calDate.get(Calendar.DAY_OF_YEAR) >= calNow.get(Calendar.DAY_OF_YEAR))
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Valid PDB date check error for $pdbDate: ${e.message}")
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
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("EEEE, dd-MMM, h:mm a", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
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
        val inputFormat = when {
            internalPdb.contains(" ") -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }.apply {
            timeZone = TimeZone.getTimeZone("Pacific/Efate")
        }
        val date = inputFormat.parse(internalPdb)
        val calDate = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate")).apply { time = date }
        val calNow = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate"))
        Log.d("EmpowerSWR", "isPdbInternalExpired: internalPdb=$internalPdb, calDate=${calDate.time}, calNow=${calNow.time}, isExpired=${calNow.after(calDate)}")
        calNow.after(calDate)
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Internal PDB expiration check error for $internalPdb: ${e.message}")
        true
    }
}

// Determine PDB action based on date and time
private fun getPdbAction(startDate: String?, endDate: String?): String {
    val now = Calendar.getInstance(TimeZone.getTimeZone("Pacific/Efate"))
    val isMorning = now.get(Calendar.HOUR_OF_DAY) < 12
    return when {
        isTodayPdbDate(startDate) -> if (isMorning) "PDB1-am" else "PDB1-pm"
        isTodayPdbDate(endDate) -> if (isMorning) "PDB2-am" else "PDB2-pm"
        else -> "Confirming at PDB" // Fallback
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
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val isNetworkAvailable = remember { mutableStateOf(isNetworkAvailable(localContext)) }
    val isScreenActive = remember { mutableStateOf(true) }
    var lastNetworkErrorShown by remember { mutableStateOf(0L) }

    // Monitor lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    Log.d("EmpowerSWR", "Lifecycle: ON_START - Screen active")
                    isScreenActive.value = true
                    isNetworkAvailable.value = isNetworkAvailable(localContext)
                }
                Lifecycle.Event.ON_STOP -> {
                    Log.d("EmpowerSWR", "Lifecycle: ON_STOP - Screen likely went to sleep")
                    isScreenActive.value = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    Log.d("EmpowerSWR", "Lifecycle: ON_RESUME - Checking network")
                    isNetworkAvailable.value = isNetworkAvailable(localContext)
                    if (isNetworkAvailable.value && token != null && isScreenActive.value) {
                        val workerId = PrefsHelper.getWorkerId(localContext)
                        if (workerId != null) {
                            Log.d("EmpowerSWR", "ON_RESUME: Refreshing data for workerId: $workerId")
                            viewModel.fetchFlightDetails(workerId) { error ->
                                if (error != null) {
                                    Log.e("EmpowerSWR", "Flight fetch error on resume: ${error.message}")
                                    flightError = error.message ?: "Failed to load flight details"
                                    if (System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Check your internet and try again")
                                            lastNetworkErrorShown = System.currentTimeMillis()
                                        }
                                    }
                                } else {
                                    Log.d("EmpowerSWR", "No flight data available on resume")
                                    flightError = null
                                }
                            }
                            viewModel.fetchPdbDetails(workerId) { error ->
                                if (error != null && error.message != "No pre-departure details available") {
                                    Log.e("EmpowerSWR", "PDB fetch error on resume: ${error.message}")
                                    pdbError = error.message ?: "Failed to load PDB details"
                                    if (System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Check your internet and try again")
                                            lastNetworkErrorShown = System.currentTimeMillis()
                                        }
                                    }
                                } else {
                                    Log.d("EmpowerSWR", "No PDB data available on resume")
                                    pdbError = null
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Timer trigger for countdown refresh
    var timerTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(isScreenActive.value) {
        if (isScreenActive.value) {
            while (true) {
                delay(60000) // Refresh every minute only when screen is active
                timerTrigger++
                Log.d("EmpowerSWR", "Timer trigger incremented: $timerTrigger")
            }
        }
    }

    LaunchedEffect(token, timerTrigger) {
        Log.d("EmpowerSWR", "Token: $token")
        if (token == null) {
            Log.d("EmpowerSWR", "No token available, navigating to login")
            navController.navigate("login") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        } else if (isNetworkAvailable.value && isScreenActive.value) {
            val workerId = PrefsHelper.getWorkerId(context)
            if (workerId != null) {
                Log.d("EmpowerSWR", "Fetching flight and PDB details for workerId: $workerId")
                viewModel.fetchFlightDetails(workerId) { error ->
                    if (error != null) {
                        Log.e("EmpowerSWR", "Flight fetch error: ${error.message}")
                        flightError = error.message ?: "Failed to load flight details"
                        if (System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Check your internet and try again")
                                lastNetworkErrorShown = System.currentTimeMillis()
                            }
                        }
                    } else {
                        Log.d("EmpowerSWR", "No flight data available")
                        flightError = null
                    }
                }
                viewModel.fetchPdbDetails(workerId) { error ->
                    if (error != null && error.message != "No pre-departure details available") {
                        Log.e("EmpowerSWR", "PDB fetch error: ${error.message}")
                        pdbError = error.message ?: "Failed to load PDB details"
                        if (System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Check your internet and try again")
                                lastNetworkErrorShown = System.currentTimeMillis()
                            }
                        }
                    } else {
                        Log.d("EmpowerSWR", "No PDB data available")
                        pdbError = null
                    }
                }
            } else {
                Log.e("EmpowerSWR", "No worker ID available")
                flightError = "No worker ID available"
                pdbError = "No worker ID available"
                // Removed snackbar for "No worker ID available"
            }
        } else if (!isNetworkAvailable.value) {
            Log.w("EmpowerSWR", "No network, skipping data fetch")
            if (System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Check your internet and try again")
                    lastNetworkErrorShown = System.currentTimeMillis()
                }
            }
        } else {
            Log.w("EmpowerSWR", "Screen inactive, skipping data fetch")
        }
    }

    // Handle refresh
    LaunchedEffect(Unit) {
        navController.currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("refresh_flights")
            ?.observe(navController.currentBackStackEntry!!) { refresh ->
                if (refresh && isNetworkAvailable.value && isScreenActive.value) {
                    coroutineScope.launch {
                        delay(2000)
                        val workerId = PrefsHelper.getWorkerId(context)
                        if (workerId != null) {
                            Log.d("EmpowerSWR", "Refreshing flight and PDB details for workerId: $workerId")
                            var hasNetworkError = false
                            viewModel.fetchFlightDetails(workerId) { error ->
                                if (error != null) {
                                    Log.e("EmpowerSWR", "Flight refresh error: ${error.message}")
                                    flightError = error.message ?: "Failed to refresh flight details"
                                    hasNetworkError = true
                                } else {
                                    Log.d("EmpowerSWR", "No flight data available on refresh")
                                    flightError = null
                                }
                            }
                            viewModel.fetchPdbDetails(workerId) { error ->
                                if (error != null && error.message != "No pre-departure details available") {
                                    Log.e("EmpowerSWR", "PDB refresh error: ${error.message}")
                                    pdbError = error.message ?: "Failed to refresh PDB details"
                                    hasNetworkError = true
                                } else {
                                    Log.d("EmpowerSWR", "No PDB data available on refresh")
                                    pdbError = null
                                }
                            }
                            delay(1000)
                            if (hasNetworkError && System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Check your internet and try again")
                                    lastNetworkErrorShown = System.currentTimeMillis()
                                }
                            } else if (!hasNetworkError) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("All caught up!")
                                }
                            }
                        } else {
                            Log.e("EmpowerSWR", "No worker ID available for refresh")
                            flightError = "No worker ID available"
                            pdbError = "No worker ID available"
                            // Removed snackbar for "No worker ID available"
                        }
                    }
                    navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("refresh_flights")
                } else if (!isNetworkAvailable.value) {
                    if (System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Check your internet and try again")
                            lastNetworkErrorShown = System.currentTimeMillis()
                        }
                    }
                } else if (!isScreenActive.value) {
                    Log.d("EmpowerSWR", "Screen inactive, skipping refresh")
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
                        if (workerId != null && isNetworkAvailable.value && isScreenActive.value) {
                            Log.d("EmpowerSWR", "Refresh workerId: $workerId")
                            var hasNetworkError = false
                            viewModel.fetchFlightDetails(workerId) { error ->
                                if (error != null) {
                                    Log.e("EmpowerSWR", "Flight refresh error: ${error.message}")
                                    flightError = error.message ?: "Failed to refresh flight details"
                                    hasNetworkError = true
                                } else {
                                    Log.d("EmpowerSWR", "No flight data available on refresh")
                                    flightError = null
                                }
                            }
                            viewModel.fetchPdbDetails(workerId) { error ->
                                if (error != null && error.message != "No pre-departure details available") {
                                    Log.e("EmpowerSWR", "PDB refresh error: ${error.message}")
                                    pdbError = error.message ?: "Failed to refresh PDB details"
                                    hasNetworkError = true
                                } else {
                                    Log.d("EmpowerSWR", "No PDB data available on refresh")
                                    pdbError = null
                                }
                            }
                            delay(1000)
                            if (hasNetworkError && System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Check your internet and try again")
                                    lastNetworkErrorShown = System.currentTimeMillis()
                                }
                            } else if (!hasNetworkError) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("All caught up!")
                                }
                            }
                        } else {
                            Log.e("EmpowerSWR", "No worker ID, no network, or screen inactive for refresh")
                            flightError = when {
                                !isNetworkAvailable.value -> "No network connection"
                                !isScreenActive.value -> "Screen inactive"
                                else -> "No worker ID available"
                            }
                            pdbError = when {
                                !isNetworkAvailable.value -> "No network connection"
                                !isScreenActive.value -> "Screen inactive"
                                else -> "No worker ID available"
                            }
                            if (!isNetworkAvailable.value && System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Check your internet and try again")
                                    lastNetworkErrorShown = System.currentTimeMillis()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EmpowerSWR", "Refresh error: ${e.message}")
                        flightError = "Refresh failed: ${e.message}"
                        pdbError = "Refresh failed: ${e.message}"
                        if (System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Check your internet and try again")
                                lastNetworkErrorShown = System.currentTimeMillis()
                            }
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
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Pre-Departure Details Card
                pdbDetails?.let { details ->
                    Log.d("EmpowerSWR", "PDB Details: $details")
                    val isInternalPdbExpired = isPdbInternalExpired(details.internalPdb)
                    val hasValidPdb = (details.startDate != null || details.endDate != null) &&
                            (isValidPdbDate(details.startDate) || isValidPdbDate(details.endDate))
                    val hasValidInternalPdb = details.internalPdb != null && !isInternalPdbExpired
                    Log.d("EmpowerSWR", "PDB: scheme=${details.schemes}, hasValidPdb=$hasValidPdb, hasValidInternalPdb=$hasValidInternalPdb")
                    if (hasValidPdb || hasValidInternalPdb) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = RoundedCornerShape(16.dp),
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Pre-Departure Details",
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                // PDB Subcard
                                if (hasValidPdb) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Text(
                                                text = "Pre Departure",
                                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = formatPdbDate(details.startDate),
                                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (details.schemes == "PALM" && details.endDate != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = formatPdbDate(details.endDate),
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = details.pdbLocationLong ?: "N/A",
                                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
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
                                                                // Removed snackbar for "Failed to open Maps"
                                                            }
                                                        } else {
                                                            Log.e("EmpowerSWR", "No location coordinates available")
                                                            // Removed snackbar for "No location coordinates available"
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.LocationOn,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Open in Maps")
                                                }
                                                if (isTodayPdbDate(details.startDate) || isTodayPdbDate(details.endDate)) {
                                                    Button(
                                                        onClick = {
                                                            val workerId = PrefsHelper.getWorkerId(localContext)
                                                            Log.d("EmpowerSWR", "Confirming at PDB clicked, workerId: $workerId")
                                                            if (workerId == null) {
                                                                Log.e("EmpowerSWR", "No worker ID available for PDB confirmation")
                                                                // Removed snackbar for "No worker ID available"
                                                                return@Button
                                                            }
                                                            val action = getPdbAction(details.startDate, details.endDate)
                                                            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(localContext)
                                                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                                                if (location != null) {
                                                                    val lat = location.latitude
                                                                    val lng = location.longitude
                                                                    viewModel.saveLocation(workerId, lat, lng, action)
                                                                    Log.d("EmpowerSWR", "Location saved: lat=$lat, lng=$lng, action=$action")
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("You're checked in at PDB!")
                                                                    }
                                                                } else {
                                                                    Log.e("EmpowerSWR", "Unable to get location for Confirming at PDB")
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar("Couldn't get your location. Try again")
                                                                    }
                                                                }
                                                            }.addOnFailureListener { e ->
                                                                Log.e("EmpowerSWR", "Location fetch failed for Confirming at PDB: ${e.message}")
                                                                coroutineScope.launch {
                                                                    snackbarHostState.showSnackbar("Couldn't get your location. Try again")
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    ) {
                                                        Text("Confirming at PDB")
                                                    }
                                                }
                                            }
                                            if (details.pdbStatus == "None" || details.pdbStatus == "Messaged") {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Button(
                                                    onClick = {
                                                        val workerId = PrefsHelper.getWorkerId(localContext)
                                                        Log.d("EmpowerSWR", "Confirm PDB Status clicked, workerId: $workerId")
                                                        if (workerId == null) {
                                                            Log.e("EmpowerSWR", "No worker ID available for PDB status update")
                                                            // Removed snackbar for "No worker ID available"
                                                            return@Button
                                                        }
                                                        viewModel.updatePdbStatus(workerId) { success, message ->
                                                            Log.d("EmpowerSWR", "PDB status update result: success=$success, message=$message")
                                                            coroutineScope.launch {
                                                                if (success) {
                                                                    snackbarHostState.showSnackbar("Great! Your PDB is confirmed!")
                                                                } else {
                                                                    snackbarHostState.showSnackbar("Something went wrong. Try again")
                                                                }
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ThumbUp,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Tankyu. Bae mi go")
                                                }
                                            }
                                        }
                                    }
                                }
                                // Internal PDB Subcard
                                if (hasValidInternalPdb) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp)
                                        ) {
                                            Text(
                                                text = "Internal PDB",
                                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = formatPdbInternalDate(details.internalPdb),
                                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Internal PDB Status: ${details.internalPdbStatus ?: "N/A"}",
                                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (details.internalPdbStatus == "Unaware" || details.internalPdbStatus == "Messaged") {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = {
                                                        val workerId = PrefsHelper.getWorkerId(localContext)
                                                        Log.d("EmpowerSWR", "Confirm Internal PDB Status clicked, workerId: $workerId")
                                                        if (workerId == null) {
                                                            Log.e("EmpowerSWR", "No worker ID available for Internal PDB status update")
                                                            // Removed snackbar for "No worker ID available"
                                                            return@Button
                                                        }
                                                        viewModel.updatePdbInternalStatus(workerId) { success, message ->
                                                            Log.d("EmpowerSWR", "Internal PDB status update result: success=$success, message=$message")
                                                            coroutineScope.launch {
                                                                if (success) {
                                                                    snackbarHostState.showSnackbar("Awesome! Internal PDB confirmed!")
                                                                } else {
                                                                    snackbarHostState.showSnackbar("Something went wrong. Try again")
                                                                }
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
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
                    } else {
                        Log.d("EmpowerSWR", "PDB expired or invalid: startDate=${details.startDate}, endDate=${details.endDate}, internalPdb=${details.internalPdb}")
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Pre-departure details expired or unavailable",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val workerId = PrefsHelper.getWorkerId(localContext)
                                        if (workerId != null && token != null && isNetworkAvailable.value && isScreenActive.value) {
                                            Log.d("EmpowerSWR", "Retry fetching PDB details for workerId: $workerId")
                                            viewModel.fetchPdbDetails(workerId) { error ->
                                                if (error != null && error.message != "No pre-departure details available") {
                                                    Log.e("EmpowerSWR", "Retry PDB fetch error: ${error.message}")
                                                    if (System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Check your internet and try again")
                                                            lastNetworkErrorShown = System.currentTimeMillis()
                                                        }
                                                    }
                                                } else {
                                                    Log.d("EmpowerSWR", "No PDB data available on retry")
                                                    pdbError = null
                                                }
                                            }
                                        } else {
                                            Log.e("EmpowerSWR", "No worker ID, token, network, or screen inactive for retry")
                                            if (!isNetworkAvailable.value && System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Check your internet and try again")
                                                    lastNetworkErrorShown = System.currentTimeMillis()
                                                }
                                            } else if (token == null) {
                                                navController.navigate("login") {
                                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Try Again")
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
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = pdbError ?: "No pre-departure details available yet",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            if (pdbError != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Try back later.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val workerId = PrefsHelper.getWorkerId(localContext)
                                        if (workerId != null && token != null && isNetworkAvailable.value && isScreenActive.value) {
                                            Log.d("EmpowerSWR", "Retry fetching PDB details for workerId: $workerId")
                                            viewModel.fetchPdbDetails(workerId) { error ->
                                                if (error != null && error.message != "No pre-departure details available") {
                                                    Log.e("EmpowerSWR", "Retry PDB fetch error: ${error.message}")
                                                    if (System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Check your internet and try again")
                                                            lastNetworkErrorShown = System.currentTimeMillis()
                                                        }
                                                    }
                                                } else {
                                                    Log.d("EmpowerSWR", "No PDB data available on retry")
                                                    pdbError = null
                                                }
                                            }
                                        } else {
                                            Log.e("EmpowerSWR", "No worker ID, token, network, or screen inactive for retry")
                                            if (!isNetworkAvailable.value && System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Check your internet and try again")
                                                    lastNetworkErrorShown = System.currentTimeMillis()
                                                }
                                            } else if (token == null) {
                                                navController.navigate("login") {
                                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                }

                // Flight Details Card
                flightDetails?.let { details ->
                    Log.d("EmpowerSWR", "Flight Details: $details")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(16.dp),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AirplanemodeActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Flight Details",
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (details.flightStatus == "Unaware" || details.flightStatus == "Messaged") {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(0.5f),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Button(
                                        onClick = {
                                            val workerId = PrefsHelper.getWorkerId(localContext)
                                            Log.d("EmpowerSWR", "Confirm Flight Status clicked, workerId: $workerId, token: ${token ?: "null"}")
                                            if (workerId == null) {
                                                Log.e("EmpowerSWR", "No worker ID available for flight status update")
                                                // Removed snackbar for "No worker ID available"
                                                return@Button
                                            }
                                            viewModel.updateFlightStatus(workerId) { success, message ->
                                                Log.d("EmpowerSWR", "Flight status update result: success=$success, message=$message")
                                                coroutineScope.launch {
                                                    if (success) {
                                                        snackbarHostState.showSnackbar("Flight confirmed! Ready to go!")
                                                    } else {
                                                        snackbarHostState.showSnackbar("Something went wrong. Try again")
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
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
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = details.teamName ?: "",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Flight Status: ${details.flightStatus ?: "No flight yet"}",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // First Leg (International)
                            if (!details.intFlightNo.isNullOrEmpty() || !details.intDepDate.isNullOrEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    shape = RoundedCornerShape(12.dp),
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
                                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = getCheckInInfo(details.intDepDate, 2.5, true).statusText,
                                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Red
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = getCheckInInfo(details.intDepDate, 2.5, true).countdownText ?: "",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
                                                model = details.intFlightNo?.take(2)?.let { "https://db.nougro.com/images/airlines/$it.png" },
                                                contentDescription = "Airline Logo",
                                                modifier = Modifier.size(48.dp),
                                                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                                                error = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${details.intFlightNo ?: "N/A"} to ${details.intDest ?: "N/A"}",
                                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
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
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
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
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                                            )
                                        }
                                        if (isTodayDepDate(details.intDepDate) && getCheckInInfo(details.intDepDate, 2.5, true).isOpen) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    val workerId = PrefsHelper.getWorkerId(localContext)
                                                    Log.d("EmpowerSWR", "Checked In clicked, workerId: $workerId")
                                                    if (workerId == null) {
                                                        Log.e("EmpowerSWR", "No worker ID available for check-in")
                                                        // Removed snackbar for "No worker ID available"
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
                                                                snackbarHostState.showSnackbar("You're checked in!")
                                                            }
                                                        } else {
                                                            Log.e("EmpowerSWR", "Unable to get location for Checked In")
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Couldn't get your location. Try again")
                                                            }
                                                        }
                                                    }.addOnFailureListener { e ->
                                                        Log.e("EmpowerSWR", "Location fetch failed for Checked In: ${e.message}")
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Couldn't get your location. Try again")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(0.5f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Checked In")
                                            }
                                        }
                                    }
                                }
                            }

                            if (details.hotel1 != null && details.hotel1.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = details.hotel1,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            // Second Leg (Domestic)
                            if (!details.domFlightNo.isNullOrBlank() && !details.domDepDate.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    shape = RoundedCornerShape(12.dp),
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
                                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (isDifferentDay(details.intDepDate, details.domDepDate) ||
                                            isDifferentAirline(details.intFlightNo, details.domFlightNo)
                                        ) {
                                            Text(
                                                text = getCheckInInfo(details.domDepDate, 1.0, false).statusText,
                                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Red
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = getCheckInInfo(details.domDepDate, 1.0, false).countdownText ?: "",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
                                                model = details.domFlightNo.take(2).let { "https://db.nougro.com/images/airlines/$it.png" },
                                                contentDescription = "Airline Logo",
                                                modifier = Modifier.size(48.dp),
                                                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                                                error = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${details.domFlightNo} to ${details.domDest ?: "N/A"}",
                                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
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
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
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
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                                            )
                                        }
                                        if (isTodayDepDate(details.domDepDate) && getCheckInInfo(details.domDepDate, 1.0, false).isOpen) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    val workerId = PrefsHelper.getWorkerId(localContext)
                                                    Log.d("EmpowerSWR", "Checked In clicked, workerId: $workerId")
                                                    if (workerId == null) {
                                                        Log.e("EmpowerSWR", "No worker ID available for check-in")
                                                        // Removed snackbar for "No worker ID available"
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
                                                                snackbarHostState.showSnackbar("You're checked in!")
                                                            }
                                                        } else {
                                                            Log.e("EmpowerSWR", "Unable to get location for Checked In")
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Couldn't get your location. Try again")
                                                            }
                                                        }
                                                    }.addOnFailureListener { e ->
                                                        Log.e("EmpowerSWR", "Location fetch failed for Checked In: ${e.message}")
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Couldn't get your location. Try again")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(0.5f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Checked In")
                                            }
                                        }
                                    }
                                }
                            }

                            if (details.hotel2 != null && details.hotel2.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = details.hotel2,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            // Third Leg
                            if (!details.dom2FlightNo.isNullOrBlank() && !details.dom2DepDate.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    shape = RoundedCornerShape(12.dp),
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
                                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        if (isDifferentDay(details.domDepDate, details.dom2DepDate) ||
                                            isDifferentAirline(details.domFlightNo, details.dom2FlightNo)
                                        ) {
                                            Text(
                                                text = getCheckInInfo(details.dom2DepDate, 1.0, false).statusText,
                                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Red
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = getCheckInInfo(details.dom2DepDate, 1.0, false).countdownText ?: "",
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
                                                model = details.dom2FlightNo.take(2).let { "https://db.nougro.com/images/airlines/$it.png" },
                                                contentDescription = "Airline Logo",
                                                modifier = Modifier.size(48.dp),
                                                placeholder = painterResource(id = android.R.drawable.ic_menu_gallery),
                                                error = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${details.dom2FlightNo} to ${details.dom2Dest ?: "N/A"}",
                                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
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
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
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
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                                            )
                                        }
                                        if (isTodayDepDate(details.dom2DepDate) && getCheckInInfo(details.dom2DepDate, 1.0, false).isOpen) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    val workerId = PrefsHelper.getWorkerId(localContext)
                                                    Log.d("EmpowerSWR", "Checked In clicked, workerId: $workerId")
                                                    if (workerId == null) {
                                                        Log.e("EmpowerSWR", "No worker ID available for check-in")
                                                        // Removed snackbar for "No worker ID available"
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
                                                                snackbarHostState.showSnackbar("You're checked in!")
                                                            }
                                                        } else {
                                                            Log.e("EmpowerSWR", "Unable to get location for Checked In")
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("Couldn't get your location. Try again")
                                                            }
                                                        }
                                                    }.addOnFailureListener { e ->
                                                        Log.e("EmpowerSWR", "Location fetch failed for Checked In: ${e.message}")
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Couldn't get your location. Try again")
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(0.5f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Checked In")
                                            }
                                        }
                                    }
                                }
                            }

                            if (!details.intFlightNo.isNullOrEmpty() || !details.intDepDate.isNullOrEmpty() ||
                                !details.domFlightNo.isNullOrBlank() || !details.dom2FlightNo.isNullOrBlank()) {
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
                                                // Removed snackbar for "No worker ID available"
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
                                                        snackbarHostState.showSnackbar("Your location is saved!")
                                                    }
                                                } else {
                                                    Log.e("EmpowerSWR", "Unable to get location for Mi Stap lo Ples ia")
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Couldn't get your location. Try again")
                                                    }
                                                }
                                            }.addOnFailureListener { e ->
                                                Log.e("EmpowerSWR", "Location fetch failed for Mi Stap lo Ples ia: ${e.message}")
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Couldn't get your location. Try again")
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Mi Stap lo Ples ia!")
                                    }
                                }
                            }
                        }
                    }
                }?: run {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AirplanemodeActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = flightError ?: "No flight details available yet",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            if (flightError != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Check back later.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val workerId = PrefsHelper.getWorkerId(localContext)
                                        if (workerId != null && token != null && isNetworkAvailable.value && isScreenActive.value) {
                                            Log.d("EmpowerSWR", "Retry fetching flight details for workerId: $workerId")
                                            viewModel.fetchFlightDetails(workerId) { error ->
                                                if (error != null) {
                                                    Log.e("EmpowerSWR", "Retry flight fetch error: ${error.message}")
                                                    if (System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("Check your internet and try again")
                                                            lastNetworkErrorShown = System.currentTimeMillis()
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Log.e("EmpowerSWR", "No worker ID, token, network, or screen inactive for retry")
                                            if (!isNetworkAvailable.value && System.currentTimeMillis() - lastNetworkErrorShown > 5000) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Check your internet and try again")
                                                    lastNetworkErrorShown = System.currentTimeMillis()
                                                }
                                            } else if (token == null) {
                                                navController.navigate("login") {
                                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}