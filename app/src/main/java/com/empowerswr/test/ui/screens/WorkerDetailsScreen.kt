package com.empowerswr.test.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.empowerswr.test.EmpowerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerDetailsScreen(
    navController: NavHostController,
    viewModel: EmpowerViewModel,
    workerId: String,
    context: Context
) {
    Log.d("EmpowerSWR", "Entered WorkerDetailsScreen with workerId: $workerId, context: $context")

    val worker by viewModel.workerDetails
    val history by viewModel.workerHistory
    val errorMessage by viewModel.errorMessage

    LaunchedEffect(workerId) {
        if (workerId.isNotEmpty()) {
            Log.d("EmpowerSWR", "Fetching data for workerId: $workerId")
            viewModel.setWorkerId(workerId)
            viewModel.fetchWorkerDetails()
            viewModel.fetchWorkerHistory()
        } else {
            Log.e("EmpowerSWR", "Invalid workerId: empty")
            viewModel.setErrorMessage("Invalid worker ID")
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Log.d("EmpowerSWR", "Displaying error: $it")
        }

        if (worker == null && errorMessage == null) {
            CircularProgressIndicator()
            Log.d("EmpowerSWR", "Showing loading indicator")
        } else {
            worker?.let { w ->
                Log.d("EmpowerSWR", "Rendering worker data: ${w.firstName} ${w.surname}, phone: ${w.phone}, phone2: ${w.phone2}, aunzPhone: ${w.aunzPhone}")
                Text(text = "Personal Info", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Surname: ${w.surname ?: "N/A"}")
                Text(text = "Given Names: ${w.firstName ?: "N/A"}")
                Text(text = "Preferred Name: ${w.prefName ?: "N/A"}")
                Text(text = "DoB: ${formatDateSafely(w.dob) ?: "N/A"}")
                Text(text = "Team: ${w.teamName ?: "N/A"}")

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Home Village", style = MaterialTheme.typography.headlineSmall)
                Text(text = "${w.homeVillage ?: "N/A"}, ${w.homeIsland ?: "N/A"}")

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Residential Address", style = MaterialTheme.typography.headlineSmall)
                Text(text = "${w.residentialAddress ?: "N/A"}, ${w.residentialIsland ?: "N/A"}")

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Contact Details", style = MaterialTheme.typography.headlineSmall)
                Text(text = "Phone: ${w.phone?.ifEmpty { "N/A" } ?: "N/A"}")
                Text(text = "2nd Phone: ${w.phone2?.ifEmpty { "N/A" } ?: "N/A"}")
                Text(text = "NZ/Australia Phone: ${w.aunzPhone?.ifEmpty { "N/A" } ?: "N/A"}")
                Text(text = "Email: ${w.email ?: "N/A"}")

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Driver’s License", style = MaterialTheme.typography.headlineSmall)
                Text(text = "Licence Number: ${w.dLicence ?: "N/A"}")
                Text(text = "Classes: ${w.dLClass ?: "N/A"}")
                Text(text = "Expiry: ${formatDateSafely(w.dLicenceExp) ?: "N/A"}")

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "History", style = MaterialTheme.typography.headlineSmall)
                Log.d("EmpowerSWR", "History size: ${history.size}, history: $history")
                if (history.isEmpty()) {
                    Text(text = "No history available")
                    Log.d("EmpowerSWR", "No history records found")
                } else {
                    LazyColumn {
                        items(history) { h ->
                            Log.d("EmpowerSWR", "Rendering history: team=${h.team}, employer=${h.employer}, country=${h.country}")
                            Card(modifier = Modifier.padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = "Team: ${h.team ?: "N/A"}")
                                    Text(text = "Employer: ${h.employer ?: "N/A"}")
                                    Text(text = "Country: ${h.country ?: "N/A"}")
                                    Text(text = "Start: ${formatDateSafely(h.dateFrom) ?: "N/A"}")
                                    Text(text = "End: ${formatDateSafely(h.dateTo) ?: "Ongoing"}")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { /* navController.navigate("update_details/$workerId") */ },
                    enabled = false
                ) {
                    Text("Edit Details (Not Implemented)")
                }
            }
        }
    }
}

private fun formatDateSafely(date: String?): String {
    if (date.isNullOrEmpty() || date == "0000-00-00") return ""
    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val outputFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
    return try {
        outputFormat.format(inputFormat.parse(date)!!)
    } catch (e: Exception) {
        Log.e("EmpowerSWR", "Date format error: ${e.message}, date: $date")
        ""
    }
}