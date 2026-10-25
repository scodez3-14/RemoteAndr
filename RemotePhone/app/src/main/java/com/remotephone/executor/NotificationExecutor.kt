package com.remotephone.executor

import android.util.Log
import com.remotephone.data.model.Command
import com.remotephone.data.model.CommandResult
import com.remotephone.data.model.ResultType
import com.remotephone.service.NotificationReaderService

/**
 * Executor that reads active notifications from the device
 * using NotificationListenerService.
 */
class NotificationExecutor : CommandExecutor {

    companion object {
        private const val TAG = "NotificationExecutor"
    }

    override suspend fun execute(command: Command): CommandResult {
        val service = NotificationReaderService.instance
            ?: return CommandResult(
                commandId = command.id,
                type = ResultType.TEXT,
                data = "Error: Notification Access not enabled. Please enable it in Settings → Special App Access → Notification Access."
            )

        return try {
            val notifications = service.getCapturedNotifications()

            if (notifications.isEmpty()) {
                return CommandResult(
                    commandId = command.id,
                    type = ResultType.TEXT,
                    data = "📭 No active notifications"
                )
            }

            val result = buildString {
                appendLine("📬 Active Notifications (${notifications.size})")
                appendLine("─".repeat(30))
                notifications.forEachIndexed { index: Int, notif: NotificationReaderService.NotificationItem ->
                    appendLine()
                    appendLine("${index + 1}. ${notif.title.ifEmpty { "No title" }}")
                    if (notif.text.isNotBlank()) {
                        appendLine("   ${notif.text}")
                    }
                    appendLine("   📦 ${notif.packageName}")
                    appendLine("   🕐 ${formatTime(notif.postTime)}")
                }
            }

            Log.d(TAG, "Retrieved ${notifications.size} notifications")

            CommandResult(
                commandId = command.id,
                type = ResultType.NOTIFICATION_LIST,
                data = result.trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read notifications", e)
            CommandResult(
                commandId = command.id,
                type = ResultType.TEXT,
                data = "Error: ${e.message}"
            )
        }
    }

    private fun formatTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> "${diff / 86_400_000}d ago"
        }
    }
}
