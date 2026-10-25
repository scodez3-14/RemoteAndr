package com.remotephone.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.remotephone.data.firebase.FirebaseCommandRepo
import com.remotephone.data.firebase.FirebasePairingRepo
import com.remotephone.data.model.Command
import com.remotephone.data.model.CommandResult
import com.remotephone.data.model.CommandType
import com.remotephone.data.model.ResultType
import com.remotephone.ui.component.CommandCard
import com.remotephone.ui.theme.RemotePhoneTheme
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class CommandDefinition(
    val type: CommandType,
    val label: String,
    val icon: ImageVector,
    val payload: Map<String, String> = emptyMap()
)

/**
 * CommandsScreen providing a 3-column grid of executable remote actions with loading state
 * and bottom sheet / navigation display of returned results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    commandRepo: FirebaseCommandRepo? = null,
    pairingRepo: FirebasePairingRepo? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var loadingCommandType by remember { mutableStateOf<CommandType?>(null) }
    var activeResult by remember { mutableStateOf<CommandResult?>(null) }
    var showResultSheet by remember { mutableStateOf(false) }

    val commands = listOf(
        CommandDefinition(
            type = CommandType.OPEN_APP,
            label = "Open Email",
            icon = Icons.Default.Email,
            payload = mapOf("packageName" to "com.google.android.gm")
        ),
        CommandDefinition(
            type = CommandType.SCREENSHOT,
            label = "Screenshot",
            icon = Icons.Default.CameraAlt
        ),
        CommandDefinition(
            type = CommandType.GET_BATTERY,
            label = "Battery",
            icon = Icons.Default.BatteryFull
        ),
        CommandDefinition(
            type = CommandType.GET_LOCATION,
            label = "Location",
            icon = Icons.Default.LocationOn
        ),
        CommandDefinition(
            type = CommandType.RING_PHONE,
            label = "Ring Phone",
            icon = Icons.Default.NotificationsActive
        ),
        CommandDefinition(
            type = CommandType.GET_NOTIFICATIONS,
            label = "Notifications",
            icon = Icons.Default.Notifications
        ),
        CommandDefinition(
            type = CommandType.LOCK_SCREEN,
            label = "Lock Screen",
            icon = Icons.Default.Lock
        ),
        CommandDefinition(
            type = CommandType.GET_CLIPBOARD,
            label = "Clipboard",
            icon = Icons.Default.ContentPaste
        )
    )

    fun executeCommand(cmd: CommandDefinition) {
        if (loadingCommandType != null) return
        loadingCommandType = cmd.type

        coroutineScope.launch {
            val repo = commandRepo
            val pRepo = pairingRepo
            if (repo == null || pRepo == null) {
                // Mock result if repos not provided (e.g. preview)
                loadingCommandType = null
                activeResult = CommandResult(
                    commandId = "mock",
                    type = when (cmd.type) {
                        CommandType.SCREENSHOT -> ResultType.SCREENSHOT
                        CommandType.GET_NOTIFICATIONS -> ResultType.NOTIFICATION_LIST
                        else -> ResultType.TEXT
                    },
                    data = "${cmd.label} executed successfully."
                )
                showResultSheet = true
                return@launch
            }

            try {
                val pairId = pRepo.getPairId()
                if (pairId == null) {
                    snackbarHostState.showSnackbar("No paired device found.")
                    loadingCommandType = null
                    return@launch
                }

                val command = Command(
                    type = cmd.type,
                    payload = cmd.payload,
                    sentBy = "controller"
                )
                val commandId = repo.sendCommand(pairId, command)

                // Wait for result up to 10 seconds
                val result = withTimeoutOrNull(10_000L) {
                    repo.observeResults(pairId).firstOrNull { it.commandId == commandId }
                }

                loadingCommandType = null

                if (result != null) {
                    if (result.type == ResultType.SCREENSHOT) {
                        // Navigate to full result screen for screenshots
                        navController.navigate("command_result/$commandId")
                    } else {
                        // Show in bottom sheet for text and notifications
                        activeResult = result
                        showResultSheet = true
                    }
                } else {
                    snackbarHostState.showSnackbar("Command sent. Awaiting response...")
                }
            } catch (e: Exception) {
                loadingCommandType = null
                snackbarHostState.showSnackbar("Failed to send command: ${e.message}")
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Remote Commands") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(commands) { cmd ->
                CommandCard(
                    icon = cmd.icon,
                    label = cmd.label,
                    isLoading = loadingCommandType == cmd.type,
                    onClick = { executeCommand(cmd) }
                )
            }
        }

        // Bottom sheet displaying the result
        if (showResultSheet && activeResult != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showResultSheet = false
                    activeResult = null
                },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Result Output",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = activeResult!!.data,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            val resId = activeResult?.commandId ?: "latest"
                            showResultSheet = false
                            activeResult = null
                            navController.navigate("command_result/$resId")
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Open Full View")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CommandsScreenPreview() {
    RemotePhoneTheme {
        CommandsScreen(navController = rememberNavController())
    }
}
