package com.empowerswr.luksave

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
object NotificationHandler {
    private val _notificationFlow = MutableSharedFlow<Pair<String, String>>(replay = 0)
    val notificationFlow = _notificationFlow.asSharedFlow()

    suspend fun emitNotification(title: String, body: String): Boolean {
        return try {
            _notificationFlow.emit(title to body)
            Timber.i("Notification emitted successfully")
            true
        } catch (e: Exception) {
            Timber.e(e,"Failed to emit notification")
            false
        }
    }
}