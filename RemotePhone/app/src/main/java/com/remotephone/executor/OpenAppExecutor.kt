package com.remotephone.executor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.remotephone.data.model.Command
import com.remotephone.data.model.CommandResult
import com.remotephone.data.model.ResultType

/**
 * Executor that launches apps by package name on Phone B.
 * Uses SYSTEM_ALERT_WINDOW permission to launch from background.
 */
class OpenAppExecutor(private val context: Context) : CommandExecutor {

    companion object {
        private const val TAG = "OpenAppExecutor"

        // Common app package names for quick access
        val COMMON_APPS = mapOf(
            "email" to listOf(
                "com.google.android.gm",           // Gmail
                "com.microsoft.office.outlook",     // Outlook
                "com.yahoo.mobile.client.android.mail" // Yahoo Mail
            ),
            "browser" to listOf(
                "com.android.chrome",               // Chrome
                "org.mozilla.firefox",              // Firefox
                "com.brave.browser"                 // Brave
            ),
            "messages" to listOf(
                "com.google.android.apps.messaging", // Google Messages
                "com.samsung.android.messaging"      // Samsung Messages
            ),
            "camera" to listOf(
                "com.google.android.GoogleCamera",   // Google Camera
                "com.samsung.android.app.camera"     // Samsung Camera
            ),
            "photos" to listOf(
                "com.google.android.apps.photos",    // Google Photos
                "com.samsung.android.gallery3d"      // Samsung Gallery
            ),
            "maps" to listOf(
                "com.google.android.apps.maps"       // Google Maps
            ),
            "phone" to listOf(
                "com.google.android.dialer",         // Google Phone
                "com.samsung.android.dialer"         // Samsung Phone
            ),
            "settings" to listOf(
                "com.android.settings"               // System Settings
            ),
            "whatsapp" to listOf(
                "com.whatsapp"                       // WhatsApp
            ),
            "youtube" to listOf(
                "com.google.android.youtube"         // YouTube
            )
        )
    }

    override suspend fun execute(command: Command): CommandResult {
        val packageName = command.payload["packageName"]
        val appAlias = command.payload["appAlias"]

        return try {
            val targetPackage = when {
                !packageName.isNullOrBlank() -> packageName
                !appAlias.isNullOrBlank() -> resolveAlias(appAlias)
                else -> null
            }

            if (targetPackage == null) {
                return CommandResult(
                    commandId = command.id,
                    type = ResultType.TEXT,
                    data = "Error: No package name or app alias provided"
                )
            }

            val launched = launchApp(targetPackage)
            if (launched) {
                CommandResult(
                    commandId = command.id,
                    type = ResultType.TEXT,
                    data = "Successfully opened $targetPackage"
                )
            } else {
                CommandResult(
                    commandId = command.id,
                    type = ResultType.TEXT,
                    data = "App not found: $targetPackage"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app", e)
            CommandResult(
                commandId = command.id,
                type = ResultType.TEXT,
                data = "Error: ${e.message}"
            )
        }
    }

    private fun launchApp(packageName: String): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            context.startActivity(launchIntent)
            Log.d(TAG, "Launched app: $packageName")
            true
        } else {
            Log.w(TAG, "No launch intent for package: $packageName")
            false
        }
    }

    /**
     * Resolves an app alias (e.g., "email") to an installed package name.
     */
    private fun resolveAlias(alias: String): String? {
        val candidates = COMMON_APPS[alias.lowercase()] ?: return null
        return candidates.firstOrNull { isPackageInstalled(it) }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Returns a list of all installed launchable apps.
     */
    fun getInstalledApps(): List<Map<String, String>> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = context.packageManager.queryIntentActivities(mainIntent, 0)
        return resolveInfos.map { resolveInfo ->
            mapOf(
                "packageName" to resolveInfo.activityInfo.packageName,
                "appName" to resolveInfo.loadLabel(context.packageManager).toString()
            )
        }.sortedBy { it["appName"] }
    }
}
