package com.ai.orb.assistant

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Auto-Start attempt if already granted
        if (hasAllPermissions()) {
            startService(Intent(this, FloatingBubbleService::class.java))
            finish()
        }

        setContentView(R.layout.activity_main)
        val btnStart = findViewById<Button>(R.id.btnStart)
        btnStart.setOnClickListener {
            checkPermissionsAndStart()
        }
    }

    private fun hasAllPermissions(): Boolean {
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        return overlay && isAccessibilityServiceEnabled()
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 101)
            Toast.makeText(this, "Enable 'Display over other apps' permission", Toast.LENGTH_LONG).show()
        } else if (!isAccessibilityServiceEnabled()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable 'AI Assistant Accessibility' service", Toast.LENGTH_LONG).show()
        } else {
            startService(Intent(this, FloatingBubbleService::class.java))
            Toast.makeText(this, "AI Assistant Started", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return AssistantAccessibilityService.instance != null
    }
}
