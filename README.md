# JARVIS AI Assistant for Android

A voice-first Android assistant with a movable floating orb, Bangla/English recognition attempts, OpenRouter AI chat, local phone commands, and optional Accessibility Service gestures.

## Current capabilities

- Wake phrase: say **“Jarvis”**; tap the floating orb to toggle active mode.
- Speech recognition alternates between Bangla (bn-BD) and English (en-US) sessions because Android 9 does not provide reliable simultaneous bilingual recognition through the standard SpeechRecognizer API.
- Open installed apps by name, including Facebook and Instagram.
- Local commands for time/date, media volume, Wi-Fi/Bluetooth settings screens, scroll up/down (when Accessibility is enabled), and opening the dialer or SMS composer.
- Calls and messages are **not placed or sent automatically**; the user reviews and confirms them in the Android dialer/messaging app.
- Text-to-speech uses the phone's preferred language when available, with fallback support.
- OpenRouter Chat Completions uses the openrouter/free model router. Availability and rate limits depend on OpenRouter's current free-model pool.
- Foreground-service notification while the assistant is running; boot startup is skipped if required microphone/overlay permissions are missing.

## Build APK

The GitHub Actions workflow builds a debug APK on pushes to master.

1. Open the **Actions** tab in this repository.
2. Select **Android Build APK** and open the newest run.
3. Wait for the run to finish successfully, then download **AI-Assistant-Debug-APK** and extract the APK.

The workflow checks that the Gradle wrapper exists, builds with Gradle, verifies the APK file is non-empty, and uploads the artifact for 7 days.

For a local build with Android SDK and Java 11 installed:

    chmod +x gradlew
    ./gradlew --no-daemon --stacktrace assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk.

## First-time setup

1. Install and open the APK.
2. Enter your own OpenRouter API key. It is stored in the app's private preferences; this is not encrypted secret storage, so do not use a key with more spending access than necessary.
3. Grant microphone and **Display over other apps** permissions.
4. Enable JARVIS Accessibility Service in Android Settings only if you want scroll/gesture commands.
5. Keep the foreground-service notification visible while using voice features.
6. Make sure your phone's speech-recognition service supports Bangla and English and that TTS voices are installed for the languages you want.

## Android limitations

- Android's standard SpeechRecognizer is not designed as a guaranteed always-on hotword engine and may stream audio to a recognition service. The app alternates recognition languages rather than recognizing both languages simultaneously.
- Battery optimization, network availability, speech-provider support, and manufacturer background management can interrupt listening.
- Non-root apps cannot silently toggle all Wi-Fi/Bluetooth states or bypass protected Android settings; JARVIS opens the relevant settings screen instead.
- Accessibility permission is powerful. Enable it only if you understand and want the described gesture features.
- This debug APK is not a Play Store release. Release signing, privacy policy, testing, and policy review are still needed before publishing.

## Project

- Package: com.ai.orb.assistant
- Minimum Android version: Android 7.0 (API 24)
- AI endpoint: https://openrouter.ai/api/v1/chat/completions
