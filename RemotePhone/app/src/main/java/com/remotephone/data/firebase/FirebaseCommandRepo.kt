package com.remotephone.data.firebase

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.ktx.storage
import com.remotephone.data.model.Command
import com.remotephone.data.model.CommandResult
import com.remotephone.data.model.CommandStatus
import com.remotephone.data.model.CommandType
import com.remotephone.data.model.ResultType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCommandRepo @Inject constructor(
    private val database: FirebaseDatabase = Firebase.database,
    private val storage: FirebaseStorage = Firebase.storage
) {

    companion object {
        private const val ROOT_PATH = "remotephone"
        private const val COMMANDS_PATH = "commands"
        private const val RESULTS_PATH = "results"
        private const val SCREENSHOTS_PATH = "screenshots"
    }

    private val rootRef get() = database.reference.child(ROOT_PATH)
    private val commandsRef get() = rootRef.child(COMMANDS_PATH)
    private val resultsRef get() = rootRef.child(RESULTS_PATH)

    /**
     * Sends a command to the specified pair session under remotephone/commands/{pairId}/{commandId}.
     * Returns the commandId.
     */
    suspend fun sendCommand(pairId: String, command: Command): String {
        val targetRef = commandsRef.child(pairId)
        val commandId = if (command.id.isNotEmpty()) command.id else targetRef.push().key ?: UUID.randomUUID().toString()
        val finalCommand = command.copy(
            id = commandId,
            timestamp = if (command.timestamp == 0L) System.currentTimeMillis() else command.timestamp
        )

        targetRef.child(commandId).setValue(finalCommand.toMap()).await()
        return commandId
    }

    /**
     * Observes incoming commands for the given pairId in real-time.
     */
    fun observeCommands(pairId: String): Flow<Command> = callbackFlow {
        val pairCommandsRef = commandsRef.child(pairId)

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.toCommand()?.let { command ->
                    trySend(command)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // If a command status or details update, emit the updated command
                snapshot.toCommand()?.let { command ->
                    trySend(command)
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        pairCommandsRef.addChildEventListener(listener)

        awaitClose {
            pairCommandsRef.removeEventListener(listener)
        }
    }

    /**
     * Updates the execution status of a specific command.
     */
    suspend fun updateCommandStatus(
        pairId: String,
        commandId: String,
        status: String
    ): Result<Unit> = runCatching {
        commandsRef.child(pairId).child(commandId).child("status").setValue(status).await()
    }

    /**
     * Sends execution results for a command to remotephone/results/{pairId}/{resultId}.
     */
    suspend fun sendResult(
        pairId: String,
        commandId: String,
        result: CommandResult
    ): Result<Unit> = runCatching {
        val pairResultsRef = resultsRef.child(pairId)
        val resultId = if (result.id.isNotEmpty()) result.id else pairResultsRef.push().key ?: UUID.randomUUID().toString()
        val finalResult = result.copy(
            id = resultId,
            commandId = commandId,
            timestamp = if (result.timestamp == 0L) System.currentTimeMillis() else result.timestamp
        )

        pairResultsRef.child(resultId).setValue(finalResult.toMap()).await()
    }

    /**
     * Observes command execution results for the given pairId in real-time.
     */
    fun observeResults(pairId: String): Flow<CommandResult> = callbackFlow {
        val pairResultsRef = resultsRef.child(pairId)

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.toCommandResult()?.let { commandResult ->
                    trySend(commandResult)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                snapshot.toCommandResult()?.let { commandResult ->
                    trySend(commandResult)
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        pairResultsRef.addChildEventListener(listener)

        awaitClose {
            pairResultsRef.removeEventListener(listener)
        }
    }

    /**
     * Uploads screenshot bytes to Firebase Storage under remotephone/screenshots/{filename}
     * and returns the public download URL.
     */
    suspend fun uploadScreenshot(bitmap: ByteArray): String {
        val filename = "screenshot_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
        val screenshotRef = storage.reference
            .child(ROOT_PATH)
            .child(SCREENSHOTS_PATH)
            .child(filename)

        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        screenshotRef.putBytes(bitmap, metadata).await()
        val downloadUri = screenshotRef.downloadUrl.await()
        return downloadUri.toString()
    }

    private fun DataSnapshot.toCommand(): Command? {
        if (!exists()) return null
        val id = child("id").getValue(String::class.java) ?: key ?: return null
        val typeStr = child("type").getValue(String::class.java) ?: "SCREENSHOT"
        val type = try {
            CommandType.valueOf(typeStr)
        } catch (_: Exception) {
            CommandType.SCREENSHOT
        }
        val payload = mutableMapOf<String, String>()
        child("payload").children.forEach { childSnapshot ->
            val k = childSnapshot.key
            val v = childSnapshot.getValue(String::class.java)
            if (k != null && v != null) {
                payload[k] = v
            }
        }
        val sentBy = child("sentBy").getValue(String::class.java) ?: ""
        val timestamp = child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
        val status = child("status").getValue(String::class.java) ?: CommandStatus.PENDING

        return Command(
            id = id,
            type = type,
            payload = payload,
            sentBy = sentBy,
            timestamp = timestamp,
            status = status
        )
    }

    private fun DataSnapshot.toCommandResult(): CommandResult? {
        if (!exists()) return null
        val id = child("id").getValue(String::class.java) ?: key ?: return null
        val commandId = child("commandId").getValue(String::class.java) ?: ""
        val typeStr = child("type").getValue(String::class.java) ?: "TEXT"
        val type = try {
            ResultType.valueOf(typeStr)
        } catch (_: Exception) {
            ResultType.TEXT
        }
        val data = child("data").getValue(String::class.java) ?: ""
        val timestamp = child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

        return CommandResult(
            id = id,
            commandId = commandId,
            type = type,
            data = data,
            timestamp = timestamp
        )
    }
}
