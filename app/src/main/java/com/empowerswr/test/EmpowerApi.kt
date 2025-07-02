package com.empowerswr.test

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Path

interface EmpowerApi {
    @POST("api.php/register")
    suspend fun register(@Body request: RegistrationRequest): LoginResponse

    @POST("api.php/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api.php/worker")
    suspend fun getWorkerDetails(@Query("token") token: String): WorkerResponse

    @GET("api.php/alerts")
    suspend fun getAlerts(@Query("token") token: String): List<Alert>

    @POST("api.php/worker/update")
    suspend fun updateWorker(@Query("workerId") workerId: String, @Body request: WorkerUpdateRequest): WorkerUpdateResponse

    @POST("api.php/checkin")
    suspend fun checkIn(@Query("token") token: String, @Body request: CheckInRequest): CheckInResponse

    @POST("api.php/update-fcm-token")
    suspend fun updateFcmToken(@Query("workerId") workerId: String, @Body token: String)

    @GET("api.php/teams")
    suspend fun getTeams(): List<TeamResponse>

    @GET("api.php/history")
    suspend fun getWorkerHistory(@Query("workerId") workerId: String): List<HistoryResponse>

    @POST("api.php/location")
    suspend fun saveLocation(
        @Query("token") token: String,
        @Body request: LocationRequest
    ): LocationResponse
}