package com.lilyan_lefevre.milauncher

import android.app.Application
import com.lilyan_lefevre.milauncher.data.LauncherPrefs
import com.lilyan_lefevre.milauncher.remote.RemoteServer

class LauncherApplication : Application() {

    /** Lives as long as the process: the home app is rarely killed, so the remote stays reachable. */
    val remoteServer by lazy { RemoteServer(this) }

    override fun onCreate() {
        super.onCreate()
        if (LauncherPrefs(this).remoteEnabled) runCatching { remoteServer.start() }
    }
}
