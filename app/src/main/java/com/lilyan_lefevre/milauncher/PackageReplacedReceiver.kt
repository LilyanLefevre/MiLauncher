package com.lilyan_lefevre.milauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Sent by the system right after this app is updated. Receiving it starts the process, and with it
 * [LauncherApplication], which brings the phone remote back without waiting for the launcher to show.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
