/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import androidx.core.os.LocaleListCompat
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.SupportedLocales
import me.him188.ani.app.ui.lang.settings_app_language
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_app_danmaku_refresh_rate
import me.him188.ani.app.ui.lang.settings_theme_mode_auto
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.DropdownItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Suppress("UNUSED_PARAMETER")
@Composable
internal actual fun SettingsScope.LanguageSettingsPlatform(
    state: SettingsState<UISettings>,
) {
    val supportedLocales = remember { listOf<Locale?>(null) + SupportedLocales }

    DropdownItem(
        selected = {
            val languageTag = AppCompatDelegate.getApplicationLocales()
                .toLanguageTags()
                .substringBefore(',')
                .takeUnless { it.isBlank() }
                ?: return@DropdownItem null

            supportedLocales.firstOrNull { it?.toLanguageTag() == languageTag } ?: Locale(languageTag)
        },
        values = { supportedLocales },
        itemText = { Text(renderLocale(it)) },
        onSelect = { locale ->
            val locales = locale?.let { LocaleListCompat.forLanguageTags(it.toLanguageTag()) }
                ?: LocaleListCompat.getEmptyLocaleList()
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != locales.toLanguageTags()) {
                AppCompatDelegate.setApplicationLocales(locales)
            }
        },
        title = { Text(stringResource(Lang.settings_app_language)) },
    )
}

@Composable
actual fun SettingsScope.PlayerGroupPlatform(videoScaffoldConfig: SettingsState<VideoScaffoldConfig>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val context = LocalContext.current
        val supportedModes = remember(context) {
            context.display.supportedModes.orEmpty().toList() + null
        }

        HorizontalDividerItem()
        DropdownItem(
            selected = {
                supportedModes.find { it?.modeId == videoScaffoldConfig.value.displayModeId }
            },
            values = { supportedModes },
            itemText = {
                if (it == null) {
                    Text(stringResource(Lang.settings_theme_mode_auto))
                } else {
                    Text(it.refreshRate.roundToInt().toString())
                }
            },
            onSelect = {
                videoScaffoldConfig.update(
                    videoScaffoldConfig.value.copy(
                        displayModeId = it?.modeId ?: 0,
                    ),
                )
            },
            title = {
                Text(stringResource(Lang.settings_app_danmaku_refresh_rate))
            },
        )
    }
}
