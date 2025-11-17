package com.voiceagent

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for real-time audio streaming to the backend server.
 * Sends captured audio as binary frames and receives AI responses.
 */
class WebSocketClient(
    private val backendUrl: String,
    private val listener: WebSocketListener
) {

    companion object {
        private const val TAG = "VoiceAgent"
        private const val CONNECTION_TIMEOUT_SECONDS = 10L
        private const val PING_INTERVAL_SECONDS = 30L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_DELAY_MS = 2000L
    }

    interface WebSocketListener {
        fun onConnected()
        fun onAudioReceived(audioData: ByteArray)
        fun onError(error: String)
        fun onDisconnected()
    }

    private var webSocket: okhttp3.WebSocket? = null
    private val client: OkHttpClient
    private var isConnected = false
    private var reconnectAttempts = 0
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Configure OkHttp client with timeouts and ping/pong
        client = OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No read timeout for streaming
            .writeTimeout(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Connect to the backend WebSocket server.
     */
    fun connect() {
        if (isConnected) {
            Log.w(TAG, "WebSocket already connected")
            return
        }

        try {
            // Build WebSocket URL
            val wsUrl = if (backendUrl.startsWith("http://") || backendUrl.startsWith("https://")) {
                backendUrl.replace("http://", "ws://")
                    .replace("https://", "wss://") + "/voice"
            } else {
                "wss://$backendUrl/voice"
            }

            Log.d(TAG, "Connecting to WebSocket: $wsUrl")

            val request = Request.Builder()
                .url(wsUrl)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected")
                    isConnected = true
                    reconnectAttempts = 0
                    listener.onConnected()
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    // Text messages (e.g., JSON control messages)
                    Log.d(TAG, "Received text message: $text")
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, bytes: ByteString) {
                    // Binary messages (audio data from AI)
                    Log.d(TAG, "Received audio data: ${bytes.size} bytes")
                    listener.onAudioReceived(bytes.toByteArray())
                }

                override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code - $reason")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code - $reason")
                    isConnected = false
                    listener.onDisconnected()
                }

                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket error: ${t.message}", t)
                    isConnected = false
                    listener.onError("Connection failed: ${t.message}")
                    listener.onDisconnected()

                    // Attempt reconnection
                    attemptReconnect()
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to WebSocket", e)
            listener.onError("Connection error: ${e.message}")
        }
    }

    /**
     * Send audio data to the backend.
     * Audio should be PCM16, 16kHz, mono.
     */
    fun sendAudio(audioData: ByteArray) {
        if (!isConnected || webSocket == null) {
            Log.w(TAG, "WebSocket not connected, cannot send audio")
            return
        }

        try {
            // Send as binary frame
            val byteString = ByteString.of(*audioData)
            val sent = webSocket?.send(byteString) ?: false

            if (!sent) {
                Log.w(TAG, "Failed to send audio data (queue full?)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio", e)
            listener.onError("Send error: ${e.message}")
        }
    }

    /**
     * Send a text message (for control/metadata).
     */
    fun sendText(message: String) {
        if (!isConnected || webSocket == null) {
            Log.w(TAG, "WebSocket not connected, cannot send text")
            return
        }

        try {
            webSocket?.send(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text", e)
        }
    }

    /**
     * Disconnect from the WebSocket server.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        isConnected = false
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS // Prevent reconnection
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }

    /**
     * Attempt to reconnect with exponential backoff.
     */
    private fun attemptReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnection attempts reached")
            listener.onError("Failed to reconnect after $MAX_RECONNECT_ATTEMPTS attempts")
            return
        }

        reconnectAttempts++
        val delay = RECONNECT_DELAY_MS * reconnectAttempts

        Log.d(TAG, "Attempting reconnection #$reconnectAttempts in ${delay}ms")

        scope.launch {
            delay(delay)
            connect()
        }
    }

    /**
     * Clean up resources.
     */
    fun release() {
        disconnect()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
