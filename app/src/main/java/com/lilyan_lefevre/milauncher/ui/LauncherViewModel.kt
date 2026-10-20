package com.lilyan_lefevre.milauncher.ui

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
import kotlin.time.Duration.Companion.minutes

const val MAX_FAVORITES = 6

data class LauncherUiState(
    val apps: List<InstalledApp> = emptyList(),
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val weather: Weather? = null,
) {
    private val visible by lazy { apps.filter { it.packageName !in hidden } }

    /** Barre principale : les favoris, ou à défaut les apps les plus récentes. */
    val bar: List<InstalledApp> by lazy {
        val byPackage = visible.associateBy { it.packageName }
        favorites.mapNotNull { byPackage[it] }.ifEmpty { visible.take(MAX_FAVORITES) }
    }

    /** Toutes les autres apps visibles, affichées sous la barre en vue étendue. */
    val others: List<InstalledApp> by lazy { visible - bar.toSet() }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val apps = AppRepository(application)
    private val prefs = LauncherPrefs(application)
    private val weatherRepository = WeatherRepository(application)

    private val _state = MutableStateFlow(LauncherUiState(favorites = prefs.favorites, hidden = prefs.hidden))
    val state: StateFlow<LauncherUiState> = _state

    private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Émis quand Home est pressé alors que le launcher est déjà affiché. */
    val homePressed: SharedFlow<Unit> = _homePressed

    init {
        viewModelScope.launch {
            while (true) {
                val weather = weatherRepository.fetch()
                if (weather != null) _state.update { it.copy(weather = weather) }
                // Nouvel essai rapide si le réseau n'était pas encore prêt (démarrage de la box).
                delay(if (weather != null) 30.minutes else 1.minutes)
            }
        }
    }

    /** Recharge la liste : appelé à chaque retour sur le launcher (installations, ordre récent). */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = apps.loadApps()
            _state.update { it.copy(apps = loaded) }
        }
    }

    fun onHomePressed() {
        _homePressed.tryEmit(Unit)
    }

    fun launch(app: InstalledApp) = apps.launch(app.packageName)

    fun isFavorite(app: InstalledApp) = app.packageName in _state.value.favorites

    fun canAddFavorite() = _state.value.favorites.size < MAX_FAVORITES

    fun addFavorite(app: InstalledApp) = saveFavorites(_state.value.favorites + app.packageName)

    fun removeFavorite(app: InstalledApp) = saveFavorites(_state.value.favorites - app.packageName)

    /** Décale un favori de [offset] places dans la barre. */
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
        // Une app masquée quitte aussi les favoris pour libérer sa place.
        if (hidden) removeFavorite(app)
        _state.update { it.copy(hidden = updated) }
    }

    private fun saveFavorites(favorites: List<String>) {
        prefs.favorites = favorites
        _state.update { it.copy(favorites = favorites) }
    }
}
