package com.ai.orb.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnStart = findViewById<Button>(R.id.btnStart)

        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        etApiKey.setText(prefs.getString("api_key", ""))

        btnStart.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter an API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("api_key", key).apply()
            checkAndRequestPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkAndRequestPermissions()
            } else {
                Toast.makeText(this, "Microphone permission is required for voice commands.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        when {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 102)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this) -> {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
                Toast.makeText(this, "Allow 'Display over other apps'", Toast.LENGTH_LONG).show()
            }
            else -> {
                val serviceIntent = Intent(this, FloatingBubbleService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(this, serviceIntent)
                else startService(serviceIntent)

                if (!isAccessibilityServiceEnabled()) {
                    Toast.makeText(
                        this,
                        "JARVIS started. Enable its Accessibility Service only for scroll/gesture commands.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, "JARVIS voice assistant started.", Toast.LENGTH_SHORT).show()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()) {
                    Toast.makeText(this, "For better background reliability, allow JARVIS to run without battery optimization.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return AssistantAccessibilityService.instance != null
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
    }
}
