package com.empowerswr.test

data class RegistrationRequest(val passport: String, val surname: String, val pin: String)
data class LoginRequest(val workerId: String, val pin: String)
data class LoginResponse(val token: String, val expiry: Long, val workerId: String)
data class WorkerResponse(
    val ID: String?,
    val firstName: String?,
    val surname: String?,
    val prefName: String?,
    val dob: String?,
    val teamName: String?,
    val homeVillage: String?,
    val homeIsland: String?,
    val residentialAddress: String?,
    val residentialIsland: String?,
    val phone: String?,
    val phone2: String?,
    val aunzPhone: String?,
    val email: String?,
    val dLicence: String?,
    val dLClass: String?,
    val dLicenceExp: String?,
    val notices: String?,
    val ppno: String?,
    val birthplace: String?,
    val ppissued: String?,
    val ppexpiry: String?
)
data class HistoryResponse(
    val team: String?,
    val employer: String?,
    val country: String?,
    val dateFrom: String?,
    val dateTo: String?
)
data class Alert(val message: String)
data class CheckInRequest(val phone: String)
data class CheckInResponse(val success: Boolean, val message: String? = null)
data class Notification(val title: String, val body: String)