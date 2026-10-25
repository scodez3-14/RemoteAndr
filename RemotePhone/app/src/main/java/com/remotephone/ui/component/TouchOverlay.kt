package com.remotephone.ui.component

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.remotephone.data.model.TouchEvent
import com.remotephone.data.model.TouchType

/**
 * Transparent overlay that captures all touch events on the mirror view,
 * detects gesture type (tap, swipe, long press), and forwards as TouchEvent.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchOverlay(
    onTouchEvent: (TouchEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tapThresholdPx = with(density) { 10.dp.toPx() }

    var downX by remember { mutableStateOf(0f) }
    var downY by remember { mutableStateOf(0f) }
    var downTime by remember { mutableStateOf(0L) }
    var lastTouchPoint by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        downTime = System.currentTimeMillis()
                        lastTouchPoint = Offset(event.x, event.y)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        lastTouchPoint = Offset(event.x, event.y)
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        val endX = event.x
                        val endY = event.y
                        val duration = System.currentTimeMillis() - downTime
                        val dx = endX - downX
                        val dy = endY - downY
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                        val touchEvent = when {
                            // Tap: small movement, short duration
                            distance < tapThresholdPx && duration < 200L -> {
                                TouchEvent(
                                    type = TouchType.TAP.rawValue,
                                    x = downX, y = downY
                                )
                            }
                            // Long press: small movement, long duration
                            distance < tapThresholdPx && duration >= 500L -> {
                                TouchEvent(
                                    type = TouchType.LONG_PRESS.rawValue,
                                    x = downX, y = downY,
                                    duration = duration
                                )
                            }
                            // Swipe: large movement
                            else -> {
                                TouchEvent(
                                    type = TouchType.SWIPE.rawValue,
                                    x = downX, y = downY,
                                    x2 = endX, y2 = endY,
                                    duration = duration
                                )
                            }
                        }

                        onTouchEvent(touchEvent)
                        lastTouchPoint = null
                        true
                    }

                    else -> false
                }
            }
    ) {
        // Draw ripple at touch point
        lastTouchPoint?.let { point ->
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                radius = 30f,
                center = point
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = 50f,
                center = point
            )
        }
    }
}
