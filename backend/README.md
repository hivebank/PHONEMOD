# Voice Agent Backend

Node.js backend server that powers the AI voice agent system. Handles WebSocket connections from Android devices and integrates with OpenAI's Realtime API for natural voice conversations.

## Features

- **Real-time Audio Processing** - WebSocket server for streaming audio
- **OpenAI Realtime API Integration** - GPT-4o voice conversations
- **Calendar Integration** - Google Calendar API for appointment scheduling
- **Function Calling** - AI can check availability and book appointments
- **Scalable Architecture** - Built with Express and WebSocket
- **Easy Deployment** - Ready for Railway, Heroku, or any Node.js host

## Requirements

- Node.js 18 or higher
- OpenAI API key (with Realtime API access)
- Google Calendar credentials (optional, for appointment features)

## Quick Start

### 1. Install Dependencies

```bash
cd backend
npm install
```

### 2. Configure Environment

Create a `.env` file:

```bash
cp .env.example .env
```

Edit `.env` and add your credentials:

```env
PORT=3000
OPENAI_API_KEY=sk-your-key-here
BUSINESS_NAME="Your Business Name"
BUSINESS_PHONE="+1234567890"
```

### 3. Run Locally

```bash
# Development mode (auto-restart)
npm run dev

# Production mode
npm start
```

Server will start on http://localhost:3000

Test with:
```bash
curl http://localhost:3000/health
```

## Deployment

### Deploy to Railway

Railway is the recommended hosting platform for this backend.

#### 1. Install Railway CLI

```bash
npm install -g @railway/cli
```

#### 2. Login to Railway

```bash
railway login
```

#### 3. Create New Project

```bash
railway init
```

Follow the prompts to create a new project.

#### 4. Set Environment Variables

```bash
railway variables set OPENAI_API_KEY=sk-your-key-here
railway variables set BUSINESS_NAME="Your Business Name"
railway variables set BUSINESS_PHONE="+1234567890"
railway variables set NODE_ENV=production
```

#### 5. Deploy

```bash
railway up
```

#### 6. Get Your URL

```bash
railway domain
```

This will give you a URL like `your-app.railway.app`. Use this URL in your Android app!

### Deploy to Heroku

```bash
# Login to Heroku
heroku login

# Create app
heroku create your-voice-agent

# Set environment variables
heroku config:set OPENAI_API_KEY=sk-your-key-here
heroku config:set BUSINESS_NAME="Your Business Name"
heroku config:set NODE_ENV=production

# Deploy
git push heroku main

# Open app
heroku open
```

### Deploy to Your Own Server

```bash
# On your server
git clone <your-repo>
cd backend
npm install --production

# Create .env file with your credentials
nano .env

# Install PM2 for process management
npm install -g pm2

# Start server with PM2
pm2 start src/server.js --name voice-agent

# Save PM2 config
pm2 save

# Setup PM2 to start on boot
pm2 startup
```

## Google Calendar Setup (Optional)

If you want the AI to book appointments:

### 1. Create Service Account

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a new project or select existing
3. Enable Google Calendar API
4. Create Service Account credentials
5. Download JSON key file

### 2. Share Calendar

1. Open Google Calendar
2. Go to calendar settings
3. Share with service account email (found in JSON file)
4. Grant "Make changes to events" permission

### 3. Configure Backend

```bash
# Copy credentials file to backend directory
cp ~/Downloads/credentials.json backend/credentials.json

# Set environment variable
railway variables set GOOGLE_CALENDAR_CREDENTIALS=./credentials.json
```

## API Endpoints

### GET /health

Health check endpoint.

**Response:**
```json
{
  "status": "ok",
  "timestamp": "2024-01-01T00:00:00.000Z",
  "activeConnections": 0,
  "config": {
    "openaiModel": "gpt-4o-realtime-preview-2024-10-01",
    "voice": "alloy",
    "calendarEnabled": true
  }
}
```

### WebSocket /voice

WebSocket endpoint for audio streaming.

**Connection:**
```javascript
const ws = new WebSocket('wss://your-app.railway.app/voice');
```

**Binary Messages (Audio):**
- Send: PCM16 audio chunks from Android (16kHz, mono)
- Receive: PCM16 audio responses from AI (16kHz, mono)

**Text Messages (Control):**
```json
// Ping
{ "type": "ping" }

// Commit audio buffer
{ "type": "commit_audio" }
```

## Configuration

