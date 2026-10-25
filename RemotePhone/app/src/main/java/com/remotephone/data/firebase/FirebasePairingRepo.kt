package com.remotephone.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
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
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebasePairingRepo @Inject constructor(
    private val auth: FirebaseAuth = Firebase.auth,
    private val database: FirebaseDatabase = Firebase.database
) {

    companion object {
        private const val ROOT_PATH = "remotephone"
        private const val USERS_PATH = "users"
        private const val PAIRING_CODES_PATH = "pairingCodes"
        private const val PAIRS_PATH = "pairs"
        private const val SIGNALING_PATH = "signaling"
        private const val CODE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val rootRef get() = database.reference.child(ROOT_PATH)
    private val usersRef get() = rootRef.child(USERS_PATH)
    private val pairingCodesRef get() = rootRef.child(PAIRING_CODES_PATH)

    /**
     * Generates a random 6-digit pairing code, stores it at remotephone/pairingCodes/{code}
     * with userId and expiresAt (5 minutes validity), and returns the code.
     */
    suspend fun generatePairingCode(): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")
        val random = SecureRandom()
        val code = String.format("%06d", random.nextInt(1_000_000))
        val expiresAt = System.currentTimeMillis() + CODE_EXPIRY_MS

        val codeData = mapOf(
            "code" to code,
            "userId" to uid,
            "createdAt" to ServerValue.TIMESTAMP,
            "expiresAt" to expiresAt
        )

        pairingCodesRef.child(code).setValue(codeData).await()
        return code
    }

    /**
     * Validates a 6-digit pairing code, links both devices mutually,
     * deletes the pairing code, and returns the resulting pairId.
     */
    suspend fun validateAndPair(code: String): Result<String> = runCatching {
        val currentUserId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        val codeSnapshot = pairingCodesRef.child(code).get().await()
        if (!codeSnapshot.exists()) {
            throw IllegalArgumentException("Invalid pairing code")
        }

        val targetUserId = codeSnapshot.child("userId").getValue(String::class.java)
            ?: throw IllegalStateException("Pairing code data is missing target user ID")

        if (targetUserId == currentUserId) {
            throw IllegalArgumentException("Cannot pair device with itself")
        }

        val expiresAt = codeSnapshot.child("expiresAt").getValue(Long::class.java) ?: 0L
        if (System.currentTimeMillis() > expiresAt) {
            pairingCodesRef.child(code).removeValue().await()
            throw IllegalStateException("Pairing code has expired")
        }

        val pairId = listOf(currentUserId, targetUserId).sorted().joinToString("_")

        val updates = hashMapOf<String, Any?>(
            "$USERS_PATH/$currentUserId/pairedWith" to targetUserId,
            "$USERS_PATH/$currentUserId/pairId" to pairId,
            "$USERS_PATH/$targetUserId/pairedWith" to currentUserId,
            "$USERS_PATH/$targetUserId/pairId" to pairId,
            "$PAIRS_PATH/$pairId" to mapOf(
                "pairId" to pairId,
                "user1" to currentUserId,
                "user2" to targetUserId,
                "pairedAt" to ServerValue.TIMESTAMP
            ),
            "$PAIRING_CODES_PATH/$code" to null
        )

        rootRef.updateChildren(updates).await()
        pairId
    }

    /**
     * Retrieves the pairId for the currently authenticated user, or null if not paired.
     */
    suspend fun getPairId(): String? = runCatching {
        val uid = auth.currentUser?.uid ?: return null
        val snapshot = usersRef.child(uid).get().await()

        val pairId = snapshot.child("pairId").getValue(String::class.java)
        if (!pairId.isNullOrEmpty()) {
            pairId
        } else {
            val pairedWith = snapshot.child("pairedWith").getValue(String::class.java)
            if (!pairedWith.isNullOrEmpty()) {
                listOf(uid, pairedWith).sorted().joinToString("_")
            } else {
                null
            }
        }
    }.getOrNull()

    /**
     * Observes real-time changes to the paired device's DeviceInfo.
     */
    fun getPairedDeviceInfo(): Flow<DeviceInfo?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val currentUserPairedWithRef = usersRef.child(uid).child("pairedWith")
        var targetUserListener: ValueEventListener? = null
        var targetUserRef: DatabaseReference? = null

        val currentListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val pairedWith = snapshot.getValue(String::class.java)

                // Clean up previous paired user listener if any
                targetUserListener?.let { targetUserRef?.removeEventListener(it) }
                targetUserListener = null
                targetUserRef = null

                if (pairedWith.isNullOrEmpty()) {
                    trySend(null)
                } else {
                    val ref = usersRef.child(pairedWith)
                    targetUserRef = ref

                    val newListener = object : ValueEventListener {
                        override fun onDataChange(targetSnapshot: DataSnapshot) {
                            if (!targetSnapshot.exists()) {
                                trySend(null)
                                return
                            }

                            val deviceInfo = DeviceInfo(
                                userId = targetSnapshot.child("userId").getValue(String::class.java) ?: pairedWith,
                                deviceName = targetSnapshot.child("deviceName").getValue(String::class.java) ?: "",
                                deviceModel = targetSnapshot.child("deviceModel").getValue(String::class.java) ?: "",
                                androidVersion = targetSnapshot.child("androidVersion").getValue(String::class.java) ?: "",
                                fcmToken = targetSnapshot.child("fcmToken").getValue(String::class.java) ?: "",
                                pairedWith = targetSnapshot.child("pairedWith").getValue(String::class.java),
                                isOnline = targetSnapshot.child("isOnline").getValue(Boolean::class.java) ?: false,
                                lastSeen = targetSnapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                            )
                            trySend(deviceInfo)
                        }

                        override fun onCancelled(error: DatabaseError) {
                            close(error.toException())
                        }
                    }

                    targetUserListener = newListener
                    ref.addValueEventListener(newListener)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        currentUserPairedWithRef.addValueEventListener(currentListener)

        awaitClose {
            currentUserPairedWithRef.removeEventListener(currentListener)
            targetUserListener?.let { targetUserRef?.removeEventListener(it) }
        }
    }

    /**
     * Removes pairing from both the current device and the paired device,
     * and clears any pair and signaling data.
     */
    suspend fun unpair(): Result<Unit> = runCatching {
        val currentUserId = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")

        val currentSnapshot = usersRef.child(currentUserId).get().await()
        val pairedWith = currentSnapshot.child("pairedWith").getValue(String::class.java)
        val pairId = currentSnapshot.child("pairId").getValue(String::class.java)
            ?: pairedWith?.let { listOf(currentUserId, it).sorted().joinToString("_") }

        val updates = hashMapOf<String, Any?>(
            "$USERS_PATH/$currentUserId/pairedWith" to null,
            "$USERS_PATH/$currentUserId/pairId" to null
        )

        if (!pairedWith.isNullOrEmpty()) {
            updates["$USERS_PATH/$pairedWith/pairedWith"] = null
            updates["$USERS_PATH/$pairedWith/pairId"] = null
        }

        if (!pairId.isNullOrEmpty()) {
            updates["$PAIRS_PATH/$pairId"] = null
            updates["$SIGNALING_PATH/$pairId"] = null
        }

        rootRef.updateChildren(updates).await()
    }
}
