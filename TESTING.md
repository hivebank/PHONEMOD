# Testing Checklist

Complete testing guide for the AI Voice Agent system.

## Pre-Flight Checklist

Before testing the complete system, ensure these prerequisites are met:

### Backend Requirements

- [ ] Node.js 18+ installed
- [ ] OpenAI API key obtained (with Realtime API access)
- [ ] Backend dependencies installed (`npm install`)
- [ ] Environment variables configured (`.env` file created)
- [ ] Google Calendar credentials configured (optional)

### Android Requirements

- [ ] Android device or emulator (Android 8.0+)
- [ ] Android Studio installed
- [ ] USB debugging enabled on device
- [ ] ADB connected and working

---

## Part 1: Backend Testing

### 1.1 Local Backend Setup

**Test: Install and configure backend**

```bash
cd backend
npm install
cp .env.example .env
# Edit .env with your credentials
```

- [ ] Dependencies install without errors
- [ ] `.env` file created and configured
- [ ] OpenAI API key is valid

**Expected Output:**
```
added XXX packages
```

### 1.2 Backend Startup

**Test: Start backend server**

```bash
npm start
```

- [ ] Server starts without errors
- [ ] Displays configuration on startup
- [ ] Shows "Server ready to accept connections"
- [ ] No error messages in console

**Expected Output:**
```
============================================================
Voice Agent Backend Server
============================================================

Initializing Google Calendar...
Google Calendar initialized successfully (or warning if not configured)

============================================================
Server running on port 3000
Health check: http://localhost:3000/health
WebSocket endpoint: ws://localhost:3000/voice
============================================================

Configuration:
  OpenAI Model: gpt-4o-realtime-preview-2024-10-01
  Voice: alloy
  Business: Your Business Name
============================================================

Server ready to accept connections...
```

### 1.3 Health Check Endpoint

**Test: Verify health endpoint responds**

```bash
curl http://localhost:3000/health
```

- [ ] Returns JSON response
- [ ] Status is "ok"
- [ ] Shows configuration

**Expected Output:**
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

### 1.4 WebSocket Connection Test

**Test: Connect to WebSocket endpoint**

Install websocat (for testing):
```bash
# macOS
brew install websocat

# Or use npm package
npm install -g wscat
```

Test connection:
```bash
wscat -c ws://localhost:3000/voice
```

- [ ] WebSocket connects successfully
- [ ] Receives "ready" message from server
- [ ] Backend logs show client connection

**Expected Output (backend logs):**
```
[client_xxxxx] Client connected from ::1
[client_xxxxx] Connected to OpenAI
```

**Expected Output (wscat):**
```
Connected (press CTRL+C to quit)
< {"type":"ready","message":"Connected to AI voice agent"}
```

### 1.5 OpenAI Connection Test

**Test: Verify OpenAI Realtime API connection**

When WebSocket client connects, backend should:

- [ ] Connect to OpenAI automatically
- [ ] Log "Connected to OpenAI Realtime API"
- [ ] Log "Session created" with session ID
- [ ] Log "Session updated successfully"

**Expected Output (backend logs):**
```
Connecting to OpenAI Realtime API...
Connected to OpenAI Realtime API
Initializing OpenAI session...
OpenAI event: session.created
Session created: sess_xxxxx
OpenAI event: session.updated
Session updated successfully
```

---

## Part 2: Android App Testing

### 2.1 Build Android App

**Test: Build APK**

```bash
cd android-voice-agent
chmod +x gradlew
./gradlew assembleDebug
```

- [ ] Build completes without errors
- [ ] APK created at `app/build/outputs/apk/debug/voice-agent-debug.apk`
- [ ] No compilation errors
- [ ] No dependency resolution errors

**Expected Output:**
```
BUILD SUCCESSFUL in 30s
```

### 2.2 Install Android App

**Test: Install on device**

```bash
adb devices  # Verify device connected
adb install app/build/outputs/apk/debug/voice-agent-debug.apk
```

- [ ] Device appears in `adb devices`
- [ ] Installation succeeds
- [ ] App appears in app drawer
- [ ] App icon is visible

**Expected Output:**
```
Performing Streamed Install
Success
```

### 2.3 App Launch

**Test: Launch application**

- [ ] App opens without crashing
- [ ] Main UI appears
- [ ] All UI elements visible (text fields, buttons, status)
- [ ] No ANR (Application Not Responding) errors

