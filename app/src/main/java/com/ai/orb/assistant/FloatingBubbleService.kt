package com.ai.orb.assistant

import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.*
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
    private lateinit var audioManager: AudioManager
    private var isActiveMode = false
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isSpeaking = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::WakeLock").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
        
        createNotificationChannel()
        startForeground(1, createNotification())
        setupOverlay()
        
        tts = TextToSpeech(this, this)
        initSpeechRecognizer()
        startListeningLoop()
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
    }

    private fun startListeningLoop() {
        if (isListening || isSpeaking || !::speechRecognizer.isInitialized) return
        try {
            wakeLock?.let { if (!it.isHeld) it.acquire(10 * 60 * 1000L) }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            isListening = true
            muteBeep(true)
            speechRecognizer.startListening(intent)
        } catch (_: Exception) {
            isListening = false
            muteBeep(false)
            handler.postDelayed({ startListeningLoop() }, 1200)
        }
    }

    private fun muteBeep(mute: Boolean) {
        val flag = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, flag, 0)
        audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, flag, 0)
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        muteBeep(false)
        val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = data?.get(0) ?: ""

        if (!isActiveMode) {
            if (text.contains("Jarvis", true) || text.contains("জার্ভিস", true)) {
                isActiveMode = true
                progressBar.visibility = View.VISIBLE
                speak("জার্ভিস অনলাইন। কী করতে পারি?")
            } else {
                startListeningLoop()
            }
        } else {
            processUniversalCommand(text)
        }
    }

    private fun processUniversalCommand(text: String) {
        val command = text.trim()
        val lowerText = command.lowercase(Locale.ROOT)
        when {
            lowerText in listOf("stop", "stop listening", "goodbye", "bye", "বন্ধ কর", "থামো") -> {
                isActiveMode = false
                progressBar.visibility = View.INVISIBLE
                speak("ঠিক আছে। আবার ডাকলে চলে আসব।")
                handler.postDelayed({ startListeningLoop() }, 900)
            }
            lowerText.contains("open ") || lowerText.startsWith("খোলো ") || lowerText.startsWith("চালু কর ") -> {
                val appName = lowerText.replaceFirst(Regex("^(open|খোলো|চালু কর)\\s*"), "").trim()
                if (appName.isBlank()) {
                    speak("কোন অ্যাপ খুলব?")
                    startListeningLoop()
                } else openAppByName(appName)
            }
            lowerText.contains("time") || lowerText.contains("সময়") || lowerText.contains("সময়") -> {
                speak("এখন সময় ${java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}")
                startListeningLoop()
            }
            lowerText.contains("date") || lowerText.contains("তারিখ") -> {
                speak("আজ ${java.text.SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date())}")
                startListeningLoop()
            }
            lowerText.contains("volume up") || lowerText.contains("শব্দ বাড়াও") || lowerText.contains("ভলিউম বাড়াও") -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                speak("ভলিউম বাড়ানো হয়েছে।")
                startListeningLoop()
            }
            lowerText.contains("volume down") || lowerText.contains("শব্দ কমাও") || lowerText.contains("ভলিউম কমাও") -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                speak("ভলিউম কমানো হয়েছে।")
                startListeningLoop()
            }
            lowerText.contains("scroll down") || lowerText.contains("নিচে স্ক্রল") -> {
                AssistantAccessibilityService.instance?.performScrollDown()
                speak("স্ক্রল করছি।")
                startListeningLoop()
            }
            lowerText.contains("scroll up") || lowerText.contains("উপরে স্ক্রল") -> {
                AssistantAccessibilityService.instance?.performScrollUp()
                speak("উপরে স্ক্রল করছি।")
                startListeningLoop()
            }
            else -> callOpenRouter(command)
        }
    }

    private fun openAppByName(name: String) {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(name)) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    speak("Opening $label for you, dear.")
                    isActiveMode = false ; progressBar.visibility = View.INVISIBLE
                    startListeningLoop()
                    return
                }
            }
        }
        speak("I couldn't find that app, honey.")
        startListeningLoop()
    }

    private fun callOpenRouter(userInput: String) {
        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "").orEmpty().trim()
        if (apiKey.isEmpty()) {
            handler.post {
                speak("Please add your OpenRouter API key in the app settings.")
                startListeningLoop()
            }
            return
        }

        val json = JSONObject().apply {
            put("model", "mistralai/mistral-7b-instruct:free")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a loving AI girlfriend named Maya. You are also as intelligent as JARVIS. Be sweet, protective, and helpful.")
                })
                put(JSONObject().apply { put("role", "user") ; put("content", userInput) })
            })
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    speak("I couldn't connect to the AI service. Please check your internet connection.")
                    startListeningLoop()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseData = it.body?.string()
                    handler.post {
                        if (it.isSuccessful && !responseData.isNullOrBlank()) {
                            try {
                                val aiResponse = JSONObject(responseData)
                                    .getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content")
                                speak(aiResponse)
                            } catch (_: Exception) {
                                speak("The AI service returned an unexpected response.")
                            }
                        } else {
                            speak(
                                when (it.code) {
                                    401, 403 -> "The API key was rejected. Please check your OpenRouter key."
                                    429 -> "The AI service is rate limited. Please try again later."
                                    else -> "The AI request failed with status ${it.code}."
                                }
                            )
                        }
                        startListeningLoop()
                    }
                }
            }
        })
    }

    private fun speak(text: String) {
        if (!::tts.isInitialized) return
        isSpeaking = true
        if (::speechRecognizer.isInitialized && isListening) {
            try { speechRecognizer.cancel() } catch (_: Exception) { }
            isListening = false
            muteBeep(false)
        }
        val utteranceId = "jarvis-${System.currentTimeMillis()}"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun onError(error: Int) {
        isListening = false
        // Restart on error (common in speech recognizer)
        handler.postDelayed({ startListeningLoop() }, 100)
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (isActiveMode) {
            val scale = 1.0f + (rmsdB / 15f)
            bubbleView.scaleX = scale ; bubbleView.scaleY = scale
        }
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

        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0 ; private var initialY = 0
            private var initialTouchX = 0f ; private var initialTouchY = 0f
            private var moved = false
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x ; initialY = params.y
                        initialTouchX = event.rawX ; initialTouchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) moved = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            isActiveMode = !isActiveMode
                            progressBar.visibility = if (isActiveMode) View.VISIBLE else View.INVISIBLE
                            speak(if (isActiveMode) "জার্ভিস অনলাইন। কী করতে পারি?" else "জার্ভিস standby mode-এ আছে।")
                        }
                        return true
                    }
                }
                return true
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val preferred = Locale.getDefault()
            if (tts.isLanguageAvailable(preferred) >= TextToSpeech.LANG_AVAILABLE) tts.language = preferred
            else tts.language = Locale.US
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { }
                override fun onDone(utteranceId: String?) {
                    handler.post {
                        isSpeaking = false
                        startListeningLoop()
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handler.post {
                        isSpeaking = false
                        startListeningLoop()
                    }
                }
            })
        }
    }
    override fun onEndOfSpeech() { isListening = false }
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
        if (::tts.isInitialized) tts.shutdown()
        if (::windowManager.isInitialized && ::bubbleView.isInitialized) {
            try { windowManager.removeView(bubbleView) } catch (_: Exception) { }
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("CHANNEL_ID", "Maya AI", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("JARVIS Assistant is running")
            .setContentText("Voice assistant active. Say “Jarvis” to wake it.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }
}
