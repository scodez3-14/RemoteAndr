package com.remotephone.executor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.remotephone.data.model.Command
import com.remotephone.data.model.CommandResult
import com.remotephone.data.model.ResultType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Executor that reads or writes the device clipboard.
 * Clipboard operations must run on the main thread.
 */
class ClipboardExecutor(private val context: Context) : CommandExecutor {

    companion object {
        private const val TAG = "ClipboardExecutor"
    }

    override suspend fun execute(command: Command): CommandResult {
        val textToSet = command.payload["text"]

        return if (textToSet != null) {
            setClipboard(command.id, textToSet)
        } else {
            getClipboard(command.id)
        }
    }

    private suspend fun getClipboard(commandId: String): CommandResult {
        return suspendCancellableCoroutine { continuation ->
            Handler(Looper.getMainLooper()).post {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = clipboard.primaryClip
                    val text = clipData?.getItemAt(0)?.text?.toString()

                    val result = if (!text.isNullOrBlank()) {
                        CommandResult(
                            commandId = commandId,
                            type = ResultType.TEXT,
                            data = "📋 Clipboard Content:\n$text"
                        )
                    } else {
                        CommandResult(
                            commandId = commandId,
                            type = ResultType.TEXT,
                            data = "📋 Clipboard is empty"
                        )
                    }

                    Log.d(TAG, "Read clipboard: ${text?.take(50) ?: "empty"}")
                    continuation.resume(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read clipboard", e)
                    continuation.resume(
                        CommandResult(
                            commandId = commandId,
                            type = ResultType.TEXT,
                            data = "Error: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    private suspend fun setClipboard(commandId: String, text: String): CommandResult {
        return suspendCancellableCoroutine { continuation ->
            Handler(Looper.getMainLooper()).post {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("RemotePhone", text)
                    clipboard.setPrimaryClip(clip)

                    Log.d(TAG, "Set clipboard: ${text.take(50)}")
                    continuation.resume(
                        CommandResult(
                            commandId = commandId,
                            type = ResultType.TEXT,
                            data = "📋 Clipboard set to:\n$text"
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set clipboard", e)
                    continuation.resume(
                        CommandResult(
                            commandId = commandId,
                            type = ResultType.TEXT,
                            data = "Error: ${e.message}"
                        )
                    )
                }
            }
        }
    }
}
