package com.empowerswr.test

import android.content.Context

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
}