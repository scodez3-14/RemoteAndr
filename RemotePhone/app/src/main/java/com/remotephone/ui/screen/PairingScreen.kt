package com.remotephone.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.remotephone.data.firebase.FirebasePairingRepo
import com.remotephone.ui.theme.RemotePhoneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * State representation for device pairing operations.
 */
sealed class PairingState {
    object Idle : PairingState()
    object Loading : PairingState()
    data class CodeGenerated(val code: String, val secondsRemaining: Int) : PairingState()
    data class Success(val pairId: String) : PairingState()
    data class Error(val message: String) : PairingState()
}

/**
 * PairingScreen with two tabs: Generate Code (host) and Enter Code (client).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pairingRepo: FirebasePairingRepo? = null,
    initialState: PairingState = PairingState.Idle
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var pairingState by remember { mutableStateOf(initialState) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Timer for generated code countdown
    var countdownSeconds by remember { mutableIntStateOf(300) }

    LaunchedEffect(pairingState) {
        if (pairingState is PairingState.CodeGenerated) {
            countdownSeconds = (pairingState as PairingState.CodeGenerated).secondsRemaining
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds -= 1
            }
            pairingState = PairingState.Error("Pairing code expired. Please generate a new one.")
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Pair Devices") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Generate Code") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Enter Code") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                if (selectedTab == 0) {
                    GenerateCodeTab(
                        pairingState = pairingState,
                        countdownSeconds = countdownSeconds,
                        onGenerateCode = {
                            coroutineScope.launch {
                                pairingState = PairingState.Loading
                                val repo = pairingRepo
                                if (repo != null) {
                                    try {
                                        val code = repo.generatePairingCode()
                                        pairingState = PairingState.CodeGenerated(code, 300)
                                    } catch (e: Exception) {
                                        pairingState = PairingState.Error(e.message ?: "Failed to generate code")
                                    }
                                } else {
                                    // Mock for preview/offline
                                    pairingState = PairingState.CodeGenerated("583921", 300)
                                }
                            }
                        },
                        onCopyCode = { code ->
                            clipboardManager.setText(AnnotatedString(code))
                        }
                    )
                } else {
                    EnterCodeTab(
                        pairingState = pairingState,
                        onValidateCode = { code ->
                            coroutineScope.launch {
                                pairingState = PairingState.Loading
                                val repo = pairingRepo
                                if (repo != null) {
                                    val res = repo.validateAndPair(code)
                                    if (res.isSuccess) {
                                        pairingState = PairingState.Success(res.getOrThrow())
                                        delay(1200L)
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    } else {
                                        pairingState = PairingState.Error(
                                            res.exceptionOrNull()?.message ?: "Invalid or expired code"
                                        )
                                    }
                                } else {
                                    // Mock for preview
                                    pairingState = PairingState.Success("pair_123")
                                    delay(1000L)
                                    navController.popBackStack()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GenerateCodeTab(
    pairingState: PairingState,
    countdownSeconds: Int,
    onGenerateCode: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Enter this code on the other phone",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Share this 6-digit code with your secondary device to securely connect them.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(28.dp))

    val currentCode = (pairingState as? PairingState.CodeGenerated)?.code ?: "------"

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentCode.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (pairingState is PairingState.CodeGenerated) {
                val minutes = countdownSeconds / 60
                val seconds = countdownSeconds % 60
                val formattedTime = String.format("%02d:%02d", minutes, seconds)

                Text(
                    text = "Expires in $formattedTime",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (countdownSeconds < 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { onCopyCode(currentCode) }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Code")
                }
            } else {
                Text(
                    text = "Tap below to generate a new secure code",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(28.dp))

    Button(
        onClick = onGenerateCode,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        if (pairingState is PairingState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp
            )
        } else {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate New Code")
        }
    }

    if (pairingState is PairingState.Error) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = pairingState.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EnterCodeTab(
    pairingState: PairingState,
    onValidateCode: (String) -> Unit
) {
    var otpDigits by remember { mutableStateOf(List(6) { "" }) }
    val focusRequesters = remember { List(6) { FocusRequester() } }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Enter 6-Digit Pairing Code",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Type the code shown on your primary device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(32.dp))

    // 6-digit OTP fields
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        otpDigits.forEachIndexed { index, digit ->
            OtpBox(
                value = digit,
                focusRequester = focusRequesters[index],
                onValueChange = { newVal ->
                    if (newVal.length <= 1 && newVal.all { it.isDigit() }) {
                        val newDigits = otpDigits.toMutableList()
                        newDigits[index] = newVal
                        otpDigits = newDigits

                        if (newVal.isNotEmpty() && index < 5) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    } else if (newVal.length == 6 && newVal.all { it.isDigit() }) {
                        // Paste support
                        otpDigits = newVal.map { it.toString() }
                        focusRequesters[5].requestFocus()
                    }
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    val isComplete = otpDigits.all { it.isNotEmpty() }
    val fullCode = otpDigits.joinToString("")

    Button(
        onClick = { onValidateCode(fullCode) },
        enabled = isComplete && pairingState !is PairingState.Loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        if (pairingState is PairingState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Validating Code...")
        } else {
            Text("Pair Device")
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    AnimatedVisibility(visible = pairingState is PairingState.Success) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Paired successfully! Redirecting...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF4CAF50)
            )
        }
    }

    AnimatedVisibility(visible = pairingState is PairingState.Error) {
        val errorMsg = (pairingState as? PairingState.Error)?.message ?: "Error"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = errorMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun OtpBox(
    value: String,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp, 56.dp)
            .border(
                width = if (value.isNotEmpty()) 2.dp else 1.dp,
                color = if (value.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleLarge.copy(
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PairingScreenPreview() {
    RemotePhoneTheme {
        PairingScreen(navController = rememberNavController())
    }
}