### 2.4 Permissions Request

**Test: Request all permissions**

1. Tap "Request Permissions" button
2. Grant each permission when prompted

- [ ] Permission dialogs appear
- [ ] All permissions can be granted
- [ ] Permissions status updates after granting
- [ ] Shows "✓ All permissions granted" when complete

**Required Permissions:**
- Phone State
- Record Audio
- Answer Calls
- Internet Access
- Foreground Service
- Notifications (Android 13+)

### 2.5 Default Phone App

**Test: Set as default phone app**

1. Tap "Set as Default Phone App"
2. Select "Voice Agent" from the list

- [ ] Default dialer dialog appears
- [ ] Voice Agent is in the list
- [ ] Can select Voice Agent
- [ ] Button updates to "✓ Default Phone App"

**Note:** This is CRITICAL - app will not auto-answer without being default phone app.

### 2.6 Backend Configuration

**Test: Configure backend URL**

1. Enter backend URL (e.g., `localhost:3000` or `your-app.railway.app`)
2. Tap "Save Backend URL"

- [ ] URL saves successfully
- [ ] Toast message confirms save
- [ ] URL persists after app restart

### 2.7 Connection Test

**Test: Test connection to backend**

1. Ensure backend is running
2. Tap "Test Connection"

- [ ] Status shows "Testing connection..."
- [ ] Button disables during test
- [ ] Success: Shows "✓ Connected to backend"
- [ ] Failure: Shows error message

**Expected (success):**
- Green status text
- Toast: "Connection successful!"

**Expected (failure):**
- Red status text
- Toast with error details

---

## Part 3: End-to-End Testing

### 3.1 Complete System Test

**Prerequisites:**
- [ ] Backend running and healthy
- [ ] Android app installed and configured
- [ ] All permissions granted
- [ ] Set as default phone app
- [ ] Connection test passed
- [ ] Test phone available for calling

### 3.2 Incoming Call Test

**Test: Make a test call to the device**

1. Start monitoring logs:
```bash
# Terminal 1: Backend logs
cd backend
npm start

# Terminal 2: Android logs
adb logcat -s VoiceAgent:* -v time
```

2. Call the Android device from another phone

**Expected Behavior:**

**Step 1: Call received**
- [ ] App automatically answers call (no manual action needed)
- [ ] Backend logs show client connection
- [ ] OpenAI connection established

**Android Logs:**
```
VoiceAgent: Call added: 4
VoiceAgent: Auto-answering incoming call
VoiceAgent: Call is now active, starting audio streaming
VoiceAgent: WebSocket connected, starting audio capture
VoiceAgent: Audio capture started
```

**Backend Logs:**
```
[client_xxxxx] Client connected from xxx.xxx.xxx.xxx
[client_xxxxx] Connected to OpenAI
```

**Step 2: AI greeting plays**
- [ ] Caller hears AI voice greeting
- [ ] Greeting is clear and understandable
- [ ] No audio distortion

**Expected Audio:**
> "Thank you for calling [Your Business], how can I help you?"

**Step 3: Conversation**
- [ ] Caller can speak and AI responds
- [ ] Turn detection works (AI knows when to respond)
- [ ] Responses are relevant and natural
- [ ] No significant lag (under 2 seconds)

**Android Logs:**
```
VoiceAgent: User started speaking
VoiceAgent: User stopped speaking
```

**Backend Logs:**
```
OpenAI event: input_audio_buffer.speech_started
User started speaking
OpenAI event: input_audio_buffer.speech_stopped
User stopped speaking
OpenAI event: response.audio.delta
OpenAI event: response.audio.done
```

**Step 4: Function calling (if calendar enabled)**

Test: "I'd like to book an appointment for tomorrow at 2 PM"

- [ ] AI acknowledges request
- [ ] Backend logs show function call
- [ ] Calendar API called
- [ ] AI responds with confirmation or alternative times

**Backend Logs:**
```
Function call: check_availability { date: '2024-01-02', time: '14:00' }
Executing function: check_availability
Function call: book_appointment
Appointment booked: evt_xxxxx
```

**Step 5: Call end**
- [ ] Either party can hang up
- [ ] Call ends cleanly
- [ ] Resources released
- [ ] No memory leaks

