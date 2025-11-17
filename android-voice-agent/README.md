# Voice Agent - Android App

AI-powered voice agent that automatically answers incoming phone calls and uses OpenAI's Realtime API to interact with callers.

## Features

- **Auto-answer incoming calls** - Automatically picks up calls when set as default phone app
- **Real-time audio streaming** - Streams call audio to backend via WebSocket
- **AI voice responses** - Plays AI-generated audio responses during calls
- **Low latency** - PCM16 audio at 16kHz for optimal performance
- **Easy configuration** - Simple UI for backend setup and permissions

## Requirements

- Android 8.0 (API 26) or higher
- Device with phone capability
- Internet connection
- Backend server URL (see `../backend/` for server setup)

## Build Instructions

### Prerequisites

- Android Studio Arctic Fox or later
- JDK 8 or later
- Android SDK with API 34

### Building the APK

1. **Open project in Android Studio:**
   ```bash
   cd android-voice-agent
   # Open this directory in Android Studio
   ```

2. **Build from command line:**
   ```bash
   # Make gradlew executable (Linux/Mac)
   chmod +x gradlew

   # Build debug APK
   ./gradlew assembleDebug

   # On Windows:
   gradlew.bat assembleDebug
   ```

3. **Find the APK:**
   ```
   app/build/outputs/apk/debug/voice-agent-debug.apk
   ```

## Installation

### Via ADB (Recommended)

1. **Enable USB Debugging on your Android device:**
   - Go to Settings > About Phone
   - Tap "Build Number" 7 times to enable Developer Options
   - Go to Settings > Developer Options
   - Enable "USB Debugging"

2. **Connect device and install:**
   ```bash
   # Install APK
   adb install app/build/outputs/apk/debug/voice-agent-debug.apk

   # Or reinstall if already installed
   adb install -r app/build/outputs/apk/debug/voice-agent-debug.apk
   ```

### Via File Transfer

1. Build the APK (see above)
2. Copy `voice-agent-debug.apk` to your device
3. Open the APK file on your device
4. Tap "Install" (you may need to enable "Install from Unknown Sources")

## Setup

### 1. Configure Backend URL

1. Open the Voice Agent app
2. Enter your backend URL (e.g., `your-app.railway.app`)
3. Tap "Save Backend URL"
4. Tap "Test Connection" to verify connectivity

### 2. Grant Permissions

The app requires the following permissions:

- **Phone State** - Detect incoming calls
- **Record Audio** - Capture call audio
- **Answer Calls** - Automatically answer incoming calls
- **Internet** - Connect to backend server
- **Foreground Service** - Keep service running during calls

To grant permissions:
1. Tap "Request Permissions" in the app
2. Grant all requested permissions

### 3. Set as Default Phone App

1. Tap "Set as Default Phone App"
2. Select "Voice Agent" from the list
3. Confirm the change

**Important:** The app MUST be set as the default phone app to automatically answer calls.

## Usage

Once configured:

1. When a call comes in, the app will automatically answer
2. Audio from the call is streamed to your backend
3. AI responses are played back to the caller
4. The call continues until either party hangs up

## Viewing Logs

To debug or monitor the app:

```bash
# View all Voice Agent logs
adb logcat -s VoiceAgent:*

# View with timestamp
adb logcat -v time -s VoiceAgent:*

# Clear logs and start fresh
adb logcat -c && adb logcat -s VoiceAgent:*
```

## Troubleshooting

### App doesn't answer calls

- **Check:** Is the app set as default phone app?
- **Check:** Are all permissions granted?
- **Check:** Is the device connected to internet?
- **View logs:** `adb logcat -s VoiceAgent:*`

### No audio captured

- **Check:** Is "Record Audio" permission granted?
- **Check:** Is the call actually active (not ringing)?
- **Try:** Restarting the app
- **Note:** Some Android versions restrict VOICE_CALL audio source

### WebSocket connection fails

- **Check:** Is the backend URL correct?
- **Check:** Is the backend server running?
- **Check:** Is your device connected to internet?
- **Try:** Test connection using the "Test Connection" button
- **Check:** Backend logs for connection errors

### Audio quality issues

- **Check:** Network connection quality
- **Try:** Using WiFi instead of mobile data
- **Note:** The app uses 16kHz PCM16 audio - this is optimized for speech, not music

## Permissions Explained

| Permission | Why It's Needed |
|------------|----------------|
| READ_PHONE_STATE | Detect when calls come in and their status |
| CALL_PHONE | Make outbound calls (required for default phone app) |
| RECORD_AUDIO | Capture audio from the phone call |
| ANSWER_PHONE_CALLS | Automatically answer incoming calls |
| INTERNET | Connect to backend WebSocket server |
| FOREGROUND_SERVICE | Keep the service running during active calls |
| FOREGROUND_SERVICE_PHONE_CALL | Declare foreground service type for calls |

## Architecture

```
┌─────────────┐
│   Phone     │
│   Call      │
└──────┬──────┘
       │
       ▼
┌──────────────────┐
│  CallService     │  Auto-answers call
│  (InCallService) │  Manages call lifecycle
└────────┬─────────┘
         │
         ▼
    ┌────────────┐
    │ AudioHandler│  Captures call audio (PCM16, 16kHz)
    │             │  Plays AI responses
    └──────┬─────┘
           │
           ▼
    ┌──────────────┐
    │ WebSocketClient│ Connects to backend
    │               │ Streams binary audio
    └───────┬───────┘
            │
            ▼
      [ Backend Server ]
            │
            ▼
      [ OpenAI Realtime API ]
```

## Development

### Project Structure

```
app/src/main/java/com/voiceagent/
├── CallService.kt         # InCallService implementation
├── AudioHandler.kt        # Audio capture/playback
├── WebSocketClient.kt     # WebSocket communication
├── MainActivity.kt        # UI and configuration
└── PermissionsHelper.kt   # Permission management
```

### Key Components

- **CallService**: Extends `InCallService` to intercept and handle phone calls
- **AudioHandler**: Uses `AudioRecord` and `AudioTrack` for audio I/O
- **WebSocketClient**: OkHttp WebSocket client for real-time communication
- **MainActivity**: Simple UI for configuration and setup

### Audio Format

- **Sample Rate:** 16000 Hz (16 kHz)
- **Encoding:** PCM 16-bit (signed, little-endian)
- **Channels:** Mono (1 channel)
- **Chunk Size:** 100ms (~3200 bytes)

This format matches OpenAI Realtime API requirements.

## Security Notes

- Audio is streamed over WebSocket (use WSS for encryption)
- No audio is stored locally
- Backend URL is stored in SharedPreferences
- Ensure your backend uses authentication/authorization

## License

See project root for license information.

## Support

For issues or questions:
1. Check logs: `adb logcat -s VoiceAgent:*`
2. Verify all setup steps completed
3. Check backend server logs
4. Review `../TESTING.md` for common issues
