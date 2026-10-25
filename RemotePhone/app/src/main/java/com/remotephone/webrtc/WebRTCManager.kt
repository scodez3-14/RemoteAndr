package com.remotephone.webrtc

import android.content.Context
import android.util.Log
import com.remotephone.data.firebase.FirebaseSignalingRepo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack

/**
 * State representing WebRTC PeerConnection connection status.
 */
enum class ConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}

/**
 * Core WebRTC Manager handling PeerConnection lifecycle, signaling negotiation,
 * video track delivery, and data channels for remote control.
 */
class WebRTCManager(
    private val context: Context,
    private val signalingRepo: FirebaseSignalingRepo,
    private val pairId: String
) {

    private val coroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Coroutine unhandled error in WebRTCManager", throwable)
        }
    )

    private var signalingJobs = mutableListOf<Job>()

    // EGL Base for hardware acceleration
    val eglBase: EglBase = EglBase.create()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    // State flows exposed to callers
    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _dataChannel = MutableStateFlow<DataChannel?>(null)
    val dataChannel: StateFlow<DataChannel?> = _dataChannel.asStateFlow()

    private var localDataChannel: DataChannel? = null
    private var isOfferer: Boolean = false

    init {
        initializePeerConnectionFactory()
    }

    /**
     * Initializes the WebRTC PeerConnectionFactory with hardware codecs and EGL context.
     */
    private fun initializePeerConnectionFactory() {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true, // enableIntelVp8Encoder
            true  // enableH264HighProfile
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    /**
     * Creates and initializes a [PeerConnection] with ICE servers from [IceServerProvider].
     */
    fun createPeerConnection(): PeerConnection? {
        val factory = peerConnectionFactory ?: return null

        val rtcConfig = PeerConnection.RTCConfiguration(IceServerProvider.getIceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 2
        }

        val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                Log.d(TAG, "onSignalingChange: $newState")
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "onIceConnectionChange: $newState")
                when (newState) {
                    PeerConnection.IceConnectionState.NEW,
                    PeerConnection.IceConnectionState.CHECKING -> _connectionState.value = ConnectionState.CONNECTING
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> _connectionState.value = ConnectionState.CONNECTED
                    PeerConnection.IceConnectionState.DISCONNECTED -> _connectionState.value = ConnectionState.DISCONNECTED
                    PeerConnection.IceConnectionState.FAILED -> _connectionState.value = ConnectionState.FAILED
                    PeerConnection.IceConnectionState.CLOSED -> _connectionState.value = ConnectionState.IDLE
                    null -> {}
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d(TAG, "onConnectionChange: $newState")
                when (newState) {
                    PeerConnection.PeerConnectionState.NEW,
                    PeerConnection.PeerConnectionState.CONNECTING -> _connectionState.value = ConnectionState.CONNECTING
                    PeerConnection.PeerConnectionState.CONNECTED -> _connectionState.value = ConnectionState.CONNECTED
                    PeerConnection.PeerConnectionState.DISCONNECTED -> _connectionState.value = ConnectionState.DISCONNECTED
                    PeerConnection.PeerConnectionState.FAILED -> _connectionState.value = ConnectionState.FAILED
                    PeerConnection.PeerConnectionState.CLOSED -> _connectionState.value = ConnectionState.IDLE
                    null -> {}
                }
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "onIceGatheringChange: $newState")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    Log.d(TAG, "onIceCandidate: sending candidate ${candidate.sdpMid}")
                    coroutineScope.launch {
                        try {
                            val candidateJson = """{"sdpMid":"${candidate.sdpMid}","sdpMLineIndex":${candidate.sdpMLineIndex},"sdp":"${candidate.sdp}"}"""
                            signalingRepo.sendIceCandidate(pairId, isOfferer, candidateJson)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send ICE candidate", e)
                        }
                    }
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                Log.d(TAG, "onIceCandidatesRemoved")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "onIceConnectionReceivingChange: $receiving")
            }

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "onAddStream: ${stream?.id}")
                stream?.videoTracks?.firstOrNull()?.let { track ->
                    _remoteVideoTrack.value = track
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "onRemoveStream: ${stream?.id}")
            }

            override fun onDataChannel(channel: DataChannel?) {
                Log.d(TAG, "onDataChannel received: ${channel?.label()}")
                channel?.let {
                    _dataChannel.value = it
                }
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded")
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                Log.d(TAG, "onTrack: kind=${track?.kind()}, id=${track?.id()}")
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                }
            }

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track()
                Log.d(TAG, "onAddTrack: kind=${track?.kind()}, id=${track?.id()}")
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                }
            }

            override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) {
                Log.d(TAG, "onSelectedCandidatePairChanged: $event")
            }
        })

        peerConnection = pc
        return pc
    }

    /**
     * For OFFERER (Phone A controller):
     * 1. Creates WebRTC SDP Offer.
     * 2. Sets local description.
     * 3. Sends offer via [signalingRepo].
     * 4. Listens for remote answer and sets remote description.
     * 5. Exchanges ICE candidates.
     */
    fun startAsOfferer() {
        isOfferer = true
        if (peerConnection == null) {
            createPeerConnection()
        }
        val pc = peerConnection ?: return

        _connectionState.value = ConnectionState.CONNECTING

        // Create ordered reliable DataChannel on offerer side
        createDataChannel(TouchDataChannel.CHANNEL_LABEL)

        // Listen for remote answer
        val answerJob = coroutineScope.launch {
            signalingRepo.observeAnswer(pairId)
                .catch { e -> Log.e(TAG, "Error observing answer", e) }
                .collect { sdpString ->
                    if (sdpString != null) {
                        Log.d(TAG, "Received remote SDP answer")
                        val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
                        pc.setRemoteDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                Log.d(TAG, "Set remote description (answer) succeeded")
                            }

                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "Failed to set remote description (answer): $error")
                                _connectionState.value = ConnectionState.FAILED
                            }
                        }, sdp)
                    }
                }
        }
        signalingJobs.add(answerJob)

        // Listen for remote ICE candidates
        observeRemoteIceCandidates(pc)

        // Create offer with media constraints
        createOffer()
    }

    /**
     * Creates SDP Offer and sends it via signaling repo.
     */
    fun createOffer() {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                Log.d(TAG, "SDP offer created successfully")
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Set local description (offer) succeeded, sending to signaling")
                        coroutineScope.launch {
                            try {
                                signalingRepo.sendOffer(pairId, desc.description)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to send offer to signaling", e)
                                _connectionState.value = ConnectionState.FAILED
                            }
                        }
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Failed to set local description (offer): $error")
                        _connectionState.value = ConnectionState.FAILED
                    }
                }, desc)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
                _connectionState.value = ConnectionState.FAILED
            }
        }, constraints)
    }

    /**
     * For ANSWERER (Phone B controlled):
     * 1. Listens for SDP offer via [signalingRepo].
     * 2. Sets remote description.
     * 3. Creates SDP answer and sets local description.
     * 4. Sends answer back via [signalingRepo].
     * 5. Exchanges ICE candidates.
     */
    fun startAsAnswerer(localVideoTrack: VideoTrack? = null) {
        isOfferer = false
        if (peerConnection == null) {
            createPeerConnection()
        }
        val pc = peerConnection ?: return

        _connectionState.value = ConnectionState.CONNECTING

        if (localVideoTrack != null) {
            addVideoTrack(localVideoTrack)
        }

        // Listen for remote ICE candidates
        observeRemoteIceCandidates(pc)

        // Listen for remote offer
        val offerJob = coroutineScope.launch {
            signalingRepo.observeOffer(pairId)
                .catch { e -> Log.e(TAG, "Error observing offer", e) }
                .collect { sdpString ->
                    if (sdpString != null) {
                        Log.d(TAG, "Received remote SDP offer")
                        val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpString)
                        handleRemoteOffer(pc, sdp)
                    }
                }
        }
        signalingJobs.add(offerJob)
    }

    private fun handleRemoteOffer(pc: PeerConnection, sdp: SessionDescription) {
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "Set remote description (offer) succeeded, creating answer")
                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                }
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        if (answerDesc == null) return
                        Log.d(TAG, "SDP answer created successfully")
                        pc.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                Log.d(TAG, "Set local description (answer) succeeded, sending to signaling")
                                coroutineScope.launch {
                                    try {
                                        signalingRepo.sendAnswer(pairId, answerDesc.description)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to send answer to signaling", e)
                                        _connectionState.value = ConnectionState.FAILED
                                    }
                                }
                            }

                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "Failed to set local description (answer): $error")
                                _connectionState.value = ConnectionState.FAILED
                            }
                        }, answerDesc)
                    }

                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "Failed to create answer: $error")
                        _connectionState.value = ConnectionState.FAILED
                    }
                }, constraints)
            }

            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote description (offer): $error")
                _connectionState.value = ConnectionState.FAILED
            }
        }, sdp)
    }

    private fun observeRemoteIceCandidates(pc: PeerConnection) {
        // Observe the OTHER side's candidates (if we're offerer, observe answerer's candidates)
        val iceJob = coroutineScope.launch {
            signalingRepo.observeIceCandidates(pairId, !isOfferer)
                .catch { e -> Log.e(TAG, "Error observing ICE candidates", e) }
                .collect { candidateJson ->
                    try {
                        // Parse JSON: {"sdpMid":"...","sdpMLineIndex":0,"sdp":"..."}
                        val json = org.json.JSONObject(candidateJson)
                        val candidate = IceCandidate(
                            json.getString("sdpMid"),
                            json.getInt("sdpMLineIndex"),
                            json.getString("sdp")
                        )
                        Log.d(TAG, "Received remote ICE candidate: ${candidate.sdpMid}")
                        pc.addIceCandidate(candidate)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse ICE candidate", e)
                    }
                }
        }
        signalingJobs.add(iceJob)
    }

    /**
     * Adds a local screen capture video track to the [PeerConnection].
     */
    fun addVideoTrack(videoTrack: VideoTrack) {
        val pc = peerConnection ?: return
        val streamIds = listOf("screen_stream")
        pc.addTrack(videoTrack, streamIds)
        Log.d(TAG, "Added local video track: ${videoTrack.id()} with stream ${streamIds[0]}")
    }

    /**
     * Creates a reliable ordered [DataChannel] for touch events and low-latency interaction.
     */
    fun createDataChannel(label: String = TouchDataChannel.CHANNEL_LABEL): DataChannel? {
        val pc = peerConnection ?: return null
        val init = DataChannel.Init().apply {
            ordered = true
            negotiated = false
        }
        val channel = pc.createDataChannel(label, init)
        localDataChannel = channel
        _dataChannel.value = channel
        Log.d(TAG, "Created local DataChannel: $label")
        return channel
    }

    /**
     * Disconnects active WebRTC session and cleans up PeerConnection, Factory, and EGL contexts.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebRTC session...")

        // Cancel all ongoing signaling collection jobs
        signalingJobs.forEach { it.cancel() }
        signalingJobs.clear()

        _connectionState.value = ConnectionState.DISCONNECTED

        try {
            localDataChannel?.unregisterObserver()
            localDataChannel?.close()
            localDataChannel?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing localDataChannel", e)
        }
        localDataChannel = null
        _dataChannel.value = null

        try {
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing PeerConnection", e)
        }
        peerConnection = null

        try {
            peerConnectionFactory?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing PeerConnectionFactory", e)
        }
        peerConnectionFactory = null

        try {
            eglBase.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing EglBase", e)
        }

        _remoteVideoTrack.value = null
        _connectionState.value = ConnectionState.IDLE
        Log.d(TAG, "WebRTC disconnect completed")
    }

    /**
     * Helper base class for [SdpObserver] callbacks.
     */
    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }

    companion object {
        private const val TAG = "WebRTCManager"
    }
}
