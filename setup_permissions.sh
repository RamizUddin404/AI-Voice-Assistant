#!/bin/bash
# AI Assistant Auto-Permission Script
# Use this while your phone is connected via ADB

PKG="com.ai.orb.assistant"
SERVICE="$PKG/.AssistantAccessibilityService"

echo "Granting Overlay permission..."
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow

echo "Granting Microphone permission..."
adb shell pm grant $PKG android.permission.RECORD_AUDIO

echo "Enabling Accessibility Service..."
adb shell settings put secure enabled_accessibility_services $PKG/$SERVICE
adb shell settings put secure accessibility_enabled 1

echo "Done! No manual 'Allow' clicks needed."
