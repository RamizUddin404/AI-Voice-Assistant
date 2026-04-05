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
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import androidx.core.app.NotificationCompat
import java.util.*

class FloatingBubbleService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var progressBar: ProgressBar
    private var isActiveMode = false // Standby vs Active

    override fun onCreate() {
        super.onCreate()
        
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::WakeLock")
        wakeLock.acquire()

        createNotificationChannel()
        startForeground(1, createNotification())

        setupOverlay()
        
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
        
        startStandbyListening()
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
        params.x = 100 ; params.y = 100

        bubbleView = LayoutInflater.from(this).inflate(R.layout.layout_orb_bubble, null)
        progressBar = bubbleView.findViewById(R.id.listening_waves)
        windowManager.addView(bubbleView, params)
    }

    private fun startStandbyListening() {
        isActiveMode = false
        progressBar.visibility = View.INVISIBLE
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechRecognizer.startListening(intent)
    }

    private fun startActiveListening() {
        isActiveMode = true
        progressBar.visibility = View.VISIBLE
        speak("Yes? I am listening.")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechRecognizer.startListening(intent)
    }

    override fun onResults(results: Bundle?) {
        val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = data?.get(0) ?: ""

        if (!isActiveMode) {
            // Standby Mode: Looking for Wake Word
            if (text.contains("Orb", true) || text.contains("Assistant", true) || text.contains("Hey", true)) {
                startActiveListening()
            } else {
                startStandbyListening()
            }
        } else {
            // Active Mode: Process Command
            processCommand(text)
            startStandbyListening() // Go back to standby after command
        }
    }

    private fun processCommand(text: String) {
        val lowerText = text.lowercase()
        
        when {
            lowerText.contains("open youtube") -> {
                speak("Opening YouTube for you, friend.")
                val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            lowerText.contains("scroll down") -> {
                speak("Scrolling down now.")
                AssistantAccessibilityService.instance?.performScrollDown()
            }
            else -> {
                // Friendly AI Response placeholder
                speak("That sounds interesting, but I need to be smarter to help with that. What else can I do?")
            }
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts.language = Locale.US
    }

    override fun onError(error: Int) { startStandbyListening() }
    override fun onEndOfSpeech() {}
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {
        if (isActiveMode) {
            val scale = 1.0f + (rmsdB / 20f)
            bubbleView.scaleX = scale
            bubbleView.scaleY = scale
        }
    }
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        tts.stop() ; tts.shutdown()
        speechRecognizer.destroy()
        windowManager.removeView(bubbleView)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("CHANNEL_ID", "AI Assistant", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("AI Friend Active")
            .setContentText("Say 'Orb' to talk to me.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }
}
