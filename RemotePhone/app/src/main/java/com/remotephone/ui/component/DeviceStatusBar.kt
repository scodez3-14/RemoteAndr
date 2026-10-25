package com.remotephone.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.remotephone.data.model.DeviceInfo
import com.remotephone.ui.theme.RemotePhoneTheme

/**
 * Shows paired device status including model, name, online/offline indicator,
 * and last seen timestamp.
 */
@Composable
fun DeviceStatusBar(
    deviceInfo: DeviceInfo?,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "Device Icon",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayName = when {
                        deviceInfo == null -> "No DevicePaired"
                        deviceInfo.deviceName.isNotBlank() -> deviceInfo.deviceName
                        deviceInfo.deviceModel.isNotBlank() -> deviceInfo.deviceModel
                        else -> "Remote Phone"
                    }

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (deviceInfo != null) {
                        val statusColor = if (deviceInfo.isOnline) Color(0xF44CAF50) else Color(0xFEE53935)
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }
                }

                val subtitleText = when {
                    deviceInfo == null -> "Pair a phone to start remote access"
                    deviceInfo.isOnline -> {
                        val model = if (deviceInfo.deviceModel.isNotBlank()) deviceInfo.deviceModel else "Online"
                        "$model • Connected"
                    }
                    else -> {
                        val model = if (deviceInfo.deviceModel.isNotBlank()) "${deviceInfo.deviceModel} •�" else ""
                        "${model}Last seen: ${formatLastSeen(deviceInfo.lastSeen)}"
                    }
                }

                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatLastSeen(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

@Preview (showBackground = true)
@Composable
fun DeviceStatusBarOnlinePreview() {
    RemotePhoneTheme {
        DeviceStatusBar(
            deviceInfo = DeviceInfo(
                deviceName = "Pixel 8 Pro",
                deviceModel = "Google Pixel 8 Pro",
                isOnline = true,
                lastSeen = System.currentTimeMillis()
            )
        )
    }
}
