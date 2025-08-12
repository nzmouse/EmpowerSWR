package com.empowerswr.luksave.network

import retrofit2.http.GET
import retrofit2.http.Header

interface ListFilesService {
    @GET("list_files.php")
    suspend fun listFiles(@Header("Authorization") token: String): List<FileItem>
}

data class FileItem(val name: String, val url: String, val extension: String)