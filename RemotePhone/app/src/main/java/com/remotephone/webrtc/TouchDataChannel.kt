package com.remotephone.webrtc

import android.util.Log
import com.remotephone.data.model.TouchEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Handles serialization, transmission, and deserialization of [TouchEvent] messages
 * across a WebRTC [DataChannel].
 */
class TouchDataChannel {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    private var onTouchEventCallback: ((TouchEvent) -> Unit)? = null

    /**
     * Sends a [TouchEvent] over the provided [DataChannel].
     *
     * @param dataChannel The active WebRTC data channel.
     * @param event The touch or navigation event to send.
     * @return True if sent successfully, false otherwise.
     */
    fun sendTouchEvent(dataChannel: DataChannel, event: TouchEvent): Boolean {
        if (dataChannel.state() != DataChannel.State.OPEN) {
            Log.w(TAG, "Cannot send TouchEvent: DataChannel is not OPEN (current state: ${dataChannel.state()})")
            return false
        }

        return try {
            val jsonString = json.encodeToString(event)
            val byteBuffer = ByteBuffer.wrap(jsonString.toByteArray(StandardCharsets.UTF_8))
            val buffer = DataChannel.Buffer(byteBuffer, false)
            dataChannel.send(buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing or sending TouchEvent: $event", e)
            false
        }
    }

    /**
     * Registers a callback listener and attaches a [DataChannel.Observer] to incoming messages.
     *
     * @param dataChannel The active WebRTC data channel to observe.
     * @param callback The callback invoked when a deserialized [TouchEvent] is received.
     */
    fun attachToDataChannel(dataChannel: DataChannel, callback: (TouchEvent) -> Unit) {
        setOnTouchEventReceived(callback)
        dataChannel.registerObserver(createObserver())
    }

    /**
     * Sets the callback to be triggered when a [TouchEvent] is received.
     */
    fun setOnTouchEventReceived(callback: (TouchEvent) -> Unit) {
        this.onTouchEventCallback = callback
    }

    /**
     * Creates a [DataChannel.Observer] that handles incoming messages and decodes [TouchEvent].
     */
    fun createObserver(): DataChannel.Observer {
        return object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {
                // Buffer level changed
            }

            override fun onStateChange() {
                // Observer state changes if needed
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                try {
                    val data = buffer.data
                    val bytes = ByteArray(data.remaining())
                    data.get(bytes)
                    val message = String(bytes, StandardCharsets.UTF_8)
                    val event = json.decodeFromString<TouchEvent>(message)
                    onTouchEventCallback?.invoke(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode incoming TouchEvent payload", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "TouchDataChannel"
        const val CHANNEL_LABEL = "touch"
    }
}
