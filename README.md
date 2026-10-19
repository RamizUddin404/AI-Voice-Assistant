# JARVIS AI Assistant for Android

A voice-first Android assistant with a movable floating orb, OpenRouter-powered chat, local voice commands, and optional Accessibility Service actions.

## Current capabilities

- Wake phrase: say **“Jarvis”** (recognition availability depends on the installed Android speech service).
- AI chat through the OpenRouter Chat Completions API using a key you provide.
- Local commands: open an installed app, tell the time/date, adjust media volume, and scroll up/down when Accessibility is enabled.
- Text-to-speech follows the phone's default locale when supported.
- Foreground service notification while the assistant is active.
- API/network error messages and safer service cleanup.

## Build APK

The repository includes a GitHub Actions workflow that builds a debug APK on pushes to `master`.

1. Open the **Actions** tab in this repository.
2. Select **Android Build APK** and open the newest run.
3. When the run succeeds, download the `AI-Assistant-Debug-APK` artifact and extract the APK.

For a local build with Android SDK and Java 11 installed:

```bash
chmod +x gradlew
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## First-time setup

1. Install and open the APK.
2. Enter your own OpenRouter API key. The key is stored in the app's private preferences; this is not encrypted secret storage, so do not use a key with more spending access than necessary.
3. Grant microphone and overlay permissions when prompted.
4. Enable the assistant Accessibility Service only if you want scroll/gesture commands.
5. Keep the foreground-service notification visible while using voice features.

## Important Android limits

- Android does not guarantee a permanent always-on wake-word listener. Battery optimization, the speech recognition provider, microphone restrictions, and OEM background management can stop recognition.
- Speech recognition language support depends on the phone's installed recognition service. Bangla recognition is not guaranteed by every provider.
- This app does not have root access and cannot silently bypass Android permissions. Wi-Fi/Bluetooth control, protected settings, and some app actions are restricted by Android.
- Accessibility access is powerful. Enable it only if you understand and want the described on-screen gesture features.
- This debug APK is not a Play Store release. Release signing, privacy policy, testing, and policy review are still needed before publishing.

## Project

- Package: `com.ai.orb.assistant`
- Minimum Android version: Android 7.0 (API 24)
- AI endpoint: `https://openrouter.ai/api/v1/chat/completions`
