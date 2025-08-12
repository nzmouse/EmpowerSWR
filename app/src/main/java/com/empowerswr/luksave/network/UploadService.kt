package com.empowerswr.luksave.network

import okhttp3.MultipartBody
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadService {
    @Multipart
    @POST("uploads.php")
    suspend fun uploadFile(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Header("X-Is-Signed") isSigned: String = "false"
    ): String
}
