package com.lilyan_lefevre.milauncher.data

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.lilyan_lefevre.milauncher.remote.RemoteAccessibilityService

/** Special permissions granted in the system settings rather than through a runtime dialog. */
object Permissions {

    /** Recency order and screen time. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        return appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    /** Arrows, OK and Back from the phone remote. */
    fun hasRemoteAccessibility(context: Context): Boolean {
        val component = ComponentName(context, RemoteAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.split(':')?.any { it.equals(component, ignoreCase = true) } == true
    }

    // Android TV only has the list screens for these two: there is no per-app page to deep link to.
    fun usageAccessSettings() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
