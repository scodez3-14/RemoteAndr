package com.remotephone.service

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
import com.remotephone.MainActivity
import com.remotephone.R

/**
 * Handles Firebase Cloud Messaging push events.
 *
 * Responsibilities:
 * - onNewToken(token) → update device token in Firebase via FirebaseAuthRepo / device registry
 * - onMessageReceived(message) → check message data for command type:
 *     - "start_mirror" → prompt/start ScreenMirrorService
 *     - "command" → start CommandExecutorService
 *     - Show high-priority notification if app is in background
 */
class RemoteFCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "RemoteFCMService"
        private const val CHANNEL_FCM_ALERTS = "fcm_alerts"
        private const val NOTIFICATION_ID = 3003
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM Registration Token: $token")
        // Token update: Sync to Firebase under current user / device path
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: $data")
            handleDataPayload(data)
        }

        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            showNotification(it.title ?: "RemotePhone", it.body ?: "New remote notification")
        }
    }

    private fun handleDataPayload(data: Map<String, String>) {
        val type = data["type"] ?: data["action"] ?: ""
        when (type) {
            "start_mirror" -> {
                Log.i(TAG, "FCM received start_mirror request")
                // Cannot directly acquire MediaProjection from background without user consent;
                // notify user or bring app to foreground to initiate projection flow.
                showHighPriorityAlert(
                    title = "Screen Sharing Requested",
                    message = "A paired device requested to start screen mirroring. Tap to open and accept.",
                    targetAction = "start_mirror"
                )
            }
            "command" -> {
                Log.i(TAG, "FCM received command trigger. Starting CommandExecutorService...")
                CommandExecutorService.start(this)
            }
            else -> {
                val title = data["title"] ?: "Remote Phone Alert"
                val body = data["body"] ?: data["message"] ?: "Incoming remote notification"
                showHighPriorityAlert(title, body)
            }
        }
    }

    private fun showHighPriorityAlert(title: String, message: String, targetAction: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (targetAction != null) {
                putExtra("action", targetAction)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_FCM_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showNotification(title: String, body: String) {
        showHighPriorityAlert(title, body)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Remote Alerts"
            val descriptionText = "Notifications for incoming remote control requests and alerts"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_FCM_ALERTS, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
