/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.settings.mediasource.selector.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatIndexGrouped
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatIndexed
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatJsonPathIndexed
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.animation.StandardEasing
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.EasingDurations
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_base_url
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_base_url_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_channel_format_grouped
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_channel_format_no_channel
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_distinguish_channel_name
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_distinguish_channel_name_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_distinguish_subject_name
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_distinguish_subject_name_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_player_select_resource
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_referer_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_request_interval
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_request_interval_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_url
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_url_placeholder
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_url_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_source_user_agent
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_source_user_agent_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_subject_format_multi_tag
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_subject_format_single_tag
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_video_playback
import me.him188.ani.app.ui.settings.mediasource.editor.CollapsibleAutoMatchSection
import me.him188.ani.app.ui.settings.mediasource.editor.ConfigLongTextField
import me.him188.ani.app.ui.settings.mediasource.editor.ConfigTextField
import me.him188.ani.app.ui.settings.mediasource.editor.FormatSelectionButtonRow
import me.him188.ani.app.ui.settings.mediasource.editor.MediaDefaultsSection
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceConfigurationColumn
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceEditorDefaults
import me.him188.ani.app.ui.settings.mediasource.editor.SectionTitle
import me.him188.ani.app.ui.settings.mediasource.editor.SwitchListItem
import me.him188.ani.app.ui.settings.mediasource.rss.createTestSaveableStorage
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun SelectorConfigurationPane(
    state: SelectorConfigState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
    textFieldShape: Shape = MediaSourceEditorDefaults.textFieldShape,
) {
    MediaSourceConfigurationColumn(
        state, modifier, contentPadding,
        verticalSpacing = verticalSpacing, textFieldShape = textFieldShape,
    ) {
        val listItemColors = MediaSourceEditorDefaults.listItemColors
        val itemPadding = Modifier.padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))

        // 列表规则: 怎样把站点上的条目、线路、剧集列出来, 以及怎样从播放页提取视频.
        // 只填这一部分, 数据源就可以浏览和手动选集.

        SectionTitle(MediaSourceEditorDefaults.STEP_NAME_1, Modifier.padding(top = verticalSpacing, bottom = 12.dp))

        Column {
            ConfigTextField(
                state.searchUrl, { state.searchUrl = it },
                label = stringResource(Lang.settings_mediasource_selector_config_search_url),
                placeholder = stringResource(Lang.settings_mediasource_selector_config_search_url_placeholder),
                supportingText = stringResource(Lang.settings_mediasource_selector_config_search_url_supporting),
                isError = state.searchUrlIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                state.rawBaseUrl, { state.rawBaseUrl = it },
                label = stringResource(Lang.settings_mediasource_selector_config_base_url),
                itemPadding,
                placeholder = state.baseUrlPlaceholder,
                supportingText = stringResource(Lang.settings_mediasource_selector_config_base_url_supporting),
                isError = state.searchUrlIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigLongTextField(
                state.requestInterval.inWholeMilliseconds,
                { state.requestInterval = it.milliseconds },
                label = stringResource(Lang.settings_mediasource_selector_config_request_interval),
                itemPadding,
                supportingText = stringResource(Lang.settings_mediasource_selector_config_request_interval_description),
                isValid = { it >= 0 },
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                state.userAgent, { state.userAgent = it },
                label = stringResource(Lang.settings_mediasource_selector_config_source_user_agent),
                itemPadding,
                supportingText = stringResource(Lang.settings_mediasource_selector_config_source_user_agent_description),
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
        }

        FormatSelectionButtonRow(
            state.allSubjectFormats,
            isSelected = { state.subjectFormatId == it.id },
            onSelect = { state.subjectFormatId = it.id },
            Modifier.fillMaxWidth().padding(top = verticalSpacing, bottom = 4.dp),
            enabled = state.enableEdit,
        ) { format ->
            Text(
                when (format) { // type-safe to handle all formats
                    SelectorSubjectFormatA -> stringResource(Lang.settings_mediasource_selector_config_subject_format_single_tag)
                    SelectorSubjectFormatIndexed -> stringResource(Lang.settings_mediasource_selector_config_subject_format_multi_tag)
                    SelectorSubjectFormatJsonPathIndexed -> "JsonPath"
                },
                softWrap = false,
            )
        }

        AnimatedContent(
            SelectorSubjectFormat.findById(state.subjectFormatId),
            Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
                .animateContentSize(tween(EasingDurations.standard, easing = StandardEasing)),
            transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
        ) { format ->
            SelectorSubjectConfigurationColumn(
                format, state,
                textFieldShape, verticalSpacing,
                Modifier.fillMaxWidth(),
            )
        }

        SectionTitle(MediaSourceEditorDefaults.STEP_NAME_2, Modifier.padding(top = verticalSpacing, bottom = 12.dp))

        FormatSelectionButtonRow(
            state.allChannelFormats,
            isSelected = { state.channelFormatId == it.id },
            onSelect = { state.channelFormatId = it.id },
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            enabled = state.enableEdit,
        ) { format ->
            Text(
                when (format) { // type-safe to handle all formats
                    SelectorChannelFormatNoChannel -> stringResource(Lang.settings_mediasource_selector_config_channel_format_no_channel)
                    SelectorChannelFormatIndexGrouped -> stringResource(Lang.settings_mediasource_selector_config_channel_format_grouped)
                },
                softWrap = false,
            )
        }

        AnimatedContent(
            state.channelFormatId,
            Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
                .animateContentSize(tween(EasingDurations.standard, easing = StandardEasing)),
            transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
        ) { formatId ->
            SelectorChannelFormatColumn(formatId, state, Modifier.fillMaxWidth())
        }

        SectionTitle(MediaSourceEditorDefaults.STEP_NAME_3, Modifier.padding(top = verticalSpacing, bottom = 12.dp))

        SelectorMatchVideoSection(
            state,
            textFieldShape = textFieldShape,
            verticalSpacing = verticalSpacing,
        )

        MediaDefaultsSection(
            state.defaultResolution, { state.defaultResolution = it },
            state.defaultSubtitleLanguage, { state.defaultSubtitleLanguage = it },
            enabled = state.enableEdit,
            listItemColors = listItemColors,
        )

        SectionTitle(
            stringResource(Lang.settings_mediasource_selector_config_player_select_resource),
            Modifier.padding(top = verticalSpacing, bottom = 12.dp),
        )

        Column(Modifier, verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
            val conf = state.selectMediaConfig
            SwitchListItem(
                stringResource(Lang.settings_mediasource_selector_config_distinguish_subject_name),
                conf.distinguishSubjectName, { conf.distinguishSubjectName = it },
                supporting = stringResource(Lang.settings_mediasource_selector_config_distinguish_subject_name_description),
                enabled = state.enableEdit,
                colors = listItemColors,
            )
            SwitchListItem(
                stringResource(Lang.settings_mediasource_selector_config_distinguish_channel_name),
                conf.distinguishChannelName, { conf.distinguishChannelName = it },
                supporting = stringResource(Lang.settings_mediasource_selector_config_distinguish_channel_name_description),
                enabled = state.enableEdit,
                colors = listItemColors,
            )
        }

        SectionTitle(
            stringResource(Lang.settings_mediasource_selector_config_video_playback),
            Modifier.padding(top = verticalSpacing, bottom = 12.dp),
        )

        Column(Modifier, verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
            val conf = state.matchVideoConfig.videoHeaders
            ConfigTextField(
                conf.referer, { conf.referer = it },
                label = "Referer",
                supportingText = stringResource(Lang.settings_mediasource_selector_config_referer_description),
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
        }

        // 自动匹配层: 全部可选, 默认收起.
        CollapsibleAutoMatchSection(
            state.autoMatch,
            enabled = state.enableEdit,
            Modifier.padding(top = verticalSpacing),
            textFieldShape = textFieldShape,
            verticalSpacing = verticalSpacing,
            listItemColors = listItemColors,
        )
    }
}

@Composable
@Preview
fun PreviewSelectorConfigurationPane() = ProvideCompositionLocalsForPreview {
    Surface {
        SelectorConfigurationPane(
            remember {
                SelectorConfigState(
                    createTestSaveableStorage(
                        SelectorMediaSourceArguments.Default,
                    ),
                    allowEditState = stateOf(true),
                )
            },
        )
    }
}
