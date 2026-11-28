/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import me.him188.ani.app.data.models.preference.OpenSubtitlesSettings
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.opensubtitles_anonymous
import me.him188.ani.app.ui.lang.opensubtitles_description
import me.him188.ani.app.ui.lang.opensubtitles_enabled
import me.him188.ani.app.ui.lang.opensubtitles_key_description
import me.him188.ani.app.ui.lang.opensubtitles_languages
import me.him188.ani.app.ui.lang.opensubtitles_languages_description
import me.him188.ani.app.ui.lang.opensubtitles_not_configured
import me.him188.ani.app.ui.lang.opensubtitles_password
import me.him188.ani.app.ui.lang.opensubtitles_username
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextFieldItem
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScope.OpenSubtitlesGroup(state: SettingsState<OpenSubtitlesSettings>) {
    val settings by state
    Group(title = { Text("OpenSubtitles") }) {
        SwitchItem(
            checked = settings.enabled,
            onCheckedChange = { state.update(settings.copy(enabled = it)) },
            title = { Text(stringResource(Lang.opensubtitles_enabled)) },
            description = { Text(stringResource(Lang.opensubtitles_description)) },
        )
        TextFieldItem(
            value = settings.apiKey,
            title = { Text("API Key") },
            placeholder = { Text(stringResource(Lang.opensubtitles_not_configured)) },
            exposedItem = { Text(if (it.isBlank()) stringResource(Lang.opensubtitles_not_configured) else "••••••••") },
            textFieldDescription = { Text(stringResource(Lang.opensubtitles_key_description)) },
            visualTransformation = PasswordVisualTransformation(),
            showVisibilityToggle = true,
            sanitizeValue = { it.trim() },
            onValueChangeCompleted = { state.update(settings.copy(apiKey = it)) },
        )
        TextFieldItem(
            value = settings.username,
            title = { Text(stringResource(Lang.opensubtitles_username)) },
            placeholder = { Text(stringResource(Lang.opensubtitles_anonymous)) },
            sanitizeValue = { it.trim() },
            onValueChangeCompleted = { state.update(settings.copy(username = it)) },
        )
        TextFieldItem(
            value = settings.password,
            title = { Text(stringResource(Lang.opensubtitles_password)) },
            exposedItem = { Text(if (it.isEmpty()) stringResource(Lang.opensubtitles_not_configured) else "••••••••") },
            visualTransformation = PasswordVisualTransformation(),
            showVisibilityToggle = true,
            onValueChangeCompleted = { state.update(settings.copy(password = it)) },
        )
        TextFieldItem(
            value = settings.languages,
            title = { Text(stringResource(Lang.opensubtitles_languages)) },
            textFieldDescription = { Text(stringResource(Lang.opensubtitles_languages_description)) },
            onValueChangeCompleted = { state.update(settings.copy(languages = it.trim())) },
        )
    }
}
