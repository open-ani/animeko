/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_account_popup_cancel
import me.him188.ani.app.ui.lang.settings_account_popup_logout
import me.him188.ani.app.ui.lang.settings_account_popup_logout_button
import me.him188.ani.app.ui.lang.settings_account_popup_logout_confirm
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.layout.TvModalOverlay
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionModal
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

private enum class LogoutFocus : TvFocusKey { Cancel }

@Composable
internal fun TvLogoutConfirmation(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(LogoutFocus.Cancel)
    TvModalOverlay(onClose = onCancel, modifier = Modifier.tvFocusNavSignal(focus), background = {}) {
        TvOptionModal(
            title = stringResource(Lang.settings_account_popup_logout),
            subtitle = stringResource(Lang.settings_account_popup_logout_confirm),
            modifier = Modifier.testTag("tv-logout-confirmation"),
            width = 400.dp,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvOptionRow(
                    title = stringResource(Lang.settings_account_popup_cancel),
                    modifier = Modifier.weight(1f).testTag("tv-logout-cancel")
                        .tvFocusAnchor(focus, LogoutFocus.Cancel),
                    onClick = onCancel,
                )
                TvOptionRow(
                    title = stringResource(Lang.settings_account_popup_logout_button),
                    modifier = Modifier.weight(1f).testTag("tv-logout-confirm"),
                    onClick = onConfirm,
                )
            }
        }
    }
}
