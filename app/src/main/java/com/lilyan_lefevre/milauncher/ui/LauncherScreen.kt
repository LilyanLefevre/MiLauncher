package com.lilyan_lefevre.milauncher.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lilyan_lefevre.milauncher.data.HourlyForecast
import com.lilyan_lefevre.milauncher.data.InstalledApp
import com.lilyan_lefevre.milauncher.data.Weather
import com.lilyan_lefevre.milauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private const val COLUMNS = 6

@Composable
fun LauncherScreen(viewModel: LauncherViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LauncherContent(
        state = state,
        homePressed = viewModel.homePressed,
        onLaunch = viewModel::launch,
        menuActions = { appActions(it, viewModel, context) },
        onToggleHidden = viewModel::setHidden,
    )
}

/**
 * Deux états :
 * - accueil : grands widgets, barre de favoris en bas de l'écran ;
 * - étendu (flèche bas depuis la barre) : widgets réduits, barre en haut et toutes les apps dessous.
 * Remonter sur la barre, Retour ou Home ramènent à l'accueil.
 */
@Composable
fun LauncherContent(
    state: LauncherUiState,
    homePressed: Flow<Unit>,
    onLaunch: (InstalledApp) -> Unit,
    menuActions: (InstalledApp) -> List<MenuAction>,
    onToggleHidden: (app: InstalledApp, hide: Boolean) -> Unit,
    initiallyExpanded: Boolean = false,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var menuApp by remember { mutableStateOf<InstalledApp?>(null) }
    var managingApps by remember { mutableStateOf(false) }
    val barFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val appFocus = remember { AppFocus() }

    // Donner le focus à la barre la replie : voir son onFocusChanged.
    BackHandler { barFocus.requestFocus() }
    LaunchedEffect(Unit) { homePressed.collect { barFocus.requestFocus() } }
    LaunchedEffect(state.bar.isNotEmpty()) { if (state.bar.isNotEmpty()) barFocus.requestFocus() }
    LaunchedEffect(expanded) { if (expanded) gridFocus.requestFocus() }
    // Après une action du menu, l'app a pu changer de place : on la suit, sinon retour à la barre.
    LaunchedEffect(appFocus.packageName) {
        val target = appFocus.packageName ?: return@LaunchedEffect
        val displayed = state.bar.any { it.packageName == target } || (expanded && state.others.any { it.packageName == target })
        if (displayed) appFocus.requester.requestFocus() else barFocus.requestFocus()
        appFocus.packageName = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1B2333), MaterialTheme.colorScheme.background), radius = 1400f))
            .padding(horizontal = 56.dp, vertical = 40.dp),
    ) {
        if (expanded) {
            CompactWidgets(state.weather)
            Spacer(Modifier.height(24.dp))
        } else {
            LargeWidgets(state.weather)
            Spacer(Modifier.weight(1f))
        }

        AppRow(
            apps = state.bar,
            firstItemFocus = barFocus,
            appFocus = appFocus,
            onClick = onLaunch,
            onLongClick = { menuApp = it },
            modifier = Modifier
                .onFocusChanged { if (it.hasFocus) expanded = false }
                .onPreviewKeyEvent {
                    // Rien sous la barre à l'accueil : la flèche bas ouvre la vue étendue.
                    val down = it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown
                    if (down) expanded = true
                    down
                },
        )

        if (expanded) {
            AllApps(
                apps = state.others,
                firstItemFocus = gridFocus,
                appFocus = appFocus,
                onClick = onLaunch,
                onLongClick = { menuApp = it },
                onManageApps = { managingApps = true },
                onOpenSettings = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) },
            )
        }
    }

    menuApp?.let { app ->
        AppMenuDialog(
            title = app.label,
            actions = menuActions(app),
            onDismiss = { menuApp = null; appFocus.packageName = app.packageName },
        )
    }
    if (managingApps) {
        ManageAppsDialog(
            apps = state.apps.sortedBy { it.label.lowercase() },
            hidden = state.hidden,
            onToggle = onToggleHidden,
            onDismiss = { managingApps = false },
        )
    }
}

/** Demande de focus sur une app précise, quelle que soit sa position. */
private class AppFocus {
    var packageName by mutableStateOf<String?>(null)
    val requester = FocusRequester()
}

