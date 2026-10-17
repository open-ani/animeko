/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.jsonapi.play

import androidx.annotation.UiThread
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiPreparedRequest
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiVideoCandidate
import me.him188.ani.app.domain.mediasource.test.jsonapi.JsonApiMediaSourceTester
import me.him188.ani.app.domain.mediasource.test.jsonapi.JsonApiPlayTestResult
import me.him188.ani.app.domain.mediasource.test.jsonapi.JsonApiTestEpisode
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.FastLinearProgressIndicator
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_copied
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_candidates
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_copy
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_episode_list_no_video
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_fallback
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_from_episode_list
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_no_candidate
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_not_found
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_not_json
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_open_video
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_request
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_response
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_selected
import me.him188.ani.app.ui.lang.settings_mediasource_jsonapi_play_status
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_edit_config
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_open_link_failed
import me.him188.ani.app.ui.lang.settings_mediasource_selector_episode_open_original_link
import me.him188.ani.app.ui.settings.mediasource.RefreshIndicationDefaults
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceEditorDefaults
import me.him188.ani.app.ui.settings.mediasource.jsonapi.edit.JsonApiConfigState
import me.him188.ani.app.ui.settings.mediasource.jsonapi.edit.JsonApiPlayStageColumn
import org.jetbrains.compose.resources.stringResource

/**
 * 播放阶段的测试: 对当前查看的剧集发播放请求, 展示请求、响应与解析出的候选; 地址来自剧集列表时只展示地址.
 */
@Stable
class JsonApiPlayState(
    private val itemState: State<JsonApiTestEpisode?>,
    private val tester: JsonApiMediaSourceTester,
) {
    val episodeName: String by derivedStateOf { itemState.value?.presentation?.name ?: "" }
    val episodeUrl: String by derivedStateOf { itemState.value?.episode?.url ?: "" }
    val channelMatch: String? by derivedStateOf { itemState.value?.channelMatch }

    val result: Flow<JsonApiPlayTestResult?> get() = tester.playResultFlow
    val isRunning: Flow<Boolean> get() = tester.playRunning.isRunning

    /**
     * 把当前查看的剧集交给测试器. 离开页面时停止播放阶段的测试.
     */
    @UiThread
    suspend fun observeChanges() {
        try {
            snapshotFlow { itemState.value }.collect { tester.setPlayEpisode(it) }
        } finally {
            tester.setPlayEpisode(null)
        }
    }

    fun restart() {
        tester.playLifecycle.restart()
    }
}

object JsonApiPlayPaneDefaults {
    @Composable
    fun TopAppBar(
        state: JsonApiPlayState,
        modifier: Modifier = Modifier,
        windowInsets: WindowInsets = WindowInsets(0.dp),
        navigationIcon: @Composable () -> Unit = {},
    ) {
        val onRefresh = { state.restart() }
        val result by state.result.collectAsStateWithLifecycle(null)
        TopAppBar(
            navigationIcon = navigationIcon,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(state.episodeName, style = LocalTextStyle.current)
                    RefreshIndicationDefaults.RefreshIconButton(onClick = onRefresh)
                    RefreshIndicationDefaults.RefreshResultTextButton(result = result, onRefresh = onRefresh)
                }
            },
            actions = {
                val uriHandler = LocalUriHandler.current
                val toaster = LocalToaster.current
                if (state.episodeUrl.startsWith("http")) {
                    val openLinkFailedText = stringResource(Lang.settings_mediasource_selector_episode_open_link_failed)
                    val openOriginalLinkText = stringResource(
                        Lang.settings_mediasource_selector_episode_open_original_link,
                        state.episodeName,
                    )
                    IconButton(
                        {
                            try {
                                uriHandler.openUri(state.episodeUrl)
                            } catch (e: Throwable) {
                                toaster.toast(openLinkFailedText)
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.ArrowOutward, openOriginalLinkText)
                    }
                }
            },
            colors = AniThemeDefaults.topAppBarColors(),
            modifier = modifier,
            windowInsets = windowInsets,
        )
    }

    /**
     * 紧凑窗口下详情页底部的配置面板: 步骤 3 的配置.
     */
    @Composable
    fun ConfigurationContent(
        state: JsonApiConfigState,
        modifier: Modifier = Modifier,
        contentPadding: PaddingValues = PaddingValues(0.dp),
        textFieldShape: Shape = MediaSourceEditorDefaults.textFieldShape,
        verticalSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
    ) {
        Column(modifier.padding(contentPadding)) {
            Row(Modifier.padding(bottom = 16.dp)) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                    Text(stringResource(Lang.settings_mediasource_selector_episode_edit_config))
                }
            }
            JsonApiPlayStageColumn(
                state,
                textFieldShape = textFieldShape,
                verticalSpacing = verticalSpacing,
            )
        }
    }
}

