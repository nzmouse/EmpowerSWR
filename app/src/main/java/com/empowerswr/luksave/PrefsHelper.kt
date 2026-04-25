package com.empowerswr.luksave

import android.content.Context
import androidx.core.content.edit
import timber.log.Timber

object PrefsHelper {
    private const val PREFS_NAME = "EmpowerSWRPrefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_TOKEN_EXPIRY = "token_expiry"
    private const val KEY_WORKER_ID = "worker_id"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_HAS_REGISTERED = "has_registered"

    fun saveWorkerId(context: Context, workerId: String) {
        val prefs = context.getSharedPreferences("EmpowerSWRPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("workerId", workerId).apply()
    }

    fun getWorkerId(context: Context?): String? {
        return context?.getSharedPreferences("EmpowerSWRPrefs", Context.MODE_PRIVATE)
            ?.getString("workerId", null)
    }

    fun saveFcmToken(context: Context, fcmToken: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_FCM_TOKEN, fcmToken) }
    }

    fun saveToken(context: Context, token: String, expiry: Long) {
        val prefs = context.getSharedPreferences("EmpowerSWRPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("token", token).putLong("tokenExpiry", expiry).apply()
    }

    fun getToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null)
        Timber.i("Retrieved JWT Token: $token")
        return token
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
        prefs.edit { putBoolean(KEY_HAS_REGISTERED, hasRegistered) }
    }

    fun hasRegistered(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAS_REGISTERED, false)
    }

    fun clearToken(context: Context) {
        val prefs = context.getSharedPreferences("EmpowerSWRPrefs", Context.MODE_PRIVATE)
        prefs.edit().remove("token").remove("tokenExpiry").apply()
    }
    fun getWorkerDetails(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val givenName = prefs.getString("firstName", "Unknown") ?: "Unknown"
        val surname = prefs.getString("surname", "Unknown") ?: "Unknown"
        return Pair(givenName, surname)
    }
    fun saveWorkerDetails(context: Context?, firstName: String?, surname: String?) {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putString("firstName", firstName ?: "Unknown")
                putString("surname", surname ?: "Unknown")
            }
        }
    }
    fun clearPrefs(context: Context?) {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()

            // Also clear any other stored values if needed
            // e.g. prefs.edit().remove("workerId").apply()
        }
    }

    fun saveUsername(context: Context, username: String?) {
        val prefs = context.getSharedPreferences("EmpowerPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("username", username).apply()
    }

    fun getUsername(context: Context): String? {
        val prefs = context.getSharedPreferences("EmpowerPrefs", Context.MODE_PRIVATE)
        return prefs.getString("username", null)
    }
}