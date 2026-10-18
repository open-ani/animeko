/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_selector_cookies
import me.him188.ani.app.ui.lang.settings_mediasource_selector_cookies_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_enable_nested_link
import me.him188.ani.app.ui.lang.settings_mediasource_selector_enable_nested_link_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_match_nested_link
import me.him188.ani.app.ui.lang.settings_mediasource_selector_match_nested_link_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_match_video_link
import me.him188.ani.app.ui.lang.settings_mediasource_selector_match_video_link_description
import me.him188.ani.app.ui.settings.mediasource.editor.ConfigTextField
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceEditorDefaults
import me.him188.ani.app.ui.settings.mediasource.editor.SwitchListItem
import org.jetbrains.compose.resources.stringResource

/**
 * 步骤 3: 从播放页匹配视频地址的配置. 也用于剧集详情页底部的配置面板.
 */
@Composable
internal fun SelectorMatchVideoSection(
    state: SelectorConfigState,
    modifier: Modifier = Modifier,
    textFieldShape: Shape = MediaSourceEditorDefaults.textFieldShape,
    verticalSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
) {
    Column(modifier) {
        val matchVideoConfig = state.matchVideoConfig
        SwitchListItem(
            stringResource(Lang.settings_mediasource_selector_enable_nested_link),
            matchVideoConfig.enableNestedUrl, { matchVideoConfig.enableNestedUrl = it },
            Modifier.padding(bottom = (verticalSpacing - 8.dp).coerceAtLeast(0.dp)),
            supporting = stringResource(Lang.settings_mediasource_selector_enable_nested_link_description),
            enabled = state.enableEdit,
        )

        AniAnimatedVisibility(visible = matchVideoConfig.enableNestedUrl) {
            ConfigTextField(
                matchVideoConfig.matchNestedUrl, { matchVideoConfig.matchNestedUrl = it },
                label = stringResource(Lang.settings_mediasource_selector_match_nested_link),
                Modifier.padding(bottom = verticalSpacing),
                supportingText = stringResource(Lang.settings_mediasource_selector_match_nested_link_description),
                isError = matchVideoConfig.matchNestedUrlIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
        }

        ConfigTextField(
            matchVideoConfig.matchVideoUrl, { matchVideoConfig.matchVideoUrl = it },
            label = stringResource(Lang.settings_mediasource_selector_match_video_link),
            Modifier.padding(bottom = verticalSpacing),
            supportingText = stringResource(Lang.settings_mediasource_selector_match_video_link_description),
            isError = matchVideoConfig.matchVideoUrlIsError,
            enabled = state.enableEdit,
            shape = textFieldShape,
        )
        ConfigTextField(
            matchVideoConfig.cookies, { matchVideoConfig.cookies = it },
            label = stringResource(Lang.settings_mediasource_selector_cookies),
            supportingText = stringResource(Lang.settings_mediasource_selector_cookies_description),
            enabled = state.enableEdit,
            shape = textFieldShape,
        )
    }
}
