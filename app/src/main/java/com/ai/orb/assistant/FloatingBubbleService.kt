package com.ai.orb.assistant

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.*
import android.widget.ProgressBar
import android.widget.Toast
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
    private var ttsReady = false
    private var nextBanglaRecognition = Locale.getDefault().language == "bn"
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var isSpeaking = false
    private var overlayAdded = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            setupOverlay()
        } catch (_: RuntimeException) {
            stopSelf()
            return
        }

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JARVIS::WakeLock").apply {
            setReferenceCounted(false)
        }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            isActiveMode = false
            Toast.makeText(this, "JARVIS needs microphone permission. Open the app and grant it.", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        try {
            wakeLock?.let { if (!it.isHeld) it.acquire(10 * 60 * 1000L) }
            val languageTag = if (nextBanglaRecognition) "bn-BD" else "en-US"
            nextBanglaRecognition = !nextBanglaRecognition
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
            isListening = true
            speechRecognizer.startListening(intent)
        } catch (_: Exception) {
            isListening = false
            handler.removeCallbacks(restartListeningRunnable)
            handler.postDelayed(restartListeningRunnable, 1200)
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.filter { it.isNotBlank() }.orEmpty()
        if (candidates.isEmpty()) {
            handler.postDelayed({ startListeningLoop() }, 400)
            return
        }

        if (!isActiveMode) {
            val wakeText = candidates.firstOrNull { containsWakeWord(it) }
            if (wakeText != null) {
                isActiveMode = true
                progressBar.visibility = View.VISIBLE
                val commandAfterWake = wakeText
                    .replace(Regex("(?i)jarvis"), "")
                    .replace("জার্ভিস", "")
                    .replace("জারভিস", "")
                    .trim(' ', ',', '.', '!', '?')
                if (commandAfterWake.isBlank()) {
                    speak("জার্ভিস অনলাইন। কী করতে পারি?")
                } else {
                    processUniversalCommand(commandAfterWake)
                }
            } else {
                startListeningLoop()
            }
        } else {
            processUniversalCommand(candidates.first())
        }
    }

    private fun containsWakeWord(text: String): Boolean {
        return text.contains("Jarvis", true) ||
            text.contains("জার্ভিস") || text.contains("জারভিস")
    }

    private fun processUniversalCommand(text: String) {
        val command = text.trim()
        val lowerText = command.lowercase(Locale.ROOT)
        when {
            lowerText in listOf("stop", "stop listening", "goodbye", "bye", "বন্ধ কর", "থামো", "শোনা বন্ধ কর") -> {
                isActiveMode = false
                progressBar.visibility = View.INVISIBLE
                speak("ঠিক আছে। আবার ডাকলে চলে আসব।")
            }
            lowerText.contains("wifi") || lowerText.contains("wi-fi") ||
                lowerText.contains("ওয়াইফাই") || lowerText.contains("ওয়াইফাই") -> {
                openSystemSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi settings")
            }
            lowerText.contains("bluetooth") || lowerText.contains("ব্লুটুথ") -> {
                openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth settings")
            }
            isDialCommand(lowerText) -> openDialer(command)
            isMessageCommand(lowerText) -> openMessageComposer(command)
            lowerText.contains("open ") || lowerText.startsWith("খোলো ") ||
                lowerText.startsWith("চালু কর ") || lowerText.startsWith("খুলে দাও ") -> {
                val appName = lowerText.replaceFirst(Regex("^(open|খোলো|চালু কর|খুলে দাও)\\s*"), "").trim()
                if (appName.isBlank()) {
                    speak("কোন অ্যাপ খুলব?")
                } else {
                    openAppByName(appName)
                }
            }
            lowerText.contains("time") || lowerText.contains("সময়") || lowerText.contains("সময়") -> {
                speak("এখন সময় " + java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()))
            }
            lowerText.contains("date") || lowerText.contains("তারিখ") -> {
                speak("আজ " + java.text.SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date()))
            }
            lowerText.contains("volume up") || lowerText.contains("শব্দ বাড়াও") ||
                lowerText.contains("ভলিউম বাড়াও") || lowerText.contains("ভলিউম বাড়াও") -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                speak("ভলিউম বাড়ানো হয়েছে।")
            }
            lowerText.contains("volume down") || lowerText.contains("শব্দ কমাও") ||
                lowerText.contains("ভলিউম কমাও") -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                speak("ভলিউম কমানো হয়েছে।")
            }
            lowerText.contains("scroll down") || lowerText.contains("নিচে স্ক্রল") -> {
                val accessibility = AssistantAccessibilityService.instance
                if (accessibility == null) {
                    speak("স্ক্রল করতে Settings থেকে JARVIS Accessibility Service চালু করুন।")
                } else {
                    accessibility.performScrollDown()
                    speak("স্ক্রল করছি।")
                }
            }
            lowerText.contains("scroll up") || lowerText.contains("উপরে স্ক্রল") -> {
                val accessibility = AssistantAccessibilityService.instance
                if (accessibility == null) {
                    speak("স্ক্রল করতে Settings থেকে JARVIS Accessibility Service চালু করুন।")
                } else {
                    accessibility.performScrollUp()
                    speak("উপরে স্ক্রল করছি।")
                }
            }
            else -> callOpenRouter(command)
        }
    }

    private fun isDialCommand(text: String): Boolean {
        return text.startsWith("call ") || text.startsWith("dial ") ||
            text.startsWith("কল কর") || text.startsWith("ফোন কর") || text == "open phone"
    }

    private fun openDialer(command: String) {
        val number = Regex("\\+?[0-9][0-9 ()-]{4,}").find(command)?.value
            ?.replace(Regex("[ ()-]"), "").orEmpty()
        val uri = if (number.isBlank()) Uri.parse("tel:") else Uri.parse("tel:" + Uri.encode(number))
        try {
            startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            speak("ডায়ালার খুলেছি। কল করার আগে নম্বরটি যাচাই করুন।")
        } catch (_: Exception) {
            speak("ফোনের ডায়ালার খুলতে পারিনি।")
        }
    }

    private fun isMessageCommand(text: String): Boolean {
        return text.startsWith("text ") || text.startsWith("sms ") ||
            text.startsWith("message ") || text.startsWith("মেসেজ") ||
            text.startsWith("এসএমএস") || text.startsWith("বার্তা পাঠাও")
    }

    private fun openMessageComposer(command: String) {
        val number = Regex("\\+?[0-9][0-9 ()-]{4,}").find(command)?.value
            ?.replace(Regex("[ ()-]"), "").orEmpty()
        val uri = if (number.isBlank()) Uri.parse("smsto:") else Uri.parse("smsto:" + Uri.encode(number))
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            speak("মেসেজ অ্যাপ খুলেছি। পাঠানোর আগে মেসেজটি যাচাই করুন।")
        } catch (_: Exception) {
            speak("মেসেজ অ্যাপ খুলতে পারিনি।")
        }
    }

    private fun openSystemSettings(action: String, label: String) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            speak(label + " খুলেছি। পরিবর্তন করতে Android Settings ব্যবহার করুন।")
        } catch (_: Exception) {
            speak("এই সেটিংস খুলতে পারিনি।")
        }
    }

    private fun openAppByName(name: String) {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
            if (label.contains(name)) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    try {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        isActiveMode = false
                        progressBar.visibility = View.INVISIBLE
                        speak("Opening " + label + ".")
                    } catch (_: Exception) {
                        speak("এই অ্যাপটি খুলতে পারিনি।")
                    }
                    return
                }
            }
        }
        speak("অ্যাপটি খুঁজে পাইনি। নামটি আবার বলুন।")
    }

    private fun callOpenRouter(userInput: String) {
        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "").orEmpty().trim()
        if (apiKey.isEmpty()) {
            speak("OpenRouter API key দিতে অ্যাপটি খুলুন।")
            return
        }

        val json = JSONObject().apply {
            put("model", "openrouter/free")
            put("max_tokens", 300)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are JARVIS, a concise and capable Bangla-English Android assistant. Reply in the language the user used. Give clear practical answers. Never claim you changed a phone setting, placed a call, sent a message, or performed a gesture unless the app explicitly confirms it. For phone actions, explain that Android may require user permission or confirmation.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userInput)
                })
            })
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("X-OpenRouter-Title", "JARVIS AI Assistant")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    speak("AI সার্ভিসে সংযোগ হয়নি। ইন্টারনেট পরীক্ষা করুন।")
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
                                speak("AI থেকে সঠিক উত্তর পাওয়া যায়নি।")
                            }
                        } else {
                            speak(when (it.code) {
                                401, 403 -> "OpenRouter API key সঠিক নয়।"
                                402 -> "OpenRouter account-এ billing বা balance পরীক্ষা করুন।"
                                429 -> "AI সার্ভিসে এখন বেশি অনুরোধ। একটু পরে চেষ্টা করুন।"
                                else -> "AI অনুরোধ ব্যর্থ হয়েছে। Status " + it.code + "."
                            })
                        }
                    }
                }
            }
        })
    }

    private fun speak(text: String) {
        if (!::tts.isInitialized) return
        if (!ttsReady) {
            handler.postDelayed({ speak(text) }, 500)
            return
        }
        isSpeaking = true
        if (::speechRecognizer.isInitialized && isListening) {
            try { speechRecognizer.cancel() } catch (_: Exception) { }
            isListening = false
        }
        val utteranceId = "jarvis-" + System.currentTimeMillis()
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            isSpeaking = false
            handler.postDelayed({ startListeningLoop() }, 500)
        }
    }

    override fun onError(error: Int) {
        isListening = false
        // Use only error constants present on Android 9; newer constants can crash older devices.
        val delay = when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1200L
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 1800L
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 2500L
            else -> 700L
        }
        handler.removeCallbacks(restartListeningRunnable)
        handler.postDelayed(restartListeningRunnable, delay)
    }

    private val restartListeningRunnable = Runnable {
        if (!isSpeaking) startListeningLoop()
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (isActiveMode && ::bubbleView.isInitialized) {
            val scale = (1.0f + (rmsdB / 15f)).coerceIn(0.9f, 1.25f)
            bubbleView.scaleX = scale
            bubbleView.scaleY = scale
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
        params.x = 150
        params.y = 150
        bubbleView = LayoutInflater.from(this).inflate(R.layout.layout_orb_bubble, null)
        progressBar = bubbleView.findViewById(R.id.listening_waves)
        windowManager.addView(bubbleView, params)
        overlayAdded = true

        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var moved = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) moved = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        try { windowManager.updateViewLayout(bubbleView, params) } catch (_: Exception) { }
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
            val bangla = Locale("bn", "BD")
            val preferred = Locale.getDefault()
            val selected = if (tts.isLanguageAvailable(preferred) >= TextToSpeech.LANG_AVAILABLE) preferred else bangla
            if (tts.isLanguageAvailable(selected) >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = selected
            } else {
                tts.language = Locale.US
            }
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
            ttsReady = true
        } else {
            Toast.makeText(this, "Text-to-speech is unavailable on this phone.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onEndOfSpeech() { isListening = false }
    override fun onReadyForSpeech(params: Bundle?) { }
    override fun onBeginningOfSpeech() { }
    override fun onBufferReceived(buffer: ByteArray?) { }
    override fun onPartialResults(partialResults: Bundle?) { }
    override fun onEvent(eventType: Int, params: Bundle?) { }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::speechRecognizer.isInitialized) {
            try { speechRecognizer.cancel() } catch (_: Exception) { }
            speechRecognizer.destroy()
        }
        if (::tts.isInitialized) tts.shutdown()
        if (overlayAdded && ::windowManager.isInitialized && ::bubbleView.isInitialized) {
            try { windowManager.removeView(bubbleView) } catch (_: Exception) { }
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("CHANNEL_ID", "JARVIS AI", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("JARVIS Assistant is running")
            .setContentText("Voice listener active. Say “Jarvis” to wake it.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }
}
