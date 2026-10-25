package com.remotephone.data.model

data class DeviceInfo(
    val userId: String = "",
    val deviceName: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val fcmToken: String = "",
    val pairedWith: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "deviceName" to deviceName,
        "deviceModel" to deviceModel,
        "androidVersion" to androidVersion,
        "fcmToken" to fcmToken,
        "pairedWith" to pairedWith,
        "isOnline" to isOnline,
        "lastSeen" to lastSeen
    )
}
