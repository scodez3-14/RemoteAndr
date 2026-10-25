package com.remotephone.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Service that observes and captures notifications on Phone B, allowing Phone A
 * to view active notifications remotely.
 */
class NotificationReaderService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationReader"

        @Volatile
        var instance: NotificationReaderService? = null
            private set

        val isServiceConnected: Boolean
            get() = instance != null
    }

    /**
     * Simplified representation of a notification for remote viewing.
     */
    data class NotificationItem(
        val id: String,
        val packageName: String,
        val title: String,
        val text: String,
        val postTime: Long,
        val isClearable: Boolean = true
    )

    private val notificationList = mutableListOf<NotificationItem>()
    private val lock = Any()

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "NotificationReaderService connected")
        refreshActiveNotifications()
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "NotificationReaderService disconnected")
        instance = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        Log.i(TAG, "NotificationReaderService destroyed")
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val item = sbn.toNotificationItem()
        synchronized(lock) {
            // Remove previous with same key/id if exists, then add updated
            notificationList.removeAll { it.id == item.id }
            notificationList.add(0, item)
            // Limit cache to 100 recent notifications
            if (notificationList.size > 100) {
                notificationList.removeAt(notificationList.size - 1)
            }
        }
        Log.d(TAG, "Notification posted from: ${item.packageName} - ${item.title}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val key = sbn.key
        synchronized(lock) {
            notificationList.removeAll { it.id == key }
        }
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
    }

    /**
     * Returns a copy of the active notifications.
     */
    fun getCapturedNotifications(): List<NotificationItem> {
        return try {
            val activeSbns = try { super.getActiveNotifications() } catch (e: Exception) { null }
            if (!activeSbns.isNullOrEmpty()) {
                val list = activeSbns.filterNotNull().map { it.toNotificationItem() }
                synchronized(lock) {
                    notificationList.clear()
                    notificationList.addAll(list)
                }
                list
            } else {
                synchronized(lock) {
                    notificationList.toList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active notifications", e)
            synchronized(lock) {
                notificationList.toList()
            }
        }
    }

    /**
     * Clears cached notifications in memory.
     */
    fun clearNotifications() {
        synchronized(lock) {
            notificationList.clear()
        }
    }

    private fun refreshActiveNotifications() {
        try {
            val active = try { super.getActiveNotifications() } catch (e: Exception) { null } ?: return
            synchronized(lock) {
                notificationList.clear()
                active.filterNotNull().forEach { sbn ->
                    notificationList.add(sbn.toNotificationItem())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh active notifications", e)
        }
    }

    private fun StatusBarNotification.toNotificationItem(): NotificationItem {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        return NotificationItem(
            id = key,
            packageName = packageName,
            title = title,
            text = text,
            postTime = postTime,
            isClearable = isClearable
        )
    }
}
