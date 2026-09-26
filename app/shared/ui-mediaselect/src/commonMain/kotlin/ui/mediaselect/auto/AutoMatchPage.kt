/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.auto

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediaselect.selector.MediaSelectorWebSourcesColumn
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isMobile

/**
 * 自动匹配页 = WEB 源列表 + 底部救援卡片. 不画顶栏, 不画「正在观看」.
 * 验证码文案沿用 webCaptchaRequiredMessage(kind) 与 iOS 分支 media_selector_web_captcha_unsupported.
 *
 * @param onClickItem 用户点了线路 chip (channel.original); 宿主负责 select + 关闭容器.
 * @param onRequestManualSearch 点了「找不到想看的？手动查找」; null 时不显示卡片 (下载对话框).
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
    val scope = rememberCoroutineScope()
    val bottomPadding = if (LocalPlatform.current.isMobile()) 0.dp else 8.dp

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
                if (state.resolveCaptcha(source)) {
                    onRestartSource(source.instanceId)
                }
            }
        },
        onRequestManualSearch = onRequestManualSearch,
        modifier
            .testTag(AutoMatchPageTestTags.ROOT)
            .padding(bottom = bottomPadding)
            .fillMaxWidth()
            .ifThen(scrollable) { verticalScroll(rememberScrollState()) },
    )
}

object AutoMatchPageTestTags {
    const val ROOT = "auto_match_page"
    const val RESCUE_CARD = "auto_match_rescue_card"
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
