/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.tv_settings_open_on_tv
import me.him188.ani.app.ui.lang.tv_settings_scan_link
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.widgets.TvOptionModal
import me.him188.ani.tv.ui.foundation.widgets.TvQrCode
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSettingsLinkEditor(dialog: TvSettingsDialog.Link, focus: TvFocusScope, onOpenUrl: (String) -> Unit) {
    val scanHint = stringResource(Lang.tv_settings_scan_link)
    val destination = remember(dialog.url) { Uri.parse(dialog.url).host ?: dialog.url }
    TvOptionModal(
        dialog.title, Modifier.testTag("tv-settings-editor"),
        footer = {
            SettingsActionButton(
                stringResource(Lang.tv_settings_open_on_tv),
                modifier = Modifier.fillMaxWidth().tvFocusAnchor(focus, editorKey("entry")).testTag("tv-settings-open-link"),
            ) { onOpenUrl(dialog.url) }
        },
    ) {
        Column(
            Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvQrCode(dialog.url, scanHint, Modifier.size(176.dp).testTag("tv-settings-link-qr"))
            Text(scanHint, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(destination, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
