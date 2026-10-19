package com.ai.orb.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AssistantAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used, but must be implemented
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun performScrollDown() {
        dispatchScroll(0f, 1f)
    }

    fun performScrollUp() {
        dispatchScroll(1f, 0f)
    }

    private fun dispatchScroll(from: Float, to: Float) {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val startY = if (from > to) metrics.heightPixels * 0.75f else metrics.heightPixels * 0.3f
        val endY = if (from > to) metrics.heightPixels * 0.3f else metrics.heightPixels * 0.75f
        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun performClick(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        dispatchGesture(gesture, null, null)
    }
}
