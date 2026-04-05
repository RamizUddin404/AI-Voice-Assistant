package com.ai.orb.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
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
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*

class FloatingBubbleService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var progressBar: ProgressBar
    private var isActiveMode = false
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private var silenceTimer: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::WakeLock").acquire()
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
        params.x = 150 ; params.y = 150
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

    private fun startActiveListening(initialGreeting: String?) {
        isActiveMode = true
        progressBar.visibility = View.VISIBLE
        if (initialGreeting != null) speak(initialGreeting)
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechRecognizer.startListening(intent)
        
        resetSilenceTimer()
    }

    private fun resetSilenceTimer() {
        silenceTimer?.let { handler.removeCallbacks(it) }
        silenceTimer = Runnable { startStandbyListening() }
        handler.postDelayed(silenceTimer!!, 15000) // 15 seconds of silence before standby
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onResults(results: Bundle?) {
        val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = data?.get(0) ?: ""

        if (!isActiveMode) {
            if (text.contains("Orb", true) || text.contains("Hey", true) || text.contains("Sweetie", true) || text.contains("Hi", true)) {
                startActiveListening("Yes baby, I'm here. What's on your mind?")
            } else {
                startStandbyListening()
            }
        } else {
            processCommand(text)
        }
    }

    private fun processCommand(text: String) {
        val lowerText = text.lowercase()
        when {
            lowerText.contains("open youtube") -> {
                speak("Sure dear, opening YouTube for you.")
                val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                startActiveListening(null) // Stay active for next command
            }
            lowerText.contains("scroll down") -> {
                speak("Okay, scrolling down.")
                AssistantAccessibilityService.instance?.performScrollDown()
                startActiveListening(null)
            }
            lowerText.contains("bye") || lowerText.contains("stop") -> {
                speak("Okay, talk to you later. Love you!")
                startStandbyListening()
            }
            else -> {
                callOpenRouter(text)
            }
        }
    }

    private fun callOpenRouter(userInput: String) {
        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: return

        val json = JSONObject().apply {
            put("model", "mistralai/mistral-7b-instruct:free") // Fast and free model
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are the user's loving, sweet, and caring AI girlfriend named Maya. Be very friendly, use sweet words, ask about their day, and be supportive. Keep responses conversational and not too long.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userInput)
                })
            })
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                speak("I'm having a little trouble connecting. Try again?")
                startActiveListening(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (response.isSuccessful && responseData != null) {
                    val aiResponse = JSONObject(responseData)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    speak(aiResponse)
                }
                // Stay in active mode to continue conversation
                handler.post { startActiveListening(null) }
            }
        })
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            // Set a slightly higher pitch for a more feminine voice if available
            tts.setPitch(1.2f)
            tts.setSpeechRate(1.0f)
        }
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (isActiveMode) {
            val scale = 1.0f + (rmsdB / 15f)
            bubbleView.scaleX = scale ; bubbleView.scaleY = scale
        }
    }

    override fun onError(error: Int) { 
        if (isActiveMode) startActiveListening(null) else startStandbyListening()
    }

    override fun onEndOfSpeech() {}
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
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
            val channel = NotificationChannel("CHANNEL_ID", "AI Girlfriend", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("Maya is here for you")
            .setContentText("Say 'Hey' to talk to your AI girlfriend.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }
}
