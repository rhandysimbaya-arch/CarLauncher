package com.rb.carlauncher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Only used to press Android's "toggle split screen" action. It ignores all screen content. */
class SplitService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        var instance: SplitService? = null
        fun toggleSplit(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) ?: false
    }
}
