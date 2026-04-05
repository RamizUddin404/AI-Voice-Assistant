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
import android.widget.ProgressBar
import androidx.core.app.NotificationCompat
import java.util.ArrayList

class FloatingBubbleService : Service(), RecognitionListener {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var progressBar: ProgressBar

    override fun onCreate() {
        super.onCreate()
        
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::WakeLock")
        wakeLock.acquire()

        createNotificationChannel()
        startForeground(1, createNotification())

        setupOverlay()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
        startListening()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel("CHANNEL_ID", "AI Assistant", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("AI Assistant Active")
            .setContentText("Listening for 'Orb' or 'Assistant'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            250, 250,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        bubbleView = LayoutInflater.from(this).inflate(R.layout.layout_orb_bubble, null)
        progressBar = bubbleView.findViewById(R.id.listening_waves)
        windowManager.addView(bubbleView, params)
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        speechRecognizer.startListening(intent)
        progressBar.visibility = View.VISIBLE
    }

    override fun onResults(results: Bundle?) {
        val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val command = data?.get(0) ?: ""
        
        if (command.contains("open YouTube", true)) {
            val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else if (command.contains("scroll down", true)) {
            AssistantAccessibilityService.instance?.performScrollDown()
        } else {
            // Process other commands via AI
            processViaAI(command)
        }
        
        startListening() 
    }

    private fun processViaAI(text: String) {
        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "")
        
        if (apiKey.isNullOrEmpty()) return

        // Here we could add a Retrofit/OkHttp call to ChatGPT
        // For now, it detects intent and acts locally
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Orb pulses with voice volume
        val scale = 1.0f + (rmsdB / 20f)
        bubbleView.scaleX = scale
        bubbleView.scaleY = scale
    }

    override fun onError(error: Int) { 
        progressBar.visibility = View.INVISIBLE
        startListening() 
    }

    override fun onEndOfSpeech() { progressBar.visibility = View.INVISIBLE }
    override fun onReadyForSpeech(params: Bundle?) { progressBar.visibility = View.VISIBLE }
    override fun onBeginningOfSpeech() {}
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
