package com.empowerswr.test

import com.google.gson.annotations.SerializedName

data class WorkerHistory(
    @SerializedName("team") val team: String?,
    @SerializedName("employer") val employer: String?,
    @SerializedName("country") val country: String?,
    @SerializedName("dateFrom") val dateFrom: String?,
    @SerializedName("dateTo") val dateTo: String?
)