@Composable
fun JsonApiPlayPaneContent(
    state: JsonApiPlayState,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding,
    itemColors: ListItemColors = ListItemDefaults.colors(),
) {
    Column(modifier) {
        Box(Modifier.height(4.dp), contentAlignment = Alignment.Center) {
            FastLinearProgressIndicator(
                state.isRunning.collectAsStateWithLifecycle(false).value,
                delayMillis = 0,
                minimumDurationMillis = 300,
            )
        }

        val result by state.result.collectAsStateWithLifecycle(null)
        val success = result as? JsonApiPlayTestResult.Success
        val listVideo = result as? JsonApiPlayTestResult.EpisodeListVideo

        val clipboard = LocalClipboard.current
        val toaster = LocalToaster.current
        val scope = rememberCoroutineScope()
        val copiedText = stringResource(Lang.settings_mediasource_copied)
        val copy: (String) -> Unit = { text ->
            scope.launch {
                clipboard.setClipEntryText(text)
                toaster.toast(copiedText)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("summary") {
                PlaySummaryRow(result, Modifier.padding(top = 20.dp))
            }

            val listVideoUrl = listVideo?.videoUrl
            if (listVideoUrl != null) {
                item("episode-list-video") {
                    CandidateItem(
                        JsonApiVideoCandidate(channelMatch = null, url = listVideoUrl),
                        isSelected = true,
                        onCopy = copy,
                        colors = itemColors,
                    )
                }
            }

            if (success != null) {
                item("request") {
                    PlayRequestCard(success.request, onCopy = copy, Modifier.fillMaxWidth())
                }
                item("response") {
                    PlayResponseCard(success, onCopy = copy, Modifier.fillMaxWidth())
                }
                val video = success.video
                if (video != null) {
                    item("candidates-title") {
                        Text(
                            stringResource(Lang.settings_mediasource_jsonapi_play_candidates, video.candidates.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(video.candidates.size, key = { "candidate-$it" }) { index ->
                        val candidate = video.candidates[index]
                        CandidateItem(
                            candidate,
                            isSelected = candidate == video.selected,
                            onCopy = copy,
                            colors = itemColors,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaySummaryRow(result: JsonApiPlayTestResult?, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            val success = result as? JsonApiPlayTestResult.Success
            val video = success?.video
            val selected = video?.selected
            when {
                result is JsonApiPlayTestResult.EpisodeListVideo -> {
                    if (result.videoUrl == null) {
                        Icon(Icons.Rounded.PriorityHigh, null, tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(Lang.settings_mediasource_jsonapi_play_episode_list_no_video))
                    } else {
                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(Lang.settings_mediasource_jsonapi_play_from_episode_list))
                    }
                }

                success == null -> {}
                success.response == null -> {
                    Icon(Icons.Rounded.PriorityHigh, null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(Lang.settings_mediasource_jsonapi_play_not_found))
                }

                video == null -> {
                    Icon(Icons.Rounded.PriorityHigh, null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(Lang.settings_mediasource_jsonapi_play_not_json))
                }

                selected == null -> {
                    Icon(Icons.Rounded.PriorityHigh, null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(Lang.settings_mediasource_jsonapi_play_no_candidate))
                }

                video.isFallback -> {
                    Icon(Icons.Rounded.PriorityHigh, null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(stringResource(Lang.settings_mediasource_jsonapi_play_fallback))
                }

                else -> {
                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary)
                    Text(selected.url, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun PlayRequestCard(
    request: JsonApiPreparedRequest,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Lang.settings_mediasource_jsonapi_play_request), style = MaterialTheme.typography.titleMedium)
        CodeBlock("${request.method} ${request.url}", onCopy)
        if (request.headers.isNotEmpty()) {
            CodeBlock(request.headers.joinToString("\n") { (name, value) -> "$name: $value" }, onCopy)
        }
        request.body?.let { CodeBlock(it, onCopy) }
    }
}

@Composable
private fun PlayResponseCard(
    success: JsonApiPlayTestResult.Success,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val response = success.response ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Lang.settings_mediasource_jsonapi_play_response), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Lang.settings_mediasource_jsonapi_play_status, response.status),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        CodeBlock(response.body.take(RESPONSE_PREVIEW_LENGTH), onCopy = { onCopy(response.body) })
    }
}

/**
 * 等宽文本块, 点击复制.
 */
@Composable
private fun CodeBlock(text: String, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    val copyLabel = stringResource(Lang.settings_mediasource_jsonapi_play_copy)
    Box(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = copyLabel) { onCopy(text) }
            .horizontalScroll(rememberScrollState()),
    ) {
        Text(
            text,
            Modifier.padding(8.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            softWrap = false,
        )
    }
}

@Composable
private fun CandidateItem(
    candidate: JsonApiVideoCandidate,
    isSelected: Boolean,
    onCopy: (String) -> Unit,
    colors: ListItemColors,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    ListItem(
        headlineContent = {
            Text(
                candidate.url,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        },
        modifier.clickable { onCopy(candidate.url) },
        supportingContent = {
            val parts = buildList {
                candidate.channelMatch?.let { add(it) }
                if (isSelected) add(stringResource(Lang.settings_mediasource_jsonapi_play_selected))
            }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
        },
        leadingContent = {
            if (isSelected) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Rounded.Close, null)
            }
        },
        trailingContent = {
            IconButton({ uriHandler.openUri(candidate.url) }) {
                Icon(Icons.Rounded.ArrowOutward, stringResource(Lang.settings_mediasource_jsonapi_play_open_video))
            }
        },
        colors = colors,
    )
}

private const val RESPONSE_PREVIEW_LENGTH = 4000
