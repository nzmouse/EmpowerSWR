package com.empowerswr.luksave

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onCreate() {
        super.onCreate()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.data["notification_title"] ?: message.notification?.title ?: message.data["title"] ?: "No Title"
        val body = message.data["notification_body"] ?: message.notification?.body ?: message.data["body"] ?: "No Body"
        Timber.i("Firebase Message Received")
        // Emit the notification to the flow within a coroutine scope
        CoroutineScope(Dispatchers.Main).launch {
            val emissionSuccess = NotificationHandler.emitNotification(title, body)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "EmpowerSWRChannel"

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("notification_title", title)
            putExtra("notification_body", body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)


        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // Replace with your notification icon
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.i("New FCM Token Received")
    }

    // Helper method to check if the app is in the foreground
    private fun isAppInForeground(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        return appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}