/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.settings

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.floor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.tv_settings_open_on_tv
import me.him188.ani.app.ui.lang.tv_settings_scan_link
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionModal
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSettingsLinkEditor(dialog: TvSettingsDialog.Link, focus: TvFocusScope, onOpenUrl: (String) -> Unit) {
    val code = remember(dialog.url) {
        QRCodeWriter().encode(dialog.url, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
    }
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
            Canvas(Modifier.size(176.dp).semantics { contentDescription = scanHint }.testTag("tv-settings-link-qr")) {
                drawRect(Color.White)
                val module = floor(size.minDimension / code.width)
                val offset = Offset(floor((size.width - module * code.width) / 2), floor((size.height - module * code.height) / 2))
                for (y in 0 until code.height) for (x in 0 until code.width) {
                    if (code[x, y]) drawRect(Color.Black, offset + Offset(x * module, y * module), Size(module, module))
                }
            }
            Text(scanHint, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(destination, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
