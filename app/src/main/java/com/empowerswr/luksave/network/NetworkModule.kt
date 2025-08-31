package com.empowerswr.luksave.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val retrofit: Retrofit by lazy {
        val logging = HttpLoggingInterceptor { message ->
        }.apply {
            setLevel(HttpLoggingInterceptor.Level.BODY)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://db.nougro.com/api/")
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create()) // Add Gson for List<FileItem>
            .build()
    }

    val uploadService: UploadService by lazy {
        retrofit.create(UploadService::class.java)
    }

    val listFilesService: ListFilesService by lazy {
        retrofit.create(ListFilesService::class.java)
    }


}