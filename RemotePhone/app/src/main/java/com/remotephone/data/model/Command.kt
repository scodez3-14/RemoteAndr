package com.remotephone.data.model

enum class CommandType {
    OPEN_APP,
    SCREENSHOT,
    GET_BATTERY,
    GET_LOCATION,
    RING_PHONE,
    GET_NOTIFICATIONS,
    LOCK_SCREEN,
    GET_CLIPBOARD,
    START_MIRROR,
    STOP_MIRROR
}

data class Command(
    val id: String = "",
    val type: CommandType = CommandType.SCREENSHOT,
    val payload: Map<String, String> = emptyMap(),
    val sentBy: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = CommandStatus.PENDING
) {
    object Status {
        const val PENDING = "PENDING"
        const val EXECUTING = "EXECUTING"
        const val COMPLETED = "COMPLETED"
        const val FAILED = "FAILED"
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "type" to type.name,
        "payload" to payload,
        "sentBy" to sentBy,
        "timestamp" to timestamp,
        "status" to status
    )
}

object CommandStatus {
    const val PENDING = "PENDING"
    const val EXECUTING = "EXECUTING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
}
