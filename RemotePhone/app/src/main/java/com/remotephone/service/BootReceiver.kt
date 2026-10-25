package com.remotephone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receiver that triggers upon device boot to restart necessary background services
 * such as [CommandExecutorService].
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i(TAG, "Boot completed broadcast received. Starting CommandExecutorService...")
            try {
                CommandExecutorService.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CommandExecutorService on boot", e)
            }
        }
    }
}
