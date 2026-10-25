package com.remotephone.ui.screen

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewArray
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import com.remotephone.data.firebase.FirebaseCommandRepo
import com.remotephone.data.firebase.FirebasePairingRepo
import com.remotephone.data.firebase.FirebaseSignalingRepo
import com.remotephone.data.model.Command
import com.remotephone.data.model.CommandType
import com.remotephone.data.model.TouchEvent
import com.remotephone.data.model.TouchType
import com.remotephone.ui.component.ConnectionIndicator
import com.remotephone.ui.component.TouchOverlay
import com.remotephone.util.DisplayMetricsHelper
import com.remotephone.webrtc.ConnectionState
import com.remotephone.webrtc.TouchDataChannel
import com.remotephone.webrtc.WebRTCManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import kotlin.math.roundToInt

/**
 * Full-screen immersive LiveMirrorScreen for real-time WebRTC screen mirroring
 * and remote touch interaction.
 *
 * NOTE: This screen does NOT use Scaffold to ensure true edge-to-edge immersion.
 */
@Composable
fun LiveMirrorScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    signalingRepo: FirebaseSignalingRepo? = null,
    commandRepo: FirebaseCommandRepo? = null,
    pairingRepo: FirebasePairingRepo? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    // Hide system status and navigation bars for full-screen immersive experience
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            val window = (context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var pairId by remember { mutableStateOf<String?>(null) }
    var webrtcManager by remember { mutableStateOf<WebRTCManager?>(null) }
    val touchDataChannel = remember { TouchDataChannel() }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var latencyMs by remember { mutableStateOf<Long?>(42L) }
    val resolutionLabel = "720p"

    // Draggable toolbar state
    var toolbarOffsetX by remember { mutableFloatStateOf(0f) }
    var toolbarOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val pRepo = pairingRepo ?: return@LaunchedEffect
        val sRepo = signalingRepo ?: return@LaunchedEffect

        val pid = pRepo.getPairId()
        if (pid != null) {
            pairId = pid
            val manager = WebRTCManager(context, sRepo, pid)
            webrtcManager = manager
            manager.startAsOfferer()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webrtcManager?.disconnect()
        }
    }

    val connectionState = webrtcManager?.connectionState?.collectAsState()?.value
        ?: ConnectionState.CONNECTING
    val remoteVideoTrack = webrtcManager?.remoteVideoTrack?.collectAsState()?.value
    val dataChannel = webrtcManager?.dataChannel?.collectAsState()?.value

    fun sendTouchEventToPeer(event: TouchEvent) {
        val dc = dataChannel
        if (dc != null) {
            touchDataChannel.sendTouchEvent(dc, event)
        }
    }

    fun sendSystemCommand(type: CommandType) {
        val pid = pairId ?: return
        val cmdRepo = commandRepo ?: return
        coroutineScope.launch(Dispatchers.IO) {
            cmdRepo.sendCommand(pid, Command(type = type, sentBy = "controller"))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
    ) {
        // 1. WebRTC SurfaceViewRenderer
        if (webrtcManager != null) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(webrtcManager!!.eglBase.eglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        setEnableHardwareScaler(true)
                        remoteVideoTrack?.addSink(this)
                    }
                },
                update = { renderer ->
                    remoteVideoTrack?.let { track ->
                        track.removeSink(renderer)
                        track.addSink(renderer)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Transparent Touch Overlay on top of live mirror
        TouchOverlay(
            onTouchEvent = { rawEvent ->
                // Scale coordinates if dimensions available
                val scaledEvent = if (containerSize.width > 0 && containerSize.height > 0) {
                    val (scaledX, scaledY) = DisplayMetricsHelper.scaleToRemote(
                        localX = rawEvent.x ?: 0f,
                        localY = rawEvent.y ?: 0f,
                        viewWidth = containerSize.width,
                        viewHeight = containerSize.height,
                        remoteWidth = 1080,
                        remoteHeight = 1920
                    )
                    val (scaledX2, scaledY2) = DisplayMetricsHelper.scaleToRemote(
                        localX = rawEvent.x2 ?: 0f,
                        localY = rawEvent.y2 ?: 0f,
                        viewWidth = containerSize.width,
                        viewHeight = containerSize.height,
                        remoteWidth = 1080,
                        remoteHeight = 1920
                    )
                    rawEvent.copy(
                        x = scaledX,
                        y = scaledY,
                        x2 = if (rawEvent.x2 != null) scaledX2 else null,
                        y2 = if (rawEvent.y2 != null) scaledY2 else null
                    )
                } else rawEvent

                sendTouchEventToPeer(scaledEvent)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 3. Top Connection Status Badge
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectionIndicator(
                isConnected = connectionState == ConnectionState.CONNECTED,
                isConnecting = connectionState == ConnectionState.CONNECTING,
                latencyMs = if (connectionState == ConnectionState.CONNECTED) latencyMs else null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            ) {
                Text(
                    text = resolutionLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 4. Floating semi-transparent draggable toolbar at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(toolbarOffsetX.roundToInt(), toolbarOffsetY.roundToInt()) }
                .padding(bottom = 24.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        toolbarOffsetX += dragAmount.x
                        toolbarOffsetY += dragAmount.y
                    }
                }
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back
                    MirrorNavButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = {
                            sendTouchEventToPeer(TouchEvent.back())
                        }
                    )

                    // Home
                    MirrorNavButton(
                        icon = Icons.Default.Home,
                        contentDescription = "Home",
                        onClick = {
                            sendTouchEventToPeer(TouchEvent.home())
                        }
                    )

                    // Recents
                    MirrorNavButton(
                        icon = Icons.Default.ViewArray,
                        contentDescription = "Recents",
                        onClick = {
                            sendTouchEventToPeer(TouchEvent.recents())
                        }
                    )

                    // Screenshot
                    MirrorNavButton(
                        icon = Icons.Default.CameraAlt,
                        contentDescription = "Screenshot",
                        onClick = {
                            sendSystemCommand(CommandType.SCREENSHOT)
                        }
                    )

                    // Lock
                    MirrorNavButton(
                        icon = Icons.Default.Lock,
                        contentDescription = "Lock",
                        onClick = {
                            sendSystemCommand(CommandType.LOCK_SCREEN)
                        }
                    )

                    // Disconnect (Red)
                    IconButton(
                        onClick = {
                            webrtcManager?.disconnect()
                            navController.popBackStack()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 5. Connecting State Overlay
        AnimatedVisibility(
            visible = connectionState == ConnectionState.CONNECTING,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connecting to remote phone...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 6. Disconnected / Failed State Overlay with Retry Button
        AnimatedVisibility(
            visible = connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.FAILED,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Stream Disconnected",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connection to remote screen was lost.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                webrtcManager?.startAsOfferer()
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                        Button(
                            onClick = { navController.popBackStack() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Back")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MirrorNavButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
    }
}
