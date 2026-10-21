package com.lilyan_lefevre.milauncher.data

import android.content.pm.ApplicationInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class InstalledApp(
    val packageName: String,
    val label: String,
    /** 16:9 TV banner when the app provides one, otherwise its icon. */
    val image: ImageBitmap,
    val hasBanner: Boolean,
    /** False for system apps, which cannot be uninstalled. */
    val canUninstall: Boolean = false,
)

class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    /** Launchable apps, most recently used first, then alphabetically. */
    fun loadApps(): List<InstalledApp> {
        val lastUsed = lastUsedTimes()
        val seen = mutableSetOf(context.packageName)
        return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
            .map { it.activityInfo }
            .filter { seen.add(it.packageName) }
            .map { it.toInstalledApp() }
            .sortedWith(compareByDescending<InstalledApp> { lastUsed[it.packageName] ?: 0L }.thenBy { it.label.lowercase() })
    }

    fun launch(packageName: String) {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun ActivityInfo.toInstalledApp(): InstalledApp {
        val banner = loadBanner(pm) ?: applicationInfo.loadBanner(pm)
        val drawable = banner ?: loadIcon(pm)
        return InstalledApp(
            packageName = packageName,
            label = loadLabel(pm).toString(),
            image = drawable.toBitmap().asImageBitmap(),
            hasBanner = banner != null,
            canUninstall = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0,
        )
    }

    /** Empty until the permission is granted through adb (see README). */
    private fun lastUsedTimes(): Map<String, Long> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        return usm.queryAndAggregateUsageStats(now - THIRTY_DAYS_MS, now).mapValues { it.value.lastTimeUsed }
    }

    private companion object {
        const val THIRTY_DAYS_MS = 30L * 24 * 3600 * 1000
    }
}
