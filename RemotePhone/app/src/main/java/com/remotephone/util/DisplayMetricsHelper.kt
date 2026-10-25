package com.remotephone.util

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Helper for display metrics and coordinate scaling between
 * controller (Phone A) and controlled (Phone B) devices.
 */
object DisplayMetricsHelper {

    data class ScreenSize(
        val widthPx: Int,
        val heightPx: Int,
        val densityDpi: Int
    ) {
        fun toMap(): Map<String, Int> = mapOf(
            "widthPx" to widthPx,
            "heightPx" to heightPx,
            "densityDpi" to densityDpi
        )

        companion object {
            fun fromMap(map: Map<String, Any?>): ScreenSize {
                return ScreenSize(
                    widthPx = (map["widthPx"] as? Number)?.toInt() ?: 1080,
                    heightPx = (map["heightPx"] as? Number)?.toInt() ?: 1920,
                    densityDpi = (map["densityDpi"] as? Number)?.toInt() ?: 420
                )
            }
        }
    }

    /**
     * Get the current device's screen size in pixels.
     */
    fun getScreenSize(context: Context): ScreenSize {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return ScreenSize(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi
        )
    }

    /**
     * Scale touch coordinates from the local display to the remote display.
     *
     * When Phone A (controller) touches its screen at (localX, localY),
     * we need to map those coordinates to Phone B's (remote) screen space,
     * accounting for the fact that the mirror view may not fill the entire
     * screen and the two phones may have different resolutions.
     *
     * @param localX Touch X coordinate on Phone A's mirror view
     * @param localY Touch Y coordinate on Phone A's mirror view
     * @param viewWidth Width of the mirror view on Phone A (in pixels)
     * @param viewHeight Height of the mirror view on Phone A (in pixels)
     * @param remoteWidth Width of Phone B's screen (in pixels)
     * @param remoteHeight Height of Phone B's screen (in pixels)
     * @return Scaled (x, y) coordinates for Phone B
     */
    fun scaleToRemote(
        localX: Float,
        localY: Float,
        viewWidth: Int,
        viewHeight: Int,
        remoteWidth: Int,
        remoteHeight: Int
    ): Pair<Float, Float> {
        if (viewWidth <= 0 || viewHeight <= 0) return Pair(localX, localY)

        val scaledX = (localX / viewWidth) * remoteWidth
        val scaledY = (localY / viewHeight) * remoteHeight

        // Clamp to remote screen bounds
        val clampedX = scaledX.coerceIn(0f, remoteWidth.toFloat())
        val clampedY = scaledY.coerceIn(0f, remoteHeight.toFloat())

        return Pair(clampedX, clampedY)
    }

    /**
     * Convert dp to pixels.
     */
    fun dpToPx(context: Context, dp: Float): Float {
        return dp * (context.resources.displayMetrics.densityDpi / 160f)
    }

    /**
     * Calculate the aspect-ratio-preserving dimensions for rendering
     * a remote screen inside a local view.
     */
    fun calculateFitDimensions(
        containerWidth: Int,
        containerHeight: Int,
        contentWidth: Int,
        contentHeight: Int
    ): Pair<Int, Int> {
        if (contentWidth <= 0 || contentHeight <= 0) return Pair(containerWidth, containerHeight)

        val containerAspect = containerWidth.toFloat() / containerHeight
        val contentAspect = contentWidth.toFloat() / contentHeight

        return if (contentAspect > containerAspect) {
            // Content is wider — fit to width
            val fitHeight = (containerWidth / contentAspect).toInt()
            Pair(containerWidth, fitHeight)
        } else {
            // Content is taller — fit to height
            val fitWidth = (containerHeight * contentAspect).toInt()
            Pair(fitWidth, containerHeight)
        }
    }
}
