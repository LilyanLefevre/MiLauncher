package com.lilyan_lefevre.milauncher.ui

import com.lilyan_lefevre.milauncher.data.AppRelease
import com.lilyan_lefevre.milauncher.data.ScreenTimePeriod
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lilyan_lefevre.milauncher.R
import com.lilyan_lefevre.milauncher.data.HourlyForecast
import com.lilyan_lefevre.milauncher.data.InstalledApp
import com.lilyan_lefevre.milauncher.data.Weather
import com.lilyan_lefevre.milauncher.data.WeatherCondition
import com.lilyan_lefevre.milauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

private const val COLUMNS = 6
private val HEADER_GAP = 24.dp

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
        onRequestScreenTime = viewModel::loadScreenTime,
        onInstallUpdate = viewModel::installUpdate,
    )
}

/**
 * Two states:
 * - home: large widgets, favorites bar at the bottom of the screen;
 * - expanded (Down from the bar): compact widgets, bar at the top and every other app below.
 * Moving back up to the bar, Back or Home return to the home state.
 */
@Composable
fun LauncherContent(
    state: LauncherUiState,
    homePressed: Flow<Unit>,
    onLaunch: (InstalledApp) -> Unit,
    menuActions: (InstalledApp) -> List<MenuAction>,
    onToggleHidden: (app: InstalledApp, hide: Boolean) -> Unit,
    onRequestScreenTime: (ScreenTimePeriod) -> Unit,
    onInstallUpdate: () -> Unit,
    initiallyExpanded: Boolean = false,
) {
    val context = LocalContext.current
    // Derived from focus: true while the grid holds focus, false while the bar does.
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var menuApp by remember { mutableStateOf<InstalledApp?>(null) }
    var managingApps by remember { mutableStateOf(false) }
    var showingScreenTime by remember { mutableStateOf(false) }
    val barFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    val appFocus = remember { AppFocus() }

    BackHandler { barFocus.requestFocus() }
    LaunchedEffect(Unit) { homePressed.collect { barFocus.requestFocus() } }
    LaunchedEffect(state.bar.isNotEmpty()) { if (state.bar.isNotEmpty()) barFocus.requestFocus() }
    // After a menu action the app may have moved: follow it, or fall back to the bar.
    LaunchedEffect(appFocus.packageName) {
        val target = appFocus.packageName ?: return@LaunchedEffect
        val displayed = (state.bar + state.others).any { it.packageName == target }
        if (displayed) appFocus.requester.requestFocus() else barFocus.requestFocus()
        appFocus.packageName = null
    }

    // One continuous page: large widgets, then the bar at the bottom of the screen, then the grid
    // below the fold. Expanding slides the whole page up until the bar sits under the compact header.
    // Only translation and alpha are animated, so no frame needs a new layout pass.
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "expandProgress",
    )
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    var compactHeight by remember { mutableStateOf(0.dp) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1B2333), MaterialTheme.colorScheme.background), radius = 1400f))
            .padding(horizontal = 56.dp, vertical = 40.dp),
    ) {
        // Distance travelled by the page; also the height left for the grid once expanded.
        val pageHeight = maxHeight
        val shift = (pageHeight - barHeight - compactHeight - HEADER_GAP).coerceAtLeast(0.dp)

        // Taller than the screen on purpose: the grid lives below the fold until the page slides up.
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = -progress * shift.toPx() },
        ) {
            // First screen: widgets at the top, the bar right below this box at the bottom edge.
            Box(Modifier.height(pageHeight - barHeight)) {
                LargeWidgets(
                    weather = state.weather,
                    // Fades out during the first half of the motion.
                    modifier = Modifier.fade { (1f - progress * 2f).coerceAtLeast(0f) },
                )
            }
            AppRow(
                apps = state.bar,
                firstItemFocus = barFocus,
                appFocus = appFocus,
                onClick = onLaunch,
                onLongClick = { menuApp = it },
                modifier = Modifier
                    .onSizeChanged { barHeight = with(density) { it.height.toDp() } }
                    .onFocusChanged { if (it.hasFocus) expanded = false },
            )
            AllApps(
                apps = state.others,
                firstItemFocus = gridFocus,
                appFocus = appFocus,
                onClick = onLaunch,
                onLongClick = { menuApp = it },
                onManageApps = { managingApps = true },
                onScreenTime = { onRequestScreenTime(ScreenTimePeriod.TODAY); showingScreenTime = true },
                update = state.update,
                updating = state.updating,
                onInstallUpdate = onInstallUpdate,
                onOpenSettings = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) },
                modifier = Modifier
                    .height(shift)
                    .fade { progress }
                    .onFocusChanged { if (it.hasFocus) expanded = true },
            )
        }

        CompactWidgets(
            weather = state.weather,
            modifier = Modifier
                .onSizeChanged { compactHeight = with(density) { it.height.toDp() } }
                // Fades in during the second half of the motion.
                .fade { (progress * 2f - 1f).coerceIn(0f, 1f) },
        )
    }

    menuApp?.let { app ->
        AppMenuDialog(
            title = app.label,
            actions = menuActions(app),
            onDismiss = { menuApp = null; appFocus.packageName = app.packageName },
        )
    }
    if (showingScreenTime) {
        ScreenTimeDialog(
            screenTime = state.screenTime,
            apps = state.apps,
            onSelectPeriod = onRequestScreenTime,
            onDismiss = { showingScreenTime = false },
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

/**
 * Alpha applied to each draw call instead of an offscreen layer: a layer would clip focused tiles,
 * whose zoom and ring extend past the bounds, for the whole fade.
 */
private fun Modifier.fade(alpha: () -> Float) = graphicsLayer {
    this.alpha = alpha()
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

/** Focus request for a given app, wherever it is displayed. */
private class AppFocus {
    var packageName by mutableStateOf<String?>(null)
    val requester = FocusRequester()
}

@Composable
private fun AllApps(
    apps: List<InstalledApp>,
    firstItemFocus: FocusRequester,
    appFocus: AppFocus,
    onClick: (InstalledApp) -> Unit,
    onLongClick: (InstalledApp) -> Unit,
    onManageApps: () -> Unit,
    onScreenTime: () -> Unit,
    update: AppRelease?,
    updating: Boolean,
    onInstallUpdate: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    Text(
        stringResource(R.string.all_apps),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    // Plain (non-lazy) grid: a few dozen apps at most, and initial focus stays reliable.
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        val rows = apps.chunked(COLUMNS)
        rows.forEachIndexed { index, row ->
            AppRow(row, if (index == 0) firstItemFocus else null, appFocus, onClick, onLongClick)
        }
        TileRow(itemCount = if (update != null) 4 else 3) {
            ActionTile(
                stringResource(R.string.manage_apps),
                onManageApps,
                Modifier.weight(1f).then(if (apps.isEmpty()) Modifier.focusRequester(firstItemFocus) else Modifier),
            )
            ActionTile(stringResource(R.string.screen_time), onScreenTime, Modifier.weight(1f))
            ActionTile(stringResource(R.string.settings), onOpenSettings, Modifier.weight(1f))
            if (update != null) {
                ActionTile(
                    if (updating) stringResource(R.string.update_downloading) else stringResource(R.string.update_to, update.version),
                    onInstallUpdate,
                    Modifier.weight(1f),
                )
            }
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
    TileRow(itemCount = apps.size, modifier = modifier) {
        apps.forEachIndexed { index, app ->
            // Keying by package keeps focus on the same app when a refresh reorders the list.
            key(app.packageName) {
                AppTile(
                    app = app,
                    onClick = { onClick(app) },
                    onLongClick = { onLongClick(app) },
                    modifier = Modifier
                        .weight(1f)
                        .then(if (index == 0 && firstItemFocus != null) Modifier.focusRequester(firstItemFocus) else Modifier)
                        .then(if (app.packageName == appFocus.packageName) Modifier.focusRequester(appFocus.requester) else Modifier),
                )
            }
        }
    }
}

/** Row of [COLUMNS] equal slots spanning the full width, so every row lines up with both edges. */
@Composable
private fun TileRow(itemCount: Int, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
        content()
        // Empty slots keep tiles in a partial row as wide as in a full one.
        repeat(COLUMNS - itemCount) { Spacer(Modifier.weight(1f)) }
    }
}

private fun appActions(app: InstalledApp, viewModel: LauncherViewModel, context: Context): List<MenuAction> {
    val favorites = viewModel.state.value.favorites
    val position = favorites.indexOf(app.packageName)
    return buildList {
        when {
            position < 0 && viewModel.canAddFavorite() -> add(MenuAction(context.getString(R.string.add_to_favorites)) { viewModel.addFavorite(app) })
            position >= 0 -> {
                add(MenuAction(context.getString(R.string.remove_from_favorites)) { viewModel.removeFavorite(app) })
                if (position > 0) add(MenuAction(context.getString(R.string.move_left)) { viewModel.moveFavorite(app, -1) })
                if (position < favorites.lastIndex) add(MenuAction(context.getString(R.string.move_right)) { viewModel.moveFavorite(app, 1) })
            }
        }
        add(MenuAction(context.getString(R.string.hide)) { viewModel.setHidden(app, hidden = true) })
        add(MenuAction(context.getString(R.string.app_info)) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${app.packageName}".toUri()))
        })
        if (app.canUninstall) {
            // System confirmation dialog; the list refreshes when the launcher resumes.
            add(MenuAction(context.getString(R.string.uninstall)) {
                context.startActivity(Intent(Intent.ACTION_DELETE, "package:${app.packageName}".toUri()))
            })
        }
    }
}

@Preview(name = "Home", device = Devices.TV_1080p)
@Composable
private fun LauncherHomePreview() {
    LauncherTheme { LauncherContent(previewState, emptyFlow(), {}, { emptyList() }, { _, _ -> }, {}, {}) }
}

@Preview(name = "Expanded", device = Devices.TV_1080p)
@Composable
private fun LauncherExpandedPreview() {
    LauncherTheme { LauncherContent(previewState, emptyFlow(), {}, { emptyList() }, { _, _ -> }, {}, {}, initiallyExpanded = true) }
}

private val previewState by lazy {
    val colors = listOf(0xFFE50914, 0xFF9146FF, 0xFF1DB954, 0xFF0F79AF, 0xFFFF5722, 0xFF113CCF, 0xFF00A8E1, 0xFFFFC107)
    val apps = listOf("Netflix", "Twitch", "Spotify", "Disney+", "ARTE", "TF1+", "Prime Video", "Galerie", "Kodi", "YouTube")
        .mapIndexed { i, label -> InstalledApp("preview.$i", label, solidBanner(Color(colors[i % colors.size])), hasBanner = true) }
    LauncherUiState(
        apps = apps,
        favorites = apps.take(4).map { it.packageName },
        weather = Weather(
            temperature = 12, condition = WeatherCondition.RAIN, city = "Lyon", min = 9, max = 16,
            hourly = listOf(HourlyForecast(2, 11), HourlyForecast(5, 11), HourlyForecast(8, 10), HourlyForecast(11, 11)),
        ),
    )
}

/** Solid-color banner for previews, since no real apps are installed there. */
private fun solidBanner(color: Color): ImageBitmap = ImageBitmap(320, 180).also {
    Canvas(it).drawRect(0f, 0f, 320f, 180f, Paint().apply { this.color = color })
}
