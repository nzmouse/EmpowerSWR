package com.empowerswr.luksave


import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface EmpowerApi {
    @POST("api.php/register")
    suspend fun register(@Body request: RegistrationRequest): Response<RegistrationResponse>

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

    @POST("user_updates.php/submit_update")  // Adjust path to match your api.php action
    suspend fun submitPendingUpdate(
        @Query("token") token: String,
        @Body pendingData: Map<String, String>  // Flexible: field_key to new_value (string or map for complex like addresses)
    ): WorkerResponse  // Returns refreshed worker with pendingFields added

    @GET("flights.php/flights")
    suspend fun getFlightDetails(
        @Query("workerId") workerId: String,
        @Query("token") token: String
    ): FlightDetails

    @GET("flights.php/pdb")
    suspend fun getPdbDetails(
        @Query("workerId") workerId: String,
        @Query("token") token: String
    ): PdbDetails

    @POST("flights.php/update_pdb_status")
    suspend fun updatePdbStatus(
        @Query("workerId") workerId: String,
        @Query("token") token: String
    ): PdbUpdateResponse

    @POST("flights.php/update_flight_status")
    suspend fun updateFlightStatus(
        @Query("workerId") workerId: String,
        @Query("token") token: String
    ): PdbUpdateResponse

    @POST("flights.php/update_pdb_internal_status")
    suspend fun updatePdbInternalStatus(
        @Query("workerId") workerId: String,
        @Query("token") token: String
    ): PdbUpdateResponse

    @GET("information.php/directory")
    suspend fun getDirectory(
        @Query("token") token: String,
        @Query("workerId") workerId: String
    ): List<DirectoryEntry>
}