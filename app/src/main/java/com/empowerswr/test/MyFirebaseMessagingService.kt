package com.empowerswr.test

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onCreate() {
        super.onCreate()
        Log.d("EmpowerSWR", "MyFirebaseMessagingService onCreate")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.data["notification_title"] ?: message.notification?.title ?: message.data["title"] ?: "No Title"
        val body = message.data["notification_body"] ?: message.notification?.body ?: message.data["body"] ?: "No Body"
        Log.d("EmpowerSWR", "Message received: Title: $title, Body: $body, Full Data: ${message.data}, Notification: ${message.notification}, App State: ${if (isAppInForeground()) "Foreground" else "Background"}")

        // Emit the notification to the flow within a coroutine scope
        CoroutineScope(Dispatchers.Main).launch {
            val emissionSuccess = NotificationHandler.emitNotification(title, body)
            Log.d("EmpowerSWR", "Emission attempt for: $title: $body, Success: $emissionSuccess, Data keys: ${message.data.keys}")
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "EmpowerSWRChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Empower SWR Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("notification_title", title)
            putExtra("notification_body", body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            Log.d("EmpowerSWR", "Intent created with extras: notification_title=$title, notification_body=$body")
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        Log.d("EmpowerSWR", "PendingIntent created with request code: 0")

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
        Log.d("EmpowerSWR", "New FCM Token: $token")
    }

    // Helper method to check if the app is in the foreground
    private fun isAppInForeground(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        return appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}