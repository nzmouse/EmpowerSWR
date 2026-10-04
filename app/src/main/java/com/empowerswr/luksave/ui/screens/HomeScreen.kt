package com.empowerswr.luksave.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.empowerswr.luksave.MedicalClinic
import com.empowerswr.luksave.EmpowerViewModel
import com.empowerswr.luksave.PrefsHelper
import com.empowerswr.luksave.findActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import android.provider.Settings
import androidx.media3.common.util.UnstableApi
import android.content.ContextWrapper
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.Dialog

// Current app version - UPDATED EVERY TIME A NEW VERSION IS RELEASED
private const val CURRENT_APP_VERSION = "2.9"   // ← Change this when you upload a new version to Play Store

// Important-task cards. Amber, not the Locate red, so workers can tell them apart.
private val TaskAmberLight = Color(0xFFFFF4D6)
private val TaskAmberDark = Color(0xFF5C4300)
private val TaskBorderLight = Color(0xFFE65100)
private val TaskBorderDark = Color(0xFFFFB300)
private val TaskTitleLight = Color(0xFFBF360C)
private val TaskTitleDark = Color(0xFFFFE082)
private val TaskButtonLight = Color(0xFFE65100)
private val TaskButtonDark = Color(0xFFFF8F00)
private suspend fun handleUsernameSubmission(
    usernameInput: String,
    viewModel: EmpowerViewModel,
    context: Context,
    keyboardController: SoftwareKeyboardController?,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    Timber.d("HomeScreen: Username prompt: Starting submission for '$usernameInput'")
    try {
        keyboardController?.hide() // Hide keyboard safely
        val workerId = PrefsHelper.getWorkerId(context)
        if (workerId.isNullOrEmpty()) {
            Timber.e("HomeScreen: Username prompt: No workerId, prompting re-login")
            onError("Session expired. Please log in again.")
            return
        }
        viewModel.updateUsername(
            usernameInput,
            context,
            onSuccess = {
                Timber.d("HomeScreen: Username prompt: Update successful for '$usernameInput'")
                onSuccess()
            },
            onError = { error ->
                Timber.e("HomeScreen: Username prompt: Update failed - $error")
                onError(error)
            }
        )
    } catch (e: Exception) {
        Timber.e(e, "HomeScreen: Username prompt: Unexpected error during submission")
        onError("Failed to update username: ${e.message}")
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Suppress("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: EmpowerViewModel,
    context: Context,
    navController: NavController
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarScope = rememberCoroutineScope { Dispatchers.Main }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var latestUpdateUrl by remember { mutableStateOf<String?>(null) }
    var phone by rememberSaveable { mutableStateOf("") }
    var fcmError by remember { mutableStateOf<String?>(null) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var contractError by remember { mutableStateOf<String?>(null) }
    var isCheckingIn by remember { mutableStateOf(false) }
    var isFindingMe by remember { mutableStateOf(false) }
    var isSigningContract by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val token by viewModel.token
    val workerDetails by viewModel.workerDetails
    val checkInSuccess by viewModel.checkInSuccess
    val contractSuccess by viewModel.contractSuccess
    var showContractCard by remember { mutableStateOf(true) }
    val showUsernamePrompt by viewModel.showUsernamePrompt
    var usernameInput by rememberSaveable { mutableStateOf("") }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var showUsernameSuccess by remember { mutableStateOf(false) }
    val checkInError by viewModel.checkInError
    val notifications by viewModel.notifications
    val notificationFromIntent by viewModel.notificationFromIntent
    val isPhoneSubmitValid by remember { derivedStateOf { phone.matches(Regex("^\\d{7,15}$")) } }
    var showCheckInSection by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showSettingsPrompt by remember { mutableStateOf(false) }
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    var secretAnswerInput by remember { mutableStateOf("") }
    var nidInput by remember { mutableStateOf("") }
    var nidExpInput by remember { mutableStateOf("") }
    var isSavingNID by remember { mutableStateOf(false) }
    var isSavingMother by remember { mutableStateOf(false) }
    var emedApptDate by rememberSaveable { mutableStateOf("") }
    var emedApptTime by rememberSaveable { mutableStateOf("") }
    var isSavingAppointment by remember { mutableStateOf(false) }
    var showSkipWarning by remember { mutableStateOf(false) }
    var skippedMotherPrompt by rememberSaveable { mutableStateOf(false) }

    // Log screen usage
    LaunchedEffect(Unit) {
        Timber.i("ScreenUsage: HomeScreen displayed, workerId=${PrefsHelper.getWorkerId(context) ?: "unknown"}, timestamp=${System.currentTimeMillis()}")
    }
    context.findEmpowerActivity() ?: run {
        throw IllegalStateException("HomeScreen must be called within a ComponentActivity")
    }

    val localContext = LocalContext.current

    // ====================== NOTIFICATION PERMISSION SETUP ======================
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            createNotificationChannel(localContext)
            Timber.i("Notifications permission granted")
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("You can enable notifications later in Settings for job alerts")
            }
        }
    }

    // Gentle auto-request (max 2 times)
    LaunchedEffect(Unit) {
        val prefs = localContext.getSharedPreferences("luksave_prefs", Context.MODE_PRIVATE)
        val requestCount = prefs.getInt("notif_request_count", 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(localContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED &&
            requestCount < 2) {

            delay(800)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            prefs.edit().putInt("notif_request_count", requestCount + 1).apply()
        }
    }


// Check if we should ask (use DataStore/SharedPreferences to track attempts)
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var requestCount by remember { mutableStateOf(prefs.getInt("notification_request_count", 0)) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED &&
            requestCount < 3) {  // Max 3 attempts

            delay(1200)  // Small delay so it doesn't clash with other things
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            prefs.edit().putInt("notification_request_count", requestCount + 1).apply()
        }
    }
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        Timber.i("Location Permission result: fineGranted=%b, coarseGranted=%b", fineGranted, coarseGranted)
        if (fineGranted || coarseGranted) {
            locationError = null
        } else {
            showSettingsPrompt = true
            locationError = "Location permission denied. Please enable it in app settings."
            Timber.tag("HomeScreen").e("Location permission denied, showing settings prompt")
        }
    }

    // Settings launcher
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            locationError = null
            Timber.i("Location permission granted after settings")
        } else {
            showSettingsPrompt = true
            locationError = "Location permission still denied. Please enable it in settings."
            Timber.tag("HomeScreen").e("Location permission still denied after settings")
        }
    }

    // Check permissions on start
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Check permission and request
    fun checkAndRequestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }
    // Mother's name is independent of National ID. Pass the NID already on file
    // so a later mother-name save does not clear it. Blank NID is allowed.
    fun saveMotherNameOnly() {
        val existingNid = workerDetails?.nid?.trim().orEmpty()
        Timber.tag("MOTHER_SAVE").d("saveMotherNameOnly() START - existingNid='$existingNid', motherName='$secretAnswerInput'")

        if (secretAnswerInput.isBlank()) {
            Toast.makeText(context, "Plis putum nem blong mama blong yu", Toast.LENGTH_SHORT).show()
            return
        }

        isSavingMother = true

        coroutineScope.launch {
            val workerId = PrefsHelper.getWorkerId(context)
            if (workerId.isNullOrEmpty()) {
                Timber.tag("MOTHER_SAVE").d("No workerId found")
                Toast.makeText(context, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
                isSavingMother = false
                return@launch
            }

            viewModel.updateWorkerNIDAndSecret(
                workerId = workerId,
                nid = existingNid,
                motherName = secretAnswerInput
            ) { success, message ->
                isSavingMother = false
                secretAnswerInput = ""

                if (success) {
                    viewModel.fetchWorkerDetails(context) { }
                    Toast.makeText(context, message ?: "Nem blong mama i save finis", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, message ?: "Failed to save. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    @Composable
    fun ChecklistItem(label: String) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
    // Settings prompt dialog
    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = { showSettingsPrompt = false },
            title = { Text("Permission Required") },
            text = { Text("Location permission is required for check-in. Please enable it in app settings.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSettingsPrompt = false
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", context.packageName, null)
                        settingsLauncher.launch(intent)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showSettingsPrompt = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSkipWarning) {
        AlertDialog(
            onDismissRequest = { showSkipWarning = false },
            title = { Text("Yu sua?") },
            text = {
                Text("Sapos yu skip nem blong mama, bae i had blong resetem PIN o faendem username sapos yu fogetem.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSkipWarning = false
                        skippedMotherPrompt = true
                    }
                ) {
                    Text("Oraet, skip")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipWarning = false }) {
                    Text("Go bak")
                }
            }
        )
    }

    // Force recomposition on workerDetails change
    LaunchedEffect(workerDetails) {
        Timber.i("workerDetails changed")
    }

    LaunchedEffect(token) {
        if (token == null) {
            Timber.tag("HomeScreen").e("No token, redirecting to login")
            navController.navigate("login") {
                popUpTo("home") { inclusive = true }
            }
        } else {
            viewModel.fetchWorkerDetails(context) { error ->
                error?.message ?: "Failed to load worker details"
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(5000)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val fcmToken = task.result
                Timber.i("Real FCM token length: ${token?.length}")
                Timber.i("Token preview: ${token?.take(50)}...")
                PrefsHelper.saveFcmToken(localContext, fcmToken)
                val workerId = PrefsHelper.getWorkerId(localContext)
                if (workerId != null) {
                    viewModel.updateFcmToken(fcmToken, localContext)
                } else {
                    Timber.tag("HomeScreen").e("No workerId available for FCM token update")
                }
            } else {
                fcmError = "Failed to get FCM token: ${task.exception?.message}"
                Timber.tag("HomeScreen").e(task.exception, "FCM Token Error: %s", task.exception?.message)
            }
        }
    }

    // Handle notifications from ViewModel
    LaunchedEffect(notifications) {
        notifications.forEach { notification ->
            snackbarScope.launch {
                try {
                    val result = snackbarHostState.showSnackbar(
                        message = "${notification.title}: ${notification.body}",
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Indefinite
                    )
                    viewModel.removeNotification(notification)
                } catch (e: Exception) {
                    Timber.tag("HomeScreen").e(e, "Failed to show notification Snackbar")
                }
            }
        }
    }

    // Handle intent-based notifications
    LaunchedEffect(notificationFromIntent) {
        notificationFromIntent?.let { (title, body) ->
            if (title != null && body != null && title.isNotBlank() && body.isNotBlank()) {
                snackbarScope.launch {
                    try {
                        Timber.i("Showing Snackbar for Title: $title, Body: $body")
                        snackbarHostState.showSnackbar(
                            message = "$title: $body",
                            actionLabel = "Dismiss",
                            duration = SnackbarDuration.Long
                        )
                        viewModel.setNotificationFromIntent(null, null)
                    } catch (e: Exception) {
                        Timber.tag("HomeScreen").e(e, "Failed to show intent notification Snackbar")
                    }
                }
            } else {
                Timber.i("Invalid notification data: Title=$title, Body=$body")
            }
        }
    }

    // Handle errors (refresh, location, contract)
    LaunchedEffect(refreshError, locationError, contractError) {
        val error = refreshError ?: locationError ?: contractError
        error?.let {
            snackbarScope.launch {
                try {
                    snackbarHostState.showSnackbar(
                        message = it,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Long
                    )
                    if (it == refreshError) refreshError = null
                    if (it == locationError) locationError = null
                    if (it == contractError) contractError = null
                } catch (e: Exception) {
                    Timber.tag("HomeScreen").e(e, "Failed to show error Snackbar")
                }
            }
        }
    }

    LaunchedEffect(checkInSuccess) {
        if (checkInSuccess == true) {
            phone = ""
            isCheckingIn = false
        }
    }
    LaunchedEffect(contractSuccess) {
        if (contractSuccess == true) {
            isSigningContract = false
            showContractCard = false
        }
    }
    LaunchedEffect(checkInError) {
        checkInError?.let { message ->
            snackbarScope.launch {
                try {
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Long
                    )
                    showCheckInSection = false
                    viewModel.clearCheckInState()
                    isCheckingIn = false
                } catch (e: Exception) {
                    Timber.tag("HomeScreen").e(e, "Failed to show check-in Snackbar")
                }
            }
        }
    }

    // Show snackbar for username success
    LaunchedEffect(showUsernameSuccess) {
        if (showUsernameSuccess) {
            try {
                snackbarHostState.showSnackbar(
                    message = "Username set successfully.  You can now use your username to log in!",
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Short
                )

            } catch (e: Exception) {
                Timber.e(e, "HomeScreen: Username prompt: Failed to show snackbar")
                usernameError = "Failed to show confirmation: ${e.message}"
            }
            showUsernameSuccess = false
        }
    }
    // Check for new app version (runs once when HomeScreen loads)
    LaunchedEffect(Unit) {
        viewModel.checkForUpdate { hasUpdate, latestVersion, updateUrl ->
            if (hasUpdate && latestVersion != null) {
                if (isNewerVersion(CURRENT_APP_VERSION, latestVersion)) {
                    showUpdateDialog = true
                    latestUpdateUrl = updateUrl
                }
            }
        }
    }
    // Helper to check if NID is expired

    fun isNIDExpired(expiryDate: String?): Boolean {
        if (expiryDate == null || expiryDate == "0000-00-00" || expiryDate.isEmpty()) return false
        return try {
            val today = java.time.LocalDate.now()
            val exp = java.time.LocalDate.parse(expiryDate)
            exp.isBefore(today)
        } catch (e: Exception) {
            false
        }
    }
    // Accepts 31/12/2028, 31-12-2028, 31.12.2028, 2028-12-31, 31 Dec 2028,
    // 31 December 2028, 31st Dec 2028, and Bislama month names. Returns yyyy-mm-dd.
    fun normaliseDate(input: String): String? {
        val cleaned = input.trim()
            .replace(Regex("(?i)(\\d+)(st|nd|rd|th)\\b"), "$1")
            .replace(Regex("[\\s/.,-]+"), "-")
            .trim('-')

        fun valid(year: Int, month: Int, day: Int): String? {
            if (month !in 1..12 || day !in 1..31 || year !in 1900..2100) return null
            return try {
                val date = java.time.LocalDate.of(year, month, day)
                "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
            } catch (_: Exception) {
                null
            }
        }

        fun monthNumber(name: String): Int? = when (name.lowercase()) {
            "jan", "january", "januware", "januari" -> 1
            "feb", "february", "febuware", "februari" -> 2
            "mar", "march", "maj", "mas" -> 3
            "apr", "april", "epril" -> 4
            "may", "mei" -> 5
            "jun", "june" -> 6
            "jul", "july", "julai" -> 7
            "aug", "august", "ogis", "ogast" -> 8
            "sep", "sept", "september", "septemba" -> 9
            "oct", "october", "oktoba" -> 10
            "nov", "november", "novemba" -> 11
            "dec", "december", "desemba" -> 12
            else -> null
        }

        Regex("^(\\d{1,2})-(\\d{1,2})-(\\d{4})$").find(cleaned)?.let { match ->
            val (d, m, y) = match.destructured
            return valid(y.toInt(), m.toInt(), d.toInt())
        }

        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").find(cleaned)?.let { match ->
            val (y, m, d) = match.destructured
            return valid(y.toInt(), m.toInt(), d.toInt())
        }

        Regex("^(\\d{1,2})-(\\d{1,2})-(\\d{2})$").find(cleaned)?.let { match ->
            val (d, m, y) = match.destructured
            val year = 2000 + y.toInt()
            return valid(year, m.toInt(), d.toInt())
        }

        Regex("^(\\d{1,2})-([A-Za-z]{3,12})-(\\d{4})$").find(cleaned)?.let { match ->
            val (d, m, y) = match.destructured
            val month = monthNumber(m) ?: return null
            return valid(y.toInt(), month, d.toInt())
        }

        Regex("^(\\d{1,2})-([A-Za-z]{3,12})-(\\d{2})$").find(cleaned)?.let { match ->
            val (d, m, y) = match.destructured
            val month = monthNumber(m) ?: return null
            return valid(2000 + y.toInt(), month, d.toInt())
        }

        return null
    }

    fun normaliseTime(input: String): String? {
        val cleaned = input.trim().lowercase().replace(".", ":").replace(Regex("\\s+"), "")
        val match = Regex("^(\\d{1,2}):(\\d{2})(am|pm)?$").find(cleaned) ?: return null
        val (hRaw, mRaw, ampm) = match.destructured
        var hour = hRaw.toIntOrNull() ?: return null
        val minute = mRaw.toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        when (ampm) {
            "am" -> if (hour !in 1..12) return null else if (hour == 12) hour = 0
            "pm" -> if (hour !in 1..12) return null else if (hour != 12) hour += 12
            else -> if (hour !in 0..23) return null
        }
        return "%02d:%02d".format(hour, minute)
    }

    fun formatEmedDateBislama(value: String?): String? {
        if (value.isNullOrBlank() || value.startsWith("0000-00-00")) return null
        val parsed = try {
            java.time.LocalDateTime.parse(value.trim().replace(" ", "T").take(19))
        } catch (_: Exception) {
            return null
        }
        val day = listOf("Sande", "Mande", "Tiusde", "Wenesde", "Tosde", "Fraede", "Sarede")[parsed.dayOfWeek.value % 7]
        val month = listOf(
            "Jenuware", "Februari", "Maj", "Epril", "Mei", "Jun",
            "Julae", "Ogis", "Septemba", "Oktoba", "Novemba", "Disemba"
        )[parsed.monthValue - 1]
        val hour24 = parsed.hour
        val hour12 = when (val h = hour24 % 12) { 0 -> 12; else -> h }
        val ampm = if (hour24 < 12) "am" else "pm"
        return "$day ${parsed.dayOfMonth} $month, $hour12:${"%02d".format(parsed.minute)}$ampm"
    }

    fun saveMedicalAppointment(workerId: String) {
        val date = normaliseDate(emedApptDate)
        val time = normaliseTime(emedApptTime)
        if (workerId.isBlank()) {
            Toast.makeText(context, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }
        if (date == null || time == null) {
            Toast.makeText(context, "Plis pikim date mo taem", Toast.LENGTH_LONG).show()
            return
        }
        viewModel.updateEmedicalDate(context, workerId, "appointment", date, time)
    }

    fun saveMedicalDone(workerId: String, date: String, time: String) {
        if (workerId.isBlank()) {
            Toast.makeText(context, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }
        viewModel.updateEmedicalDate(context, workerId, "done", date, time)
    }
    // Save NID + Expiry Date
    // Save when NID number is missing (saves both NID + Expiry)
    fun saveNIDAndExpiry() {
        Timber.tag("NID_EXPIRY").d("saveNIDAndExpiry() START - nid='$nidInput', expiry='$nidExpInput'")

        if (nidInput.length < 4) {
            Toast.makeText(context, "National ID i mas gat least 4 digits", Toast.LENGTH_SHORT).show()
            return
        }

        val normalisedExpiry = normaliseDate(nidExpInput)
        if (normalisedExpiry == null) {
            Toast.makeText(context, "Plis yusum date olsem: 31/12/2028, 31-12-2028, o 31 Dec 2028", Toast.LENGTH_LONG).show()
            return
        }

        isSavingNID = true

        coroutineScope.launch {
            val workerId = PrefsHelper.getWorkerId(context)
            if (workerId.isNullOrEmpty()) {
                Toast.makeText(context, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
                isSavingNID = false
                return@launch
            }

            viewModel.updateNIDAndExpiry(workerId, nidInput, normalisedExpiry) { success, message ->
                isSavingNID = false
                nidInput = ""
                nidExpInput = ""

                if (success) {
                    viewModel.fetchWorkerDetails(context) { }
                    Toast.makeText(context, message ?: "National ID mo expiry i save finis", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, message ?: "Failed to save. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Update only expiry date (when NID already exists but expired)
    fun saveNIDExpiryOnly() {
        Timber.tag("NID_EXPIRY").d("saveNIDExpiryOnly() START - expiry='$nidExpInput'")

        val normalisedExpiry = normaliseDate(nidExpInput)
        if (normalisedExpiry == null) {
            Toast.makeText(context, "Plis yusum date olsem: 31/12/2028, 31-12-2028, o 31 Dec 2028", Toast.LENGTH_LONG).show()
            return
        }

        isSavingNID = true

        coroutineScope.launch {
            val workerId = PrefsHelper.getWorkerId(context)
            if (workerId.isNullOrEmpty()) {
                Toast.makeText(context, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
                isSavingNID = false
                return@launch
            }

            viewModel.updateNIDExpiryOnly(workerId, normalisedExpiry) { success, message ->
                isSavingNID = false
                nidExpInput = ""

                if (success) {
                    viewModel.fetchWorkerDetails(context) { }
                    Toast.makeText(context, message ?: "NID Expiry i update finis", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, message ?: "Failed to update expiry. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    try {
                        isRefreshing = true
                        viewModel.fetchWorkerDetails(context) { error ->
                            if (error != null) {
                                refreshError = error.message?.let { "Refresh failed: $it" } ?: "Refresh failed"
                            }
                            isRefreshing = false
                        }
                        delay(1000)
                    } catch (e: Exception) {
                        refreshError = "Refresh failed: ${e.message}"
                        Timber.tag("HomeScreen").e(e, "Refresh error")
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ==================== NOTIFICATION PERMISSION CARD ====================
                item {
                    Text(
                        text = "Home Screen - Welcome!",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                            localContext,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true

                    if (!isNotificationGranted) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSystemInDarkTheme()) {
                                    Color(0xFF4A3C1B)      // Dark golden brown
                                } else {
                                    Color(0xFFFFF3CD)      // Light yellow (original)
                                }
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSystemInDarkTheme()) {
                                    Color(0xFFFFD54F)      // Gold border in dark mode
                                } else {
                                    Color(0xFFFFB300)
                                }
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Never miss a job match!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSystemInDarkTheme()) Color(0xFFFFE082) else Color.Unspecified
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Enable notifications to get instant alerts for new jobs, applications, interviews, and messages.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSystemInDarkTheme()) Color.LightGray else Color.Unspecified
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { /* your existing onClick */ },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Enable Notifications Now")
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (!isFindingMe) {
                                coroutineScope.launch {
                                    isFindingMe = true
                                    checkAndRequestLocationPermission()
                                    performLocationUpdate(
                                        context = context,
                                        fusedLocationClient = fusedLocationClient,
                                        viewModel = viewModel,
                                        action = "Find-Me",
                                        onError = { error -> locationError = error }
                                    )
                                    isFindingMe = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.inversePrimary
                        ),
                        enabled = !isFindingMe
                    ) {
                        Text("Find Me")
                    }

                    workerDetails?.let { worker ->
                        if ((worker.notices == "Locate" || worker.notices == "Messaged") && showCheckInSection) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.error,
                                        MaterialTheme.shapes.medium
                                    ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "IMPORTANT: Mifala traem faendem yu naoia. Kolem ofis long 34357.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = phone,
                                        onValueChange = { newValue ->
                                            if (newValue.matches(Regex("^\\d*$"))) {
                                                phone = newValue
                                            }
                                        },
                                        label = { Text("Fon namba blong yu") },
                                        isError = phone.isNotEmpty() && !isPhoneSubmitValid,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Phone,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (isPhoneSubmitValid && !isCheckingIn) {
                                                    keyboardController?.hide()
                                                    coroutineScope.launch {
                                                        isCheckingIn = true
                                                        checkAndRequestLocationPermission()
                                                        performCheckInAndSaveLocation(
                                                            context = context,
                                                            fusedLocationClient = fusedLocationClient,
                                                            viewModel = viewModel,
                                                            phone = phone,
                                                            onError = { error: String -> locationError = error }
                                                        )
                                                    }
                                                } else {
                                                    Timber.tag("HomeScreen").e("Invalid phone number or check-in in progress")
                                                }
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = MaterialTheme.colorScheme.error,
                                            unfocusedIndicatorColor = MaterialTheme.colorScheme.error,
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                            errorContainerColor = MaterialTheme.colorScheme.surface,
                                            errorTextColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        enabled = !isCheckingIn
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            if (isPhoneSubmitValid && !isCheckingIn) {
                                                keyboardController?.hide()
                                                coroutineScope.launch {
                                                    isCheckingIn = true
                                                    checkAndRequestLocationPermission()
                                                    performCheckInAndSaveLocation(
                                                        context = context,
                                                        fusedLocationClient = fusedLocationClient,
                                                        viewModel = viewModel,
                                                        phone = phone,
                                                        onError = { error: String -> locationError = error }
                                                    )
                                                }
                                            } else {
                                                Timber.tag("HomeScreen").e("Invalid phone number or check-in in progress")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.inversePrimary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        enabled = isPhoneSubmitValid && !isCheckingIn
                                    ) {
                                        Text("Check In", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        // Status Card
                        workerDetails?.let { worker ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.shapes.medium
                                    ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "STATUS",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = worker.notices ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        // ==================== MOTHER'S NAME CARD (PIN reset secret) ====================
                        // Independent of National ID. Shown only when mother's name is missing.
                        workerDetails?.let { worker ->
                            if (worker.secretQuestion.isNullOrBlank() && !skippedMotherPrompt) {
                                val taskDark = isSystemInDarkTheme()
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .border(
                                            3.dp,
                                            if (taskDark) TaskBorderDark else TaskBorderLight,
                                            MaterialTheme.shapes.medium
                                        ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (taskDark) TaskAmberDark else TaskAmberLight,
                                        contentColor = if (taskDark) TaskTitleDark else TaskTitleLight
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "YU MAS MEKEM",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (taskDark) TaskBorderDark else TaskBorderLight
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Nem blong mama (blong resetem PIN)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (taskDark) TaskTitleDark else TaskTitleLight
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Putum first name blong mami blong yu. Bae mifala askem sapos yu fogetem PIN o username. Yu save mekem hem sapos National ID i no stap yet.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (taskDark) Color(0xFFFFF8E1) else Color(0xFF3E2723)
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            text = "Wanem nem blong mami blong yu?",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        OutlinedTextField(
                                            value = secretAnswerInput,
                                            onValueChange = { secretAnswerInput = it.trim() },
                                            label = { Text("Ansa") },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = if (taskDark) TaskBorderDark else TaskBorderLight,
                                                unfocusedBorderColor = if (taskDark) TaskBorderDark else TaskBorderLight,
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(20.dp))

                                        Button(
                                            onClick = { saveMotherNameOnly() },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = secretAnswerInput.isNotBlank() && !isSavingMother,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (taskDark) TaskButtonDark else TaskButtonLight,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            if (isSavingMother) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = Color.White
                                                )
                                            } else {
                                                Text("Save nem blong mama")
                                            }
                                        }

                                        TextButton(onClick = { showSkipWarning = true }) {
                                            Text(
                                                "Skip / Mekem nara taem",
                                                color = if (taskDark) TaskTitleDark else TaskTitleLight
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // ==================== NATIONAL ID CARD (NID Number + Expiry) ====================
                        workerDetails?.let { worker ->
                            val hasNID = !worker.nid.isNullOrBlank()
                            val hasExpiry = !worker.NIDExp.isNullOrBlank() && worker.NIDExp != "0000-00-00"
                            val isExpired = hasExpiry && isNIDExpired(worker.NIDExp)

                            // 1. Missing NID → Show full card (NID + Expiry)
                            if (!hasNID) {
                                val taskDark = isSystemInDarkTheme()
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .border(
                                            3.dp,
                                            if (taskDark) TaskBorderDark else TaskBorderLight,
                                            MaterialTheme.shapes.medium
                                        ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (taskDark) TaskAmberDark else TaskAmberLight,
                                        contentColor = if (taskDark) TaskTitleDark else TaskTitleLight
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "YU MAS MEKEM",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (taskDark) TaskBorderDark else TaskBorderLight
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "National ID Card",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (taskDark) TaskTitleDark else TaskTitleLight
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Putum namba mo expiry date blong National ID kad blong yu. I helpem mifala konfaemem yu, mo helpem resetem PIN.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (taskDark) Color(0xFFFFF8E1) else Color(0xFF3E2723)
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        OutlinedTextField(
                                            value = nidInput,
                                            onValueChange = { if (it.matches(Regex("^\\d*$"))) nidInput = it },
                                            label = { Text("National ID Card Number (NIN)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        OutlinedTextField(
                                            value = nidExpInput,
                                            onValueChange = { newValue ->
                                                // Allow digits, letters (for month names), spaces, /, -, and limit length
                                                if (newValue.matches(Regex("^[\\dA-Za-z\\s/.,-]{0,22}$"))) {
                                                    nidExpInput = newValue
                                                }
                                            },
                                            label = { Text("Expiry Date (e.g. 31/12/2028 or 31 Dec 2028)") },
                                            placeholder = { Text("31 Dec 2028") },
                                            // Ascii, not Text: Text keeps the number pad after the NID field on many phones.
                                            keyboardOptions = KeyboardOptions(
                                                capitalization = KeyboardCapitalization.Words,
                                                keyboardType = KeyboardType.Ascii,
                                                imeAction = ImeAction.Done
                                            ),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Button(
                                            onClick = {
                                                if (!hasNID) saveNIDAndExpiry()
                                                else saveNIDExpiryOnly()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = (hasNID || nidInput.length >= 4) &&
                                                    normaliseDate(nidExpInput) != null &&
                                                    !isSavingNID,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (taskDark) TaskButtonDark else TaskButtonLight,
                                                contentColor = Color.White
                                            )
                                        ) {
                                            if (isSavingNID) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                            } else {
                                                Text(if (!hasNID) "Save NID & Expiry Date" else "Update Expiry Date")
                                            }
                                        }
                                    }
                                }
                            }
                            // 2. Has NID but expired → Show warning + expiry update only
                            else if (isExpired) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .border(3.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.medium),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "YU MAS MEKEM",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "National ID blong yu i expaia finis!",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Plis putum niu expiry date blong National ID kad blong yu.",
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        // Improved Expiry Input - accepts dd/mm/yyyy, dd-mm-yyyy, dd mmm yyyy
                                        OutlinedTextField(
                                            value = nidExpInput,
                                            onValueChange = { newValue ->
                                                if (newValue.matches(Regex("^[\\dA-Za-z\\s/.,-]{0,22}$"))) {
                                                    nidExpInput = newValue
                                                }
                                            },
                                            label = { Text("Niu Expiry Date") },
                                            placeholder = { Text("31 Dec 2028") },
                                            keyboardOptions = KeyboardOptions(
                                                capitalization = KeyboardCapitalization.Words,
                                                keyboardType = KeyboardType.Ascii,
                                                imeAction = ImeAction.Done
                                            ),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Button(
                                            onClick = { saveNIDExpiryOnly() },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = normaliseDate(nidExpInput) != null && !isSavingNID
                                        ) {
                                            if (isSavingNID) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                            } else {
                                                Text("Update Expiry Date")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (worker.contract == "Ready to Sign" && showContractCard) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.shapes.medium
                                    ),
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
                                        text = "ACTION REQUIRED: Your contract is ready to sign!",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            if (!isSigningContract) {
                                                coroutineScope.launch {
                                                    isSigningContract = true
                                                    viewModel.signContract(context)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.inversePrimary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        enabled = !isSigningContract
                                    ) {
                                        Text("OK!  Bae mi kam saen wantaem", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        // ==================== SMART "THINGS YOU MUST DO" CARD ====================
                        workerDetails?.let { worker ->
                            val country = worker.rsecountry?.trim()?.uppercase() ?: ""
                            val scheme = when {
                                country.contains("NZ") || country == "NEW ZEALAND" -> "RSE"
                                country.contains("AU") || country == "AUSTRALIA" || country == "PALM" -> "PALM"
                                else -> "BOTH"
                            }

                            // Load dynamic tasks from backend
                            var requiredTasks by remember { mutableStateOf<List<String>>(emptyList()) }

                            LaunchedEffect(scheme) {
                                viewModel.loadRequiredTasks(scheme) { tasks ->
                                    requiredTasks = tasks
                                }
                            }

                            if (requiredTasks.isNotEmpty() &&
                                (worker.notices == "App Checkin" ||
                                        worker.notices == "App-Accepted" ||
                                        worker.notices == "Notified" ||
                                        worker.notices == "Reported In" ||
                                        worker.notices == "Underway")) {

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        Text(
                                            text = "Ol samting yu mas mekem naoia",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (scheme == "RSE") "RSE New Zealand"
                                            else if (scheme == "PALM") "PALM Australia"
                                            else "Seasonal Worker Program",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // Dynamic Checklist
                                        requiredTasks.forEach { task ->
                                            ChecklistItem(label = task)
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))

                                        if (worker.notices == "App Checkin") {
                                            Button(
                                                onClick = { navController.navigate("team") },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("View & Accept New Job")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    fcmError?.let { error ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "FCM Error: $error",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    // ==================== MEDICAL CARD - Smart Logic (NZ + HAP ID) ====================
                    workerDetails?.let { worker ->
                        val emedStatus = worker.emed?.trim() ?: ""
                        val emedKey = emedStatus.lowercase()
                            .replace('_', '-')
                            .replace(' ', '-')
                            .replace(Regex("-+"), "-")
                        val hapId = worker.hapid?.trim() ?: ""
                        val country = worker.rsecountry?.trim() ?: ""
                        val isNZ = country.equals("NZ", ignoreCase = true)

                        val normalizedHapId = hapId.trim()
                        val hasValidHapid = normalizedHapId.isNotEmpty() &&
                                normalizedHapId != "0" &&
                                normalizedHapId.lowercase() != "null"

                        val isCheckedIn = worker.notices in listOf(
                            "App Checkin",
                            "App-Checkin",
                            "App-Accepted",
                            "Notified",
                            "Reported In",
                            "Underway"
                        )
                        val hapNumber = hapId.toLongOrNull() ?: 0L
                        val hapOk = hapNumber > 0
                        val showGoingButton = hapOk && emedKey in setOf("not-yet", "notified", "sent")
                        val showAppointmentButton = hapOk && emedKey in setOf("not-yet", "notified", "sent", "app-going")
                        val showDoneButton = hapOk && emedKey in setOf("not-yet", "sent", "notified", "app-going", "app-appt")
                        val showMedicalCard = when {
                            emedStatus.equals("Not required", ignoreCase = true) -> false
                            emedKey == "app-done" -> false
                            showAppointmentButton || showDoneButton || emedKey == "app-clinic" || emedKey == "not-yet" -> true
                            !isCheckedIn -> false
                            isNZ -> true
                            emedStatus.equals("Required", ignoreCase = true) -> true
                            hasValidHapid || emedStatus.equals("Not Yet", ignoreCase = true) -> true
                            else -> emedStatus.isNotEmpty()
                        }

                        if (!showMedicalCard) return@let

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .border(
                                    2.dp,
                                    if (emedKey == "alert!") MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.shapes.medium
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (emedKey == "alert!")
                                    MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surface,
                                contentColor = if (emedKey == "alert!")
                                    MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (isNZ) "GENERAL MEDICAL (NZ)" else "eMEDICAL (Australia)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (emedKey == "alert!")
                                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                if (!isNZ) {
                                    val hapIdStyle = MaterialTheme.typography.bodyLarge
                                    val hapIdNumberSize = hapIdStyle.fontSize * 3

                                    Text(
                                        text = buildAnnotatedString {
                                            append("HAP ID: ")
                                            if (hasValidHapid) {
                                                withStyle(
                                                    SpanStyle(
                                                        fontSize = hapIdNumberSize,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                ) {
                                                    append(normalizedHapId)
                                                }
                                            } else {
                                                withStyle(
                                                    SpanStyle(
                                                        fontSize = hapIdStyle.fontSize,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                ) {
                                                    append("Not issued yet")
                                                }
                                            }
                                        },
                                        style = hapIdStyle,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                val displayStatus = when {
                                    emedStatus.isEmpty() -> "Pending"
                                    emedStatus.equals("Not required", ignoreCase = true) -> "Not Required"
                                    emedStatus.equals("Required", ignoreCase = true) -> "Required"
                                    else -> emedStatus
                                }

                                val isAlert = emedKey == "alert!" || emedStatus.equals("ALERT!", ignoreCase = true)
                                val hapIdNumberSize = MaterialTheme.typography.bodyLarge.fontSize * 3

                                if (isAlert) {
                                    Text(
                                        text = "Alert!",
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = hapIdNumberSize),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Kam luk mifala long ofis. Dokta i askem moa infomesen blong medikel blong yu.",
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        text = "Current Status: $displayStatus",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (emedKey !in setOf("finalised", "sent", "notified", "not-yet") && !isAlert) {
                                    formatEmedDateBislama(worker.emedDate)?.let { spokenDate ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = when (emedKey) {
                                                "app-clinic" -> "Medikel i finis: $spokenDate"
                                                "app-appt" -> "Appointment: $spokenDate"
                                                else -> spokenDate
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                when {
                                    isAlert -> { }

                                    emedKey == "app-clinic" -> {
                                        Text(
                                            text = "Yu mekem emedikel finis. Be nao emedikel blong yu i stap go long ol dokta blong flatem wok blong hem. Long taem emedikel i klia, bae yu luk Status i go long \"Finalised\".",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    emedKey == "app-appt" -> {
                                        Text(
                                            text = "Appointment blong yu i stap. Klinik i gat detel. Mek sua yu go long ${formatEmedDateBislama(worker.emedDate) ?: "dei ia"}.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    isNZ -> {
                                        Text(
                                            text = "No eMedical required for NZ unless advised by NZ Immigration.\nGeneral medical still needed.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    !hapOk && !isNZ -> {
                                        Text(
                                            text = "Mifala no mekem HAP ID blong yu yet. Traem jekem tumora o kalem ofis.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (showGoingButton) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            viewModel.acknowledgeGoingToMedical(context, worker.ID ?: "")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("OK, bae mi go blong mekem Medikel")
                                    }
                                }
                                if (showAppointmentButton) {
                                    var showDateTimePicker by remember { mutableStateOf(false) }
                                    var pickerStep by remember { mutableStateOf(0) }
                                    val datePickerState = rememberDatePickerState()
                                    val timePickerState = rememberTimePickerState(is24Hour = false)
                                    val dateLabel = normaliseDate(emedApptDate)?.let { iso ->
                                        val d = java.time.LocalDate.parse(iso)
                                        "%02d %s %04d".format(
                                            d.dayOfMonth,
                                            d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                                            d.year
                                        )
                                    }
                                    val timeLabel = normaliseTime(emedApptTime)

                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (dateLabel != null && timeLabel != null) {
                                        Text(
                                            text = "Appointment: $dateLabel  $timeLabel",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Button(
                                            onClick = {
                                                pickerStep = 0
                                                showDateTimePicker = true
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !isSavingAppointment
                                        ) {
                                            if (isSavingAppointment) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                            } else {
                                                Text("Mi mekem apoinmen long klinik finis")
                                            }
                                        }
                                    }

                                    if (showDateTimePicker) {
                                        Dialog(onDismissRequest = { showDateTimePicker = false }) {
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = if (pickerStep == 0) "Pikim date" else "Pikim taem",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                                                    )
                                                    if (pickerStep == 0) {
                                                        DatePicker(state = datePickerState)
                                                    } else {
                                                        TimePicker(
                                                            state = timePickerState,
                                                            modifier = Modifier
                                                                .align(Alignment.CenterHorizontally)
                                                                .padding(vertical = 16.dp)
                                                        )
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        TextButton(onClick = { showDateTimePicker = false }) {
                                                            Text("Kansel")
                                                        }
                                                        TextButton(onClick = {
                                                            if (pickerStep == 0) {
                                                                if (datePickerState.selectedDateMillis == null) {
                                                                    Toast.makeText(context, "Plis pikim wan date", Toast.LENGTH_SHORT).show()
                                                                    return@TextButton
                                                                }
                                                                pickerStep = 1
                                                            } else {
                                                                val millis = datePickerState.selectedDateMillis
                                                                if (millis == null) {
                                                                    pickerStep = 0
                                                                    return@TextButton
                                                                }
                                                                val picked = java.time.Instant.ofEpochMilli(millis)
                                                                    .atZone(java.time.ZoneOffset.UTC)
                                                                    .toLocalDate()
                                                                emedApptDate = "%04d-%02d-%02d".format(
                                                                    picked.year, picked.monthValue, picked.dayOfMonth
                                                                )
                                                                emedApptTime = "%02d:%02d".format(
                                                                    timePickerState.hour, timePickerState.minute
                                                                )
                                                                showDateTimePicker = false
                                                                saveMedicalAppointment(worker.ID ?: "")
                                                            }
                                                        }) { Text(if (pickerStep == 0) "Nekis" else "OK") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (showDoneButton) {
                                    var showDonePicker by remember { mutableStateOf(false) }
                                    var donePickerStep by remember { mutableStateOf(0) }
                                    val doneDatePickerState = rememberDatePickerState()
                                    val doneTimePickerState = rememberTimePickerState(is24Hour = false)

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            donePickerStep = 0
                                            showDonePicker = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("✅ Mi mekem medikel finis")
                                    }

                                    if (showDonePicker) {
                                        Dialog(onDismissRequest = { showDonePicker = false }) {
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = if (donePickerStep == 0) "Pikim date blong medikel" else "Pikim taem (i no mas eksak)",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                                                    )
                                                    if (donePickerStep == 0) {
                                                        DatePicker(state = doneDatePickerState)
                                                    } else {
                                                        TimePicker(
                                                            state = doneTimePickerState,
                                                            modifier = Modifier
                                                                .align(Alignment.CenterHorizontally)
                                                                .padding(vertical = 16.dp)
                                                        )
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        TextButton(onClick = { showDonePicker = false }) {
                                                            Text("Kansel")
                                                        }
                                                        TextButton(onClick = {
                                                            if (donePickerStep == 0) {
                                                                if (doneDatePickerState.selectedDateMillis == null) {
                                                                    Toast.makeText(context, "Plis pikim wan date", Toast.LENGTH_SHORT).show()
                                                                    return@TextButton
                                                                }
                                                                donePickerStep = 1
                                                            } else {
                                                                val millis = doneDatePickerState.selectedDateMillis
                                                                if (millis == null) {
                                                                    donePickerStep = 0
                                                                    return@TextButton
                                                                }
                                                                val picked = java.time.Instant.ofEpochMilli(millis)
                                                                    .atZone(java.time.ZoneOffset.UTC)
                                                                    .toLocalDate()
                                                                val date = "%04d-%02d-%02d".format(
                                                                    picked.year, picked.monthValue, picked.dayOfMonth
                                                                )
                                                                val time = "%02d:%02d".format(
                                                                    doneTimePickerState.hour, doneTimePickerState.minute
                                                                )
                                                                showDonePicker = false
                                                                saveMedicalDone(worker.ID ?: "", date, time)
                                                            }
                                                        }) { Text(if (donePickerStep == 0) "Nekis" else "OK") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (emedKey in setOf("app-going", "sent", "notified", "not-yet", "app-appt")) {
                                    val clinicPreferred = if (isNZ) "NZ" else "AU"
                                    var clinics by remember(clinicPreferred) { mutableStateOf<List<MedicalClinic>>(emptyList()) }
                                    LaunchedEffect(clinicPreferred) {
                                        viewModel.loadClinics(clinicPreferred) { clinics = it }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (isNZ) "Klinik blong general medical" else "Klinik blong eMedical",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (clinics.isEmpty()) {
                                        Text(
                                            text = "No klinik i stap yet.",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    clinics.forEach { clinic ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                        ) {
                                            Box {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp)
                                                        .padding(end = 40.dp),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        if (clinic.preferred.equals("Yes", true)) {
                                                            Icon(
                                                                imageVector = Icons.Default.Star,
                                                                contentDescription = "Preferred clinic",
                                                                tint = Color(0xFFD4A017),
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = clinic.name,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                    }
                                                    Text(
                                                        text = clinic.location,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "Isi o Had: ${clinic.difficulty}",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = "Est Vatu: ${clinic.cost}",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    Text(
                                                        text = "Taem blong mekem: ${clinic.assessTime}",
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val label = clinic.name
                                                        val uri = "geo:${clinic.lat},${clinic.lng}?q=${clinic.lat},${clinic.lng}($label)&z=15".toUri()
                                                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                            setPackage("com.google.android.apps.maps")
                                                        }
                                                        try {
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Timber.e(e, "Clinic map open error")
                                                            Toast.makeText(context, "Failed to open Maps", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.LocationOn,
                                                        contentDescription = "Lukluk long Google Maps",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

// Logout button (unchanged)
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.logout(context)
                                navController.navigate("login") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text("Log Out")
                    }
                }
            }
        }
    }
    // App Update Dialog
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Niu Version i Redi") },
            text = {
                Text("""
                    Niu version blong Luksave i stap long Google Play.
                        
                    • Ol samting we i niu:
                    • Forgetem Login Ditel Skrin (isi blong faendem username o pin)
                    • Infomesen Scrin updet  (Soem ol bank, supamaket, ples blong kaekae we i klosap)
                    • Login mo Home screen i kam mo gud
                        
                    Plis update from i gat ol best features mo fix.
                """.trimIndent())
            },
            confirmButton = {
                Button(onClick = {
                    latestUpdateUrl?.let { url ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                    showUpdateDialog = false
                }) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    // Username prompt dialog (your existing one stays below)
    if (showUsernamePrompt) {
        // ... your existing username dialog code ...
    }

    // Username prompt dialog
    if (showUsernamePrompt) {
        AlertDialog(
            onDismissRequest = { /* Enforce setting username */ },
            title = { Text("Set Your Username") },
            text = {
                Column {
                    Text("Please choose a unique username to use instead of your worker ID.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = {
                            usernameInput = it.trim()
                            usernameError = null
                        },
                        label = { Text("Username") },
                        isError = usernameError != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (usernameInput.isEmpty() || usernameInput.length < 4) {
                                    usernameError = "Username must be at least 4 characters"
                                    Timber.d("HomeScreen: Username prompt: Validation failed - Username too short: '$usernameInput'")
                                } else {
                                    coroutineScope.launch {
                                        handleUsernameSubmission(
                                            usernameInput = usernameInput,
                                            viewModel = viewModel,
                                            context = context,
                                            keyboardController = keyboardController,
                                            onSuccess = { showUsernameSuccess = true },
                                            onError = { error -> usernameError = error }
                                        )
                                    }
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    usernameError?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (usernameInput.isEmpty() || usernameInput.length < 4) {
                            usernameError = "Username must be at least 4 characters"
                            Timber.d("HomeScreen: Username prompt: Validation failed - Username too short: '$usernameInput'")
                        } else {
                            coroutineScope.launch {
                                handleUsernameSubmission(
                                    usernameInput = usernameInput,
                                    viewModel = viewModel,
                                    context = context,
                                    keyboardController = keyboardController,
                                    onSuccess = { showUsernameSuccess = true },
                                    onError = { error -> usernameError = error }
                                )
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            }
            // No dismissButton to enforce username setting
        )
    }
}

suspend fun performCheckInAndSaveLocation(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    viewModel: EmpowerViewModel,
    phone: String,
    onError: (String) -> Unit
) {
    try {
        viewModel.checkIn(phone, context)
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            val location: Location? = fusedLocationClient.lastLocation.await()
            if (location != null) {
                viewModel.saveLocation(
                    context,
                    location.latitude,
                    location.longitude,
                    "Check-In"
                )
            } else {
                onError("Unable to get location")
                Timber.tag("HomeScreen").e("Unable to get location")
            }
        } else {
            onError("Location permission not granted")
            Timber.tag("HomeScreen").e("Location permission not granted")
        }
    } catch (e: Exception) {
        onError("Failed to process check-in or location: ${e.message}")
        Timber.tag("HomeScreen").e(e, "Check-in or location error")
    }
}

suspend fun performLocationUpdate(
    context: Context,
    fusedLocationClient: FusedLocationProviderClient,
    viewModel: EmpowerViewModel,
    action: String,
    onError: (String) -> Unit
) {
    try {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            val location: Location? = fusedLocationClient.lastLocation.await()
            if (location != null) {
                viewModel.saveLocation(
                    context,
                    location.latitude,
                    location.longitude,
                    action
                )
            } else {
                onError("Unable to get location")
                Timber.tag("HomeScreen").e("Unable to get location")
            }
        } else {
            onError("Location permission not granted")
            Timber.tag("HomeScreen").e("Location permission not granted")
        }
    } catch (e: Exception) {
        onError("Failed to process location update: ${e.message}")
        Timber.tag("HomeScreen").e(e, "Location update error")
    }
}
private fun isNewerVersion(current: String, latest: String): Boolean {
    return try {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        latestParts.zip(currentParts).any { (l, c) -> l > c }
    } catch (e: Exception) {
        false
    }
}
// Safe way to get Activity from Context
fun Context.findActivity(): Activity {
    var currentContext: Context = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    throw IllegalStateException("No Activity found in context chain for HomeScreen")
}
fun createNotificationChannel(context: Context) {
    val channelId = "luksave_recruitment"
    val channelName = "Job Alerts & Recruitment"
    val importance = NotificationManager.IMPORTANCE_HIGH

    val channel = NotificationChannel(channelId, channelName, importance).apply {
        description = "Notifications for new jobs, applications, messages, etc."
        enableLights(true)
        enableVibration(true)
    }

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)
}