@Composable
private fun ColumnScope.AllApps(
    apps: List<InstalledApp>,
    firstItemFocus: FocusRequester,
    appFocus: AppFocus,
    onClick: (InstalledApp) -> Unit,
    onLongClick: (InstalledApp) -> Unit,
    onManageApps: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Text(
        "Toutes les apps",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    // Grille simple (pas de lazy) : quelques dizaines d'apps, et le focus initial reste fiable.
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        val rows = apps.chunked(COLUMNS)
        rows.forEachIndexed { index, row ->
            AppRow(row, if (index == 0) firstItemFocus else null, appFocus, onClick, onLongClick)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
            ActionTile(
                "Gérer les apps",
                onManageApps,
                if (apps.isEmpty()) Modifier.focusRequester(firstItemFocus) else Modifier,
            )
            ActionTile("Paramètres", onOpenSettings)
        }
    }
}

@Composable
private fun AppRow(
    apps: List<InstalledApp>,
    firstItemFocus: FocusRequester?,
    appFocus: AppFocus,
    onClick: (InstalledApp) -> Unit,
    onLongClick: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
        apps.forEachIndexed { index, app ->
            // La clé garde le focus sur la même app quand l'ordre change après un rafraîchissement.
            key(app.packageName) {
                AppTile(
                    app = app,
                    onClick = { onClick(app) },
                    onLongClick = { onLongClick(app) },
                    modifier = Modifier
                        .then(if (index == 0 && firstItemFocus != null) Modifier.focusRequester(firstItemFocus) else Modifier)
                        .then(if (app.packageName == appFocus.packageName) Modifier.focusRequester(appFocus.requester) else Modifier),
                )
            }
        }
    }
}

private fun appActions(app: InstalledApp, viewModel: LauncherViewModel, context: Context): List<MenuAction> {
    val favorites = viewModel.state.value.favorites
    val position = favorites.indexOf(app.packageName)
    return buildList {
        when {
            position < 0 && viewModel.canAddFavorite() -> add(MenuAction("Ajouter aux favoris") { viewModel.addFavorite(app) })
            position >= 0 -> {
                add(MenuAction("Retirer des favoris") { viewModel.removeFavorite(app) })
                if (position > 0) add(MenuAction("Déplacer à gauche") { viewModel.moveFavorite(app, -1) })
                if (position < favorites.lastIndex) add(MenuAction("Déplacer à droite") { viewModel.moveFavorite(app, 1) })
            }
        }
        add(MenuAction("Masquer") { viewModel.setHidden(app, hidden = true) })
        add(MenuAction("Infos de l'app") {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
        })
    }
}

@Preview(name = "Accueil", device = Devices.TV_1080p)
@Composable
private fun LauncherHomePreview() {
    LauncherTheme { LauncherContent(previewState, emptyFlow(), {}, { emptyList() }, { _, _ -> }) }
}

@Preview(name = "Vue étendue", device = Devices.TV_1080p)
@Composable
private fun LauncherExpandedPreview() {
    LauncherTheme { LauncherContent(previewState, emptyFlow(), {}, { emptyList() }, { _, _ -> }, initiallyExpanded = true) }
}

private val previewState by lazy {
    val colors = listOf(0xFFE50914, 0xFF9146FF, 0xFF1DB954, 0xFF0F79AF, 0xFFFF5722, 0xFF113CCF, 0xFF00A8E1, 0xFFFFC107)
    val apps = listOf("Netflix", "Twitch", "Spotify", "Disney+", "ARTE", "TF1+", "Prime Video", "Galerie", "Kodi", "YouTube")
        .mapIndexed { i, label -> InstalledApp("preview.$i", label, solidBanner(Color(colors[i % colors.size])), hasBanner = true) }
    LauncherUiState(
        apps = apps,
        favorites = apps.take(4).map { it.packageName },
        weather = Weather(
            temperature = 12, description = "Pluie", city = "Lyon", min = 9, max = 16,
            hourly = listOf(HourlyForecast("02h", 11), HourlyForecast("05h", 11), HourlyForecast("08h", 10), HourlyForecast("11h", 11)),
        ),
    )
}

/** Bannière unie pour les previews, faute de vraies apps installées. */
private fun solidBanner(color: Color): ImageBitmap = ImageBitmap(320, 180).also {
    Canvas(it).drawRect(0f, 0f, 320f, 180f, Paint().apply { this.color = color })
}
