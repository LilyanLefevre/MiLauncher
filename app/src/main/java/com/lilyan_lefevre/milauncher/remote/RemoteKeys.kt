package com.lilyan_lefevre.milauncher.remote

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import com.lilyan_lefevre.milauncher.MainActivity

/** Turns remote button names into actions on the TV. */
internal class RemoteKeys(private val context: Context) {

    enum class Result { DONE, NEEDS_ACCESSIBILITY, UNKNOWN }

    private val audio = context.getSystemService(AudioManager::class.java)

    /** Arrows and OK need the accessibility service, and the D-pad global actions of Android 13. */
    val navigationAvailable: Boolean
        get() = RemoteAccessibilityService.instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun press(key: String): Result {
        // Volume and media keys work without any special permission.
        when (key) {
            "volume_up" -> return adjustVolume(AudioManager.ADJUST_RAISE)
            "volume_down" -> return adjustVolume(AudioManager.ADJUST_LOWER)
            "play_pause" -> return media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> return media(KeyEvent.KEYCODE_MEDIA_NEXT)
            "previous" -> return media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        val service = RemoteAccessibilityService.instance
        if (key == "home" && service == null) {
            // Home still works without the service: bring the launcher back.
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return Result.DONE
        }
        val action = globalAction(key) ?: return Result.UNKNOWN
        if (service == null || (action.needsTiramisu && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)) {
            return Result.NEEDS_ACCESSIBILITY
        }
        service.performGlobalAction(action.id)
        return Result.DONE
    }

    private fun adjustVolume(direction: Int): Result {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return Result.DONE
    }

    /** Sent to the app currently playing media: next video in YouTube, next track in Spotify… */
    private fun media(keyCode: Int): Result {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        return Result.DONE
    }

    private class GlobalAction(val id: Int, val needsTiramisu: Boolean = true)

    private fun globalAction(key: String): GlobalAction? = when (key) {
        "up" -> GlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_UP)
        "down" -> GlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN)
        "left" -> GlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_LEFT)
        "right" -> GlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT)
        "ok" -> GlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER)
        "back" -> GlobalAction(AccessibilityService.GLOBAL_ACTION_BACK, needsTiramisu = false)
        "home" -> GlobalAction(AccessibilityService.GLOBAL_ACTION_HOME, needsTiramisu = false)
        else -> null
    }
}
