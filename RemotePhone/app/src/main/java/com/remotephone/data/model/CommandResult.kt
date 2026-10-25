package com.remotephone.data.model

enum class ResultType {
    SCREENSHOT,
    TEXT,
    NOTIFICATION_LIST
}

data class CommandResult(
    val id: String = "",
    val commandId: String = "",
    val type: ResultType = ResultType.TEXT,
    val data: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "commandId" to commandId,
        "type" to type.name,
        "data" to data,
        "timestamp" to timestamp
    )
}
