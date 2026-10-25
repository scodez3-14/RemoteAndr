package com.remotephone

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.remotephone.util.Constants

/**
 * Application class for RemotePhone.
 * Initializes Firebase and creates notification channels.
 */
class RemotePhoneApp : Application() {

    companion object {
        private const val TAG = "RemotePhoneApp"
        
        lateinit var instance: RemotePhoneApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "RemotePhone starting...")

        // Initialize Firebase
        initFirebase()

        // Create notification channels
        createNotificationChannels()

        Log.d(TAG, "RemotePhone initialized")
    }

    private fun initFirebase() {
        try {
            FirebaseApp.initializeApp(this)

            // Enable offline persistence for Realtime Database
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)

            // Keep key paths synced for offline support
            val database = FirebaseDatabase.getInstance()
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                database.getReference("${Constants.DB_USERS}/${currentUser.uid}")
                    .keepSynced(true)
            }

            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
        }
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Screen mirroring channel (high importance — ongoing session)
        val mirrorChannel = NotificationChannel(
            Constants.CHANNEL_MIRROR,
            "Screen Mirroring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active screen sharing session notification"
            setShowBadge(false)
        }

        // Command service channel (low importance — background service)
        val commandChannel = NotificationChannel(
            Constants.CHANNEL_COMMAND,
            "Command Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background command execution service"
            setShowBadge(false)
        }

        // Alerts channel (high importance — remote control requests)
        val alertChannel = NotificationChannel(
            Constants.CHANNEL_ALERTS,
            "Remote Control Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for incoming remote control requests"
            enableVibration(true)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannels(
            listOf(mirrorChannel, commandChannel, alertChannel)
        )

        Log.d(TAG, "Notification channels created")
    }
}
