/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.qrlogin

import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.downloads_episode_picker_back
import me.him188.ani.app.ui.lang.remote_settings_network_allow
import me.him188.ani.app.ui.lang.remote_settings_network_permission
import me.him188.ani.app.ui.lang.remote_settings_network_settings
import org.jetbrains.compose.resources.stringResource
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.core.net.toUri

@Composable
actual fun LocalNetworkAccessGate(onBack: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun allowed() =
        Build.VERSION.SDK_INT < 37 ||
                context.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") ==
                PackageManager.PERMISSION_GRANTED
    // Permission revocation during an active session is reported by its network state.
    // Keep the session UI composed so that its exit guard and cleanup remain active.
    var entered by remember { mutableStateOf(allowed()) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) entered = true
        }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (allowed()) entered = true }
    LaunchedEffect(Unit) {
        if (!entered) launcher.launch("android.permission.ACCESS_LOCAL_NETWORK")
    }
    BackHandler(enabled = !entered, onBack = onBack)
    if (entered) content()
    else
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(stringResource(Lang.remote_settings_network_permission))
            Button({ launcher.launch("android.permission.ACCESS_LOCAL_NETWORK") }) {
                Text(stringResource(Lang.remote_settings_network_allow))
            }
            TextButton(
                {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri(),
                        ),
                    )
                },
            ) {
                Text(stringResource(Lang.remote_settings_network_settings))
            }
            TextButton(onBack) { Text(stringResource(Lang.downloads_episode_picker_back)) }
        }
}
