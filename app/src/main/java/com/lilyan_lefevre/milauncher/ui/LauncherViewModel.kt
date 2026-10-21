package com.lilyan_lefevre.milauncher.ui

import com.lilyan_lefevre.milauncher.remote.RemoteServer
import com.lilyan_lefevre.milauncher.LauncherApplication
import java.net.Inet4Address
import android.net.ConnectivityManager
import com.lilyan_lefevre.milauncher.data.UpdateRepository
import com.lilyan_lefevre.milauncher.data.AppRelease
import androidx.core.net.toUri
import android.provider.Settings
import android.content.Intent
import android.content.ActivityNotFoundException
import com.lilyan_lefevre.milauncher.data.ScreenTimeRepository
import com.lilyan_lefevre.milauncher.data.ScreenTimePeriod
import com.lilyan_lefevre.milauncher.data.AppUsage
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lilyan_lefevre.milauncher.data.AppRepository
import com.lilyan_lefevre.milauncher.data.InstalledApp
import com.lilyan_lefevre.milauncher.data.LauncherPrefs
import com.lilyan_lefevre.milauncher.data.Weather
import com.lilyan_lefevre.milauncher.data.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

const val MAX_FAVORITES = 6

data class LauncherUiState(
    val apps: List<InstalledApp> = emptyList(),
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val weather: Weather? = null,
    /** Loaded on demand when the screen time dialog opens. */
    val screenTime: ScreenTime? = null,
    /** A newer GitHub release, if any. */
    val update: AppRelease? = null,
    val updating: Boolean = false,
    val remote: RemoteInfo = RemoteInfo(),
) {
    private val visible by lazy { apps.filter { it.packageName !in hidden } }

    /** Main bar: the favorites, or the most recently used apps when there are none. */
    val bar: List<InstalledApp> by lazy {
        val byPackage = visible.associateBy { it.packageName }
        favorites.mapNotNull { byPackage[it] }.ifEmpty { visible.take(MAX_FAVORITES) }
    }

    /** All other visible apps, shown below the bar in the expanded view. */
    val others: List<InstalledApp> by lazy { visible - bar.toSet() }
}

data class ScreenTime(val period: ScreenTimePeriod, val usage: List<AppUsage>)

/** Phone remote status; [url] is null while the box has no IPv4 address. */
data class RemoteInfo(val enabled: Boolean = false, val url: String? = null, val pin: String = "") {
    /** Opens the remote already paired: the code sits in the fragment, which browsers never send. */
    val pairingUrl: String? get() = url?.let { "$it/#pin=$pin" }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val apps = AppRepository(application)
    private val prefs = LauncherPrefs(application)
    private val weatherRepository = WeatherRepository(application)
    private val screenTimeRepository = ScreenTimeRepository(application)
    private val updateRepository = UpdateRepository(application)

    private val _state = MutableStateFlow(LauncherUiState(favorites = prefs.favorites, hidden = prefs.hidden))
    val state: StateFlow<LauncherUiState> = _state

    private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emitted when Home is pressed while the launcher is already shown. */
    val homePressed: SharedFlow<Unit> = _homePressed

    init {
        viewModelScope.launch {
            while (true) {
                val weather = weatherRepository.fetch()
                if (weather != null) _state.update { it.copy(weather = weather) }
                // Retry soon if the network was not ready yet (device boot).
                delay(if (weather != null) 30.minutes else 1.minutes)
            }
        }
        viewModelScope.launch {
            while (true) {
                val release = updateRepository.newerRelease()
                _state.update { it.copy(update = release) }
                delay(12.hours)
            }
        }
    }

    /** Installs the pending update, or opens the "install unknown apps" setting if not allowed yet. */
    fun installUpdate() {
        val release = _state.value.update ?: return
        if (!updateRepository.canInstall()) {
            openUnknownSourcesSetting()
            return
        }
        _state.update { it.copy(updating = true) }
        viewModelScope.launch {
            // On success the system confirmation takes over, then the new version restarts the launcher.
            runCatching { updateRepository.install(release) }
            _state.update { it.copy(updating = false) }
        }
    }

    private fun openUnknownSourcesSetting() {
        val app = getApplication<Application>()
        val uri = "package:${app.packageName}".toUri()
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Some TV builds lack this screen: the app details page links to the same setting.
            app.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Reloads the app list; called whenever the launcher comes back (installs, recency order). */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = apps.loadApps()
            _state.update { it.copy(apps = loaded) }
        }
    }

    fun loadScreenTime(period: ScreenTimePeriod) {
        viewModelScope.launch(Dispatchers.IO) {
            val usage = screenTimeRepository.usage(period)
            _state.update { it.copy(screenTime = ScreenTime(period, usage)) }
        }
    }

    fun refreshRemote() {
        val server = getApplication<LauncherApplication>().remoteServer
        val address = localIpv4Address()
        _state.update {
            it.copy(remote = RemoteInfo(server.isRunning, address?.let { ip -> "http://$ip:${RemoteServer.PORT}" }, prefs.remotePin))
        }
    }

    fun setRemoteEnabled(enabled: Boolean) {
        val server = getApplication<LauncherApplication>().remoteServer
        prefs.remoteEnabled = enabled
        if (enabled) runCatching { server.start() } else server.stop()
        refreshRemote()
    }

    fun newRemotePin() {
        prefs.newRemotePin()
        refreshRemote()
    }

    private fun localIpv4Address(): String? {
        val connectivity = getApplication<Application>().getSystemService(ConnectivityManager::class.java)
        return connectivity.getLinkProperties(connectivity.activeNetwork)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }

    fun onHomePressed() {
        _homePressed.tryEmit(Unit)
    }

    fun launch(app: InstalledApp) = apps.launch(app.packageName)

    fun isFavorite(app: InstalledApp) = app.packageName in _state.value.favorites

    fun canAddFavorite() = _state.value.favorites.size < MAX_FAVORITES

    fun addFavorite(app: InstalledApp) = saveFavorites(_state.value.favorites + app.packageName)

    fun removeFavorite(app: InstalledApp) = saveFavorites(_state.value.favorites - app.packageName)

    /** Moves a favorite by [offset] positions in the bar. */
    fun moveFavorite(app: InstalledApp, offset: Int) {
        val favorites = _state.value.favorites.toMutableList()
        val from = favorites.indexOf(app.packageName)
        if (from < 0) return
        val to = (from + offset).coerceIn(0, favorites.lastIndex)
        favorites.add(to, favorites.removeAt(from))
        saveFavorites(favorites)
    }

    fun setHidden(app: InstalledApp, hidden: Boolean) {
        val updated = if (hidden) _state.value.hidden + app.packageName else _state.value.hidden - app.packageName
        prefs.hidden = updated
        // A hidden app also leaves the favorites to free its slot.
        if (hidden) removeFavorite(app)
        _state.update { it.copy(hidden = updated) }
    }

    private fun saveFavorites(favorites: List<String>) {
        prefs.favorites = favorites
        _state.update { it.copy(favorites = favorites) }
    }
}
