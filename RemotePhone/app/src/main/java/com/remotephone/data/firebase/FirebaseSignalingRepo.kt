package com.remotephone.data.firebase

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSignalingRepo @Inject constructor(
    private val database: FirebaseDatabase = Firebase.database
) {

    companion object {
        private const val ROOT_PATH = "remotephone"
        private const val SIGNALING_PATH = "signaling"
        private const val OFFER_PATH = "offer"
        private const val ANSWER_PATH = "answer"
        private const val CANDIDATES_PATH = "candidates"
        private const val OFFERER_NODE = "offerer"
        private const val ANSWERER_NODE = "answerer"
    }

    private val signalingRef get() = database.reference.child(ROOT_PATH).child(SIGNALING_PATH)

    /**
     * Sends the WebRTC offer SDP to remotephone/signaling/{pairId}/offer.
     */
    suspend fun sendOffer(pairId: String, sdp: String): Result<Unit> = runCatching {
        signalingRef.child(pairId).child(OFFER_PATH).setValue(sdp).await()
    }

    /**
     * Sends the WebRTC answer SDP to remotephone/signaling/{pairId}/answer.
     */
    suspend fun sendAnswer(pairId: String, sdp: String): Result<Unit> = runCatching {
        signalingRef.child(pairId).child(ANSWER_PATH).setValue(sdp).await()
    }

    /**
     * Observes incoming SDP offer on remotephone/signaling/{pairId}/offer.
     */
    fun observeOffer(pairId: String): Flow<String?> = callbackFlow {
        val offerRef = signalingRef.child(pairId).child(OFFER_PATH)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sdp = snapshot.getValue(String::class.java)
                trySend(sdp)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        offerRef.addValueEventListener(listener)

        awaitClose {
            offerRef.removeEventListener(listener)
        }
    }

    /**
     * Observes incoming SDP answer on remotephone/signaling/{pairId}/answer.
     */
    fun observeAnswer(pairId: String): Flow<String?> = callbackFlow {
        val answerRef = signalingRef.child(pairId).child(ANSWER_PATH)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sdp = snapshot.getValue(String::class.java)
                trySend(sdp)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        answerRef.addValueEventListener(listener)

        awaitClose {
            answerRef.removeEventListener(listener)
        }
    }

    /**
     * Sends an ICE candidate to the appropriate node based on the sender role.
     * @param isOfferer true if the local sender is the offerer, false if answerer.
     */
    suspend fun sendIceCandidate(pairId: String, isOfferer: Boolean, candidate: String): Result<Unit> = runCatching {
        val node = if (isOfferer) OFFERER_NODE else ANSWERER_NODE
        signalingRef.child(pairId)
            .child(CANDIDATES_PATH)
            .child(node)
            .push()
            .setValue(candidate)
            .await()
    }

    /**
     * Observes incoming ICE candidates from the remote peer.
     * @param isOfferer true if local client is the offerer (which observes candidates from answerer),
     *                  false if local client is answerer (observes candidates from offerer).
     */
    fun observeIceCandidates(pairId: String, isOfferer: Boolean): Flow<String> = callbackFlow {
        val remoteNode = if (isOfferer) ANSWERER_NODE else OFFERER_NODE
        val candidatesRef = signalingRef.child(pairId)
            .child(CANDIDATES_PATH)
            .child(remoteNode)

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val candidate = snapshot.getValue(String::class.java)
                if (candidate != null) {
                    trySend(candidate)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        candidatesRef.addChildEventListener(listener)

        awaitClose {
            candidatesRef.removeEventListener(listener)
        }
    }

    /**
     * Clears all signaling data for the pair session (offer, answer, candidates).
     */
    suspend fun clearSignaling(pairId: String): Result<Unit> = runCatching {
        signalingRef.child(pairId).removeValue().await()
    }
}
