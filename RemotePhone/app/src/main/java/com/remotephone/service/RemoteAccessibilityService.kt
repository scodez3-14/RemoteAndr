package com.remotephone.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Core engine for remote control and touch injection on Phone B.
 * Extends [AccessibilityService] to execute remote gestures and system actions.
 */
class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RemoteAccessibility"

        @Volatile
        var instance: RemoteAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null
    }

    private val callbackExecutor: Executor by lazy {
        Executors.newSingleThreadExecutor()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "RemoteAccessibilityService connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "RemoteAccessibilityService unbind")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "RemoteAccessibilityService destroyed")
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: We don't process external accessibility events
    }

    override fun onInterrupt() {
        // No-op
        Log.w(TAG, "RemoteAccessibilityService interrupted")
    }

    /**
     * Inject a single tap gesture at screen coordinates ([x], [y]).
     */
    fun injectTap(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap completed at ($x, $y)")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap cancelled at ($x, $y)")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Inject a swipe gesture from ([startX], [startY]) to ([endX], [endY]) over [duration] ms.
     */
    fun injectSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 300L,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration.coerceAtLeast(1L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Swipe completed from ($startX, $startY) to ($endX, $endY)")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Swipe cancelled from ($startX, $startY) to ($endX, $endY)")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Inject a long press gesture at screen coordinates ([x], [y]) for [duration] ms.
     */
    fun injectLongPress(
        x: Float,
        y: Float,
        duration: Long = 1000L,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration.coerceAtLeast(100L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Long press completed at ($x, $y)")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Long press cancelled at ($x, $y)")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Trigger system Back button navigation.
     */
    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    /**
     * Trigger system Home button navigation.
     */
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    /**
     * Trigger system Recent Apps overview.
     */
    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /**
     * Trigger system screen lock action.
     */
    fun lockScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    /**
     * Capture screenshot using Android 11+ (API 30+) Accessibility API.
     */
    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                callbackExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val colorSpace = screenshot.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            val copy = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()
                            callback(copy ?: bitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to convert screenshot buffer", e)
                            callback(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "takeScreenshot failed with error code: $errorCode")
                        callback(null)
                    }
                }
            )
        } else {
            Log.w(TAG, "takeScreenshot requires API 30+")
            callback(null)
        }
    }

    /**
     * Generic representation of touch events received remotely.
     */
    data class TouchEvent(
        val type: String, // "tap", "swipe", "long_press", "back", "home", "recents", "lock"
        val x: Float = 0f,
        val y: Float = 0f,
        val endX: Float = 0f,
        val endY: Float = 0f,
        val duration: Long = 300L
    )

    /**
     * Process touch/control events forwarded from remote phone.
     */
    fun processTouchEvent(event: TouchEvent, onComplete: ((Boolean) -> Unit)? = null) {
        when (event.type.lowercase()) {
            "tap", "click" -> injectTap(event.x, event.y, onComplete)
            "swipe", "scroll", "drag" -> injectSwipe(
                event.x,
                event.y,
                event.endX,
                event.endY,
                event.duration,
                onComplete
            )
            "long_press", "longpress" -> injectLongPress(event.x, event.y, event.duration, onComplete)
            "back" -> {
                val res = goBack()
                onComplete?.invoke(res)
            }
            "home" -> {
                val res = goHome()
                onComplete?.invoke(res)
            }
            "recents" -> {
                val res = openRecents()
                onComplete?.invoke(res)
            }
            "lock" -> {
                val res = lockScreen()
                onComplete?.invoke(res)
            }
            else -> {
                Log.w(TAG, "Unknown touch event type: ${event.type}")
                onComplete?.invoke(false)
            }
        }
    }
}
