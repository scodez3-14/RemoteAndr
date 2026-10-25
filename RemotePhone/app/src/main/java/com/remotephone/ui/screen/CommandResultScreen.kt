package com.remotephone.ui.screen

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.remotephone.data.firebase.FirebaseCommandRepo
import com.remotephone.data.firebase.FirebasePairingRepo
import com.remotephone.data.model.CommandResult
import com.remotephone.data.model.ResultType
import com.remotephone.ui.theme.RemotePhoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class ParsedNotification(
    val title: String,
    val text: String,
    val packageName: String,
    val time: String
)

/**
 * Screen displaying the result of a remote command (SCREENSHOT, NOTIFICATION_LIST, TEXT).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandResultScreen(
    commandId: String,
    navController: NavController,
    modifier: Modifier = Modifier,
    commandRepo: FirebaseCommandRepo? = null,
    pairingRepo: FirebasePairingRepo? = null,
    previewResult: CommandResult? = null
) {
    var result by remember { mutableStateOf<CommandResult?>(previewResult) }
    var isLoading by remember { mutableStateOf(previewResult == null) }

    LaunchedEffect(commandId) {
        if (previewResult != null) return@LaunchedEffect
        val repo = commandRepo ?: return@LaunchedEffect
        val pRepo = pairingRepo ?: return@LaunchedEffect
        val pairId = pRepo.getPairId() ?: return@LaunchedEffect

        repo.observeResults(pairId).collect { res ->
            if (res.commandId == commandId || commandId == "latest") {
                result = res
                isLoading = false
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (result?.type) {
                            ResultType.SCREENSHOT -> "Screenshot Result"
                            ResultType.NOTIFICATION_LIST -> "Device Notifications"
                            else -> "Command Result"
                        }
                    )
                },
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Waiting for remote execution...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                result == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No result received",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                else -> {
                    val currentResult = result!!
                    when (currentResult.type) {
                        ResultType.SCREENSHOT -> ZoomableScreenshotView(imageUrlOrBase64 = currentResult.data)
                        ResultType.NOTIFICATION_LIST -> NotificationResultList(rawText = currentResult.data)
                        ResultType.TEXT -> TextResultView(data = currentResult.data)
                    }
                }
            }
        }
    }
}

/**
 * Zoomable and pannable image view supporting URL download or base64 decoding.
 */
@Composable
fun ZoomableScreenshotView(imageUrlOrBase64: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isImageLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) {
            Offset(
                x = offset.x + offsetChange.x,
                y = offset.y + offsetChange.y
            )
        } else {
            Offset.Zero
        }
    }

    LaunchedEffect(imageUrlOrBase64) {
        withContext(Dispatchers.IO) {
            try {
                if (imageUrlOrBase64.startsWith("http://") || imageUrlOrBase64.startsWith("https://")) {
                    val url = URL(imageUrlOrBase64)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.doInput = true
                    connection.connect()
                    val input: InputStream = connection.inputStream
                    bitmap = BitmapFactory.decodeStream(input)
                } else {
                    val cleanBase64 = if (imageUrlOrBase64.contains(",")) {
                        imageUrlOrBase64.substringAfter(",")
                    } else imageUrlOrBase64
                    val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                    bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                }
                isImageLoading = false
            } catch (e: Exception) {
                hasError = true
                isImageLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when {
            isImageLoading -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            hasError || bitmap == null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = "Failed to load",
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Failed to load screenshot",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Remote Screenshot",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state = transformState)
                )
            }
        }
    }
}

/**
 * Parses and displays formatted notifications list in modern cards.
 */
@Composable
fun NotificationResultList(rawText: String) {
    val items = remember(rawText) { parseNotifications(rawText) }

    if (items.isEmpty()) {
        TextResultView(data = rawText)
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Active Notifications (" + items.size + ")",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            items(items) { item ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (item.title.isNotBlank()) item.title else "Notification",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (item.time.isNotBlank()) {
                                    Text(
                                        text = item.time,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (item.text.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (item.packageName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Text card for displaying general command execution feedback or payload output.
 */
@Composable
fun TextResultView(data: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Output",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = data,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

private fun parseNotifications(rawText: String): List<ParsedNotification> {
    val result = mutableListOf<ParsedNotification>()
    val lines = rawText.lines()
    var currentTitle = ""
    var currentText = ""
    var currentPkg = ""
    var currentTime = ""

    for (line in lines) {
        val trimmed = line.trim()
        val isNumbered = trimmed.length > 2 && trimmed[0].isDigit() && (trimmed[1] == '.' || (trimmed.length > 3 && trimmed[1].isDigit() && trimmed[2] == '.'))
        when {
            isNumbered -> {
                if (currentTitle.isNotBlank() || currentPkg.isNotBlank()) {
                    result.add(ParsedNotification(currentTitle, currentText, currentPkg, currentTime))
                    currentText = ""
                    currentPkg = ""
                    currentTime = ""
                }
                currentTitle = trimmed.substringAfter(".").trim()
            }
            trimmed.contains("package:") || trimmed.contains("pkg:") || trimmed.contains("📦") -> {
                currentPkg = trimmed.replace("📦", "").replace("package:", "").trim()
            }
            trimmed.contains("ago") || trimmed.contains("now") || trimmed.contains("🕐") -> {
                currentTime = trimmed.replace("🕐", "").trim()
            }
            trimmed.isNotBlank() && !trimmed.contains("Active Notifications") && !trimmed.startsWith("-") && !trimmed.startsWith("=") -> {
                if (currentText.isBlank()) {
                    currentText = trimmed
                } else {
                    currentText += "\n" + trimmed
                }
            }
        }
    }
    if (currentTitle.isNotBlank() || currentPkg.isNotBlank()) {
        result.add(ParsedNotification(currentTitle, currentText, currentPkg, currentTime))
    }
    return result
}

@Preview(showBackground = true)
@Composable
fun CommandResultTextPreview() {
    RemotePhoneTheme {
        CommandResultScreen(
            commandId = "123",
            navController = rememberNavController(),
            previewResult = CommandResult(
                id = "1",
                commandId = "123",
                type = ResultType.TEXT,
                data = "Battery: 85% (Charging)\nTemperature: 31°C\nHealth: Good"
            )
        )
    }
}
