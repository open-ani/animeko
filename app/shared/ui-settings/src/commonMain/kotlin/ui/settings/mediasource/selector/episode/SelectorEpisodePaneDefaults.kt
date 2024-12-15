/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.TopAppBarGoBackButton
import me.him188.ani.app.ui.settings.mediasource.RefreshIndicationDefaults
import me.him188.ani.app.ui.settings.mediasource.selector.edit.MatchVideoSection
import me.him188.ani.app.ui.settings.mediasource.selector.edit.SelectorConfigState
import me.him188.ani.app.ui.settings.mediasource.selector.edit.SelectorConfigurationDefaults

object SelectorEpisodePaneDefaults {
    @Composable
    fun TopAppBar(
        state: SelectorEpisodeState,
        modifier: Modifier = Modifier.Companion,
        windowInsets: WindowInsets = WindowInsets(0.dp),
    ) {
        val onRefresh = { state.searcher.restartCurrentSearch() }
        val searchResult by state.searcher.searchResultFlow.collectAsStateWithLifecycle()
        TopAppBar(
            navigationIcon = {
                TopAppBarGoBackButton()
            },
            title = {
                Row(
                    verticalAlignment = Alignment.Companion.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(state.episodeName, style = LocalTextStyle.current)
                    RefreshIndicationDefaults.RefreshIconButton(
                        onClick = onRefresh,
                    )
                    RefreshIndicationDefaults.RefreshResultTextButton(
                        result = searchResult,
                        onRefresh = onRefresh,
                    )
                }
            },
            actions = {
                val uriHandler = LocalUriHandler.current
                val toaster = LocalToaster.current
                if (state.episodeUrl.isNotBlank() && state.episodeUrl.startsWith("http")) {
                    IconButton(
                        {
                            try {
                                uriHandler.openUri(state.episodeUrl)
                            } catch (e: Throwable) {
                                toaster.toast("无法打开链接")
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.ArrowOutward, "打开原始链接 ${state.episodeName}")
                    }
                }
            },
            colors = AniThemeDefaults.topAppBarColors(),
            modifier = modifier,
            windowInsets = windowInsets,
        )
    }

    @Composable
    fun ConfigurationContent(
        state: SelectorConfigState,
        modifier: Modifier = Modifier.Companion,
        contentPadding: PaddingValues = PaddingValues(0.dp),
        textFieldShape: Shape = SelectorConfigurationDefaults.textFieldShape,
        verticalSpacing: Dp = SelectorConfigurationDefaults.verticalSpacing,
    ) {
        Column(modifier.padding(contentPadding)) {
            Row(Modifier.Companion.padding(bottom = 16.dp)) {
                ProvideTextStyle(
                    MaterialTheme.typography.titleLarge,
                ) {
                    Text("编辑配置")
                }
            }
            SelectorConfigurationDefaults.MatchVideoSection(
                state,
                textFieldShape = textFieldShape,
                verticalSpacing = verticalSpacing,
            )
        }
    }

}