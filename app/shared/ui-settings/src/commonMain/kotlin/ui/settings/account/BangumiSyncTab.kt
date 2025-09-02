/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import me.him188.ani.app.data.models.bangumi.BangumiSyncCommand
import me.him188.ani.app.data.models.bangumi.BangumiSyncOp
import me.him188.ani.app.data.models.bangumi.BangumiSyncState
import me.him188.ani.app.data.repository.subject.BangumiSyncCommandRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.search.createTestPager
import me.him188.ani.app.ui.search.loadErrorItem
import me.him188.ani.app.ui.search.pagingFooterStateItem
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.user.BangumiFullSyncStateDialog
import me.him188.ani.client.models.AniCollectionType
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.logging.trace
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@Stable
class BangumiSyncTabViewModel() : AbstractViewModel(), KoinComponent {
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val bangumiSyncCommandRepository: BangumiSyncCommandRepository by inject()
    private val flowRestarter = FlowRestarter()

    val syncCommandsFlow: Flow<PagingData<BangumiSyncCommand>> =
        bangumiSyncCommandRepository.syncCommandsPager().cachedIn(backgroundScope).restartable(flowRestarter)

    val syncState: MutableStateFlow<BangumiSyncState?> = MutableStateFlow(BangumiSyncState.Preparing)

    private fun restartSyncCommandsFlow() {
        flowRestarter.restart()
    }

    suspend fun fullSync() {
        subjectCollectionRepository.performBangumiFullSync()

        while (true) {
            val state = subjectCollectionRepository.getBangumiFullSyncState()
            logger.trace { "Full sync state: $state" }
            syncState.emit(state)
            if (state is BangumiSyncState.Finished) {
                break
            }
            delay(1.seconds)
        }
        restartSyncCommandsFlow()
    }

    suspend fun executeSyncCommands() {
        bangumiSyncCommandRepository.executeSyncCommands()
        restartSyncCommandsFlow()
    }
}

@Composable
fun BangumiSyncTab(
    vm: BangumiSyncTabViewModel = viewModel<BangumiSyncTabViewModel> { BangumiSyncTabViewModel() },
    modifier: Modifier = Modifier
) {
    val asyncHandler = rememberAsyncHandler()

    BangumiSyncTabImpl(
        syncCommandsFlow = vm.syncCommandsFlow,
        syncState = vm.syncState,
        onFullSyncClick = {
            asyncHandler.launch {
                vm.fullSync()
            }
        },
        onPushClick = {
            asyncHandler.launch {
                vm.executeSyncCommands()
            }
        },
        onSyncCancel = {
            asyncHandler.cancelLast()
        },
        isBangumiSyncing = asyncHandler.isWorking,
        modifier = modifier,
    )
}


/**
 * Bangumi 同步设置. 只有用户已经登录并且绑定了 Bangumi 才可以进入此 tab.
 */
@Composable
fun BangumiSyncTabImpl(
    syncCommandsFlow: Flow<PagingData<BangumiSyncCommand>>,
    syncState: Flow<BangumiSyncState?>,
    onFullSyncClick: () -> Unit,
    onPushClick: () -> Unit,
    onSyncCancel: () -> Unit,
    isBangumiSyncing: Boolean,
    modifier: Modifier = Modifier,
) = SettingsTab(modifier) {
    Group({ Text("手动全量同步") }) {
        TextItem(
            title = {
                Text("重新下载全部 Bangumi 数据")
            },
            onClick = onFullSyncClick,
            onClickEnabled = !isBangumiSyncing,
            description = {
                Text("将 Bangumi 的收藏数据下载到 Animeko 收藏服务。通常来说不需要进行这个操作，Animeko 能自动完成同步。仅在你有发现数据不一致的情况时才需要手动下载。此操作可能需要数分钟才能完成，在同步过程中其他功能不可用。请注意，每十分钟只能执行一次全量同步")
            },
        )
    }

    val items = syncCommandsFlow.collectAsLazyPagingItems()
    Group(
        { Text("同步队列") },
        description = { Text("待执行的同步操作") },
        actions = {
            TextButton(onPushClick, enabled = !isBangumiSyncing) {
                Icon(Icons.Default.Publish, null, Modifier.size(ButtonDefaults.IconSize))
                Text("执行全部")
            }
        },
    ) {
        val motionScheme = LocalAniMotionScheme.current
        LazyColumn(Modifier.fillMaxWidth()) {
            loadErrorItem(items)

            items(
                items.itemCount,
                key = items.itemKey { "BangumiSyncCommand-" + it.id },
                contentType = { 1 },
            ) { index ->
                val item = items[index]
                TextItem(
                    title = {
                        Text(
                            item?.let { describe(it.op) }
                                ?: "加载中加载中加载中加载中...", // placeholder, no localization
                        )
                    },
                    description = {
                        Text(item?.id ?: "加载中...")
                    },
                    modifier = Modifier.placeholder(item == null)
                        .animateItem(
                            motionScheme.feedItemFadeInSpec,
                            motionScheme.feedItemPlacementSpec,
                            motionScheme.feedItemFadeOutSpec,
                        ),
                )
            }

            pagingFooterStateItem(items)
        }
    }

    if (isBangumiSyncing) {
        val syncState by syncState.collectAsStateWithLifecycle(null)
        BangumiFullSyncStateDialog(
            state = syncState,
            onDismissRequest = onSyncCancel,
        )
    }
}

private fun describe(op: BangumiSyncOp?): String {
    return when (op) {
        is BangumiSyncOp.AddCollection -> "更新收藏：${op.subjectId} (${op.type.name})"
        is BangumiSyncOp.DeleteCollection -> "删除收藏：${op.subjectId}"
        is BangumiSyncOp.UpdateCollection -> {
            if (op.type == null) return "删除收藏：${op.subjectId}"
            "更新收藏：${op.subjectId} (${op.type?.name})"
        }

        is BangumiSyncOp.UpdateEpisodeCollection -> {
            val type = op.type
            if (type == null) return "标记剧集为未看过：${op.episodeId}"
            "标记剧集为看过：${op.episodeId} (${type.name})"
        }

        null -> "未知操作（请更新版本）"
    }
}

@TestOnly
val TestAniBangumiSyncCommandEntities
    get() = listOf(
        BangumiSyncCommand(
            id = Uuid.randomString(),
            op = BangumiSyncOp.AddCollection(
                subjectId = 1,
                type = AniCollectionType.ON_HOLD,
            ),
            createdAt = Clock.System.now() - 1.days,
        ),
        BangumiSyncCommand(
            id = Uuid.randomString(),
            op = BangumiSyncOp.UpdateEpisodeCollection(
                subjectId = 1,
                episodeId = 2,
                type = AniEpisodeCollectionType.DONE,
            ),
            createdAt = Clock.System.now() - 0.5.hours,
        ),
    )

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewBangumiSyncTab() = ProvideCompositionLocalsForPreview {
    BangumiSyncTabImpl(
        syncCommandsFlow = createTestPager(TestAniBangumiSyncCommandEntities),
        syncState = flowOf(null),
        onFullSyncClick = {},
        onPushClick = {},
        onSyncCancel = {},
        isBangumiSyncing = false,
        modifier = Modifier.fillMaxWidth(),
    )
}
