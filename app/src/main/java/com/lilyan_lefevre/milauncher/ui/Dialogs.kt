package com.lilyan_lefevre.milauncher.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.lilyan_lefevre.milauncher.R
import com.lilyan_lefevre.milauncher.data.InstalledApp
import com.lilyan_lefevre.milauncher.data.ScreenTimePeriod

data class MenuAction(val label: String, val onClick: () -> Unit)

/** Long-press menu of an app. */
@Composable
fun AppMenuDialog(title: String, actions: List<MenuAction>, onDismiss: () -> Unit) {
    DialogFrame(title, onDismiss) { firstItem ->
        actions.forEachIndexed { index, action ->
            ListItem(
                selected = false,
                onClick = {
                    onDismiss()
                    action.onClick()
                },
                headlineContent = { Text(action.label) },
                modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier
            )
        }
    }
}

/** Every app with a shown/hidden switch, applied immediately. */
@Composable
fun ManageAppsDialog(
    apps: List<InstalledApp>,
    hidden: Set<String>,
    onToggle: (app: InstalledApp, hide: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    DialogFrame(stringResource(R.string.shown_apps), onDismiss) { firstItem ->
        apps.forEachIndexed { index, app ->
            val shown = app.packageName !in hidden
            ListItem(
                selected = false,
                onClick = { onToggle(app, shown) },
                headlineContent = { Text(app.label) },
                trailingContent = { Switch(checked = shown, onCheckedChange = null) },
                modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier
            )
        }
    }
}

/** Time spent per app over the selected period, computed on the device. */
@Composable
fun ScreenTimeDialog(
    screenTime: ScreenTime?,
    apps: List<InstalledApp>,
    usageAccess: Boolean,
    onGrantUsageAccess: () -> Unit,
    onSelectPeriod: (ScreenTimePeriod) -> Unit,
    onDismiss: () -> Unit
) {
    // Only launchable apps: system services (voice assistant, keyboard…) are not screen time.
    val labels = apps.associate { it.packageName to it.label }
    // Under a minute would read "0 min": not worth a row.
    val usage = screenTime?.usage.orEmpty().filter { it.packageName in labels && it.foregroundMillis >= 60_000 }
    val longest = usage.maxOfOrNull { it.foregroundMillis } ?: 0L

    DialogFrame(stringResource(R.string.screen_time), onDismiss) { firstItem ->
        if (!usageAccess) {
            PermissionItem(R.string.usage_access_grant, R.string.usage_access_hint, onGrantUsageAccess, Modifier.focusRequester(firstItem))
        }
        ScreenTimePeriod.entries.forEachIndexed { index, period ->
            ListItem(
                selected = screenTime?.period == period,
                onClick = { onSelectPeriod(period) },
                headlineContent = { Text(stringResource(period.label)) },
                modifier = if (index == 0 && usageAccess) Modifier.focusRequester(firstItem) else Modifier
            )
        }
        Text(
            if (screenTime != null && usage.isEmpty()) {
                stringResource(R.string.screen_time_empty)
            } else {
                stringResource(R.string.screen_time_total, formatDuration(usage.sumOf { it.foregroundMillis }))
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
        )
        usage.forEach { app ->
            // Focusable rows so the D-pad can scroll through a long list.
            ListItem(
                selected = false,
                onClick = {},
                headlineContent = { Text(labels.getValue(app.packageName)) },
                trailingContent = { Text(formatDuration(app.foregroundMillis)) },
                supportingContent = {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth(app.foregroundMillis.toFloat() / longest)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                }
            )
        }
    }
}

/** Turns the phone remote on or off and shows how to reach it. */
@Composable
fun RemoteDialog(
    remote: RemoteInfo,
    onEnable: (Boolean) -> Unit,
    onNewPin: () -> Unit,
    onEnableNavigation: () -> Unit,
    onDismiss: () -> Unit
) {
    DialogFrame(stringResource(R.string.remote), onDismiss, width = 600.dp) { firstItem ->
        ListItem(
            selected = false,
            onClick = { onEnable(!remote.enabled) },
            headlineContent = { Text(stringResource(R.string.remote_enable)) },
            trailingContent = { Switch(checked = remote.enabled, onCheckedChange = null) },
            modifier = Modifier.focusRequester(firstItem)
        )
        if (remote.enabled) {
            val dim = MaterialTheme.colorScheme.onSurfaceVariant
            Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                if (remote.url == null) {
                    Text(stringResource(R.string.remote_no_network), color = dim)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        QrCode(
                            content = remote.pairingUrl!!,
                            contentDescription = stringResource(R.string.remote_qr_description),
                            modifier = Modifier.size(168.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Column {
                            Text(stringResource(R.string.remote_instructions), color = dim)
                            // Browsers add the scheme themselves: the short form fits on one line.
                            Text(
                                remote.url.removePrefix("http://"),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Text(stringResource(R.string.remote_pin, remote.pin), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            if (!remote.navigation) {
                PermissionItem(R.string.remote_navigation_grant, R.string.remote_navigation_hint, onEnableNavigation)
            }
            ListItem(selected = false, onClick = onNewPin, headlineContent = { Text(stringResource(R.string.remote_new_pin)) })
        }
    }
}

/** Row that opens the system screen where a missing permission is granted. */
@Composable
private fun PermissionItem(@StringRes title: Int, @StringRes hint: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(hint)) },
        modifier = modifier
    )
}

private val ScreenTimePeriod.label: Int
    @StringRes get() = when (this) {
        ScreenTimePeriod.TODAY -> R.string.today
        ScreenTimePeriod.LAST_7_DAYS -> R.string.last_7_days
    }

@Composable
private fun DialogFrame(title: String, onDismiss: () -> Unit, width: Dp = 480.dp, items: @Composable (firstItem: FocusRequester) -> Unit) {
    val firstItem = remember { FocusRequester() }
    var pressedInside by remember { mutableStateOf(false) }
    // The platform width cap would override the requested width.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.width(width)
        ) {
            Column(
                Modifier
                    // The long press that opened the menu ends with a key release, which would click
                    // the first item: swallow key events until a fresh press happens in the dialog.
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) pressedInside = true
                        !pressedInside
                    }
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(12.dp))
                items(firstItem)
            }
        }
        // Inside the Dialog content: its window has its own composition.
        LaunchedEffect(Unit) { firstItem.requestFocus() }
    }
}
