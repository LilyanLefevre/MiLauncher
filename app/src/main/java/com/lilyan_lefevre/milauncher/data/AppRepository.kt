package com.lilyan_lefevre.milauncher.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream

data class InstalledApp(
    val packageName: String,
    val label: String,
    /** 16:9 TV banner when the app provides one, otherwise its icon. */
    val image: ImageBitmap,
    val hasBanner: Boolean,
    /** False for system apps, which cannot be uninstalled. */
    val canUninstall: Boolean = false
)

class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    /** Launchable apps, most recently used first, then alphabetically. */
    fun loadApps(): List<InstalledApp> {
        val order = recencyOrder()
        return launcherActivities()
            .map { it.toInstalledApp() }
            .sortedWith(compareBy(order) { it.packageName to it.label })
    }

    /** PNG of the app banner, or of its icon when it has none, at most [maxWidth] pixels wide. */
    fun imagePng(packageName: String, maxWidth: Int = 320): ByteArray? {
        val info = launcherActivities().firstOrNull { it.packageName == packageName } ?: return null
        val drawable = info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm) ?: info.loadIcon(pm)
        val sourceWidth = drawable.intrinsicWidth.coerceAtLeast(1)
        val width = sourceWidth.coerceAtMost(maxWidth)
        val height = (drawable.intrinsicHeight.coerceAtLeast(1) * width / sourceWidth).coerceAtLeast(1)
        val bitmap = drawable.toBitmap(width, height)
        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
    }

    /** Package name to label of every launchable app, without loading banners, in the same order as [loadApps]. */
    fun labels(): Map<String, String> {
        val order = recencyOrder()
        return launcherActivities()
            .map { it.packageName to it.loadLabel(pm).toString() }
            .sortedWith(order)
            .toMap()
    }

    /** Most recently used first, then alphabetically, on (package name, label) pairs. */
    private fun recencyOrder(): Comparator<Pair<String, String>> {
        val lastUsed = lastUsedTimes()
        return compareByDescending<Pair<String, String>> { lastUsed[it.first] ?: 0L }.thenBy { it.second.lowercase() }
    }

    /** One launcher activity per package, TV entry points first, excluding this launcher. */
    private fun launcherActivities(): List<ActivityInfo> {
        val seen = mutableSetOf(context.packageName)
        return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(it), 0) }
            .map { it.activityInfo }
            .filter { seen.add(it.packageName) }
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
            canUninstall = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
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
