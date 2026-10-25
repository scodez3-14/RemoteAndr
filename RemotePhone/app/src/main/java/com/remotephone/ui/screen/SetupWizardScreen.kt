package com.remotephone.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.remotephone.service.NotificationReaderService
import com.remotephone.service.RemoteAccessibilityService
import com.remotephone.ui.theme.RemotePhoneTheme

data class SetupStepItem(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isCritical: Boolean = true,
    val settingsIntentAction: String,
    val useAppPackageUri: Boolean = false
)

/**
 * Step-by-step setup screen guiding the user through required permissions and special accesses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var accessibilityGranted by remember { mutableStateOf(RemoteAccessibilityService.isServiceRunning) }
    var notificationGranted by remember { mutableStateOf(NotificationReaderService.isServiceConnected) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryIgnored by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    var restrictedSettingsAllowed by remember { mutableStateOf(true) }

    fun refreshStatuses() {
        accessibilityGranted = RemoteAccessibilityService.isServiceRunning
        notificationGranted = NotificationReaderService.isServiceConnected
        overlayGranted = Settings.canDrawOverlays(context)
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        batteryIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    val steps = listOf(
        SetupStepItem(
            stepNumber = 1,
            title = "Allow Restricted Settings",
            description = "Android 13+ requires allowing restricted settings from App Info to enable Accessibility.",
            isGranted = restrictedSettingsAllowed,
            isCritical = false,
            settingsIntentAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            useAppPackageUri = true
        ),
        SetupStepItem(
            stepNumber = 2,
            title = "Accessibility Service",
            description = "Required for remote touch injection, silent screenshots, and system navigation.",
            isGranted = accessibilityGranted,
            isCritical = true,
            settingsIntentAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
        ),
        SetupStepItem(
            stepNumber = 3,
            title = "Notification Access",
            description = "Allows reading and syncing incoming notifications to your controller device.",
            isGranted = notificationGranted,
            isCritical = false,
            settingsIntentAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        ),
        SetupStepItem(
            stepNumber = 4,
            title = "Draw Over Other Apps",
            description = "Allows launching apps from background and maintaining floating control overlays.",
            isGranted = overlayGranted,
            isCritical = true,
            settingsIntentAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            useAppPackageUri = true
        ),
        SetupStepItem(
            stepNumber = 5,
            title = "Battery Optimization",
            description = "Exempts RemotePhone from background battery killing so services stay alive.",
            isGranted = batteryIgnored,
            isCritical = false,
            settingsIntentAction = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        )
    )

    val allCriticalDone = accessibilityGranted && overlayGranted

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Setup Wizard") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Button(
                    onClick = { navController.popBackStack() },
                    enabled = allCriticalDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp)
                ) {
                    Text(if (allCriticalDone) "Continue" else "Complete Required Steps")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Permissions & Access",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Grant these permissions on this device so it can be controlled and monitored remotely.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(steps) { step ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = step.stepNumber.toString(),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = step.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (step.isCritical) {
                                    Text(
                                        text = "Required",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (step.isGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = if (step.isGranted) "Granted" else "Needed",
                                tint = if (step.isGranted) Color(0xFF4CAF50) else Color(0xFFFFB300),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { refreshStatuses() },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Check", style = MaterialTheme.typography.labelMedium)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            FilledTonalButton(
                                onClick = {
                                    val intent = if (step.useAppPackageUri) {
                                        Intent(
                                            step.settingsIntentAction,
                                            Uri.fromParts("package", context.packageName, null)
                                        )
                                    } else {
                                        Intent(step.settingsIntentAction)
                                    }.apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching { context.startActivity(intent) }
                                },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Open Settings", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SetupWizardScreenPreview() {
    RemotePhoneTheme {
        SetupWizardScreen(navController = rememberNavController())
    }
}
