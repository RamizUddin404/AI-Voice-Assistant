package com.ai.orb.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnStart = findViewById<Button>(R.id.btnStart)

        // Load existing key
        val prefs = getSharedPreferences("assistant_prefs", Context.MODE_PRIVATE)
        etApiKey.setText(prefs.getString("api_key", ""))

        btnStart.setOnClickListener {
            val key = etApiKey.text.toString()
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter an API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Save key
            prefs.edit().putString("api_key", key).apply()
            
            checkPermissionsAndStart()
        }
    }

    private fun checkPermissionsAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 102)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 101)
        } else if (!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        } else {
            startService(Intent(this, FloatingBubbleService::class.java))
            finish()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return AssistantAccessibilityService.instance != null
    }
}
