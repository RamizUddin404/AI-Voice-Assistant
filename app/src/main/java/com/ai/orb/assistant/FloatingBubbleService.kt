package com.ai.orb.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.util.ArrayList

class FloatingBubbleService : Service(), RecognitionListener {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        
        // 1. Acquire WakeLock
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::WakeLock")
        wakeLock.acquire()

        // 2. Start Foreground Notification
        createNotificationChannel()
        startForeground(1, createNotification())

        // 3. Setup UI
        setupOverlay()

        // 4. Voice Engine
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
        startListening()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "CHANNEL_ID", "AI Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("AI Assistant Active")
            .setContentText("Listening for commands...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            250, 250,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        bubbleView = LayoutInflater.from(this).inflate(R.layout.layout_orb_bubble, null)
        windowManager.addView(bubbleView, params)
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        speechRecognizer.startListening(intent)
    }

    override fun onResults(results: Bundle?) {
        val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val command = data?.get(0) ?: ""
        
        // Custom Wake Word Logic: if text contains "Assistant" or "Orb"
        if (command.contains("Assistant", true) || command.contains("Orb", true)) {
            processCommand(command)
        }
        
        startListening() 
    }

    private fun processCommand(text: String) {
        val lowerText = text.lowercase()
        if (lowerText.contains("open youtube")) {
            val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else if (lowerText.contains("scroll down")) {
            AssistantAccessibilityService.instance?.performScrollDown()
        }
    }

    override fun onEndOfSpeech() { startListening() }
    override fun onError(error: Int) { startListening() }
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock.isHeld) wakeLock.release()
        speechRecognizer.destroy()
        windowManager.removeView(bubbleView)
    }
}
