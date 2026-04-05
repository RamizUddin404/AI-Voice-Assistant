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

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnStart = findViewById<Button>(R.id.btnStart)

        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        etApiKey.setText(prefs.getString("api_key", ""))

        btnStart.setOnClickListener {
            val key = etApiKey.text.toString()
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter an API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("api_key", key).apply()
            checkAndRequestPermissions()
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
            !isAccessibilityServiceEnabled() -> {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Enable 'AI Assistant Accessibility'", Toast.LENGTH_LONG).show()
            }
            !isIgnoringBatteryOptimizations() -> {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
            else -> {
                startService(Intent(this, FloatingBubbleService::class.java))
                Toast.makeText(this, "Maya is now active in background!", Toast.LENGTH_SHORT).show()
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
