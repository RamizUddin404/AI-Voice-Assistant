# AI Voice Assistant - Futuristic Orb

This is a professional AI Voice Assistant for Android featuring a floating interactive 3D-style neon orb.

## Features
- **Floating Interactive Bubble**: Always-on-top UI for quick access.
- **Background Service**: Runs continuously with a WakeLock to prevent the system from killing the process.
- **Speech System**: Uses Android's SpeechRecognizer for online/offline voice commands.
- **Accessibility Control**: Can perform system-wide gestures like scrolling and clicking.
- **System Access**: Can open apps and interact with other applications.

## How to Build
1. Open **Android Studio**.
2. Select **Open** and choose the `AI-Voice-Assistant` folder.
3. Wait for Gradle to sync.
4. Build and run on your Android device (Android 7.0+ recommended).

## Setup After Installation
1. Launch the app and click **ACTIVATE**.
2. You will be prompted to allow **Display over other apps**. Enable it.
3. You will be prompted to enable **Accessibility Service**. Find "AI Assistant Accessibility" and turn it on.
4. The Neon Orb will appear on your screen.

## Voice Commands
- "Orb, open YouTube"
- "Orb, scroll down"
- (You can add more logic in `FloatingBubbleService.kt`)

## GitHub Setup
To push this to your GitHub repository:
```bash
git init
git add .
git commit -m "Initial commit of AI Voice Assistant"
git remote add origin YOUR_GITHUB_REPO_URL
git push -u origin main
```
