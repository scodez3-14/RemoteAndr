package com.remotephone.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Screen capturer for Phone B (controlled side).
 * Captures screen output via MediaProjection and creates a WebRTC VideoTrack.
 */
class ScreenCapturer(private val context: Context) {

    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoTrack: VideoTrack? = null

    private var currentWidth = 1080
    private var currentHeight = 1920
    private var currentFps = 30

    /**
     * Starts capturing the screen using the permission Intent and creates a [VideoTrack].
     *
     * @param mediaProjectionData The permission result intent from MediaProjectionManager.
     * @param factory The initialized PeerConnectionFactory.
     * @param eglBase The shared EglBase instance for OpenGL hardware texture rendering.
     * @param width The capture width in pixels.
     * @param height The capture height in pixels.
     * @param fps The capture frame rate in frames per second.
     * @return The resulting [VideoTrack] containing screen capture frames.
     */
    fun startCapture(
        mediaProjectionData: Intent,
        factory: PeerConnectionFactory,
        eglBase: EglBase,
        width: Int,
        height: Int,
        fps: Int
    ): VideoTrack {
        stopCapture()

        currentWidth = width
        currentHeight = height
        currentFps = fps

        Log.d(TAG, "Starting screen capture: ${width}x${height} @ ${fps}fps")

        val capturer = ScreenCapturerAndroid(mediaProjectionData, object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection stopped by system or user")
                stopCapture()
            }
        })
        videoCapturer = capturer

        // Create SurfaceTextureHelper on a dedicated thread with shared EGL context
        val helper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext)
        surfaceTextureHelper = helper

        // Create VideoSource with isScreencast = true for screencasting optimizations
        val source = factory.createVideoSource(true)
        videoSource = source

        // Initialize the capturer with helper and observer
        capturer.initialize(helper, context.applicationContext, source.capturerObserver)
        capturer.startCapture(width, height, fps)

        // Create VideoTrack from the video source
        val track = factory.createVideoTrack(VIDEO_TRACK_ID, source)
        track.setEnabled(true)
        videoTrack = track

        return track
    }

    /**
     * Dynamically adjusts the capture resolution and frame rate for adaptive bitrate / network condition adaptation.
     */
    fun changeCaptureFormat(width: Int, height: Int, fps: Int) {
        currentWidth = width
        currentHeight = height
        currentFps = fps
        Log.d(TAG, "Changing capture format: ${width}x${height} @ ${fps}fps")
        try {
            videoCapturer?.changeCaptureFormat(width, height, fps)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change capture format", e)
        }
    }

    /**
     * Stops screen capturing and cleanly releases all resources.
     */
    fun stopCapture() {
        Log.d(TAG, "Stopping screen capture and cleaning up resources")
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VideoCapturer", e)
        }

        try {
            videoCapturer?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing VideoCapturer", e)
        }
        videoCapturer = null

        try {
            videoTrack?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing VideoTrack", e)
        }
        videoTrack = null

        try {
            videoSource?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing VideoSource", e)
        }
        videoSource = null

        try {
            surfaceTextureHelper?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing SurfaceTextureHelper", e)
        }
        surfaceTextureHelper = null
    }

    companion object {
        private const val TAG = "ScreenCapturer"
        const val VIDEO_TRACK_ID = "screen_video_track"
    }
}
