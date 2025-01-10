/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatIndexGrouped
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorFormatId
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatIndexed
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatJsonPathIndexed
import me.him188.ani.app.ui.foundation.animation.StandardEasing
import me.him188.ani.app.ui.foundation.effects.moveFocusOnEnter
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.EasingDurations
import me.him188.ani.app.ui.settings.mediasource.rss.edit.MediaSourceHeadline
import me.him188.ani.datasources.api.topic.Resolution
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun SelectorConfigurationPane(
    state: SelectorConfigState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalSpacing: Dp = SelectorConfigurationDefaults.verticalSpacing,
    textFieldShape: Shape = SelectorConfigurationDefaults.textFieldShape,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        // 大图标和标题
        MediaSourceHeadline(state.iconUrl, state.displayName)

        Column(
            Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp),
        ) {
            val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

            Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                OutlinedTextField(
                    state.displayName, { state.displayName = it },
                    Modifier
                        .fillMaxWidth()
                        .moveFocusOnEnter(),
                    label = { Text("名称*") },
                    placeholder = { Text("设置显示在列表中的名称") },
                    isError = state.displayNameIsError,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
                OutlinedTextField(
                    state.iconUrl, { state.iconUrl = it },
                    Modifier
                        .fillMaxWidth()
                        .moveFocusOnEnter(),
                    label = { Text("图标链接") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text(SelectorConfigurationDefaults.STEP_NAME_1)
                }
            }

            Column {
                OutlinedTextField(
                    state.searchUrl, { state.searchUrl = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text("搜索链接") },
                    placeholder = {
                        Text(
                            "示例：https://www.nyacg.net/search.html?wd={keyword}",
                            color = MaterialTheme.colorScheme.outline,
                        )
                    },
                    supportingText = {
                        Text(
                            """
                                    替换规则：
                                    {keyword} 替换为条目 (番剧) 名称
                                """.trimIndent(),
                        )
                    },
                    isError = state.searchUrlIsError,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
                OutlinedTextField(
                    state.rawBaseUrl, { state.rawBaseUrl = it },
                    Modifier
                        .padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
                        .fillMaxWidth().moveFocusOnEnter(),
                    label = { Text("Base URL (可选)") },
                    placeholder = state.baseUrlPlaceholder?.let {
                        {
                            Text(it, color = MaterialTheme.colorScheme.outline)
                        }
                    },
                    supportingText = {
                        Text(
                            """可选。用于拼接条目详情 (剧集列表) 页面 URL，将会影响步骤 2。默认自动从搜索链接生成""".trimIndent(),
                        )
                    },
                    isError = state.searchUrlIsError,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
                ListItem(
                    headlineContent = { Text("仅使用第一个词") },
                    Modifier
                        .padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
                        .clickable(enabled = state.enableEdit) {
                            state.searchUseOnlyFirstWord = !state.searchUseOnlyFirstWord
                        },
                    supportingContent = { Text("以空格分割，仅使用第一个词搜索。适用于搜索兼容性差的情况") },
                    trailingContent = {
                        Switch(
                            state.searchUseOnlyFirstWord, { state.searchUseOnlyFirstWord = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )
                ListItem(
                    headlineContent = { Text("去除特殊字符") },
                    Modifier
                        .padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
                        .clickable(enabled = state.enableEdit) {
                            state.searchRemoveSpecial = !state.searchRemoveSpecial
                        },
                    supportingContent = { Text("去除特殊字符以及 \"电影\" 等字样，提升搜索成功率") },
                    trailingContent = {
                        Switch(
                            state.searchRemoveSpecial, { state.searchRemoveSpecial = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )

                var searchUseSubjectNamesCount by remember(state.searchUseSubjectNamesCount) {
                    mutableStateOf(state.searchUseSubjectNamesCount.toString())
                }
                OutlinedTextField(
                    searchUseSubjectNamesCount,
                    {
                        searchUseSubjectNamesCount = it
                        state.searchUseSubjectNamesCount = it.toIntOrNull() ?: state.searchUseSubjectNamesCount
                    },
                    Modifier
                        .padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
                        .fillMaxWidth().moveFocusOnEnter(),
                    label = { Text("尝试条目名称数量") },
                    supportingText = {
                        Text(
                            """
                                每次播放使用多少个条目名称进行查询。
                                为 1 则只使用主中文名称，为 2 额外使用日文原名，大于 2 将额外使用其他别名，别名的数量不固定。
                                一般用 1 就够了，使用多个名称将会显著增加播放时的等待时间。
                                """.trimIndent(),
                        )
                    },
                    isError = searchUseSubjectNamesCount.toIntOrNull().let {
                        it == null || it < 1
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
                var requestIntervalString by remember(state.requestInterval) {
                    mutableStateOf(state.requestInterval.inWholeMilliseconds.toString())
                }
                OutlinedTextField(
                    requestIntervalString,
                    {
                        requestIntervalString = it
                        state.requestInterval = it.toLongOrNull()?.milliseconds ?: state.requestInterval
                    },
                    Modifier
                        .padding(top = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
                        .fillMaxWidth().moveFocusOnEnter(),
                    label = { Text("搜索请求间隔时间 (毫秒)") },
                    supportingText = {
                        Text(
                            """控制每发送一个请求后等待多久后再发送下一个请求""".trimIndent(),
                        )
                    },
                    isError = requestIntervalString.toLongOrNull() == null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
            }

            SelectorSubjectFormatSelectionButtonRow(
                state,
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                enabled = state.enableEdit,
            )

            AnimatedContent(
                SelectorSubjectFormat.findById(state.subjectFormatId),
                Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth()
                    .animateContentSize(tween(EasingDurations.standard, easing = StandardEasing)),
                transitionSpec = AniThemeDefaults.standardAnimatedContentTransition,
            ) { format ->
                SelectorSubjectConfigurationColumn(
                    format, state,
                    textFieldShape, verticalSpacing, listItemColors,
                    Modifier.fillMaxWidth(),
                )
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text(SelectorConfigurationDefaults.STEP_NAME_2)
                }
            }

            SelectorChannelSelectionButtonRow(
                state,
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                enabled = state.enableEdit,
            )

            AnimatedContent(
                state.channelFormatId,
                Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth()
                    .animateContentSize(tween(EasingDurations.standard, easing = StandardEasing)),
                transitionSpec = AniThemeDefaults.standardAnimatedContentTransition,
            ) { formatId ->
                SelectorChannelFormatColumn(formatId, state, Modifier.fillMaxWidth())
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text("过滤设置")
                }
            }

            Column(
                Modifier,
                verticalArrangement = Arrangement.spacedBy((verticalSpacing - 16.dp).coerceAtLeast(0.dp)),
            ) {
                ListItem(
                    headlineContent = { Text("使用条目名称过滤") },
                    Modifier.focusable(false).clickable(
                        enabled = state.enableEdit,
                    ) { state.filterBySubjectName = !state.filterBySubjectName },
                    supportingContent = { Text("要求资源标题包含条目名称。适用于数据源可能搜到无关内容的情况。此功能只对 4.4.0 以前版本有效，对其他版本无效") },
                    trailingContent = {
                        Switch(
                            state.filterBySubjectName, { state.filterBySubjectName = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )
                ListItem(
                    headlineContent = { Text("使用剧集序号过滤") },
                    Modifier.focusable(false).clickable(
                        enabled = state.enableEdit,
                    ) { state.filterByEpisodeSort = !state.filterByEpisodeSort },
                    supportingContent = { Text("要求资源标题包含剧集序号。适用于数据源可能搜到无关内容的情况。通常建议开启") },
                    trailingContent = {
                        Switch(
                            state.filterByEpisodeSort, { state.filterByEpisodeSort = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text(SelectorConfigurationDefaults.STEP_NAME_3)
                }
            }

            SelectorConfigurationDefaults.MatchVideoSection(
                state,
                textFieldShape = textFieldShape,
                verticalSpacing = verticalSpacing,
            )

            kotlin.run {
                var showMenu by rememberSaveable { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("标记分辨率") },
                    Modifier.focusable(false).clickable(
                        enabled = state.enableEdit,
                    ) { showMenu = !showMenu },
                    supportingContent = { Text("将此数据源的资源都标记为该分辨率。不影响查询，只在播放器中选择数据源时用做偏好和过滤选项。") },
                    trailingContent = {
                        TextButton(onClick = { showMenu = true }) {
                            Text(state.defaultResolution.displayName)
                        }
                        if (showMenu) {
                            DropdownMenu(showMenu, { showMenu = false }) {
                                for (resolution in Resolution.entries.asReversed()) {
                                    DropdownMenuItem(
                                        text = { Text(resolution.displayName) },
                                        onClick = {
                                            state.defaultResolution = resolution
                                            showMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = listItemColors,
                )
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text("在播放器内选择资源时")
                }
            }

            Column(Modifier, verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                val conf = state.selectMediaConfig
                ListItem(
                    headlineContent = { Text("区分条目名称") },
                    Modifier.focusable(false).clickable(
                        enabled = state.enableEdit,
                    ) { conf.distinguishSubjectName = !conf.distinguishSubjectName },
                    supportingContent = {
                        Text(
                            "关闭后，所有步骤 1 搜索到的条目都将被视为同一个，它们的相同标题的剧集将会被去重。" +
                                    "开启此项则不会这样去重。\n" +
                                    "此选项不影响测试结果，影响播放器内选择数据源时的结果。",
                        )
                    },
                    trailingContent = {
                        Switch(
                            conf.distinguishSubjectName, { conf.distinguishSubjectName = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )
                ListItem(
                    headlineContent = { Text("区分线路名称") },
                    Modifier.focusable(false).clickable(
                        enabled = state.enableEdit,
                    ) { conf.distinguishChannelName = !conf.distinguishChannelName },
                    supportingContent = {
                        Text(
                            "关闭后，线路名称不同，但只要标题相同的剧集就会被去重。" +
                                    "开启此项则不会这样去重。\n" +
                                    "此选项不影响测试结果，影响播放器内选择数据源时的结果。",
                        )
                    },
                    trailingContent = {
                        Switch(
                            conf.distinguishChannelName, { conf.distinguishChannelName = it },
                            enabled = state.enableEdit,
                        )
                    },
                    colors = listItemColors,
                )
            }

            Row(Modifier.padding(top = verticalSpacing, bottom = 12.dp)) {
                ProvideTextStyleContentColor(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.colorScheme.primary,
                ) {
                    Text("播放视频时")
                }
            }

            Column(Modifier, verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                val conf = state.matchVideoConfig.videoHeaders
                OutlinedTextField(
                    conf.referer, { conf.referer = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text("Referer") },
                    supportingText = { Text("播放视频时执行的 HTTP 请求的 Referer，可留空") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
                OutlinedTextField(
                    conf.userAgent, { conf.userAgent = it },
                    Modifier.fillMaxWidth().moveFocusOnEnter(),
                    label = { Text("User-Agent") },
                    supportingText = { Text("播放视频时执行的 HTTP 请求的 User-Agent") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = textFieldShape,
                    enabled = state.enableEdit,
                )
            }

            Row(Modifier.align(Alignment.End).padding(top = verticalSpacing, bottom = 12.dp)) {
                if (state.enableEdit) {
                    ProvideTextStyleContentColor(
                        MaterialTheme.typography.labelMedium,
                        MaterialTheme.colorScheme.outline,
                    ) {
                        Text("提示：修改自动保存")
                    }
                }
            }
        }

    }
}


@Composable
private fun SelectorSubjectFormatSelectionButtonRow(
    state: SelectorConfigState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        @Composable
        fun Btn(
            id: SelectorFormatId, index: Int,
            label: @Composable () -> Unit,
        ) {
            SegmentedButton(
                state.subjectFormatId == id,
                { state.subjectFormatId = id },
                SegmentedButtonDefaults.itemShape(index, state.allSubjectFormats.size),
                icon = { SegmentedButtonDefaults.Icon(state.subjectFormatId == id) },
                label = label,
                enabled = enabled,
            )
        }

        for ((index, format) in state.allSubjectFormats.withIndex()) {
            Btn(format.id, index) {
                Text(
                    when (format) { // type-safe to handle all formats
                        SelectorSubjectFormatA -> "单标签"
                        SelectorSubjectFormatIndexed -> "多标签"
                        SelectorSubjectFormatJsonPathIndexed -> "JsonPath"
                    },
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun SelectorChannelSelectionButtonRow(
    state: SelectorConfigState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        @Composable
        fun Btn(
            id: SelectorFormatId, index: Int,
            label: @Composable () -> Unit,
        ) {
            SegmentedButton(
                state.channelFormatId == id,
                { state.channelFormatId = id },
                SegmentedButtonDefaults.itemShape(index, state.allChannelFormats.size),
                icon = { SegmentedButtonDefaults.Icon(state.channelFormatId == id) },
                label = label,
                enabled = enabled,
            )
        }

        for ((index, selectorChannelFormat) in state.allChannelFormats.withIndex()) {
            Btn(selectorChannelFormat.id, index) {
                Text(
                    when (selectorChannelFormat) { // type-safe to handle all formats
                        SelectorChannelFormatNoChannel -> "不区分线路"
                        SelectorChannelFormatIndexGrouped -> "线路分组"
                    },
                    softWrap = false,
                )
            }
        }
    }
}
