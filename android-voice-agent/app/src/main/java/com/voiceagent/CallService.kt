package com.voiceagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * InCallService implementation that automatically answers incoming calls
 * and manages audio streaming to the AI backend.
 */
class CallService : InCallService() {

    companion object {
        private const val TAG = "VoiceAgent"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "VoiceAgentChannel"
    }

    private var currentCall: Call? = null
    private var audioHandler: AudioHandler? = null
    private var webSocketClient: WebSocketClient? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            handleCallStateChange(call, state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CallService created")
        createNotificationChannel()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.details.state}")

        currentCall = call
        call.registerCallback(callCallback)

        // Start foreground service to prevent Android from killing us
        startForeground(NOTIFICATION_ID, createNotification("Call in progress"))

        // Auto-answer the call
        when (call.details.state) {
            Call.STATE_RINGING -> {
                Log.d(TAG, "Auto-answering incoming call")
                call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            }
            Call.STATE_ACTIVE -> {
                // Call already active, start streaming
                startAudioStreaming(call)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")

        call.unregisterCallback(callCallback)
        stopAudioStreaming()
        currentCall = null

        // Stop foreground service
        stopForeground(true)
    }

    private fun handleCallStateChange(call: Call, state: Int) {
        Log.d(TAG, "Call state changed: $state")

        when (state) {
            Call.STATE_ACTIVE -> {
                Log.d(TAG, "Call is now active, starting audio streaming")
                startAudioStreaming(call)
            }
            Call.STATE_DISCONNECTED -> {
                Log.d(TAG, "Call disconnected")
                stopAudioStreaming()
            }
            Call.STATE_RINGING -> {
                Log.d(TAG, "Call is ringing, will auto-answer")
                call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
            }
        }
    }

    private fun startAudioStreaming(call: Call) {
        if (audioHandler != null) {
            Log.d(TAG, "Audio streaming already started")
            return
        }

        serviceScope.launch {
            try {
                // Get backend URL from preferences
                val prefs = getSharedPreferences("VoiceAgentPrefs", Context.MODE_PRIVATE)
                val backendUrl = prefs.getString("backend_url", "") ?: ""

                if (backendUrl.isEmpty()) {
                    Log.e(TAG, "Backend URL not configured")
                    return@launch
                }

                // Initialize WebSocket client
                webSocketClient = WebSocketClient(backendUrl, object : WebSocketClient.WebSocketListener {
                    override fun onConnected() {
                        Log.d(TAG, "WebSocket connected, starting audio capture")
                        startAudioCapture()
                    }

                    override fun onAudioReceived(audioData: ByteArray) {
                        // Play AI response audio
                        audioHandler?.playAudio(audioData)
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "WebSocket error: $error")
                    }

                    override fun onDisconnected() {
                        Log.d(TAG, "WebSocket disconnected")
                    }
                })

                // Connect to backend
                webSocketClient?.connect()

            } catch (e: Exception) {
                Log.e(TAG, "Error starting audio streaming", e)
            }
        }
    }

    private fun startAudioCapture() {
        try {
            // Initialize audio handler
            audioHandler = AudioHandler(object : AudioHandler.AudioCaptureListener {
                override fun onAudioCaptured(audioData: ByteArray) {
                    // Send captured audio to backend via WebSocket
                    webSocketClient?.sendAudio(audioData)
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Audio capture error: $error")
                }
            })

            // Start capturing audio
            audioHandler?.startCapture()
            Log.d(TAG, "Audio capture started")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio capture", e)
        }
    }

    private fun stopAudioStreaming() {
        Log.d(TAG, "Stopping audio streaming")

        // Stop audio handler
        audioHandler?.stopCapture()
        audioHandler?.release()
        audioHandler = null

        // Disconnect WebSocket
        webSocketClient?.disconnect()
        webSocketClient = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Agent Call Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for active voice agent calls"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallService destroyed")
        stopAudioStreaming()
        serviceScope.cancel()
    }
}
