const WebSocket = require('ws');
const config = require('./config');
const calendar = require('./calendar-integration');

/**
 * OpenAI Realtime API client for handling voice conversations.
 * Manages WebSocket connection to OpenAI and processes audio streams.
 */

class OpenAIRealtimeClient {
  constructor(onAudioResponse, onError) {
    this.ws = null;
    this.onAudioResponse = onAudioResponse;
    this.onError = onError;
    this.isConnected = false;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 3;
  }

  /**
   * Connect to OpenAI Realtime API
   */
  async connect() {
    return new Promise((resolve, reject) => {
      try {
        const url = `wss://api.openai.com/v1/realtime?model=${config.openai.model}`;

        console.log('Connecting to OpenAI Realtime API...');

        this.ws = new WebSocket(url, {
          headers: {
            'Authorization': `Bearer ${config.openai.apiKey}`,
            'OpenAI-Beta': 'realtime=v1'
          }
        });

        this.ws.on('open', () => {
          console.log('Connected to OpenAI Realtime API');
          this.isConnected = true;
          this.reconnectAttempts = 0;
          this.initializeSession();
          resolve();
        });

        this.ws.on('message', (data) => {
          this.handleMessage(data);
        });

        this.ws.on('error', (error) => {
          console.error('OpenAI WebSocket error:', error);
          this.onError(`OpenAI error: ${error.message}`);
        });

        this.ws.on('close', (code, reason) => {
          console.log(`OpenAI WebSocket closed: ${code} - ${reason}`);
          this.isConnected = false;
          this.handleDisconnect();
        });

        // Timeout for connection
        setTimeout(() => {
          if (!this.isConnected) {
            reject(new Error('Connection timeout'));
          }
        }, 10000);

      } catch (error) {
        console.error('Error connecting to OpenAI:', error);
        reject(error);
      }
    });
  }

  /**
   * Initialize OpenAI session with configuration
   */
  initializeSession() {
    const sessionConfig = {
      type: 'session.update',
      session: {
        modalities: ['text', 'audio'],
        instructions: config.getSystemPrompt(),
        voice: config.openai.voice,
        input_audio_format: 'pcm16',
        output_audio_format: 'pcm16',
        input_audio_transcription: {
          model: 'whisper-1'
        },
        turn_detection: config.openai.turnDetection,
        tools: calendar.getFunctionDefinitions(),
        tool_choice: 'auto',
        temperature: 0.8,
        max_response_output_tokens: 4096
      }
    };

    console.log('Initializing OpenAI session...');
    this.send(sessionConfig);
  }

  /**
   * Handle incoming messages from OpenAI
   */
  handleMessage(data) {
    try {
      const message = JSON.parse(data);

      // Log event type for debugging
      if (message.type !== 'response.audio.delta') {
        console.log('OpenAI event:', message.type);
      }

      switch (message.type) {
        case 'session.created':
          console.log('Session created:', message.session.id);
          break;

        case 'session.updated':
          console.log('Session updated successfully');
          break;

        case 'conversation.item.created':
          console.log('Conversation item created');
          break;

        case 'response.audio.delta':
          // Audio response chunk - forward to client
          if (message.delta) {
            const audioBuffer = Buffer.from(message.delta, 'base64');
            this.onAudioResponse(audioBuffer);
          }
          break;

        case 'response.audio.done':
          console.log('Audio response completed');
          break;

        case 'response.function_call_arguments.done':
          // Function call completed - execute it
          this.handleFunctionCall(message);
          break;

        case 'input_audio_buffer.speech_started':
          console.log('User started speaking');
          break;

        case 'input_audio_buffer.speech_stopped':
          console.log('User stopped speaking');
          break;

        case 'conversation.item.input_audio_transcription.completed':
          console.log('Transcription:', message.transcript);
          break;

        case 'response.done':
          console.log('Response completed');
          break;

        case 'error':
          console.error('OpenAI error:', message.error);
          this.onError(`OpenAI error: ${message.error.message}`);
          break;

        default:
          // Log unknown event types for debugging
          if (message.type) {
            console.log('Unhandled event type:', message.type);
          }
      }

    } catch (error) {
      console.error('Error handling OpenAI message:', error);
    }
  }

  /**
   * Handle function calls from OpenAI
   */
  async handleFunctionCall(message) {
    try {
      const { call_id, name, arguments: argsString } = message;
      const args = JSON.parse(argsString);

      console.log(`Function call: ${name}`, args);

      // Execute function via calendar integration
      const result = await calendar.handleFunctionCall(name, args);

      // Send result back to OpenAI
      this.send({
        type: 'conversation.item.create',
        item: {
          type: 'function_call_output',
          call_id: call_id,
          output: JSON.stringify(result)
        }
      });

      // Request response generation
      this.send({
        type: 'response.create'
      });

    } catch (error) {
      console.error('Error handling function call:', error);
    }
  }

  /**
   * Send audio to OpenAI
   * @param {Buffer} audioBuffer - PCM16 audio data
   */
  sendAudio(audioBuffer) {
    if (!this.isConnected || !this.ws) {
      console.warn('Not connected to OpenAI, cannot send audio');
      return;
    }

    try {
      // Convert buffer to base64
      const audioBase64 = audioBuffer.toString('base64');

      // Send audio append event
      this.send({
        type: 'input_audio_buffer.append',
        audio: audioBase64
      });

    } catch (error) {
      console.error('Error sending audio to OpenAI:', error);
    }
  }

  /**
   * Commit audio buffer (tells OpenAI we're done sending audio for now)
   */
  commitAudio() {
    if (!this.isConnected || !this.ws) {
      return;
    }

    this.send({
      type: 'input_audio_buffer.commit'
    });
  }

  /**
   * Send message to OpenAI
   */
  send(message) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.warn('WebSocket not ready, cannot send message');
      return;
    }

    try {
      this.ws.send(JSON.stringify(message));
    } catch (error) {
      console.error('Error sending message to OpenAI:', error);
    }
  }

  /**
   * Handle disconnection
   */
  handleDisconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const delay = 2000 * this.reconnectAttempts;

      console.log(`Attempting reconnection #${this.reconnectAttempts} in ${delay}ms...`);

      setTimeout(() => {
        this.connect().catch(error => {
          console.error('Reconnection failed:', error);
        });
      }, delay);
    } else {
      console.error('Max reconnection attempts reached');
      this.onError('Lost connection to OpenAI');
    }
  }

  /**
   * Disconnect from OpenAI
   */
  disconnect() {
    console.log('Disconnecting from OpenAI...');
    this.isConnected = false;
    this.reconnectAttempts = this.maxReconnectAttempts; // Prevent reconnection

    if (this.ws) {
      this.ws.close(1000, 'Client disconnect');
      this.ws = null;
    }
  }

  /**
   * Check if connected
   */
  isReady() {
    return this.isConnected && this.ws && this.ws.readyState === WebSocket.OPEN;
  }
}

module.exports = OpenAIRealtimeClient;
