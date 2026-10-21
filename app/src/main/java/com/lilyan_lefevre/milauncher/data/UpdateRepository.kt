package com.lilyan_lefevre.milauncher.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.lilyan_lefevre.milauncher.BuildConfig
import com.lilyan_lefevre.milauncher.UpdateStatusReceiver
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AppRelease(val version: String, val apkUrl: String)

/**
 * Checks the latest GitHub release of [BuildConfig.UPDATE_REPO] and installs its APK.
 * The only request goes to GitHub; Android refuses the update unless it carries the same signature.
 */
class UpdateRepository(private val context: Context) {

    /** The latest release when it is newer than the installed version, null otherwise or offline. */
    suspend fun newerRelease(): AppRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").readText())
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@runCatching null
            val version = json.getString("tag_name").removePrefix("v")
            AppRelease(version, apk.getString("browser_download_url")).takeIf { isNewer(version, BuildConfig.VERSION_NAME) }
        }.getOrNull()
    }

    /** False until "install unknown apps" is allowed for the launcher. */
    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    /** Downloads the APK and hands it to the system installer, which asks the user to confirm. */
    suspend fun install(release: AppRelease) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            URL(release.apkUrl).openStream().use { input ->
                session.openWrite("milauncher.apk", 0, -1).use { output -> input.copyTo(output) }
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateStatusReceiver::class.java),
                // Mutable: the installer adds the status extras to this intent.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(callback.intentSender)
        }
    }
}

/** Compares dotted versions numerically ("1.10" is newer than "1.9"); missing parts count as 0. */
internal fun isNewer(candidate: String, current: String): Boolean {
    fun parts(version: String) = version.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (diff != 0) return diff > 0
    }
    return false
}
