package com.lilyan_lefevre.milauncher.remote

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Lets the phone remote move the focus (arrows, OK, Back, Home) in any app. Declared without event
 * types or window content access (res/xml/remote_accessibility_service.xml): it reads nothing on screen
 * and only performs the global actions the remote asks for.
 */
class RemoteAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        /** The running service, null while it is turned off in the accessibility settings. */
        @Volatile
        var instance: RemoteAccessibilityService? = null
            private set
    }
}
