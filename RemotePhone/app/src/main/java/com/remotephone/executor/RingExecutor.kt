package com.remotephone.executor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.remotephone.data.model.Command
import com.remotephone.data.model.CommandResult
import com.remotephone.data.model.ResultType

/**
 * Executor that rings the phone at maximum volume for 30 seconds.
 * Useful for finding a lost/misplaced phone.
 */
class RingExecutor(private val context: Context) : CommandExecutor {

    companion object {
        private const val TAG = "RingExecutor"
        private const val RING_DURATION_MS = 30_000L
    }

    private var mediaPlayer: MediaPlayer? = null
    private var previousVolume: Int = 0

    override suspend fun execute(command: Command): CommandResult {
        return try {
            val durationMs = command.payload["duration"]?.toLongOrNull() ?: RING_DURATION_MS

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Save current volume and set to max
            previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            // Get default alarm ringtone
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // Play the ringtone
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                setDataSource(context, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }

            Log.d(TAG, "Ringing phone at max volume for ${durationMs / 1000}s")

            // Stop after duration
            Handler(Looper.getMainLooper()).postDelayed({
                stopRinging(audioManager)
            }, durationMs)

            CommandResult(
                commandId = command.id,
                type = ResultType.TEXT,
                data = "🔔 Phone is ringing at maximum volume for ${durationMs / 1000} seconds!"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ring phone", e)
            CommandResult(
                commandId = command.id,
                type = ResultType.TEXT,
                data = "Error: ${e.message}"
            )
        }
    }

    private fun stopRinging(audioManager: AudioManager) {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
            // Restore previous volume
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
            Log.d(TAG, "Stopped ringing, volume restored")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ring", e)
        }
    }
}
