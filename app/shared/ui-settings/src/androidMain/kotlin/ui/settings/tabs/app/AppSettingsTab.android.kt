/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.intl.Locale
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.createAppLocaleController
import me.him188.ani.app.platform.findActivity
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.SupportedLocales
import me.him188.ani.app.ui.lang.renderLocale
import me.him188.ani.app.ui.lang.settings_app_danmaku_refresh_rate
import me.him188.ani.app.ui.lang.settings_app_language
import me.him188.ani.app.ui.lang.settings_player_exoplayer_preinit_effect_graph
import me.him188.ani.app.ui.lang.settings_player_exoplayer_preinit_effect_graph_desc
import me.him188.ani.app.ui.lang.settings_theme_mode_auto
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.DropdownItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Suppress("UNUSED_PARAMETER")
@Composable
internal actual fun SettingsScope.LanguageSettingsPlatform(
    state: SettingsState<UISettings>,
) {
    val supportedLocales = remember { listOf<Locale?>(null) + SupportedLocales }
    val context = LocalContext.current
    val localeController = remember(context) { createAppLocaleController(context) }
    var selectedLanguageTag by remember(context, LocalConfiguration.current) {
        mutableStateOf(localeController.languageTag)
    }

    DropdownItem(
        selected = {
            val languageTag = selectedLanguageTag ?: return@DropdownItem null

            supportedLocales.firstOrNull { it?.toLanguageTag() == languageTag } ?: Locale(languageTag)
        },
        values = { supportedLocales },
        itemText = { Text(renderLocale(it)) },
        onSelect = { locale ->
            context.findActivity()?.let { activity ->
                localeController.setLanguageTag(activity, locale?.toLanguageTag())
                selectedLanguageTag = localeController.languageTag
            }
        },
        title = { Text(stringResource(Lang.settings_app_language)) },
    )
}

@Composable
actual fun SettingsScope.PlayerGroupPlatform(
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
    @Suppress("UNUSED_PARAMETER") playerKernelConfig: SettingsState<PlayerKernelConfig>,
) {
    val kernelConfig by playerKernelConfig

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
    HorizontalDividerItem()
    SwitchItem(
        checked = kernelConfig.exoPlayerInitEffectGraphInAdvance,
        onCheckedChange = {
            playerKernelConfig.update(
                kernelConfig.copy(exoPlayerInitEffectGraphInAdvance = it),
            )
        },
        title = { Text(stringResource(Lang.settings_player_exoplayer_preinit_effect_graph)) },
        description = { Text(stringResource(Lang.settings_player_exoplayer_preinit_effect_graph_desc)) },
    )
}
