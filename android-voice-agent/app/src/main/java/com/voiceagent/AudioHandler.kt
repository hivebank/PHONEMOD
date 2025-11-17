package com.voiceagent

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * Handles audio capture from phone calls and playback of AI responses.
 * Uses PCM16 format at 16kHz mono for compatibility with OpenAI Realtime API.
 */
class AudioHandler(private val listener: AudioCaptureListener) {

    companion object {
        private const val TAG = "VoiceAgent"

        // Audio format constants - CRITICAL: Must match OpenAI Realtime API requirements
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Buffer size - 100ms chunks for low latency
        private const val BUFFER_SIZE_MS = 100
        private const val BUFFER_SIZE = SAMPLE_RATE * BUFFER_SIZE_MS / 1000 * 2 // 2 bytes per sample
    }

    interface AudioCaptureListener {
        fun onAudioCaptured(audioData: ByteArray)
        fun onError(error: String)
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isCapturing = false
    private val captureScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Queue for audio playback
    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    private var isPlaybackRunning = false

    /**
     * Start capturing audio from the phone call.
     * Uses VOICE_CALL audio source to capture both sides of the conversation.
     */
    fun startCapture() {
        if (isCapturing) {
            Log.w(TAG, "Audio capture already running")
            return
        }

        try {
            // Calculate buffer size
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                listener.onError("Invalid audio buffer size")
                return
            }

            val bufferSize = maxOf(minBufferSize, BUFFER_SIZE)
            Log.d(TAG, "Audio buffer size: $bufferSize bytes")

            // Initialize AudioRecord with VOICE_CALL source
            // This captures audio from the phone call
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Use VOICE_COMMUNICATION for call audio
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                listener.onError("Failed to initialize AudioRecord")
                audioRecord?.release()
                audioRecord = null
                return
            }

            // Initialize AudioTrack for playback
            initAudioTrack()

            // Start recording
            audioRecord?.startRecording()
            isCapturing = true

            Log.d(TAG, "Audio capture started successfully")

            // Start capture loop in coroutine
            captureScope.launch {
                captureLoop()
            }

            // Start playback loop
            captureScope.launch {
                playbackLoop()
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Missing audio recording permission", e)
            listener.onError("Missing audio recording permission: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio capture", e)
            listener.onError("Error starting capture: ${e.message}")
        }
    }

    /**
     * Initialize AudioTrack for playing AI responses.
     */
    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG_OUT,
                AUDIO_FORMAT
            )

            val bufferSize = maxOf(minBufferSize, BUFFER_SIZE)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioTrack")
                audioTrack?.release()
                audioTrack = null
                return
            }

            audioTrack?.play()
            isPlaybackRunning = true
            Log.d(TAG, "AudioTrack initialized and playing")

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }

    /**
     * Continuous loop that captures audio and sends it to the listener.
     */
    private suspend fun captureLoop() {
        val buffer = ByteArray(BUFFER_SIZE)

        while (isCapturing && audioRecord != null) {
            try {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (bytesRead > 0) {
                    // Copy only the actual data read
                    val audioData = buffer.copyOf(bytesRead)

                    // Send to listener (which will forward to WebSocket)
                    withContext(Dispatchers.Main) {
                        listener.onAudioCaptured(audioData)
                    }
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.e(TAG, "AudioRecord invalid operation")
                    break
                } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "AudioRecord bad value")
                    break
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in capture loop", e)
                withContext(Dispatchers.Main) {
                    listener.onError("Capture error: ${e.message}")
                }
                break
            }
        }

        Log.d(TAG, "Capture loop ended")
    }

    /**
     * Continuous loop that plays audio from the queue.
     */
    private suspend fun playbackLoop() {
        while (isPlaybackRunning && audioTrack != null) {
            try {
                // Take audio data from queue (blocks if empty)
                val audioData = withContext(Dispatchers.IO) {
                    playbackQueue.take()
                }

                // Write to AudioTrack
                val written = audioTrack?.write(audioData, 0, audioData.size) ?: 0

                if (written < 0) {
                    Log.e(TAG, "Error writing to AudioTrack: $written")
                }

            } catch (e: InterruptedException) {
                Log.d(TAG, "Playback loop interrupted")
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in playback loop", e)
            }
        }

        Log.d(TAG, "Playback loop ended")
    }

    /**
     * Play audio received from the AI backend.
     * Audio data should be PCM16, 16kHz, mono.
     */
    fun playAudio(audioData: ByteArray) {
        if (!isPlaybackRunning || audioTrack == null) {
            Log.w(TAG, "AudioTrack not ready for playback")
            return
        }

        try {
            // Add to playback queue
            playbackQueue.offer(audioData)
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing audio for playback", e)
        }
    }

    /**
     * Stop capturing audio.
     */
    fun stopCapture() {
        Log.d(TAG, "Stopping audio capture")
        isCapturing = false
        isPlaybackRunning = false

        audioRecord?.stop()
        audioTrack?.stop()
    }

    /**
     * Release all audio resources.
     */
    fun release() {
        Log.d(TAG, "Releasing audio resources")

        stopCapture()
        captureScope.cancel()

        audioRecord?.release()
        audioRecord = null

        audioTrack?.release()
        audioTrack = null

        playbackQueue.clear()
    }
}
