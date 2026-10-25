package com.remotephone.webrtc

import org.webrtc.PeerConnection

/**
 * Provides STUN/TURN ICE server configurations for WebRTC PeerConnection.
 */
object IceServerProvider {

    private val GOOGLE_STUN_SERVERS = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302"
    )

    /**
     * Returns a list of [PeerConnection.IceServer] configured for STUN/TURN connection traversal.
     */
    fun getIceServers(): List<PeerConnection.IceServer> {
        return GOOGLE_STUN_SERVERS.map { uri ->
            PeerConnection.IceServer.builder(uri)
                .createIceServer()
        }
    }
}
