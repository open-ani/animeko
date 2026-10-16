/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.settings.mediasource.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_selector_auto_match
import me.him188.ani.app.ui.lang.settings_mediasource_selector_auto_match_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_auto_match_enabled
import me.him188.ani.app.ui.lang.settings_mediasource_selector_auto_match_enabled_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_filter_by_episode_sort
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_filter_by_episode_sort_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_cache_ttl
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_cache_ttl_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_first_word
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_first_word_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_remove_special
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_remove_special_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_subject_names_count
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_search_subject_names_count_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_prefer_shorter_name
import me.him188.ani.app.ui.lang.settings_mediasource_selector_prefer_shorter_name_description
import me.him188.ani.app.ui.lang.settings_mediasource_selector_tier
import me.him188.ani.app.ui.lang.settings_mediasource_selector_tier_description
import me.him188.ani.datasources.api.source.MediaSourceTier
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.minutes

@TestOnly
object MediaSourceEditorTestTags {
    const val AUTO_MATCH_HEADER = "MediaSourceEditor.autoMatchHeader"
    const val AUTO_MATCH_ENABLED = "MediaSourceEditor.autoMatchEnabled"
    const val TIER = "MediaSourceEditor.tier"
}

/**
 * 可展开的 "自动匹配" 区: 标题行默认收起, 展开后显示 [AutoMatchSection]. 全部为可选配置.
 */
@Composable
fun CollapsibleAutoMatchSection(
    state: AutoMatchConfigState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    textFieldShape: Shape = MediaSourceEditorDefaults.textFieldShape,
    verticalSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
    listItemColors: ListItemColors = MediaSourceEditorDefaults.listItemColors,
) {
    Column(modifier) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        AutoMatchSectionHeader(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            colors = listItemColors,
        )
        AniAnimatedVisibility(visible = expanded) {
            AutoMatchSection(
                state,
                enabled = enabled,
                Modifier.fillMaxWidth().padding(top = 4.dp),
                textFieldShape = textFieldShape,
                verticalSpacing = verticalSpacing,
                listItemColors = listItemColors,
            )
        }
    }
}

@Composable
private fun AutoMatchSectionHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = MediaSourceEditorDefaults.listItemColors,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)
    ListItem(
        headlineContent = {
            ProvideTextStyleContentColor(
                MaterialTheme.typography.titleMedium,
                MaterialTheme.colorScheme.primary,
            ) {
                Text(stringResource(Lang.settings_mediasource_selector_auto_match))
            }
        },
        modifier
            .focusable(false)
            .clickable(onClick = onToggle)
            .semantics { stateDescription = if (expanded) "expanded" else "collapsed" }
            .testTag(MediaSourceEditorTestTags.AUTO_MATCH_HEADER),
        supportingContent = { Text(stringResource(Lang.settings_mediasource_selector_auto_match_description)) },
        trailingContent = {
            Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rotation))
        },
        colors = colors,
    )
}

/**
 * 自动匹配层的全部配置.
 */
@Composable
fun AutoMatchSection(
    state: AutoMatchConfigState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    textFieldShape: Shape = MediaSourceEditorDefaults.textFieldShape,
    verticalSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
    listItemColors: ListItemColors = MediaSourceEditorDefaults.listItemColors,
) {
    Column(modifier) {
        val itemPadding = Modifier.padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))

        SwitchListItem(
            stringResource(Lang.settings_mediasource_selector_auto_match_enabled),
            state.enabled, { state.enabled = it },
            supporting = stringResource(Lang.settings_mediasource_selector_auto_match_enabled_description),
            enabled = enabled,
            switchModifier = Modifier.testTag(MediaSourceEditorTestTags.AUTO_MATCH_ENABLED),
            colors = listItemColors,
        )

        ConfigLongTextField(
            state.tier.value.toLong(),
            { state.tier = MediaSourceTier(it.toUInt()) },
            label = stringResource(Lang.settings_mediasource_selector_tier),
            itemPadding.testTag(MediaSourceEditorTestTags.TIER),
            supportingText = stringResource(Lang.settings_mediasource_selector_tier_description),
            isValid = { it in 0..UInt.MAX_VALUE.toLong() },
            enabled = enabled,
            shape = textFieldShape,
        )

        SwitchListItem(
            stringResource(Lang.settings_mediasource_selector_config_search_first_word),
            state.searchUseOnlyFirstWord, { state.searchUseOnlyFirstWord = it },
            itemPadding,
            supporting = stringResource(Lang.settings_mediasource_selector_config_search_first_word_description),
            enabled = enabled,
            colors = listItemColors,
        )
        SwitchListItem(
            stringResource(Lang.settings_mediasource_selector_config_search_remove_special),
            state.searchRemoveSpecial, { state.searchRemoveSpecial = it },
            itemPadding,
            supporting = stringResource(Lang.settings_mediasource_selector_config_search_remove_special_description),
            enabled = enabled,
            colors = listItemColors,
        )
        SwitchListItem(
            stringResource(Lang.settings_mediasource_selector_prefer_shorter_name),
            state.preferShorterName, { state.preferShorterName = it },
            itemPadding,
            supporting = stringResource(Lang.settings_mediasource_selector_prefer_shorter_name_description),
            enabled = enabled,
            colors = listItemColors,
        )

        ConfigLongTextField(
            state.searchUseSubjectNamesCount.toLong(),
            { state.searchUseSubjectNamesCount = it.toInt() },
            label = stringResource(Lang.settings_mediasource_selector_config_search_subject_names_count),
            itemPadding,
            supportingText = stringResource(Lang.settings_mediasource_selector_config_search_subject_names_count_description),
            isValid = { it in 1..Int.MAX_VALUE.toLong() },
            enabled = enabled,
            shape = textFieldShape,
        )
        ConfigLongTextField(
            state.searchCacheTtl.inWholeMinutes,
            { state.searchCacheTtl = it.minutes },
            label = stringResource(Lang.settings_mediasource_selector_config_search_cache_ttl),
            itemPadding,
            supportingText = stringResource(Lang.settings_mediasource_selector_config_search_cache_ttl_description),
            isValid = { it >= 0 },
            enabled = enabled,
            shape = textFieldShape,
        )

        SwitchListItem(
            stringResource(Lang.settings_mediasource_selector_config_filter_by_episode_sort),
            state.filterByEpisodeSort, { state.filterByEpisodeSort = it },
            itemPadding,
            supporting = stringResource(Lang.settings_mediasource_selector_config_filter_by_episode_sort_description),
            enabled = enabled,
            colors = listItemColors,
        )
    }
}
