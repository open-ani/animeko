/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.subject

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaSourceResultsFilterer
import me.him188.ani.app.domain.media.fetch.restart
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.layout.desktopTitleBarPadding
import me.him188.ani.app.ui.mediafetch.MediaSelectorView
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresenter
import me.him188.ani.app.ui.mediafetch.ViewKind
import me.him188.ani.app.ui.mediafetch.rememberMediaSelectorState

/**
 * 追加缓存流程中的弹窗: 选择数据源的 [ModalBottomSheet] 与选择存储位置的对话框.
 *
 * 从旧版 `EpisodeCacheListGroup` 中抽取, 不依赖 settings 框架, 可被条目缓存页与全局缓存管理页复用.
 *
 * @param hideMediaSelector 用户是否关闭了 media selector.
 * 这种情况下优先考虑是想置于后台或者点错了, 之后重新点击可以复用查询结果.
 */
@Composable
fun EpisodeCacheRequesterDialogs(
    state: EpisodeCacheListState,
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    mediaSelectorSettingsProvider: () -> Flow<MediaSelectorSettings>,
    hideMediaSelector: Boolean,
    onRequestHideMediaSelector: () -> Unit,
) {
    state.currentSelectStorageTask?.let { task ->
        val attemptedTrySelect by task.attemptedTrySelect.collectAsStateWithLifecycle(false)
        if (!attemptedTrySelect) return@let

        SelectMediaStorageDialog(
            options = task.options,
            onSelect = { state.selectStorage(it) },
            onDismissRequest = { state.cancelStorageSelector(task) },
            Modifier,
        )
    }

    state.currentSelectMediaTask?.let { task ->
        val attemptedTrySelect by task.attemptedTrySelect.collectAsStateWithLifecycle(false)
        if (!attemptedTrySelect) return@let

        val scope = rememberCoroutineScope()

        val filteredResults = remember(task.fetchSession.mediaSourceResults, mediaSelectorSettingsProvider, scope) {
            MediaSourceResultsFilterer(
                MutableStateFlow(task.fetchSession.mediaSourceResults),
                settings = mediaSelectorSettingsProvider(),
                flowScope = scope,
            ).filteredSourceResults
                .shareIn(scope, started = SharingStarted.WhileSubscribed(5000), replay = 1)
        }

        // 注意, 这里会一直 collect mediaSourceResults
        val sourceResults by remember(
            task.fetchSession.mediaSourceResults,
            mediaSelectorSettingsProvider,
            scope,
            filteredResults,
        ) {
            // TODO: shit
            MediaSourceResultListPresenter(
                filteredResults,
            ).presentationFlow.map {
                MediaSourceResultListPresentation(it)
            }.shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)
        }.collectAsStateWithLifecycle(MediaSourceResultListPresentation.Empty)
        if (!hideMediaSelector) {
            ModalBottomSheet(
                onDismissRequest = {
                    // 不要取消任务, 用户可能是点错了, 之后重新点击可以复用查询结果
                    onRequestHideMediaSelector()
                },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                modifier = Modifier.desktopTitleBarPadding().statusBarsPadding(),
                contentWindowInsets = { BottomSheetDefaults.windowInsets.add(WindowInsets.desktopTitleBar()) },
            ) {
                val selectorPresentation =
                    rememberMediaSelectorState(mediaSourceInfoProvider, filteredResults) { task.mediaSelector }
                val (viewKind, onViewKindChange) = rememberSaveable { mutableStateOf(ViewKind.WEB) }

                // todo: shit
                val fetchRequest by task.fetchSession.request.collectAsState(null)

                MediaSelectorView(
                    selectorPresentation,
                    viewKind,
                    onViewKindChange,
                    fetchRequest,
                    {
                        task.fetchSession.setFetchRequest(it)
                    },
                    sourceResults,
                    onRestartSource = {
                        task.fetchSession.restart(it)
                    },
                    onRefresh = { task.fetchSession.restartAll() },
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
                        .navigationBarsPadding()
                        .fillMaxHeight() // 防止添加筛选后数量变少导致 bottom sheet 高度变化
                        .fillMaxWidth(),
                    stickyHeaderBackgroundColor = BottomSheetDefaults.ContainerColor,
                    onClickItem = {
                        state.selectMedia(it)
                    },
                )
            }
        }
    }
}
