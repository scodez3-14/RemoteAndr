package com.remotephone.data.firebase

import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.remotephone.data.model.DeviceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepo @Inject constructor(
    private val auth: FirebaseAuth = Firebase.auth,
    private val database: FirebaseDatabase = Firebase.database
) {

    companion object {
        private const val ROOT_PATH = "remotephone"
        private const val USERS_PATH = "users"
    }

    private val usersRef get() = database.reference.child(ROOT_PATH).child(USERS_PATH)

    /**
     * Signs in anonymously using Firebase Auth.
     * Returns the user ID on success.
     */
    suspend fun signInAnonymously(): Result<String> = runCatching {
        val existingUid = auth.currentUser?.uid
        if (existingUid != null) {
            existingUid
        } else {
            val authResult = auth.signInAnonymously().await()
            authResult.user?.uid ?: throw IllegalStateException("Firebase user is null after anonymous sign in")
        }
    }

    /**
     * Returns the current authenticated user's ID, or null if not signed in.
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    /**
     * Registers the current device in the database at remotephone/users/{uid}.
     * Sets device metadata, online status, and establishes an onDisconnect handler.
     */
    suspend fun registerDevice(deviceName: String, fcmToken: String): Result<Unit> = runCatching {
        val uid = getCurrentUserId() ?: throw IllegalStateException("User is not signed in")
        val userRef = usersRef.child(uid)

        // Preserve existing pairedWith if already set
        val existingSnapshot = userRef.child("pairedWith").get().await()
        val pairedWith = existingSnapshot.getValue(String::class.java)

        val deviceInfo = DeviceInfo(
            userId = uid,
            deviceName = deviceName,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidVersion = Build.VERSION.RELEASE ?: "",
            fcmToken = fcmToken,
            pairedWith = pairedWith,
            isOnline = true,
            lastSeen = System.currentTimeMillis()
        )

        userRef.setValue(deviceInfo.toMap()).await()

        // Set onDisconnect hooks to automatically mark offline
        userRef.child("isOnline").onDisconnect().setValue(false)
        userRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
    }

    /**
     * Updates the online status of the current user and manages onDisconnect hooks.
     */
    suspend fun updateOnlineStatus(isOnline: Boolean): Result<Unit> = runCatching {
        val uid = getCurrentUserId() ?: throw IllegalStateException("User is not signed in")
        val userRef = usersRef.child(uid)

        userRef.child("isOnline").setValue(isOnline).await()
        userRef.child("lastSeen").setValue(ServerValue.TIMESTAMP).await()

        if (isOnline) {
            userRef.child("isOnline").onDisconnect().setValue(false)
            userRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP)
        } else {
            userRef.child("isOnline").onDisconnect().cancel()
            userRef.child("lastSeen").onDisconnect().cancel()
        }
    }

    /**
     * Updates the FCM registration token for the current user.
     */
    suspend fun updateFcmToken(token: String): Result<Unit> = runCatching {
        val uid = getCurrentUserId() ?: throw IllegalStateException("User is not signed in")
        usersRef.child(uid).child("fcmToken").setValue(token).await()
    }

    /**
     * Observes real-time changes to the DeviceInfo of a specific user.
     */
    fun observeDeviceInfo(userId: String): Flow<DeviceInfo?> = callbackFlow {
        val userRef = usersRef.child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }

                val deviceInfo = DeviceInfo(
                    userId = snapshot.child("userId").getValue(String::class.java) ?: userId,
                    deviceName = snapshot.child("deviceName").getValue(String::class.java) ?: "",
                    deviceModel = snapshot.child("deviceModel").getValue(String::class.java) ?: "",
                    androidVersion = snapshot.child("androidVersion").getValue(String::class.java) ?: "",
                    fcmToken = snapshot.child("fcmToken").getValue(String::class.java) ?: "",
                    pairedWith = snapshot.child("pairedWith").getValue(String::class.java),
                    isOnline = snapshot.child("isOnline").getValue(Boolean::class.java) ?: false,
                    lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                )
                trySend(deviceInfo)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        userRef.addValueEventListener(listener)

        awaitClose {
            userRef.removeEventListener(listener)
        }
    }
}
