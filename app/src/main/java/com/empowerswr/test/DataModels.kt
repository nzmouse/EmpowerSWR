package com.empowerswr.test

data class RegistrationRequest(val passport: String, val surname: String, val pin: String)
data class LoginRequest(val workerId: String, val pin: String)
data class LoginResponse(val token: String, val expiry: Long, val workerId: String)
data class WorkerResponse(
    val givenName: String?,
    val surname: String?,
    val teamName: String?,
    val notices: String?
)
data class Alert(val message: String)
data class CheckInRequest(val phone: String)
data class CheckInResponse(val success: Boolean, val message: String? = null)
data class Notification(val title: String, val body: String)