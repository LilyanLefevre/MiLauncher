package com.lilyan_lefevre.milauncher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.lilyan_lefevre.milauncher.data.InstalledApp

data class MenuAction(val label: String, val onClick: () -> Unit)

/** Menu de l'appui long sur une app. */
@Composable
fun AppMenuDialog(title: String, actions: List<MenuAction>, onDismiss: () -> Unit) {
    DialogFrame(title, onDismiss) { firstItem ->
        actions.forEachIndexed { index, action ->
            ListItem(
                selected = false,
                onClick = { onDismiss(); action.onClick() },
                headlineContent = { Text(action.label) },
                modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
            )
        }
    }
}

/** Liste de toutes les apps avec un interrupteur affichée / masquée, appliqué immédiatement. */
@Composable
fun ManageAppsDialog(
    apps: List<InstalledApp>,
    hidden: Set<String>,
    onToggle: (app: InstalledApp, hide: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogFrame("Apps affichées", onDismiss) { firstItem ->
        apps.forEachIndexed { index, app ->
            val shown = app.packageName !in hidden
            ListItem(
                selected = false,
                onClick = { onToggle(app, shown) },
                headlineContent = { Text(app.label) },
                trailingContent = { Switch(checked = shown, onCheckedChange = null) },
                modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
            )
        }
    }
}

@Composable
private fun DialogFrame(title: String, onDismiss: () -> Unit, items: @Composable (firstItem: FocusRequester) -> Unit) {
    val firstItem = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.width(480.dp),
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(12.dp))
                items(firstItem)
            }
        }
        // Dans le contenu du Dialog : sa fenêtre a sa propre composition.
        LaunchedEffect(Unit) { firstItem.requestFocus() }
    }
}
