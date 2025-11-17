const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const config = require('./config');
const calendar = require('./calendar-integration');
const OpenAIRealtimeClient = require('./openai-realtime');

/**
 * Voice Agent Backend Server
 *
 * This server:
 * 1. Accepts WebSocket connections from Android app
 * 2. Forwards audio to OpenAI Realtime API
 * 3. Streams AI responses back to Android
 * 4. Handles calendar integration for appointments
 */

// Initialize Express app
const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server, path: '/voice' });

// Middleware
app.use(cors());
app.use(express.json());

// Track active connections
const activeConnections = new Map();

/**
 * Health check endpoint
 */
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    activeConnections: activeConnections.size,
    config: {
      openaiModel: config.openai.model,
      voice: config.openai.voice,
      calendarEnabled: !!calendar
    }
  });
});

/**
 * Root endpoint
 */
app.get('/', (req, res) => {
  res.json({
    name: 'Voice Agent Backend',
    version: '1.0.0',
    endpoints: {
      health: '/health',
      websocket: '/voice'
    }
  });
});

/**
 * WebSocket connection handler
 */
wss.on('connection', async (clientWs, req) => {
  const clientId = `client_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  console.log(`[${clientId}] Client connected from ${req.socket.remoteAddress}`);

  // Create OpenAI client for this connection
  const openaiClient = new OpenAIRealtimeClient(
    // Audio response callback
    (audioBuffer) => {
      if (clientWs.readyState === WebSocket.OPEN) {
        clientWs.send(audioBuffer);
      }
    },
    // Error callback
    (error) => {
      console.error(`[${clientId}] OpenAI error:`, error);
      if (clientWs.readyState === WebSocket.OPEN) {
        clientWs.send(JSON.stringify({
          type: 'error',
          message: error
        }));
      }
    }
  );

  // Store connection
  activeConnections.set(clientId, {
    clientWs,
    openaiClient,
    connectedAt: new Date()
  });

  // Connect to OpenAI
  try {
    await openaiClient.connect();
    console.log(`[${clientId}] Connected to OpenAI`);

    // Send ready message to client
    if (clientWs.readyState === WebSocket.OPEN) {
      clientWs.send(JSON.stringify({
        type: 'ready',
        message: 'Connected to AI voice agent'
      }));
    }

  } catch (error) {
    console.error(`[${clientId}] Failed to connect to OpenAI:`, error);
    clientWs.close(1011, 'Failed to initialize AI connection');
    activeConnections.delete(clientId);
    return;
  }

  /**
   * Handle incoming messages from Android client
   */
  clientWs.on('message', (data, isBinary) => {
    if (isBinary) {
      // Binary data = audio from Android
      if (openaiClient.isReady()) {
        openaiClient.sendAudio(data);
      }
    } else {
      // Text message (JSON control messages)
      try {
        const message = JSON.parse(data);
        handleControlMessage(clientId, message, openaiClient);
      } catch (error) {
        console.error(`[${clientId}] Error parsing message:`, error);
      }
    }
  });

  /**
   * Handle client disconnection
   */
  clientWs.on('close', (code, reason) => {
    console.log(`[${clientId}] Client disconnected: ${code} - ${reason}`);

    // Disconnect OpenAI
    if (openaiClient) {
      openaiClient.disconnect();
    }

    // Remove from active connections
    activeConnections.delete(clientId);
  });

  /**
   * Handle client errors
   */
  clientWs.on('error', (error) => {
    console.error(`[${clientId}] Client error:`, error);
  });
});

/**
 * Handle control messages from client
 */
function handleControlMessage(clientId, message, openaiClient) {
  console.log(`[${clientId}] Control message:`, message.type);

  switch (message.type) {
    case 'ping':
      // Respond to ping
      const connection = activeConnections.get(clientId);
      if (connection && connection.clientWs.readyState === WebSocket.OPEN) {
        connection.clientWs.send(JSON.stringify({
          type: 'pong',
          timestamp: Date.now()
        }));
      }
      break;

    case 'commit_audio':
      // Tell OpenAI we're done sending audio for now
      if (openaiClient) {
        openaiClient.commitAudio();
      }
      break;

    default:
      console.log(`[${clientId}] Unknown control message type: ${message.type}`);
  }
}

/**
 * Server initialization
 */
async function startServer() {
  console.log('='.repeat(60));
  console.log('Voice Agent Backend Server');
  console.log('='.repeat(60));

  // Initialize calendar (optional)
  console.log('\nInitializing Google Calendar...');
  await calendar.initializeCalendar();

  // Start server
  const port = config.port;
  server.listen(port, () => {
    console.log('\n' + '='.repeat(60));
    console.log(`Server running on port ${port}`);
    console.log(`Health check: http://localhost:${port}/health`);
    console.log(`WebSocket endpoint: ws://localhost:${port}/voice`);
    console.log('='.repeat(60));
    console.log('\nConfiguration:');
    console.log(`  OpenAI Model: ${config.openai.model}`);
    console.log(`  Voice: ${config.openai.voice}`);
    console.log(`  Business: ${config.business.name}`);
    console.log('='.repeat(60));
    console.log('\nServer ready to accept connections...\n');
  });

  // Handle graceful shutdown
  process.on('SIGTERM', gracefulShutdown);
  process.on('SIGINT', gracefulShutdown);
}

/**
 * Graceful shutdown
 */
function gracefulShutdown() {
  console.log('\nShutting down gracefully...');

  // Close all active connections
  activeConnections.forEach((connection, clientId) => {
    console.log(`Closing connection: ${clientId}`);
    connection.openaiClient.disconnect();
    connection.clientWs.close(1001, 'Server shutting down');
  });

  // Close server
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });

  // Force exit after 10 seconds
  setTimeout(() => {
    console.error('Forced shutdown after timeout');
    process.exit(1);
  }, 10000);
}

// Start the server
startServer().catch(error => {
  console.error('Failed to start server:', error);
  process.exit(1);
});
