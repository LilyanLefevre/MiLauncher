package com.lilyan_lefevre.milauncher.data

import android.content.Context
import androidx.core.content.edit
import java.security.SecureRandom

/** User choices: ordered favorites and hidden apps. */
class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = prefs.getString(KEY_FAVORITES, "")!!.split(',').filter { it.isNotEmpty() }
        set(value) = prefs.edit { putString(KEY_FAVORITES, value.joinToString(",")) }

    var hidden: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN, emptySet())!!
        set(value) = prefs.edit { putStringSet(KEY_HIDDEN, value) }

    var remoteEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMOTE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_REMOTE_ENABLED, value) }

    /** Pairing code of the phone remote, created on first use. */
    val remotePin: String
        get() = prefs.getString(KEY_REMOTE_PIN, null) ?: newRemotePin()

    fun newRemotePin(): String {
        val pin = "%06d".format(SecureRandom().nextInt(1_000_000))
        prefs.edit { putString(KEY_REMOTE_PIN, pin) }
        return pin
    }

    private companion object {
        const val KEY_FAVORITES = "favs"
        const val KEY_HIDDEN = "hidden"
        const val KEY_REMOTE_ENABLED = "remote_enabled"
        const val KEY_REMOTE_PIN = "remote_pin"
    }
}
