package com.maya.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MayaAccessibilityService : AccessibilityService() {

    companion object { @Volatile var inst: MayaAccessibilityService? = null }

    private val lastNodes = ArrayList<AccessibilityNodeInfo>()

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

    private fun findEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findEditable(c)?.let { return it }
        }
        return null
    }

    private fun inputNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root)
    }

    /** Jo text box khula ho usme likhta hai */
    fun typeText(txt: String): Boolean {
        val node = inputNode() ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, txt)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Keyboard ka Enter / Search dabata hai (Android 11 ya naye par) */
    fun pressEnter(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val node = inputNode() ?: return false
        return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
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

    // =====================================================================
    //  Agent ke liye: screen padhna aur number se tap / likhna
    // =====================================================================

    private fun labelOf(n: AccessibilityNodeInfo, depth: Int = 0): String {
        val own = (n.text ?: n.contentDescription ?: n.hintText)?.toString()?.trim() ?: ""
        if (own.isNotEmpty() || depth >= 2) return own
        val sb = StringBuilder()
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            val t = labelOf(c, depth + 1)
            if (t.isNotEmpty()) { if (sb.isNotEmpty()) sb.append(' '); sb.append(t) }
        }
        return sb.toString()
    }

    private fun collect(n: AccessibilityNodeInfo, sb: StringBuilder) {
        if (lastNodes.size >= 60 || sb.length > 3200) return
        if (n.isVisibleToUser) {
            val kind = if (n.isEditable) "edit" else if (n.isClickable) "click" else ""
            if (kind.isNotEmpty()) {
                sb.append(lastNodes.size).append('|').append(kind).append('|').append(labelOf(n).take(40)).append('\n')
                lastNodes.add(n)
            } else if (n.childCount == 0) {
                val t = labelOf(n)
                if (t.isNotEmpty()) sb.append("-|text|").append(t.take(40)).append('\n')
            }
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            collect(c, sb)
        }
    }

    /** Screen ke tap/likhne layak hisse "number|kind|naam" ki list mein */
    fun readScreen(): String {
        lastNodes.clear()
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collect(root, sb)
        return sb.toString()
    }

    fun clickIndex(i: Int): Boolean {
        val n = lastNodes.getOrNull(i) ?: return false
        var x: AccessibilityNodeInfo? = n
        while (x != null && !x.isClickable) x = x.parent
        return x?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    fun typeIndex(i: Int, txt: String): Boolean {
        val n = lastNodes.getOrNull(i) ?: inputNode() ?: return false
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, txt)
        }
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
