/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.auto

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.selector.PlaybackSourceMemory
import me.him188.ani.app.domain.media.selector.PlaybackAutoSelectionState
import me.him188.ani.app.domain.media.selector.MediaAutoSelectExhaustCause
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_retry_failed_routes
import me.him188.ani.app.ui.lang.media_selector_runtime_failures
import me.him188.ani.app.ui.lang.media_selector_auto_exhausted
import me.him188.ani.app.ui.lang.media_selector_auto_no_candidates
import me.him188.ani.app.ui.lang.media_selector_auto_no_preference_match
import me.him188.ani.app.ui.lang.media_selector_auto_wait_expired
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediaselect.selector.MediaSelectorWebSourcesColumn
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource

/**
 * 自动匹配页 = WEB 源列表 + 救援按钮 (在查询失败的源之前, 见 [MediaSelectorWebSourcesColumn]). 不画顶栏, 不画「正在观看」.
 * 验证码文案沿用 webCaptchaRequiredMessage(kind) 与 iOS 分支 media_selector_web_captcha_unsupported.
 *
 * @param onClickItem 用户点了线路 chip (channel.original); 宿主负责 select + 关闭容器.
 * @param onRequestManualSearch 点了「找不到想看的？手动查找」; null 时不显示按钮 (宿主没有手动查找).
 * @param scrollable 为 true 时整页 verticalScroll (列表本身是 Column, 不是 LazyColumn).
 */
@Composable
fun AutoMatchPage(
    state: MediaSelectorState,
    onClickItem: (Media) -> Unit,
    onRestartSource: (instanceId: String) -> Unit,
    onRequestManualSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    val playbackMemory by state.playbackMemorySnapshots.collectAsStateWithLifecycle(PlaybackSourceMemory.Snapshot())
    val autoSelection by state.autoSelectionState.collectAsStateWithLifecycle(PlaybackAutoSelectionState.State.Idle)
    val scope = rememberCoroutineScope()
    val bottomPadding = if (LocalPlatform.current.isMobile()) 0.dp else 8.dp

    Column(
        modifier
            .testTag(AutoMatchPageTestTags.ROOT)
            .padding(bottom = bottomPadding)
            .fillMaxWidth()
            .ifThen(scrollable) { verticalScroll(rememberScrollState()) },
    ) {
        val exhausted = autoSelection as? PlaybackAutoSelectionState.State.Exhausted
        if (exhausted != null) {
            Column(Modifier.padding(16.dp).testTag(AutoMatchPageTestTags.EXHAUSTED)) {
                Text(stringResource(Lang.media_selector_auto_exhausted))
                val reason = when (exhausted.cause) {
                    MediaAutoSelectExhaustCause.NO_PREFERENCE_MATCH -> Lang.media_selector_auto_no_preference_match
                    MediaAutoSelectExhaustCause.FUZZY_DEADLINE -> Lang.media_selector_auto_wait_expired
                    else -> Lang.media_selector_auto_no_candidates
                }
                Text(stringResource(reason))
            }
        }
        if (playbackMemory.failures.isNotEmpty()) {
            Text(stringResource(Lang.media_selector_runtime_failures, playbackMemory.failures.size), Modifier.padding(16.dp))
            TextButton(
                onClick = state::retryFailedPlaybackSources,
                modifier = Modifier.testTag(AutoMatchPageTestTags.RETRY_FAILED_ROUTES),
            ) {
                Text(stringResource(Lang.media_selector_retry_failed_routes))
            }
        }
        MediaSelectorWebSourcesColumn(
            presentation.webSources,
            selectedSource = { presentation.selectedWebSource },
            selectedChannel = { presentation.selectedWebSourceChannel },
            onSelect = { _, channel ->
                channel.original?.let { onClickItem(it) }
            },
            onRefresh = { onRestartSource(it.instanceId) },
            onResolveCaptcha = { source ->
                scope.launch {
                    if (state.resolveCaptcha(source) == SolveOutcome.Solved) {
                        onRestartSource(source.instanceId)
                    }
                }
            },
            onRequestManualSearch = onRequestManualSearch,
            Modifier.fillMaxWidth(),
        )
    }
}

object AutoMatchPageTestTags {
    const val ROOT = "auto_match_page"
    const val RESCUE_BUTTON = "auto_match_rescue_button"
    const val RETRY_FAILED_ROUTES = "auto_match_retry_failed_routes"
    const val EXHAUSTED = "auto_match_exhausted"
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewAutoMatchPage() {
    ProvideCompositionLocalsForPreview {
        Surface {
            AutoMatchPage(
                state = rememberTestMediaSelectorState(),
                onClickItem = {},
                onRestartSource = {},
                onRequestManualSearch = {},
            )
        }
    }
}