All configuration is in `src/config.js` and environment variables.

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| PORT | No | 3000 | Server port |
| NODE_ENV | No | development | Environment (development/production) |
| OPENAI_API_KEY | **Yes** | - | OpenAI API key |
| GOOGLE_CALENDAR_CREDENTIALS | No | ./credentials.json | Path to Google credentials |
| GOOGLE_CALENDAR_ID | No | primary | Google Calendar ID |
| BUSINESS_NAME | No | Our Business | Your business name |
| BUSINESS_PHONE | No | +1234567890 | Your business phone |
| BUSINESS_HOURS | No | 9 AM - 9 PM daily | Your business hours |

### Customizing AI Behavior

Edit `src/config.js` to customize:

- **Voice**: Change OpenAI voice (alloy, echo, fable, onyx, nova, shimmer)
- **Turn Detection**: Adjust when AI responds
- **System Prompt**: Customize AI personality and instructions

Or set custom system prompt via environment:

```bash
railway variables set CUSTOM_SYSTEM_PROMPT="Your custom prompt here"
```

## Architecture

```
┌─────────────────┐
│  Android App    │
│  (WebSocket)    │
└────────┬────────┘
         │
         ▼
┌─────────────────────┐
│  Express Server     │
│  /voice endpoint    │
└────────┬────────────┘
         │
         ├──────────────────┐
         │                  │
         ▼                  ▼
┌──────────────────┐  ┌──────────────┐
│ OpenAI Realtime  │  │ Google       │
│ API (WebSocket)  │  │ Calendar API │
└──────────────────┘  └──────────────┘
```

### Components

- **server.js** - Express server and WebSocket handling
- **openai-realtime.js** - OpenAI Realtime API client
- **calendar-integration.js** - Google Calendar integration
- **config.js** - Configuration management

## Testing

### Test Health Endpoint

```bash
curl https://your-app.railway.app/health
```

### Test WebSocket Connection

Install `websocat`:
```bash
# macOS
brew install websocat

# Linux
cargo install websocat
```

Test connection:
```bash
websocat wss://your-app.railway.app/voice
```

### Monitor Logs

**Railway:**
```bash
railway logs
```

**Heroku:**
```bash
heroku logs --tail
```

**PM2:**
```bash
pm2 logs voice-agent
```

## Troubleshooting

### OpenAI Connection Fails

- **Check API key**: Ensure `OPENAI_API_KEY` is set correctly
- **Check model access**: Verify you have access to `gpt-4o-realtime-preview-2024-10-01`
- **Check logs**: Look for specific error messages

### WebSocket Connection Fails

- **Check URL**: Ensure Android app has correct backend URL
- **Check HTTPS**: Use `wss://` for secure connections (required in production)
- **Check firewall**: Ensure WebSocket port is not blocked

### Calendar Functions Not Working

- **Check credentials**: Ensure `credentials.json` exists and is valid
- **Check permissions**: Ensure service account has calendar access
- **Check logs**: Function errors are logged to console

### High Memory Usage

- Reduce number of concurrent connections
- Add connection limits in server.js
- Use a load balancer for scaling

## Development

### Project Structure

```
backend/
├── src/
│   ├── server.js              # Main server
│   ├── openai-realtime.js     # OpenAI client
│   ├── calendar-integration.js # Calendar API
│   └── config.js              # Configuration
├── package.json
├── .env.example
├── railway.json
└── README.md
```

### Adding New Features

1. **Add new function for AI:**
   - Edit `calendar-integration.js`
   - Add function definition in `getFunctionDefinitions()`
   - Implement handler in `handleFunctionCall()`

2. **Modify AI behavior:**
   - Edit system prompt in `config.js`
   - Adjust turn detection settings
   - Change voice or temperature

3. **Add new endpoints:**
   - Edit `server.js`
   - Add Express routes as needed

## Security Notes

- Always use HTTPS in production (WSS for WebSocket)
- Keep OpenAI API key secret
- Don't commit `.env` or `credentials.json` to git
- Use environment variables for all secrets
- Implement rate limiting for production
- Add authentication if needed

## Performance

- Server handles ~100 concurrent connections per instance
- Audio streaming uses minimal bandwidth (~32 KB/s per call)
- OpenAI Realtime API has ~200-500ms latency
- Consider load balancing for >100 concurrent calls

## Cost Estimation

**OpenAI Realtime API:**
- ~$0.06 per minute of audio input
- ~$0.24 per minute of audio output
- Average call: 3-5 minutes = $1-2 per call

**Railway Hosting:**
- Free tier: $5/month credit
- Pro tier: $20/month for production usage

## License

See project root for license information.

## Support

For issues:
1. Check server logs
2. Verify all environment variables are set
3. Test health endpoint
4. Review OpenAI API status
5. Check `../TESTING.md` for common issues
