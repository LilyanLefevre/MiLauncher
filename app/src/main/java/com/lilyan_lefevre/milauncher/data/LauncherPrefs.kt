package com.lilyan_lefevre.milauncher.data

import android.content.Context
import androidx.core.content.edit

/** Choix de l'utilisateur : favoris (ordonnés) et apps masquées. */
class LauncherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = prefs.getString(KEY_FAVORITES, "")!!.split(',').filter { it.isNotEmpty() }
        set(value) = prefs.edit { putString(KEY_FAVORITES, value.joinToString(",")) }

    var hidden: Set<String>
        get() = prefs.getStringSet(KEY_HIDDEN, emptySet())!!
        set(value) = prefs.edit { putStringSet(KEY_HIDDEN, value) }

    private companion object {
        const val KEY_FAVORITES = "favs"
        const val KEY_HIDDEN = "hidden"
    }
}
