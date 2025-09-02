package com.empowerswr.luksave

import com.google.gson.annotations.SerializedName
import java.util.Date

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
    val birthProvince: String?,
    val ppissued: String?,
    val ppexpiry: String?,
    val pendingFields: List<String>? = null
)
data class HistoryResponse(
    val team: String?,
    val employer: String?,
    val country: String?,
    val dateFrom: String?,
    val dateTo: String?
)
data class Alert(val message: String)
data class Notification(val title: String, val body: String)
data class CheckInRequest(
    val phone: String,
    val worker_id: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class LocationResponse(val success: Boolean, val message: String?)
data class CheckInResponse(val success: Boolean, val message: String? = null)

data class LocationRequest(
    val worker_id: String,
    val latitude: Double,
    val longitude: Double,
    val action: String
)

data class RegistrationResponse(
    val token: String,
    val workerId: String,
    val expiry: Long
)


data class FlightDetails(
    @SerializedName("flightID") val flightID: Int,
    @SerializedName("intDepDate") val intDepDate: String,
    @SerializedName("intFlightNo") val intFlightNo: String,
    @SerializedName("intDest") val intDest: String,
    @SerializedName("intArrDate") val intArrDate: String,
    @SerializedName("domFlightNo") val domFlightNo: String?,
    @SerializedName("domDest") val domDest: String?,
    @SerializedName("domDepDate") val domDepDate: String?,
    @SerializedName("domArrDate") val domArrDate: String?,
    @SerializedName("dom2FlightNo") val dom2FlightNo: String?,
    @SerializedName("dom2Dest") val dom2Dest: String?,
    @SerializedName("dom2DepDate") val dom2DepDate: String?,
    @SerializedName("dom2ArrDate") val dom2ArrDate: String?,
    @SerializedName("hotel1") val hotel1: String?,
    @SerializedName("hotel2") val hotel2: String?,
    @SerializedName("teamName") val teamName: String?,
    @SerializedName("firstName") val firstName: String?,
    @SerializedName("surname") val surname: String?,
    @SerializedName("flightStatus") val flightStatus: String?
)
data class PdbDetails(
    @SerializedName("startDate") val startDate: String?,
    @SerializedName("endDate") val endDate: String? = null, // Optional, hidden if schemes = 'RSE'
    @SerializedName("location") val location: String?,
    @SerializedName("pdbLocationLong") val pdbLocationLong: String?,
    @SerializedName("schemes") val schemes: String?,
    @SerializedName("pdbDate") val pdbDate: Date?,
    @SerializedName("pdbLat") val pdbLat: Double?,
    @SerializedName("pdbLong") val pdbLong: Double?,
    @SerializedName("pdbStatus") val pdbStatus: String?,
    @SerializedName("internalPdb") val internalPdb: String?,
    @SerializedName("internalPdbStatus") val internalPdbStatus: String?
)
data class PdbUpdateResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("error") val error: String? = null
)

data class DirectoryEntry(
    val dirID: Int,
    val dirName: String,
    val dirType: String,
    val dirLat: Float?,
    val dirLong: Float?,
    val dirPhone: String?,
    val dirPhone2: String?,
    val dirEmail: String?,
    val dirCard: String
)

data class Team(
    @SerializedName("teamId") val teamId: Int,
    @SerializedName("teamName") val teamName: String?,
    @SerializedName("depDateEst") val estDepDate: String?,
    @SerializedName("teamFarm1") val teamFarm1: String?,
    @SerializedName("teamFarm2") val teamFarm2: String?,
    @SerializedName("teamFarm3") val teamFarm3: String?,
    @SerializedName("classification") val classification: String?,
    @SerializedName("payType") val payType: String?,
    @SerializedName("classification2") val classification2: String?,
    @SerializedName("payType2") val payType2: String?,
    @SerializedName("classification3") val classification3: String?,
    @SerializedName("payType3") val payType3: String?,
    @SerializedName("award1") val award1: String?,
    @SerializedName("award2") val award2: String?,
    @SerializedName("award3") val award3: String?,
    @SerializedName("rate1") val rate1: String?,
    @SerializedName("rate2") val rate2: String?,
    @SerializedName("rate3") val rate3: String?,
    @SerializedName("DepDate") val depDate: String?,
    @SerializedName("rse1Name") val rse1Name: String?,
    @SerializedName("RSE1Purpose") val rse1Purpose: String?,
    @SerializedName("RSE1End") val rse1End: String?,
    @SerializedName("rse2Name") val rse2Name: String?,
    @SerializedName("ATR2") val atr2: String?,
    @SerializedName("RSE2Start") val rse2Start: String?,
    @SerializedName("RSE2Purpose") val rse2Purpose: String?,
    @SerializedName("RSE2End") val rse2End: String?,
    @SerializedName("rse3Name") val rse3Name: String?,
    @SerializedName("ATR3") val atr3: String?,
    @SerializedName("RSE3Start") val rse3Start: String?,
    @SerializedName("RSE3Purpose") val rse3Purpose: String?,
    @SerializedName("RSE3End") val rse3End: String?,
    @SerializedName("ATREnd") val atrEnd: String?,
    @SerializedName("ArrDate") val arrDate: String?,
    @SerializedName("IntDepdate") val intDepDate: String?,
    @SerializedName("ArrivalDate") val arrivalDate: String?,
    @SerializedName("employer") val employer: String?
)

data class TeamLocation(
    @SerializedName("googlemapAddress") val googleMapAddress: String?,
    @SerializedName("farmGPSLat") val farmGPSLat: Float?,
    @SerializedName("farmGPSLong") val farmGPSLong: Float?,
    @SerializedName("farmCrop") val farmCrop: String?,
    @SerializedName("farmName") val farmName: String?
)

data class FeedbackRequest(
    val workerId: String,
    val teamId: Int?,
    val feedbackText: String,
    val screen: String?
)

data class FeedbackResponse(
    @SerializedName("message") val message: String
)