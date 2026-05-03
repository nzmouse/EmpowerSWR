package com.empowerswr.luksave


import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
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

    @POST("api.php/checkin")
    suspend fun checkIn(@Query("token") token: String, @Body request: CheckInRequest): CheckInResponse

    @POST("api.php/update-fcm-token")
    suspend fun updateFcmToken(
        @Query("workerId") workerId: String,
        @Body token: RequestBody   // Send as plain text
    ): Response<Unit>
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

    @GET("flights.php/inbound")
    suspend fun getInboundFlightDetails(
        @Query("workerId") workerId: String,
        @Query("token") token: String
    ): InboundFlightDetails?

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

    @GET("team.php/teams")
    suspend fun getTeams(
        @Query("token") token: String,
        @Query("workerId") workerId: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): List<Team>

    @GET("team.php/teams/locations")
    suspend fun getTeamLocations(
        @Query("token") token: String,
        @Query("workerId") workerId: String,
        @Query("teamId") teamId: Int
    ): List<TeamLocation>

    @POST("team.php/teams/feedback")
    suspend fun submitTeamFeedback(@Query("token") token: String, @Body body: FeedbackRequest): Response<Unit>

    // Global feedback
    @POST("feedback.php/submit")
    suspend fun submitFeedback(@Query("token") token: String, @Body body: FeedbackRequest): Response<Unit>
    @POST("team.php/teams/accept")
    suspend fun acceptApplication(
        @Query("token") token: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @GET("team.php/teams/notices")
    suspend fun getNotices(
        @Query("token") token: String,
        @Query("workerId") workerId: String
    ): Response<Map<String, String?>>

    @POST("updateContract.php/update_contract_status")
    suspend fun signContract(
        @Query("token") token: String,
        @Query("workerId") workerId: String
    ): SignContractResponse

    @POST("api.php/updateUsername")
    suspend fun updateUsername(
        @Query("token") token: String,
        @Body request: UpdateUsernameRequest
    ): UpdateUsernameResponse

    @POST("api.php/update-medical")  // or whatever path your backend uses
    suspend fun updateMedical(
        @Query("workerId") workerId: String,
        @Query("medical") medicalDate: String? = "",
        @Query("emed") emedStatus: String
    ): Response<Map<String, String?>>

    @POST("api.php/save_nid")
    suspend fun saveNID(
        @Query("workerId") workerId: String,
        @Query("nid") nid: String
    ): ApiResponse

    @FormUrlEncoded
    @POST("api.php/verify_for_reset")
    suspend fun verifyForReset(
        @Field("type") type: String,      // "passport" or "nid"
        @Field("value") value: String,
        @Field("answer") answer: String
    ): VerifyResetResponse

    @POST("api.php/reset_pin")
    suspend fun resetPin(
        @Query("workerId") workerId: String,
        @Query("new_pin") newPin: String
    ): ApiResponse

    @POST("api.php/save_nid_and_secret")
    suspend fun saveNIDAndSecret(
        @Query("workerId") workerId: String,
        @Query("nid") nid: String,
        @Query("secretAnswer") secretAnswer: String
    ): ApiResponse

    @GET("api.php/app_version")
    suspend fun checkAppVersion(): AppVersionResponse

    @POST("api.php/update_nid_expiry")
    suspend fun updateNIDExpiry(
        @Query("workerId") workerId: String,
        @Query("expiryDate") expiryDate: String
    ): ApiResponse

    @POST("api.php/update_nid_and_expiry")
    suspend fun updateNIDAndExpiry(
        @Query("workerId") workerId: String,
        @Query("nid") nid: String,
        @Query("expiryDate") expiryDate: String
    ): ApiResponse

    @POST("api.php/update_nid_expiry_only")
    suspend fun updateNIDExpiryOnly(
        @Query("workerId") workerId: String,
        @Query("expiryDate") expiryDate: String
    ): ApiResponse

    @GET("api.php/required_tasks")
    suspend fun getRequiredTasks(@Query("scheme") scheme: String): RequiredTasksResponse

    @retrofit2.http.POST("https://places.googleapis.com/v1/places:searchNearby")  // or your PHP proxy endpoint, e.g. "proxy/places-nearby"
    suspend fun searchNearbyPlaces(
        @retrofit2.http.Body request: NearbySearchRequest,
        @retrofit2.http.Header("X-Goog-Api-Key") apiKey: String = "",  // empty if using proxy
        @retrofit2.http.Header("X-Goog-FieldMask") fieldMask: String =
            "places.displayName,places.formattedAddress,places.internationalPhoneNumber,places.location,places.types"
    ): GooglePlacesResponse

    @POST("proxy/places-nearby.php")   // or whatever endpoint you create on your PHP server
    suspend fun searchNearbyPlacesProxy(@Body request: NearbySearchRequest): GooglePlacesResponse

    @POST("api.php/notification-read")
    suspend fun reportNotificationRead(
        @Query("workerId") workerId: String,
        @Query("notificationId") notificationId: String
    ): Response<Unit>
}