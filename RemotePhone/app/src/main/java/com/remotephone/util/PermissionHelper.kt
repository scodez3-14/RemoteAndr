package com.remotephone.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import com.remotephone.service.RemoteAccessibilityService

/**
 * Helper for checking and requesting all permissions needed by RemotePhone.
 * Phone B (controlled) needs all permissions; Phone A (controller) needs minimal.
 */
object PermissionHelper {

    /**
     * Represents a setup step with its check and action.
     */
    data class SetupStep(
        val title: String,
        val description: String,
        val isGranted: Boolean,
        val isCritical: Boolean = true
    )

    /**
     * Get all setup steps with their current status.
     */
    fun getSetupSteps(context: Context): List<SetupStep> {
        return listOf(
            SetupStep(
                title = "Accessibility Service",
                description = "Required for touch injection, screenshots, and system navigation. Go to Settings → Accessibility → RemotePhone → Enable.",
                isGranted = isAccessibilityEnabled(context),
                isCritical = true
            ),
            SetupStep(
                title = "Notification Access",
                description = "Required to read notifications remotely. Go to Settings → Special App Access → Notification Access → Enable for RemotePhone.",
                isGranted = isNotificationListenerEnabled(context),
                isCritical = false
            ),
            SetupStep(
                title = "Draw Over Other Apps",
                description = "Required to launch apps from the background. Go to Settings → Special App Access → Display over other apps → Enable.",
                isGranted = canDrawOverlays(context),
                isCritical = true
            ),
            SetupStep(
                title = "Battery Optimization",
                description = "Disable battery optimization to keep the service running reliably. Go to Settings → Battery → RemotePhone → Unrestricted.",
                isGranted = isBatteryOptimizationIgnored(context),
                isCritical = true
            )
        )
    }

    /**
     * Check if the Accessibility Service is enabled.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        val expectedComponent = ComponentName(context, RemoteAccessibilityService::class.java)
        return enabledServices.any {
            ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == expectedComponent
        }
    }

    /**
     * Check if NotificationListenerService is enabled.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }

    /**
     * Check if the app can draw overlays.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Check if battery optimization is ignored for this app.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Check if all critical permissions are granted.
     */
    fun areAllCriticalPermissionsGranted(context: Context): Boolean {
        return isAccessibilityEnabled(context) &&
                canDrawOverlays(context) &&
                isBatteryOptimizationIgnored(context)
    }

    // --- Intent launchers for settings pages ---

    /**
     * Open the app's info page (for "Allow restricted settings" on Android 13+).
     */
    fun openAppInfo(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Open Accessibility settings.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Open Notification Listener settings.
     */
    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Open overlay permission settings.
     */
    fun openOverlaySettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Open battery optimization settings for this app.
     */
    @android.annotation.SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Open the correct settings page for a given step index.
     */
    fun openSettingsForStep(context: Context, stepIndex: Int) {
        when (stepIndex) {
            0 -> openAccessibilitySettings(context)
            1 -> openNotificationListenerSettings(context)
            2 -> openOverlaySettings(context)
            3 -> openBatteryOptimizationSettings(context)
        }
    }
}
