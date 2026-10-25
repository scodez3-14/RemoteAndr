package com.remotephone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.remotephone.data.firebase.FirebaseAuthRepo
import com.remotephone.data.firebase.FirebaseCommandRepo
import com.remotephone.data.firebase.FirebasePairingRepo
import com.remotephone.data.firebase.FirebaseSignalingRepo
import com.remotephone.ui.navigation.NavGraph
import com.remotephone.ui.theme.RemotePhoneTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single Activity for RemotePhone app.
 * Handles Firebase authentication, permission requests, and hosts the Compose navigation.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Firebase repositories
    private lateinit var authRepo: FirebaseAuthRepo
    private lateinit var pairingRepo: FirebasePairingRepo
    private lateinit var signalingRepo: FirebaseSignalingRepo
    private lateinit var commandRepo: FirebaseCommandRepo

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach { (permission, isGranted) ->
            Log.d(TAG, "Permission $permission: ${if (isGranted) "granted" else "denied"}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize repositories
        authRepo = FirebaseAuthRepo()
        pairingRepo = FirebasePairingRepo()
        signalingRepo = FirebaseSignalingRepo()
        commandRepo = FirebaseCommandRepo()

        // Sign in anonymously and register device
        signInAndRegister()

        // Request runtime permissions
        requestRuntimePermissions()

        setContent {
            RemotePhoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        signalingRepo = signalingRepo,
                        pairingRepo = pairingRepo,
                        commandRepo = commandRepo
                    )
                }
            }
        }
    }

    /**
     * Sign in anonymously to Firebase and register this device.
     */
    private fun signInAndRegister() {
        activityScope.launch {
            try {
                // Sign in
                val result = authRepo.signInAnonymously()
                result.onSuccess { userId ->
                    Log.d(TAG, "Signed in as: $userId")

                    // Get FCM token and register device
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        activityScope.launch {
                            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                            authRepo.registerDevice(deviceName, token)
                            authRepo.updateOnlineStatus(true)
                            Log.d(TAG, "Device registered: $deviceName")
                        }
                    }
                }
                result.onFailure { error ->
                    Log.e(TAG, "Sign in failed", error)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Auth error", e)
            }
        }
    }

    /**
     * Request necessary runtime permissions.
     */
    private fun requestRuntimePermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        // Update online status
        activityScope.launch {
            try {
                authRepo.updateOnlineStatus(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update online status", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Don't set offline on pause — service should keep running
    }

    override fun onDestroy() {
        super.onDestroy()
        // Update online status when app is destroyed (not just paused)
        activityScope.launch {
            try {
                authRepo.updateOnlineStatus(false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update offline status", e)
            }
        }
    }
}
