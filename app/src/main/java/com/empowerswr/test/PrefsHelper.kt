package com.empowerswr.test

import android.app.Application
import android.content.Context
import android.util.Log

object PrefsHelper {
    private const val PREFS_NAME = "EmpowerSWRPrefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_TOKEN_EXPIRY = "token_expiry"
    private const val KEY_WORKER_ID = "worker_id"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_HAS_REGISTERED = "has_registered"

    fun saveWorkerId(context: Context, workerId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WORKER_ID, workerId).apply()
    }

    fun getWorkerId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WORKER_ID, null)
    }

    fun saveFcmToken(context: Context, fcmToken: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, fcmToken).apply()
    }

    fun getFcmToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FCM_TOKEN, null)
    }

    fun saveToken(context: Context, token: String, expiry: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_TOKEN_EXPIRY, expiry)
            .apply()
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getTokenExpiry(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_TOKEN_EXPIRY)) {
            prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        } else {
            null
        }
    }

    fun setRegistered(context: Context, hasRegistered: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HAS_REGISTERED, hasRegistered).apply()
    }

    fun hasRegistered(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAS_REGISTERED, false)
    }

    fun clearToken(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .remove(KEY_WORKER_ID)
            .apply()
    }
    fun getWorkerDetails(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val givenName = prefs.getString("firstName", "Unknown") ?: "Unknown"
        val surname = prefs.getString("surname", "Unknown") ?: "Unknown"
        Log.d("EmpowerSWR", "Retrieved worker details: $givenName, $surname")
        return Pair(givenName, surname)
    }
    fun saveWorkerDetails(context: Context, firstName: String?, surname: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("firstName", firstName ?: "Unknown")
            .putString("surname", surname ?: "Unknown")
            .apply()
        Log.d("EmpowerSWR", "Saved worker details: ${firstName ?: "Unknown"}, ${surname ?: "Unknown"}")
    }
    fun getJwtToken(context: Context): String {
        val prefs = context.getSharedPreferences("EmpowerSWRPrefs", Context.MODE_PRIVATE)
        val token = prefs.getString("token", "") ?: ""
        Log.d("EmpowerSWR", "Retrieved JWT Token: $token")
        return token
    }

    fun clearPrefs(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}