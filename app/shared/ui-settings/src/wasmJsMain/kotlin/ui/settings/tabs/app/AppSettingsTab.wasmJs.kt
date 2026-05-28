package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.runtime.Composable
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope

@Composable
internal actual fun SettingsScope.LanguageSettingsPlatform(state: SettingsState<UISettings>) {
}

@Composable
internal actual fun SettingsScope.AppSettingsTabPlatform() {
}

@Composable
actual fun SettingsScope.PlayerGroupPlatform(videoScaffoldConfig: SettingsState<VideoScaffoldConfig>) {
}
