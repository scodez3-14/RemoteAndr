package com.remotephone.data.model

import kotlinx.serialization.Serializable

/**
 * Represents touch interactions and navigation commands sent over WebRTC DataChannel.
 *
 * Example JSON formats:
 * - {"type":"TAP","x":540.0,"y":1200.0,"duration":0}
 * - {"type":"SWIPE","x":540.0,"y":1800.0,"x2":540.0,"y2":600.0,"duration":300}
 * - {"type":"BACK"}
 * - {"type":"HOME"}
 * - {"type":"RECENTS"}
 */
enum class TouchType(val rawValue: String) {
    TAP("TAP"),
    LONG_PRESS("LONG_PRESS"),
    SWIPE("SWIPE"),
    BACK("BACK"),
    HOME("HOME"),
    RECENTS("RECENTS"),
    LOCK("LOCK")
}

@Serializable
data class TouchEvent(
    val type: String,
    val x: Float? = null,
    val y: Float? = null,
    val x2: Float? = null,
    val y2: Float? = null,
    val duration: Long? = null
) {
    companion object {
        const val TYPE_TAP = "TAP"
        const val TYPE_LONG_PRESS = "LONG_PRESS"
        const val TYPE_SWIPE = "SWIPE"
        const val TYPE_BACK = "BACK"
        const val TYPE_HOME = "HOME"
        const val TYPE_RECENTS = "RECENTS"
        const val TYPE_LOCK = "LOCK"

        fun tap(x: Float, y: Float, duration: Long = 0L): TouchEvent =
            TouchEvent(type = TYPE_TAP, x = x, y = y, duration = duration)

        fun longPress(x: Float, y: Float, duration: Long = 500L): TouchEvent =
            TouchEvent(type = TYPE_LONG_PRESS, x = x, y = y, duration = duration)

        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300L): TouchEvent =
            TouchEvent(type = TYPE_SWIPE, x = x1, y = y1, x2 = x2, y2 = y2, duration = duration)

        fun back(): TouchEvent = TouchEvent(type = TYPE_BACK)

        fun home(): TouchEvent = TouchEvent(type = TYPE_HOME)

        fun recents(): TouchEvent = TouchEvent(type = TYPE_RECENTS)

        fun lock(): TouchEvent = TouchEvent(type = TYPE_LOCK)
    }
}

