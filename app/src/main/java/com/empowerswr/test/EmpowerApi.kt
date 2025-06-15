package com.empowerswr.test

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface EmpowerApi {
    @POST("register")
    suspend fun register(@Body request: RegistrationRequest): LoginResponse

    @POST("login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("worker")
    suspend fun getWorkerDetails(@Query("token") token: String): WorkerResponse

    @GET("alerts")
    suspend fun getAlerts(@Query("token") token: String): List<Alert>

    @POST("checkin")
    suspend fun checkIn(@Body request: CheckInRequest): CheckInResponse

    @POST("update-fcm-token")
    suspend fun updateFcmToken(@Query("workerId") workerId: String, @Body token: String)
}