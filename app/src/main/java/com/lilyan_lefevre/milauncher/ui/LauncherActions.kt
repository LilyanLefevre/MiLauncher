package com.lilyan_lefevre.milauncher.ui

import com.lilyan_lefevre.milauncher.data.InstalledApp
import com.lilyan_lefevre.milauncher.data.ScreenTimePeriod

/** What the launcher screen can ask for. Every method does nothing by default, which suits previews. */
interface LauncherActions {
    fun launch(app: InstalledApp) {}
    fun menuActions(app: InstalledApp): List<MenuAction> = emptyList()
    fun setHidden(app: InstalledApp, hidden: Boolean) {}
    fun loadScreenTime(period: ScreenTimePeriod) {}
    fun installUpdate() {}
    fun refreshRemote() {}
    fun setRemoteEnabled(enabled: Boolean) {}
    fun newRemotePin() {}
}
