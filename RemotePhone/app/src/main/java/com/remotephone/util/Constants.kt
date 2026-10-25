package com.remotephone.util

/**
 * App-wide constants for RemotePhone.
 */
object Constants {

    // Firebase database root path
    const val DB_ROOT = "remotephone"
    const val DB_USERS = "$DB_ROOT/users"
    const val DB_PAIRING_CODES = "$DB_ROOT/pairingCodes"
    const val DB_SIGNALING = "$DB_ROOT/signaling"
    const val DB_COMMANDS = "$DB_ROOT/commands"
    const val DB_RESULTS = "$DB_ROOT/results"

    // Firebase Storage
    const val STORAGE_SCREENSHOTS = "$DB_ROOT/screenshots"

    // Pairing
    const val PAIRING_CODE_LENGTH = 6
    const val PAIRING_CODE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes

    // Command status
    const val STATUS_PENDING = "PENDING"
    const val STATUS_EXECUTING = "EXECUTING"
    const val STATUS_COMPLETED = "COMPLETED"
    const val STATUS_FAILED = "FAILED"

    // Notification channels
    const val CHANNEL_MIRROR = "screen_mirror"
    const val CHANNEL_COMMAND = "command_service"
    const val CHANNEL_ALERTS = "alerts"

    // Notification IDs
    const val NOTIFICATION_MIRROR = 1001
    const val NOTIFICATION_COMMAND = 1002
    const val NOTIFICATION_ALERT = 1003

    // WebRTC
    const val VIDEO_TRACK_ID = "SCREEN_TRACK"
    const val DATA_CHANNEL_TOUCH = "touch"

    // Screen capture defaults
    const val DEFAULT_CAPTURE_WIDTH = 1280
    const val DEFAULT_CAPTURE_HEIGHT = 720
    const val DEFAULT_CAPTURE_FPS = 30

    // Quality presets
    object Quality {
        val HIGH = Triple(1280, 720, 30)    // 720p @ 30fps
        val MEDIUM = Triple(854, 480, 20)   // 480p @ 20fps
        val LOW = Triple(640, 360, 15)      // 360p @ 15fps
    }

    // Touch detection thresholds
    const val TAP_THRESHOLD_DP = 10f
    const val TAP_DURATION_MS = 200L
    const val LONG_PRESS_DURATION_MS = 500L

    // SharedPreferences
    const val PREFS_NAME = "remote_phone_prefs"
    const val PREF_VIDEO_QUALITY = "video_quality"
    const val PREF_MAX_FPS = "max_fps"
    const val PREF_AUTO_RECONNECT = "auto_reconnect"

    // Intent extras
    const val EXTRA_RESULT_CODE = "result_code"
    const val EXTRA_RESULT_DATA = "result_data"
    const val EXTRA_PAIR_ID = "pair_id"
}
