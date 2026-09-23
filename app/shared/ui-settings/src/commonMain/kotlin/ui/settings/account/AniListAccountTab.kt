/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.tracking.api.TrackingAccountState
import me.him188.ani.tracking.api.TrackingProvider
import me.him188.ani.tracking.api.TrackingProviderException

private const val ANILIST_AUTH_URL = "https://anilist.co/api/v2/oauth/authorize?client_id=51393&response_type=token"

object AniListAccountChanges {
    val version = MutableStateFlow(0)

    fun notifyConnected() {
        version.value += 1
    }
}

@Composable
internal expect fun rememberAniListTrackingProvider(): TrackingProvider?

@Composable
fun SettingsScope.AniListAccountItem(openBrowser: (String) -> Unit) {
    val provider = rememberAniListTrackingProvider()
    val scope = rememberCoroutineScope()
    val accountState by provider?.accountState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(TrackingAccountState.LoggedOut) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    val accountVersion by AniListAccountChanges.version.collectAsStateWithLifecycle()
    LaunchedEffect(provider, accountVersion) {
        if (provider == null) return@LaunchedEffect
        try {
            provider.refreshAccount()
            error = false
        } catch (_: TrackingProviderException.Unauthorized) {
            error = false
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            error = true
        }
    }
    TextItem(
        title = { Text("AniList") },
        description = {
            Text(when {
                provider == null -> "Available on Android"
                error -> "Connection check failed"
                accountState is TrackingAccountState.LoggedIn ->
                    (accountState as TrackingAccountState.LoggedIn).account.displayName
                else -> "Not connected"
            })
        },
        icon = { AniListIcon() },
        action = when (accountState) {
            is TrackingAccountState.LoggedIn -> {
                { Icon(Icons.Default.Check, contentDescription = "Connected", tint = Color(0xFF4CAF50)) }
            }
            is TrackingAccountState.Refreshing -> {
                { CircularProgressIndicator(Modifier.testTag("anilistRefreshing"), strokeWidth = 2.dp) }
            }
            TrackingAccountState.LoggedOut -> null
        },
        onClick = if (provider == null) null else {
            {
                if (accountState is TrackingAccountState.LoggedIn) confirmDisconnect = true
                else openBrowser(ANILIST_AUTH_URL)
            }
        },
        modifier = Modifier.testTag("anilistAccount"),
    )

    if (confirmDisconnect && provider != null) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect AniList?") },
            text = { Text("This removes the saved AniList token from this device. Your AniList list stays online.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    scope.launch { provider.logout() }
                }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }
}
