package com.empowerswr.luksave.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.empowerswr.luksave.EmpowerViewModel
import com.empowerswr.luksave.PrefsHelper
import com.empowerswr.luksave.Team
import com.empowerswr.luksave.TeamLocation
import com.google.gson.JsonParseException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import timber.log.Timber
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.util.*

// Define inputFormats at the top level for global access
private val inputFormats: List<SimpleDateFormat>
    get() {
        val currentLocale = Locale.getDefault()
        return listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", currentLocale).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", currentLocale).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd", currentLocale).apply { timeZone=TimeZone.getTimeZone("UTC") }
        )
    }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    viewModel: EmpowerViewModel,
    navController: NavHostController,
    context: Context = LocalContext.current
) {
    val teamEntries by viewModel.teamEntries.collectAsState()
    val teamLocations by viewModel.teamLocations.collectAsState()
    val token by viewModel.token
    val workerId = PrefsHelper.getWorkerId(context) ?: ""
    val notices by viewModel.notices.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var fetchError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var isRefreshing by remember { mutableStateOf(false) }

    // Fetch notices and teams on initialization
    LaunchedEffect(token, workerId) {
        if (token == null || workerId.isEmpty()) {
            navController.navigate("login") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            val safeToken = token as String
            try {
                viewModel.fetchTeams(safeToken, workerId, limit = 20, offset = 0)
                viewModel.fetchNotices(safeToken, workerId)
                fetchError = null
            } catch (e: HttpException) {
                val errorMessage = when (e.code()) {
                    404 -> "No teams or notices found for this worker. Please check your account or team details."
                    401 -> "Invalid or expired token. Please log in again."
                    403 -> "Unauthorized for this worker."
                    500 -> "Server error. Please try again later."
                    else -> "Failed to load data: ${e.message()}"
                }
                fetchError = errorMessage
                Timber.tag("TeamScreen").e(e, "Failed to fetch teams and notices")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(errorMessage)
                }
            } catch (e: JsonParseException) {
                fetchError = "Failed to parse team data. Please try again."
                Timber.tag("TeamScreen").e(e, "JSON parsing error")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to parse team data. Please try again.")
                }
            } catch (e: Exception) {
                fetchError = "Failed to load data: ${e.message ?: "Unknown error"}"
                Timber.tag("TeamScreen").e(e, "Unexpected error during fetch")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to load data: ${e.message ?: "Unknown error"}")
                }
            }
        }
    }

    // Retry fetching notices if null with a delay
    LaunchedEffect(notices, token, workerId) {
        if (notices == null && token != null && workerId.isNotEmpty()) {
            delay(1000)
            viewModel.fetchNotices(token as String, workerId)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
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
                    text = "Please log in to view team data",
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
        } else if (teamEntries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = fetchError ?: "Team Data Loading...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (token != null && workerId.isNotEmpty()) {
                                val safeToken = token as String
                                try {
                                    viewModel.fetchTeams(safeToken, workerId, limit = 20, offset = 0)
                                    viewModel.fetchNotices(safeToken, workerId)
                                    fetchError = null
                                } catch (e: HttpException) {
                                    val errorMessage = when (e.code()) {
                                        404 -> "No teams or notices found for this worker. Please check your account or team details."
                                        401 -> "Invalid or expired token. Please log in again."
                                        403 -> "Unauthorized for this worker."
                                        500 -> "Server error. Please try again later."
                                        else -> "Failed to load data: ${e.message()}"
                                    }
                                    fetchError = errorMessage
                                    Timber.tag("TeamScreen").e(e, "Retry failed to fetch teams and notices")
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(errorMessage)
                                    }
                                } catch (e: JsonParseException) {
                                    fetchError = "Failed to parse team data. Please try again."
                                    Timber.tag("TeamScreen").e(e, "Retry JSON parsing error")
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Failed to parse team data. Please try again.")
                                    }
                                } catch (e: Exception) {
                                    fetchError = "Failed to load data: ${e.message ?: "Unknown error"}"
                                    Timber.tag("TeamScreen").e(e, "Retry unexpected error")
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Failed to load data: ${e.message ?: "Unknown error"}")
                                    }
                                }
                            } else {
                                fetchError = "Please log in to view team data"
                                Timber.tag("TeamScreen").e("Retry failed: missing authentication data")
                                navController.navigate("login") {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Retry")
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        if (token != null && workerId.isNotEmpty()) {
                            isRefreshing = true
                            try {
                                viewModel.fetchTeams(token as String, workerId, limit = 20, offset = 0)
                                viewModel.fetchNotices(token as String, workerId)
                                fetchError = null
                            } catch (e: HttpException) {
                                val errorMessage = when (e.code()) {
                                    404 -> "No teams or notices found."
                                    401 -> "Invalid or expired token. Please log in again."
                                    403 -> "Unauthorized for this worker."
                                    500 -> "Server error. Please try again later."
                                    else -> "Failed to refresh: ${e.message()}"
                                }
                                fetchError = errorMessage
                                Timber.tag("TeamScreen").e(e, "Pull-to-refresh failed")
                                snackbarHostState.showSnackbar(errorMessage)
                                if (e.code() == 401) {
                                    navController.navigate("login") {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            } catch (e: JsonParseException) {
                                fetchError = "Failed to parse team data."
                                Timber.tag("TeamScreen").e(e, "Pull-to-refresh JSON parsing error")
                                snackbarHostState.showSnackbar("Failed to parse team data.")
                            } catch (e: Exception) {
                                fetchError = "Failed to refresh: ${e.message ?: "Unknown error"}"
                                Timber.tag("TeamScreen").e(e, "Pull-to-refresh unexpected error")
                                snackbarHostState.showSnackbar("Failed to refresh: ${e.message ?: "Unknown error"}")
                            } finally {
                                isRefreshing = false
                            }
                        } else {
                            isRefreshing = false
                            Timber.tag("TeamScreen").e("Pull-to-refresh failed: missing authentication data")
                            snackbarHostState.showSnackbar("Please log in to refresh")
                            navController.navigate("login") {
                                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(teamEntries) { team ->
                        TeamCard(
                            team = team,
                            locations = teamLocations[team.teamId] ?: emptyList(),
                            onFeedbackClick = { teamId -> /* No logging needed here */ },
                            onLocationClick = { lat, long, label ->
                                val uri = "geo:$lat,$long?q=$lat,$long($label)&z=15".toUri()
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Timber.tag("TeamScreen").e(e, "Failed to open map")
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Failed to open Maps: ${e.message}")
                                    }
                                }
                            },
                            viewModel = viewModel,
                            token = token,
                            workerId = workerId,
                            navController = navController,
                            snackbarHostState = snackbarHostState,
                            notices = notices
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TeamCard(
    team: Team,
    locations: List<TeamLocation>,
    onFeedbackClick: (Int) -> Unit,
    onLocationClick: (Float, Float, String) -> Unit,
    viewModel: EmpowerViewModel,
    token: String?,
    workerId: String,
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    notices: String?
) {
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()


    // Select departure date
    val selectedDepDate = when {
        team.intDepDate != null && team.intDepDate != "0000-00-00" && team.intDepDate.isNotBlank() -> team.intDepDate
        team.depDate != null && team.depDate != "0000-00-00" && team.depDate.isNotBlank() -> team.depDate
        team.estDepDate != null && team.estDepDate != "0000-00-00" && team.estDepDate.isNotBlank() -> team.estDepDate
        else -> null
    }
    val depDateFormatted = formatDateFlexible(selectedDepDate)
    val depDateLabel = when {
        team.intDepDate != null && team.intDepDate != "0000-00-00" && team.intDepDate.isNotBlank() -> "Confirmed Departure"
        team.depDate != null && team.depDate != "0000-00-00" && team.depDate.isNotBlank() -> "Expected Departure"
        team.estDepDate != null && team.estDepDate != "0000-00-00" && team.estDepDate.isNotBlank() -> "Departure around"
        else -> "Departure"
    }
    val arrDateFormatted = formatDateFlexible(team.arrivalDate ?: team.arrDate)
    val rse1EndFormatted = formatDateFlexible(team.rse1End)
    val rse2StartFormatted = formatDateFlexible(team.rse2Start)
    val rse2EndFormatted = formatDateFlexible(team.rse2End)
    val rse3StartFormatted = formatDateFlexible(team.rse3Start)
    val rse3EndFormatted = formatDateFlexible(team.rse3End)

    // Calculate duration
    val durationText = calculateDuration(
        selectedDepDate,
        team.arrivalDate ?: team.arrDate
    )

    // Check if rse2Name or rse3Name is "~none" (case-insensitive)
    val showAssignment2 = team.rse2Name?.equals("~none", ignoreCase = true)?.not() ?: false
    val showAssignment3 = team.rse3Name?.equals("~none", ignoreCase = true)?.not() ?: false

    // Concatenate tasks for each assignment
    val tasks1 = when {
        team.rse1Purpose != null && team.classification != null -> "${team.rse1Purpose} ${team.classification}"
        team.rse1Purpose != null -> team.rse1Purpose
        team.classification != null -> team.classification
        else -> "N/A"
    }
    val tasks2 = when {
        team.rse2Purpose != null && team.classification2 != null -> "${team.rse2Purpose} ${team.classification2}"
        team.rse2Purpose != null -> team.rse2Purpose
        team.classification2 != null -> team.classification2
        else -> "N/A"
    }
    val tasks3 = when {
        team.rse3Purpose != null && team.classification3 != null -> "${team.rse3Purpose} ${team.classification3}"
        team.rse3Purpose != null -> team.rse3Purpose
        team.classification3 != null -> team.classification3
        else -> "N/A"
    }

    // Feedback Dialog
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isLoading) {
                    showFeedbackDialog = false
                    feedbackText = ""
                }
            },
            title = { Text("Submit Feedback for ${team.teamName ?: "Unnamed Team"}") },
            text = {
                Column {
                    TextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        enabled = !isLoading
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (feedbackText.isNotBlank() && token != null && workerId.isNotEmpty()) {
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    viewModel.submitFeedback(token, workerId, team.teamId, feedbackText, null)
                                    snackbarHostState.showSnackbar("Feedback submitted successfully")
                                    showFeedbackDialog = false
                                    feedbackText = ""
                                } catch (e: HttpException) {
                                    val errorMessage = when (e.code()) {
                                        400 -> "Invalid input. Please check feedback text."
                                        401 -> "Invalid or expired token. Please log in again."
                                        403 -> "Unauthorized for this worker."
                                        500 -> "Server error. Please try again later."
                                        else -> "Failed to submit feedback: ${e.message()}"
                                    }
                                    Timber.tag("TeamCard").e(e, "Feedback submission failed")
                                    snackbarHostState.showSnackbar(errorMessage)
                                    if (e.code() == 401) {
                                        navController.navigate("login") {
                                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("TeamCard").e(e, "Unexpected error during feedback submission")
                                    snackbarHostState.showSnackbar("Failed to submit feedback: ${e.message ?: "Unknown error"}")
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Please enter feedback text")
                            }
                        }
                    },
                    enabled = feedbackText.isNotBlank() && !isLoading
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Text("Submit")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isLoading) {
                            showFeedbackDialog = false
                            feedbackText = ""
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Team Information Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Team Header with Feedback Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = team.employer ?: "Employer TBA",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = team.teamName ?: "Unnamed Team",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        showFeedbackDialog = true
                        onFeedbackClick(team.teamId)
                    },
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(start = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Team Details
            Text(
                text = "Team Information:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$depDateLabel: ${depDateFormatted.takeIf { it != "N/A" } ?: "Date Unavailable"}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (team.arrivalDate != null) "Confirmed Return: $arrDateFormatted" else "Est Return: ${arrDateFormatted.takeIf { it != "N/A" } ?: "Date Unavailable"}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Duration: ${durationText.takeIf { it != "N/A" } ?: "Not Available"}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Accept Job Button (only shown for notices = 'Locate' or 'App Checkin')
            if (notices == "Locate" || notices == "App Checkin") {
                Button(
                    onClick = {
                        if (token != null && workerId.isNotEmpty()) {
                            coroutineScope.launch {
                                try {
                                    viewModel.acceptApplication(token, workerId)
                                    snackbarHostState.showSnackbar(
                                        message = "Job accepted successfully",
                                        duration = SnackbarDuration.Short
                                    )
                                    try {
                                        viewModel.fetchTeams(token, workerId, limit = 20, offset = 0)
                                        viewModel.fetchNotices(token, workerId)
                                    } catch (e: Exception) {
                                        Timber.tag("TeamCard").e(e, "Failed to refresh data after job acceptance")
                                        snackbarHostState.showSnackbar("Failed to refresh data: ${e.message ?: "Unknown error"}")
                                    }
                                } catch (e: HttpException) {
                                    val errorMessage = when (e.code()) {
                                        404 -> "No matching worker found."
                                        401 -> "Invalid or expired token."
                                        403 -> "Unauthorized for this worker."
                                        500 -> "Server error. Please try again later."
                                        else -> "Failed to accept job: ${e.message()}"
                                    }
                                    Timber.tag("TeamCard").e(e, "Failed to accept job")
                                    snackbarHostState.showSnackbar(errorMessage)
                                    if (e.code() == 401) {
                                        navController.navigate("login") {
                                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    Timber.tag("TeamCard").e(e, "Unexpected error during job acceptance")
                                    snackbarHostState.showSnackbar("Error: ${e.message ?: "Unknown error"}")
                                }
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Please log in to accept the job")
                                navController.navigate("login") {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Accept Job",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

    // Master Assignments Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inversePrimary
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Assignments:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // Assignment 1 Subcard
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "1",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        team.employer?.let {
                            Text(
                                text = "Employer: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        team.teamFarm1?.let {
                            Text(
                                text = "Farm: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Tasks: $tasks1",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        team.award1?.let {
                            Text(
                                text = "Award: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        team.rate1?.let {
                            Text(
                                text = "Hourly Rate: $$it",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        team.payType?.let {
                            Text(
                                text = "Pay Type: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (showAssignment2) {
                            Text(
                                text = "End: $rse1EndFormatted",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Assignment 2 Subcard
            if (showAssignment2) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "2",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Employer: ${team.rse2Name}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            team.teamFarm2?.let {
                                Text(
                                    text = "Farm: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Tasks: $tasks2",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            team.award2?.let {
                                Text(
                                    text = "Award: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            team.rate2?.let {
                                Text(
                                    text = "Hourly Rate: $$it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            team.payType2?.let {
                                Text(
                                    text = "Pay Type: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Start: $rse2StartFormatted",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "End: $rse2EndFormatted",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Assignment 3 Subcard
            if (showAssignment3) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "3",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Employer: ${team.rse3Name}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            team.teamFarm3?.let {
                                Text(
                                    text = "Farm: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Tasks: $tasks3",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            team.award3?.let {
                                Text(
                                    text = "Award: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            team.rate3?.let {
                                Text(
                                    text = "Hourly Rate: $$it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            team.payType3?.let {
                                Text(
                                    text = "Pay Type: $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Start: $rse3StartFormatted",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "End: $rse3EndFormatted",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Information on this card is an estimate only. Your contract and any signed variations have the correct details.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    // Farm Locations Card
    if (locations.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Work Locations:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                locations.forEach { location ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            location.farmName?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } ?: Text(
                                text = "Unnamed Farm",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            location.farmCrop?.let {
                                Text(
                                    text = "Crop: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } ?: Text(
                                text = "Crop: N/A",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            location.googleMapAddress?.let {
                                Text(
                                    text = "Address: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } ?: Text(
                                text = "Address: N/A",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (location.farmGPSLat != null && location.farmGPSLong != null) {
                            IconButton(
                                onClick = {
                                    onLocationClick(
                                        location.farmGPSLat,
                                        location.farmGPSLong,
                                        location.farmName ?: "Farm"
                                    )
                                }
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
        }
    }
}

fun formatDate(
    dateString: String?,
    inputFormat: SimpleDateFormat,
    outputFormat: SimpleDateFormat
): String {
    return try {
        dateString?.let {
            outputFormat.format(inputFormat.parse(it)!!)
        } ?: "N/A"
    } catch (e: Exception) {
        Timber.tag("TeamCard").e(e, "Failed to format date")
        "N/A"
    }
}

private fun calculateDuration(startDateString: String?, endDateString: String?): String {
    try {
        var startDate: Date? = null
        var endDate: Date? = null
        inputFormats.forEach { format ->
            try {
                if (startDate == null && startDateString != null && startDateString != "0000-00-00" && startDateString.isNotBlank()) {
                    startDate = format.parse(startDateString)
                }
                if (endDate == null && endDateString != null && endDateString != "0000-00-00" && endDateString.isNotBlank()) {
                    endDate = format.parse(endDateString)
                }
                if (startDate != null && endDate != null) return@forEach
            } catch (e: Exception) {
                Timber.tag("TeamCard").w("Failed to parse with pattern ${format.toPattern()}: ${e.message}")
            }
        }
        if (startDate != null && endDate != null) {
            val duration = Duration.between(
                Instant.ofEpochMilli(startDate.time),
                Instant.ofEpochMilli(endDate.time)
            )
            val days = duration.toDays()
            val months = (days / 30).toInt()
            val weeks = ((days % 30) / 7).toInt()
            val result = when {
                weeks == 0 -> if (months == 0) "Less than a month" else "$months month${if (months > 1) "s" else ""}"
                weeks == 4 -> "${months + 1} month${if (months + 1 > 1) "s" else ""}"
                months == 0 -> "$weeks week${if (weeks > 1) "s" else ""}"
                else -> "$months month${if (months > 1) "s" else ""}, $weeks week${if (weeks > 1) "s" else ""}"
            }
            return result
        } else {
            Timber.tag("TeamCard").w("Start or end date is null or invalid: start=$startDate, end=$endDate")
            return "N/A"
        }
    } catch (e: Exception) {
        Timber.tag("TeamCard").e(e, "Duration calculation error")
        return "N/A"
    }
}

// Helper function to format dates
private fun formatDateFlexible(dateString: String?): String {
    if (dateString == null || dateString == "0000-00-00" || dateString.isBlank()) {
        Timber.tag("TeamCard").w("Date string is null, invalid, or blank: $dateString")
        return "N/A"
    }
    val outputFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    inputFormats.forEach { format ->
        try {
            val date = format.parse(dateString)
            if (date != null) {
                val formatted = outputFormat.format(date)
                return formatted
            }
        } catch (e: Exception) {
            Timber.tag("TeamCard").w("Failed to parse $dateString with pattern ${format.toPattern()}: ${e.message}")
        }
    }
    Timber.tag("TeamCard").e("All date parsing attempts failed for: $dateString")
    return "N/A"
}