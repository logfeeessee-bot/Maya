package com.maya.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MayaAccessibilityService : AccessibilityService() {

    companion object { @Volatile var inst: MayaAccessibilityService? = null }

    override fun onServiceConnected() { inst = this }
    override fun onUnbind(intent: Intent?): Boolean { inst = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun clickText(txt: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(txt)) {
            var x: AccessibilityNodeInfo? = n
            while (x != null && !x.isClickable) x = x.parent
            if (x != null && x.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    /** up = true → ungli neeche se upar (content neeche scroll hota hai) */
    fun swipe(up: Boolean) {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val a = dm.heightPixels * 0.7f
        val b = dm.heightPixels * 0.3f
        val path = Path().apply { if (up) { moveTo(x, a); lineTo(x, b) } else { moveTo(x, b); lineTo(x, a) } }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300)).build(), null, null)
    }
}
