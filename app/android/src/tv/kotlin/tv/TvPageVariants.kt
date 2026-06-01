/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import me.him188.ani.app.ui.exploration.ExplorationPageVariant
import me.him188.ani.app.ui.exploration.LocalExplorationPageVariant
import me.him188.ani.app.ui.exploration.TvExplorationPage
import me.him188.ani.app.ui.exploration.schedule.LocalSchedulePageVariant
import me.him188.ani.app.ui.exploration.schedule.SchedulePageVariant
import me.him188.ani.app.ui.exploration.schedule.TvSchedulePage
import me.him188.ani.app.ui.exploration.search.LocalSearchPageVariant
import me.him188.ani.app.ui.exploration.search.SearchPageVariant
import me.him188.ani.app.ui.exploration.search.TvSearchPage
import me.him188.ani.app.ui.main.LocalMainScreenShellVariant
import me.him188.ani.app.ui.main.MainScreenShellVariant
import me.him188.ani.app.ui.main.TvMainScreenLayout
import me.him188.ani.app.ui.subject.collection.CollectionPageVariant
import me.him188.ani.app.ui.subject.collection.LocalCollectionPageVariant
import me.him188.ani.app.ui.subject.collection.TvCollectionPage
import me.him188.ani.app.ui.subject.details.LocalSubjectDetailsPageVariant
import me.him188.ani.app.ui.subject.details.SubjectDetailsPageVariant
import me.him188.ani.app.ui.subject.details.layout.SubjectDetailsTvPage
import me.him188.ani.app.ui.subject.episode.EpisodeScreenVariant
import me.him188.ani.app.ui.subject.episode.LocalEpisodeScreenVariant
import me.him188.ani.app.ui.subject.episode.tv.TvEpisodeScreenContent

/**
 * TV 页面变体装配: 把遥控器形态的页面实现注入各共享页面的变体插槽.
 *
 * 共享代码只认识插槽 (`Local*Variant`), 不认识 TV; 是否安装变体由应用入口决定
 * (见 MainActivity 的 UI mode 判断).
 */
/** [InstallTvPageVariants] 的条件版: 非 TV 直接组合 [content], 零影响. */
@Composable
fun MaybeInstallTvPageVariants(isTv: Boolean, content: @Composable () -> Unit) {
    if (isTv) InstallTvPageVariants(content) else content()
}

@Composable
fun InstallTvPageVariants(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMainScreenShellVariant provides MainScreenShellVariant {
                page, selfInfo, navigator, onNavigateToPage, onNavigateToSettings,
                onNavigateToSearch, onLogout, modifier, pageContent,
            ->
            TvMainScreenLayout(
                page, selfInfo, navigator, onNavigateToPage, onNavigateToSettings,
                onNavigateToSearch, onLogout, modifier, pageContent,
            )
        },
        LocalEpisodeScreenVariant provides EpisodeScreenVariant {
                vm, page, danmakuHostState, danmakuEditorState,
                setShowEditCommentSheet, pauseOnPlaying, modifier,
            ->
            TvEpisodeScreenContent(
                vm, page, danmakuHostState, danmakuEditorState,
                setShowEditCommentSheet, pauseOnPlaying, modifier,
            )
        },
        LocalExplorationPageVariant provides ExplorationPageVariant { state, modifier ->
            TvExplorationPage(state, modifier)
        },
        LocalSchedulePageVariant provides SchedulePageVariant { presentation, onRetry, modifier ->
            TvSchedulePage(presentation, onRetry, modifier)
        },
        LocalSearchPageVariant provides SearchPageVariant { state, onIntent, suggestionsPager, modifier ->
            TvSearchPage(state, onIntent, suggestionsPager, modifier)
        },
        LocalCollectionPageVariant provides CollectionPageVariant { state, modifier ->
            TvCollectionPage(state, modifier)
        },
        LocalSubjectDetailsPageVariant provides SubjectDetailsPageVariant {
                state, selfInfo, layoutParams, onPlay, onClickTag, onClickLogin, onShowComments,
                modifier, onEpisodeCollectionUpdate, showTopBar, windowInsets, backgroundPalette,
                onClickOpenExternal, onCoverImageSuccess, onClickCache,
                videoBackground, onVideoBackgroundExitUp,
            ->
            SubjectDetailsTvPage(
                state = state,
                selfInfo = selfInfo,
                layoutParams = layoutParams,
                onPlay = onPlay,
                onClickTag = onClickTag,
                onClickLogin = onClickLogin,
                onShowComments = onShowComments,
                modifier = modifier,
                onEpisodeCollectionUpdate = onEpisodeCollectionUpdate,
                showTopBar = showTopBar,
                windowInsets = windowInsets,
                backgroundPalette = backgroundPalette,
                onClickOpenExternal = onClickOpenExternal,
                onCoverImageSuccess = onCoverImageSuccess,
                onClickCache = onClickCache,
                videoBackground = videoBackground,
                onVideoBackgroundExitUp = onVideoBackgroundExitUp,
            )
        },
        content = content,
    )
}
