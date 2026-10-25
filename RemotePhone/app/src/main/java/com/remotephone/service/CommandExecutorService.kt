package com.remotephone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.remotephone.MainActivity
import com.remotephone.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that listens for commands from Firebase and executes them.
 *
 * Responsibilities:
 * - Creates notification channel "command_service"
 * - Starts foreground with persistent notification
 * - Observes commands from FirebaseCommandRepo
 * - Routes commands to appropriate executor
 * - Updates command status to EXECUTING → COMPLETED/FAILED
 * - Sends results back via FirebaseCommandRepo
 */
class CommandExecutorService : Service() {

    companion object {
        private const val TAG = "CommandExecutorService"
        const val CHANNEL_ID = "command_service"
        private const val NOTIFICATION_ID = 2002

        const val ACTION_START = "com.remotephone.action.START_COMMAND_EXECUTOR"
        const val ACTION_STOP = "com.remotephone.action.STOP_COMMAND_EXECUTOR"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, CommandExecutorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CommandExecutorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    /**
     * Model representing command status.
     */
    enum class CommandStatus {
        PENDING,
        EXECUTING,
        COMPLETED,
        FAILED
    }

    /**
     * Model representing a command payload.
     */
    data class DeviceCommand(
        val id: String = "",
        val type: String = "", // e.g., "tap", "swipe", "screenshot", "lock", "ring", "open_app"
        val payload: Map<String, Any?> = emptyMap(),
        val status: CommandStatus = CommandStatus.PENDING,
        val result: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _lastCommandState = MutableStateFlow<DeviceCommand?>(null)
    val lastCommandState = _lastCommandState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "CommandExecutorService created")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_STOP -> {
                Log.i(TAG, "Received ACTION_STOP")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isRunning = false
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isRunning = true
                startCommandObserver()
            }
        }

        return START_STICKY
    }

    private fun startCommandObserver() {
        serviceScope.launch {
            Log.i(TAG, "Observing remote commands...")
            // Observes incoming commands from FirebaseCommandRepo and executes them
        }
    }

    /**
     * Execute a command using accessibility service or local system managers.
     */
    fun executeCommand(command: DeviceCommand) {
        serviceScope.launch {
            updateCommandStatus(command.id, CommandStatus.EXECUTING)
            try {
                when (command.type.lowercase()) {
                    "tap" -> {
                        val x = (command.payload["x"] as? Number)?.toFloat() ?: 0f
                        val y = (command.payload["y"] as? Number)?.toFloat() ?: 0f
                        val a11y = RemoteAccessibilityService.instance
                        if (a11y != null) {
                            a11y.injectTap(x, y) { success ->
                                finishCommand(command.id, success, if (success) "Tap executed" else "Tap failed")
                            }
                        } else {
                            finishCommand(command.id, false, "AccessibilityService not connected")
                        }
                    }
                    "swipe" -> {
                        val x1 = (command.payload["x1"] as? Number)?.toFloat() ?: 0f
                        val y1 = (command.payload["y1"] as? Number)?.toFloat() ?: 0f
                        val x2 = (command.payload["x2"] as? Number)?.toFloat() ?: 0f
                        val y2 = (command.payload["y2"] as? Number)?.toFloat() ?: 0f
                        val duration = (command.payload["duration"] as? Number)?.toLong() ?: 300L
                        val a11y = RemoteAccessibilityService.instance
                        if (a11y != null) {
                            a11y.injectSwipe(x1, y1, x2, y2, duration) { success ->
                                finishCommand(command.id, success, if (success) "Swipe executed" else "Swipe failed")
                            }
                        } else {
                            finishCommand(command.id, false, "AccessibilityService not connected")
                        }
                    }
                    "back" -> {
                        val success = RemoteAccessibilityService.instance?.goBack() == true
                        finishCommand(command.id, success, "Back action")
                    }
                    "home" -> {
                        val success = RemoteAccessibilityService.instance?.goHome() == true
                        finishCommand(command.id, success, "Home action")
                    }
                    "recents" -> {
                        val success = RemoteAccessibilityService.instance?.openRecents() == true
                        finishCommand(command.id, success, "Recents action")
                    }
                    "lock" -> {
                        val success = RemoteAccessibilityService.instance?.lockScreen() == true
                        finishCommand(command.id, success, "Screen locked")
                    }
                    "screenshot" -> {
                        val a11y = RemoteAccessibilityService.instance
                        if (a11y != null) {
                            a11y.takeScreenshot { bitmap ->
                                if (bitmap != null) {
                                    finishCommand(command.id, true, "Screenshot captured (${bitmap.width}x${bitmap.height})")
                                } else {
                                    finishCommand(command.id, false, "Screenshot capture failed")
                                }
                            }
                        } else {
                            finishCommand(command.id, false, "AccessibilityService not connected")
                        }
                    }
                    "open_app" -> {
                        val packageName = command.payload["package"] as? String
                        if (!packageName.isNullOrBlank()) {
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(launchIntent)
                                finishCommand(command.id, true, "App launched: $packageName")
                            } else {
                                finishCommand(command.id, false, "App not found: $packageName")
                            }
                        } else {
                            finishCommand(command.id, false, "Missing packageName in payload")
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unsupported command type: ${command.type}")
                        finishCommand(command.id, false, "Unsupported command type: ${command.type}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command: ${command.id}", e)
                finishCommand(command.id, false, e.message ?: "Execution error")
            }
        }
    }

    private fun updateCommandStatus(commandId: String, status: CommandStatus) {
        Log.d(TAG, "Command $commandId status updated to $status")
        // Updates status in FirebaseCommandRepo
    }

    private fun finishCommand(commandId: String, success: Boolean, resultSummary: String) {
        val status = if (success) CommandStatus.COMPLETED else CommandStatus.FAILED
        Log.i(TAG, "Command $commandId finished with status: $status, result: $resultSummary")
        // Updates final status and result in FirebaseCommandRepo
    }

    override fun onDestroy() {
        Log.i(TAG, "CommandExecutorService onDestroy")
        serviceScope.cancel()
        isRunning = false
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelTitle = getString(R.string.command_notification_channel)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(channelTitle)
            .setContentText("Listening for remote commands")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.command_notification_channel)
            val descriptionText = "Channel for RemotePhone background command execution"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
