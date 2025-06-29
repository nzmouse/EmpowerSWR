package com.empowerswr.test

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path

interface EmpowerApi {
    @POST("register")
    suspend fun register(@Body request: RegistrationRequest): LoginResponse

    @POST("login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("worker")
    suspend fun getWorkerDetails(@Query("token") token: String): WorkerResponse

    @GET("alerts")
    suspend fun getAlerts(@Query("token") token: String): List<Alert>

    @POST("worker/update")
    suspend fun updateWorker(@Query("workerId") workerId: String, @Body request: WorkerUpdateRequest): WorkerUpdateResponse

    @POST("checkin")
    suspend fun checkIn(@Query("token") token: String, @Body request: CheckInRequest): CheckInResponse

    @POST("update-fcm-token")
    suspend fun updateFcmToken(@Query("workerId") workerId: String, @Body token: String)

    @GET("teams")
    suspend fun getTeams(): List<TeamResponse>

    @GET("history")
    suspend fun getWorkerHistory(@Query("workerId") workerId: String): List<HistoryResponse>

    @POST("location")
    suspend fun saveLocation(
        @Query("token") token: String,
        @Body request: LocationRequest
    ): LocationResponse
}