**Android Logs:**
```
VoiceAgent: Call disconnected
VoiceAgent: Stopping audio streaming
VoiceAgent: Capture loop ended
VoiceAgent: Playback loop ended
VoiceAgent: WebSocket disconnected
```

### 3.3 Audio Quality Test

**Test: Verify audio quality**

During a call:

- [ ] AI voice is clear (not robotic or distorted)
- [ ] Caller's voice is captured clearly
- [ ] No echo or feedback
- [ ] No crackling or popping sounds
- [ ] Volume levels appropriate
- [ ] No audio dropouts

**If audio issues occur, see Troubleshooting section below.**

### 3.4 Multiple Call Test

**Test: Handle multiple sequential calls**

1. Call device
2. Have conversation
3. Hang up
4. Wait 10 seconds
5. Call again

- [ ] Second call auto-answers
- [ ] Fresh conversation (doesn't remember first call)
- [ ] No resource leaks
- [ ] Backend shows new connection

### 3.5 Network Recovery Test

**Test: Handle network interruptions**

1. Start a call
2. Disable WiFi/data briefly
3. Re-enable network

- [ ] App attempts reconnection
- [ ] Reconnection succeeds (if network restored quickly)
- [ ] Logs show reconnection attempts

---

## Part 4: Stress Testing

### 4.1 Long Call Test

**Test: Extended conversation**

- [ ] Call lasts 10+ minutes
- [ ] No memory leaks
- [ ] Audio quality remains good
- [ ] No crashes

### 4.2 Rapid Speech Test

**Test: Quick back-and-forth**

- [ ] Multiple quick questions
- [ ] AI keeps up with conversation
- [ ] No buffering issues

---

## Troubleshooting Guide

### Issue: App doesn't answer calls

**Symptoms:**
- Phone rings normally
- App doesn't auto-answer

**Solutions:**

1. **Check if app is default phone app:**
```bash
adb shell dumpsys telecom
# Look for "DefaultDialerManagerAdapter"
```
- If not set, go to app and tap "Set as Default Phone App"

2. **Check permissions:**
```bash
adb shell dumpsys package com.voiceagent | grep permission
```
- Ensure all permissions are granted

3. **Check logs:**
```bash
adb logcat -s VoiceAgent:*
```
- Look for error messages

4. **Restart app:**
```bash
adb shell am force-stop com.voiceagent
# Reopen app manually
```

---

### Issue: No audio captured

**Symptoms:**
- Call connects
- AI speaks
- Caller's voice not heard by AI

**Solutions:**

1. **Check RECORD_AUDIO permission:**
```bash
adb shell pm list permissions -g | grep RECORD_AUDIO
```

2. **Check audio source:**
- Some Android versions/devices restrict VOICE_COMMUNICATION source
- Check logs for AudioRecord errors

3. **Test on different device:**
- Try on a different phone or emulator

4. **Verify audio format:**
- Check logs for "Audio buffer size" message
- Should be positive number

---

### Issue: No audio playback

**Symptoms:**
- Call connects
- Caller speaks
- No AI response heard

**Solutions:**

1. **Check backend connection:**
```bash
adb logcat -s VoiceAgent:* | grep WebSocket
```
- Should show "WebSocket connected"

2. **Check backend logs:**
- Verify OpenAI is sending audio: `response.audio.delta`

3. **Check AudioTrack initialization:**
```bash
adb logcat -s VoiceAgent:* | grep AudioTrack
```
- Should show "AudioTrack initialized and playing"

4. **Volume check:**
- Ensure call volume is up
- Try adjusting volume during call

---

### Issue: WebSocket connection fails

**Symptoms:**
- "Connection failed" error in app
- Can't test connection successfully

**Solutions:**

1. **Verify backend URL:**
- No `http://` or `wss://` prefix needed
- Just the domain: `your-app.railway.app`
- For localhost: `10.0.2.2:3000` (emulator) or `YOUR_IP:3000` (device)

2. **Check backend is running:**
```bash
curl http://localhost:3000/health
```

3. **Check network:**
- Device has internet connection
- Backend is accessible from device network
- No firewall blocking WebSocket

4. **Check backend logs:**
- Look for connection attempts
- Check for errors

5. **Use IP instead of localhost:**
```bash
# Get your computer's IP
ifconfig | grep "inet "
# Use this IP in Android app
```

---

### Issue: OpenAI errors

**Symptoms:**
- Backend logs show OpenAI errors
- "Failed to initialize AI connection"

**Solutions:**

1. **Check API key:**
```bash
echo $OPENAI_API_KEY
```
- Ensure key is valid and not expired

2. **Check model access:**
- Verify you have access to Realtime API
- Check OpenAI dashboard for API status

3. **Check API quota:**
- Ensure you haven't hit rate limits
- Check billing status

4. **Check logs for specific error:**
```bash
grep -i "openai error" backend.log
```

---

### Issue: Audio quality problems

**Symptoms:**
- Robotic voice
- Crackling or popping
- Echo or feedback

**Solutions:**

1. **Check network quality:**
- Use WiFi instead of mobile data
- Test network speed

2. **Check buffer size:**
- Logs should show reasonable buffer size (3200 bytes)

3. **Check for packet loss:**
- Backend logs may show WebSocket errors

4. **Adjust audio parameters:**
- Edit `AudioHandler.kt` BUFFER_SIZE_MS if needed

5. **Test with headphones:**
- Use wired headphones to eliminate echo

---

### Issue: Calendar functions not working

**Symptoms:**
- AI can't check availability
- Can't book appointments

**Solutions:**

1. **Check if calendar enabled:**
```bash
curl http://localhost:3000/health
```
- Look for `"calendarEnabled": true`

2. **Check credentials:**
```bash
ls backend/credentials.json
```
- File should exist

3. **Check permissions:**
- Service account should have calendar access
- Calendar should be shared with service account

4. **Check backend logs:**
```bash
grep -i "calendar" backend.log
```
- Look for initialization messages or errors

---

### Issue: App crashes

**Symptoms:**
- App closes unexpectedly
- ANR (Application Not Responding)

**Solutions:**

1. **Get crash logs:**
```bash
adb logcat -b crash
```

2. **Check memory:**
```bash
adb shell dumpsys meminfo com.voiceagent
```

3. **Reinstall app:**
```bash
adb uninstall com.voiceagent
adb install app/build/outputs/apk/debug/voice-agent-debug.apk
```

4. **Check Android version:**
- Ensure Android 8.0+ (API 26+)

---

## Performance Benchmarks

Expected performance metrics:

| Metric | Target | Acceptable |
|--------|--------|------------|
| Call answer time | < 2 seconds | < 5 seconds |
| AI response latency | < 1 second | < 2 seconds |
| Audio quality | Clear, natural | Understandable |
| Memory usage (Android) | < 100 MB | < 200 MB |
| Memory usage (Backend) | < 200 MB | < 500 MB |
| Concurrent connections | 50+ | 10+ |
| Call duration | Unlimited | 30+ minutes |

---

## Deployment Testing

### Production Checklist

Before deploying to production:

- [ ] Backend deployed to Railway/Heroku
- [ ] Environment variables set in production
- [ ] Health check returns 200 OK
- [ ] WebSocket endpoint accessible
- [ ] SSL/TLS enabled (WSS)
- [ ] Test with production URL in Android app
- [ ] Test actual phone call with production backend
- [ ] Monitor logs for errors
- [ ] Set up error alerting
- [ ] Document production URL

---

## Continuous Testing

For ongoing development:

1. **After each code change:**
   - Run build
   - Install on device
   - Test one complete call

2. **Weekly:**
   - Run full test suite
   - Check for memory leaks
   - Review backend logs

3. **Before releases:**
   - Complete all tests in this document
   - Test on multiple devices
   - Test on different networks

---

## Success Criteria

System is considered working when:

- [x] Backend passes all health checks
- [x] Android app installs and launches
- [x] All permissions granted
- [x] Set as default phone app
- [x] Connection test succeeds
- [x] Incoming call auto-answers
- [x] AI greeting plays clearly
- [x] Two-way conversation works
- [x] Call ends cleanly
- [x] No crashes or errors

---

## Getting Help

If you're still stuck after following this guide:

1. Check backend logs for errors
2. Check Android logcat for errors
3. Verify all environment variables
4. Test each component independently
5. Try on a different device/network
6. Review code for any modifications
7. Create an issue with:
   - Logs from backend
   - Logs from Android (adb logcat)
   - Steps to reproduce
   - Expected vs actual behavior
