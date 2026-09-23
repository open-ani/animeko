package me.him188.ani.app.ui.settings.account

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.foundation.icons.TrackingIconRegistry
import me.him188.ani.app.ui.settings.DetailPaneRoutes
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

@Composable
private fun formatTrackingAccountDescription(state: TrackingAccountViewState, failed: Boolean): String {
    if (failed) return stringResource(Lang.settings_tracking_connection_check_failed)
    return when (state.description) {
        "Checking connection" -> stringResource(Lang.settings_tracking_checking_connection)
        "Not connected" -> stringResource(Lang.settings_tracking_not_connected)
        "Connecting" -> stringResource(Lang.settings_tracking_connecting)
        "Sign in to Animeko to manage" -> stringResource(Lang.settings_tracking_sign_in_to_manage)
        else -> state.description
    }
}

@Composable
fun SettingsScope.TrackingAccountItem(
    connector: TrackingAccountConnector,
    openBrowser: (String) -> Unit,
    openOAuth: (OAuthPlatform) -> Unit,
    openDetails: (DetailPaneRoutes) -> Unit,
) {
    val state by connector.state.collectAsStateWithLifecycle(
        initialValue = TrackingAccountViewState("Checking connection", connected = false, refreshing = true),
    )
    val icon = remember(connector.providerId) {
        GlobalKoin.get<TrackingIconRegistry>().find(connector.providerId)
    }
    val scope = rememberCoroutineScope()
    var showActions by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(connector) {
        try {
            connector.refresh()
            failed = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }

    val descriptionText = formatTrackingAccountDescription(state, failed)

    TextItem(
        title = { Text(connector.displayName) },
        description = { Text(descriptionText) },
        icon = { if (icon != null) icon.Icon() else Text(connector.displayName.take(1)) },
        action = when {
            state.refreshing -> { { CircularProgressIndicator(strokeWidth = 2.dp) } }
            state.connected -> { { Icon(Icons.Default.Check, contentDescription = stringResource(Lang.settings_tracking_connected_action_description), tint = Color(0xFF4CAF50)) } }
            else -> null
        },
        onClick = if (!state.enabled) null else {
            {
                if (state.connected) showActions = true
                else when (val action = connector.loginAction) {
                    is TrackingLoginAction.Browser -> openBrowser(action.url)
                    is TrackingLoginAction.OAuth -> openOAuth(action.platform)
                }
            }
        },
    )

    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text(connector.displayName) },
            text = { Text(descriptionText) },
            confirmButton = {
                connector.detailsRoute?.let { route ->
                    TextButton(onClick = { showActions = false; openDetails(route) }) { Text(stringResource(Lang.settings)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showActions = false; confirmDisconnect = true }) { Text(stringResource(Lang.settings_tracking_disconnect_action)) }
            },
        )
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text(stringResource(Lang.settings_tracking_disconnect_title, connector.displayName)) },
            text = { Text(stringResource(Lang.settings_tracking_disconnect_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    scope.launch {
                        try {
                            connector.disconnect()
                            failed = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failed = true
                        }
                    }
                }) { Text(stringResource(Lang.settings_tracking_disconnect_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(stringResource(Lang.subject_collection_cancel)) } },
        )
    }
}
