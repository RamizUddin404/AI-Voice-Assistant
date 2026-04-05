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

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AIAssistant::WakeLock").acquire()
        
        createNotificationChannel()
        startForeground(1, createNotification())
        setupOverlay()
        
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
        startListeningLoop()
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

        // Make Bubble Draggable
        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0 ; private var initialY = 0
            private var initialTouchX = 0f ; private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x ; initialY = params.y
                        initialTouchX = event.rawX ; initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun startListeningLoop() {
        muteBeep(true)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        speechRecognizer.startListening(intent)
    }

    private fun muteBeep(mute: Boolean) {
        if (mute) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
        } else {
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
        }
    }

    override fun onResults(results: Bundle?) {
        muteBeep(false)
        val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = data?.get(0) ?: ""

        if (!isActiveMode) {
            if (text.contains("Orb", true) || text.contains("Maya", true) || text.contains("Hey", true)) {
                isActiveMode = true
                progressBar.visibility = View.VISIBLE
                speak("Yes baby, I'm listening. Tell me everything.")
            } else {
                startListeningLoop()
            }
        } else {
            processUniversalCommand(text)
        }
    }

    private fun processUniversalCommand(text: String) {
        val lowerText = text.lowercase()
        when {
            lowerText.contains("open") -> {
                val appName = lowerText.replace("open", "").trim()
                openAppByName(appName)
            }
            lowerText.contains("home") -> {
                AssistantAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                speak("Going home, dear.")
                exitActiveMode()
            }
            lowerText.contains("back") -> {
                AssistantAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                speak("Done.")
                startListeningLoop()
            }
            lowerText.contains("bye") || lowerText.contains("stop") -> {
                speak("Bye love, call me anytime.")
                exitActiveMode()
            }
            else -> {
                callOpenRouter(text)
            }
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
                    speak("Opening $label for you.")
                    exitActiveMode()
                    return
                }
            }
        }
        speak("I couldn't find $name on your phone, honey.")
        startListeningLoop()
    }

    private fun exitActiveMode() {
        isActiveMode = false
        progressBar.visibility = View.INVISIBLE
        startListeningLoop()
    }

    private fun callOpenRouter(userInput: String) {
        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: return

        val json = JSONObject().apply {
            put("model", "mistralai/mistral-7b-instruct:free")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a loving AI girlfriend named Maya. Be sweet, JARVIS-like in efficiency, and very helpful.")
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
            override fun onFailure(call: Call, e: IOException) { startListeningLoop() }
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (response.isSuccessful && responseData != null) {
                    val aiResponse = JSONObject(responseData).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    speak(aiResponse)
                }
                handler.post { startListeningLoop() }
            }
        })
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts.language = Locale.US }
    override fun onError(error: Int) { startListeningLoop() }
    override fun onRmsChanged(rmsdB: Float) {
        if (isActiveMode) {
            val scale = 1.0f + (rmsdB / 15f)
            bubbleView.scaleX = scale ; bubbleView.scaleY = scale
        }
    }
    // ... rest of the speech listener methods calling startListeningLoop() on finish
    override fun onEndOfSpeech() {} ; override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {} ; override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onPartialResults(partialResults: Bundle?) {} ; override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown() ; speechRecognizer.destroy()
        windowManager.removeView(bubbleView)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("CHANNEL_ID", "AI Maya", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "CHANNEL_ID")
            .setContentTitle("Maya is listening...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }
}
