/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_browser_open_failed_copied
import me.him188.ani.app.ui.lang.settings_about_app_name
import me.him188.ani.app.ui.lang.settings_danmaku_import_failed
import me.him188.ani.app.ui.lang.settings_danmaku_import_success
import me.him188.ani.app.ui.lang.settings_mediasource_rss_copied_to_clipboard
import me.him188.ani.app.ui.lang.settings_save_failed
import org.jetbrains.compose.resources.stringResource

@Composable
fun TvSettingsRoute(viewModel: TvSettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val uriHandler = LocalUriHandler.current
    val toaster = LocalToaster.current
    val copied = stringResource(Lang.settings_mediasource_rss_copied_to_clipboard)
    val importSucceeded = stringResource(Lang.settings_danmaku_import_success)
    val importFailed = stringResource(Lang.settings_danmaku_import_failed)
    val saveFailed = stringResource(Lang.settings_save_failed)
    val openFailed = stringResource(Lang.foundation_browser_open_failed_copied)
    val appName = stringResource(Lang.settings_about_app_name)
    LaunchedEffect(viewModel, toaster, copied, importSucceeded, importFailed, saveFailed, appName) {
        viewModel.events.collect { event ->
            when (event) {
                is TvSettingsEvent.Copy -> {
                    clipboard.setPrimaryClip(ClipData.newPlainText(appName, event.text))
                    toaster.toast(copied)
                }
                TvSettingsEvent.ImportSucceeded -> toaster.toast(importSucceeded)
                TvSettingsEvent.ImportFailed -> toaster.toast(importFailed)
                TvSettingsEvent.SaveFailed -> toaster.toast(saveFailed)
            }
        }
    }
    val modes = remember(context) {
        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        display?.supportedModes.orEmpty().map {
            TvSettingsDisplayMode(it.modeId, "${it.physicalWidth} × ${it.physicalHeight} · ${it.refreshRate.roundToInt()} Hz")
        }
    }
    TvSettingsScreen(
        state, viewModel::onIntent, modifier, modes,
        onOpenUrl = { url ->
            runCatching { uriHandler.openUri(url) }.onFailure {
                clipboard.setPrimaryClip(ClipData.newPlainText(appName, url))
                toaster.toast(openFailed)
            }
        },
        onImport = {
            val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            viewModel.onIntent(TvSettingsIntent.ImportRegex(text))
        },
    )
}
