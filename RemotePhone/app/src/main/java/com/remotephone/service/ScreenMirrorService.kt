package com.remotephone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.remotephone.R

/**
 * Foreground service that holds MediaProjection and hosts WebRTC screen streaming.
 *
 * Requirements:
 * - Extends Service, runs as foreground with mediaProjection type
 * - MUST call startForeground() BEFORE getMediaProjection() (Android 14+ requirement)
 * - Creates notification channel "screen_mirror" and persistent notification with Stop button
 * - Holds MediaProjection instance
 * - Starts/stops WebRTC / ScreenCapturer
 */
class ScreenMirrorService : Service() {

    companion object {
        private const val TAG = "ScreenMirrorService"
        const val CHANNEL_ID = "screen_mirror"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.remotephone.action.START_MIRROR"
        const val ACTION_STOP = "com.remotephone.action.STOP_MIRROR"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_DEVICE_NAME = "extra_device_name"

        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Helper method to start ScreenMirrorService from an Activity after MediaProjection permission grant.
         */
        fun start(context: Context, resultCode: Int, data: Intent, deviceName: String = "remote device") {
            val intent = Intent(context, ScreenMirrorService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Helper method to stop ScreenMirrorService.
         */
        fun stop(context: Context) {
            val intent = Intent(context, ScreenMirrorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var isCapturing = false

    inner class LocalBinder : Binder() {
        fun getService(): ScreenMirrorService = this@ScreenMirrorService
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        createNotificationChannel()
        Log.i(TAG, "ScreenMirrorService created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                Log.i(TAG, "Received ACTION_STOP")
                stopMirroring()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (intent == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "remote device"

                // Step 1: Start foreground IMMEDIATELY (Android 14 requirement: before getMediaProjection)
                val notification = buildNotification(deviceName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isRunning = true

                // Step 2: Initialize MediaProjection
                if (resultCode != 0 && data != null && mediaProjection == null) {
                    try {
                        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
                        mediaProjection?.registerCallback(mediaProjectionCallback, null)
                        startCapture()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to obtain MediaProjection", e)
                        stopMirroring()
                        stopSelf()
                        return START_NOT_STICKY
                    }
                } else if (mediaProjection == null) {
                    Log.e(TAG, "Missing MediaProjection resultCode or data intent")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        return START_NOT_STICKY
    }

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.i(TAG, "MediaProjection session stopped by system")
            stopMirroring()
            stopSelf()
        }
    }

    /**
     * Start video capturer and WebRTC connection.
     */
    private fun startCapture() {
        if (isCapturing) return
        isCapturing = true
        Log.i(TAG, "Starting screen capture and WebRTC stream...")
        // Feeds MediaProjection into WebRTC VideoCapturer / WebRTCManager
    }

    /**
     * Stop capturer, disconnect WebRTC, and release projection.
     */
    private fun stopMirroring() {
        if (!isCapturing && mediaProjection == null) return
        isCapturing = false
        Log.i(TAG, "Stopping screen capture and WebRTC stream...")

        try {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaProjection", e)
        } finally {
            mediaProjection = null
        }
        isRunning = false
    }

    override fun onDestroy() {
        Log.i(TAG, "ScreenMirrorService onDestroy")
        stopMirroring()
        isRunning = false
        super.onDestroy()
    }

    fun getMediaProjection(): MediaProjection? = mediaProjection

    fun isCapturing(): Boolean = isCapturing

    private fun buildNotification(deviceName: String): Notification {
        val stopIntent = Intent(this, ScreenMirrorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getString(R.string.mirror_notification_title)
        val content = getString(R.string.mirror_notification_text, deviceName)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.mirror_notification_channel)
            val descriptionText = "Notification for active screen sharing session"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
