package com.empowerswr.test

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NotificationHandler {
    private val _notificationFlow = MutableSharedFlow<Pair<String, String>>(replay = 0)
    val notificationFlow = _notificationFlow.asSharedFlow()

    suspend fun emitNotification(title: String, body: String): Boolean {
        Log.d("EmpowerSWR", "Attempting to emit notification: $title: $body")
        return try {
            _notificationFlow.emit(title to body)
            Log.d("EmpowerSWR", "Notification emitted successfully: $title: $body")
            true
        } catch (e: Exception) {
            Log.e("EmpowerSWR", "Failed to emit notification: ${e.message}", e)
            false
        }
    }
}