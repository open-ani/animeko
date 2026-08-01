/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.PlayerFrameHolder
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.subject.details.SubjectDetailsScreen
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import me.him188.ani.app.ui.subject.episode.EpisodePageState
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel

/**
 * L3 详情页覆盖层: 隐藏全部播放器组件, 正在播放的视频画面作为背景 (透明容器 + 视频遮罩,
 * 首屏只压底部, 滚动后整屏变暗 —— 与独立详情页视觉一致).
 *
 * 功能与独立详情页完全一致 (可导航到评论区); 唯一差别是选集卡片点击 = 切换当前播放集
 * 并关闭覆盖层 (复用 EpisodeSelectorState 切集链路). 返回键由 TvEpisodeScreen 根路由
 * 处理 (隐藏整个覆盖层回纯视频).
 */
@Composable
internal fun TvPlayerDetailsOverlay(
    vm: EpisodeViewModel,
    page: EpisodePageState,
    onClose: () -> Unit,
    /** 介绍页顶部按上键: 关闭详情层回到控制层的选集条 (展开态并聚焦). */
    onExitUpToStrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val detailsState = vm.episodeDetailsState

    // 打开时加载条目详情 (loader 有"已加载"守卫, 重复打开不会重新请求)
    LaunchedEffect(Unit) {
        detailsState.subjectDetailsStateLoader.load(detailsState.subjectId, detailsState.subjectInfo.value)
    }
    val subjectDetailsState by detailsState.subjectDetailsStateLoader.state
        .collectAsStateWithLifecycle(SubjectDetailsUIState.Placeholder(detailsState.subjectId))

    Box(modifier) {
        when (val state = subjectDetailsState) {
            is SubjectDetailsUIState.Ok, is SubjectDetailsUIState.Err -> {
                SubjectDetailsScreen(
                    state,
                    page.selfInfo,
                    onPlay = { episodeId ->
                        // 与手机版 onSwitchEpisode 一致: 选集列表里有这集就地切换, 否则整页导航
                        if (!vm.episodeSelectorState.selectEpisodeId(episodeId)) {
                            navigator.navigateEpisodeDetails(vm.subjectId, episodeId)
                        }
                        onClose()
                    },
                    onLoadErrorRetry = { detailsState.subjectDetailsStateLoader.reload(detailsState.subjectId) },
                    onClickTag = { navigator.navigateSubjectSearch(it.name) },
                    onEpisodeCollectionUpdate = { request ->
                        scope.launch {
                            vm.setEpisodeCollectionType.invokeSafe(request)?.let {
                                toaster.showLoadError(it)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    showTopBar = false,
                    showBlurredBackground = false,
                    videoBackground = true,
                    onVideoBackgroundExitUp = onExitUpToStrip,
                    // 缓存入口: 跳转前捕获当前画面, 作缓存页的暂停帧背景
                    onClickCacheOverride = {
                        scope.launch {
                            PlayerFrameHolder.put(captureTvPlayerFrame(vm.player))
                            navigator.navigateSubjectCaches(vm.subjectId)
                        }
                    },
                )
            }

            // 加载中: 透明暗层 + 指示器 (不走不透明的占位页, 避免视频背景闪黑)
            else -> Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
