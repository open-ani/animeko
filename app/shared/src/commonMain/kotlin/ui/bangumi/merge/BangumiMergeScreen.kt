/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeConflictKey
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeSide
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.bangumi_merge_adopt_newer
import me.him188.ani.app.ui.lang.bangumi_merge_apply
import me.him188.ani.app.ui.lang.bangumi_merge_apply_success
import me.him188.ani.app.ui.lang.bangumi_merge_auto_merged
import me.him188.ani.app.ui.lang.bangumi_merge_confirmed_progress
import me.him188.ani.app.ui.lang.bangumi_merge_headline
import me.him188.ani.app.ui.lang.bangumi_merge_headline_auto_only
import me.him188.ani.app.ui.lang.bangumi_merge_info
import me.him188.ani.app.ui.lang.bangumi_merge_info_no_auto
import me.him188.ani.app.ui.lang.bangumi_merge_synced
import me.him188.ani.app.ui.lang.bangumi_merge_synced_description
import me.him188.ani.app.ui.lang.bangumi_merge_title
import me.him188.ani.app.ui.lang.bangumi_merge_title_conflicts
import me.him188.ani.app.ui.search.LoadErrorCard
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 合并收藏 (Bangumi 冲突处理) 的自适应布局参数.
 *
 * 设计稿断点 (Figma: 同步冲突 · 双列对照):
 * - `<360dp`: 单元格内值与时间戳改为上下两行, 字段列收窄;
 * - Compact (`<600dp`): 手机布局, 按条目分组的卡片列表;
 * - Medium/Expanded (`>=600dp`): 条目左列表格布局, 内容封顶 1080dp 居中;
 * - `>=1600dp`: 双栏网格.
 */
@Immutable
data class BangumiMergeLayoutParams(
    /** 使用条目左列的表格布局 (桌面). */
    val useTableLayout: Boolean,
    /** 分组卡片流入两栏 (超宽屏). */
    val useTwoColumnGrid: Boolean,
    /** 单元格内值与时间戳上下两行 (小屏). */
    val twoLineCell: Boolean,
    val rowMinHeight: Dp,
    val fieldColumnWidth: Dp,
    val subjectColumnWidth: Dp,
    /** 内容最大宽度, [Dp.Unspecified] 表示不限. */
    val contentMaxWidth: Dp,
) {
    companion object {
        val Compact = BangumiMergeLayoutParams(
            useTableLayout = false,
            useTwoColumnGrid = false,
            twoLineCell = false,
            rowMinHeight = 36.dp,
            fieldColumnWidth = 44.dp,
            subjectColumnWidth = Dp.Unspecified,
            contentMaxWidth = Dp.Unspecified,
        )

        val CompactSmall = Compact.copy(
            twoLineCell = true,
            rowMinHeight = 44.dp,
            fieldColumnWidth = 38.dp,
        )

        val Table = BangumiMergeLayoutParams(
            useTableLayout = true,
            useTwoColumnGrid = false,
            twoLineCell = false,
            rowMinHeight = 42.dp,
            fieldColumnWidth = 72.dp,
            subjectColumnWidth = 220.dp,
            contentMaxWidth = 1080.dp,
        )

        val TwoColumnGrid = Table.copy(
            useTwoColumnGrid = true,
            contentMaxWidth = Dp.Unspecified,
        )

        @Composable
        fun calculate(
            windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass,
        ): BangumiMergeLayoutParams {
            // WindowSizeClass (V1 断点, 最大 840) 无法表达 360dp 与 1600dp 这两个设计稿断点,
            // 这两档用实际窗口宽度判断.
            val windowWidthDp = with(LocalDensity.current) {
                LocalWindowInfo.current.containerSize.width.toDp()
            }
            return when {
                windowWidthDp >= 1600.dp -> TwoColumnGrid
                windowSizeClass.isWidthAtLeastMedium -> Table
                windowWidthDp < 360.dp -> CompactSmall
                else -> Compact
            }
        }
    }
}

object BangumiMergeTestTags {
    const val LIST = "bangumiMergeList"
    const val ADOPT_NEWER = "bangumiMergeAdoptNewer"
    const val APPLY_BUTTON = "bangumiMergeApply"
    const val APPLY_BLOCKED_OVERLAY = "bangumiMergeApplyBlocked"
    const val PROGRESS_TEXT = "bangumiMergeProgressText"
    const val AUTO_MERGED_TOGGLE = "bangumiMergeAutoMergedToggle"
    const val EMPTY_STATE = "bangumiMergeEmptyState"
    const val SELECT_ALL_LOCAL = "bangumiMergeSelectAllLocal"
    const val SELECT_ALL_REMOTE = "bangumiMergeSelectAllRemote"

    fun card(subjectId: Int) = "bangumiMergeCard-$subjectId"
    fun cell(key: BangumiMergeConflictKey, side: BangumiMergeSide) =
        "bangumiMergeCell-${key.subjectId}-${key.fieldId}-${side.name}"
}

/**
 * 合并收藏界面 (有状态包装).
 */
@Composable
fun BangumiMergeScreen(
    vm: BangumiMergeViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val successText = stringResource(Lang.bangumi_merge_apply_success)

    LaunchedEffect(state.applied) {
        if (state.applied) {
            toaster.toast(successText)
            onNavigateBack()
        }
    }

    LaunchedEffect(state.applyError) {
        state.applyError?.let {
            toaster.showLoadError(it)
            vm.clearApplyError()
        }
    }

    BangumiMergeScreen(
        state = state,
        onSelect = vm::select,
        onAdoptNewer = vm::adoptNewer,
        onSelectAll = vm::selectAll,
        onApply = vm::startApply,
        onRetry = vm::reload,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        navigationIcon = navigationIcon,
        windowInsets = windowInsets,
    )
}

/**
 * 合并收藏界面 (无状态).
 *
 * 双列对照: 左列 Animeko, 右列 Bangumi, 每行一个冲突.
 */
@Composable
fun BangumiMergeScreen(
    state: BangumiMergeUiState,
    onSelect: (BangumiMergeConflictKey, BangumiMergeSide) -> Unit,
    onAdoptNewer: () -> Unit,
    onSelectAll: (BangumiMergeSide) -> Unit,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    layoutParams: BangumiMergeLayoutParams = BangumiMergeLayoutParams.calculate(),
    getTimeNow: () -> Instant = { Clock.System.now() },
) {
    val plan = state.plan
    val hasConflicts = plan != null && plan.conflictGroups.isNotEmpty()

    // 只有自动合并项时也需要"应用合并" (写入两侧与基线), 只是无需用户逐项确认.
    val hasWork = plan != null && !plan.isEmpty

    // 点击禁用态"应用合并"时, 滚动到第一个未决定的条目并闪烁提示.
    var flashRequest by remember { mutableStateOf<MergeFlashRequest?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            AniTopAppBar(
                title = {
                    AniTopAppBarDefaults.Title(
                        if (!layoutParams.useTableLayout && hasConflicts) {
                            stringResource(Lang.bangumi_merge_title_conflicts, state.totalConflictCount)
                        } else {
                            stringResource(Lang.bangumi_merge_title)
                        },
                    )
                },
                navigationIcon = navigationIcon,
                colors = AniThemeDefaults.topAppBarColors(),
                windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
            )
        },
        bottomBar = {
            if (hasWork && !state.isLoading) {
                MergeBottomBar(
                    confirmedCount = state.confirmedCount,
                    totalCount = state.totalConflictCount,
                    autoMergedCount = plan?.autoMerged?.size ?: 0,
                    canApply = state.allResolved && !state.isApplying,
                    isApplying = state.isApplying,
                    onApply = onApply,
                    onApplyBlocked = {
                        val groups = plan?.conflictGroups.orEmpty()
                        val firstUnresolved = groups.indexOfFirst { group ->
                            group.conflicts.any {
                                BangumiMergeConflictKey(group.subjectId, it.id) !in state.choices
                            }
                        }
                        if (firstUnresolved >= 0) {
                            flashRequest = MergeFlashRequest(
                                subjectId = groups[firstUnresolved].subjectId,
                                groupIndex = firstUnresolved,
                                nonce = (flashRequest?.nonce ?: 0) + 1,
                            )
                        }
                    },
                    windowInsets = windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                )
            }
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        BackHandler { onNavigateBack() }

        when {
            state.isLoading -> {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.loadError != null -> {
                Box(Modifier.padding(padding).fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadErrorCard(
                        error = state.loadError,
                        onRetry = onRetry,
                        modifier = Modifier.widthIn(max = 480.dp),
                    )
                }
            }

            plan == null || !hasWork -> {
                MergeEmptyState(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                )
            }

            else -> {
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .then(
                            if (layoutParams.contentMaxWidth != Dp.Unspecified) {
                                Modifier.wrapContentWidth().widthIn(max = layoutParams.contentMaxWidth)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    MergeInfoHeader(
                        state = state,
                        onAdoptNewer = onAdoptNewer,
                        layoutParams = layoutParams,
                    )
                    BangumiMergeConflictList(
                        state = state,
                        onSelect = onSelect,
                        onSelectAll = onSelectAll,
                        layoutParams = layoutParams,
                        flashRequest = flashRequest,
                        getTimeNow = getTimeNow,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

internal data class MergeFlashRequest(
    val subjectId: Int,
    val groupIndex: Int,
    val nonce: Int,
)

/**
 * 说明行: 冲突说明 + "采用较新的" 推荐动作.
 *
 * 桌面表格布局使用大标题 + 副标题; 移动端为紧凑说明行; 小屏 (双行单元格) 时按钮换行到说明下方.
 */
@Composable
private fun MergeInfoHeader(
    state: BangumiMergeUiState,
    onAdoptNewer: () -> Unit,
    layoutParams: BangumiMergeLayoutParams,
    modifier: Modifier = Modifier,
) {
    val autoMergedCount = state.plan?.autoMerged?.size ?: 0
    // 只有自动合并项时: 不展示冲突相关的说明与"采用较新的" (没有可作用的冲突).
    val hasConflicts = state.totalConflictCount > 0
    val infoText = when {
        !hasConflicts -> stringResource(Lang.bangumi_merge_auto_merged, autoMergedCount)
        autoMergedCount > 0 -> stringResource(Lang.bangumi_merge_info, autoMergedCount)
        else -> stringResource(Lang.bangumi_merge_info_no_auto)
    }
    val horizontalPadding = currentWindowAdaptiveInfo1().windowSizeClass.paneHorizontalPadding

    if (layoutParams.useTableLayout) {
        Row(
            modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (hasConflicts) {
                        stringResource(Lang.bangumi_merge_headline, state.totalConflictCount)
                    } else {
                        stringResource(Lang.bangumi_merge_headline_auto_only)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    infoText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasConflicts) {
                AdoptNewerButton(onAdoptNewer, Modifier.padding(start = 16.dp))
            }
        }
    } else if (layoutParams.twoLineCell) {
        Column(
            modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                infoText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasConflicts) {
                AdoptNewerButton(onAdoptNewer)
            }
        }
    } else {
        Row(
            modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                infoText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (hasConflicts) {
                AdoptNewerButton(onAdoptNewer, Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun AdoptNewerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.testTag(BangumiMergeTestTags.ADOPT_NEWER),
    ) {
        Icon(Icons.Rounded.Schedule, null, Modifier.size(ButtonDefaults.IconSize))
        Text(
            stringResource(Lang.bangumi_merge_adopt_newer),
            Modifier.padding(start = ButtonDefaults.IconSpacing),
        )
    }
}

/**
 * 底栏: 常驻进度 + "应用合并".
 *
 * 全部确认前 "应用合并" 保持禁用; 点击禁用态会通过 [onApplyBlocked] 滚动到第一个未决定项并闪烁提示,
 * 代替静默失败.
 */
@Composable
private fun MergeBottomBar(
    confirmedCount: Int,
    totalCount: Int,
    autoMergedCount: Int,
    canApply: Boolean,
    isApplying: Boolean,
    onApply: () -> Unit,
    onApplyBlocked: () -> Unit,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets)
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (totalCount == 0) {
                        // 只有自动合并项: 没有确认进度可言, 展示自动合并数量.
                        Text(
                            stringResource(Lang.bangumi_merge_auto_merged, autoMergedCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(BangumiMergeTestTags.PROGRESS_TEXT),
                        )
                    } else {
                        Text(
                            stringResource(Lang.bangumi_merge_confirmed_progress, confirmedCount, totalCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(BangumiMergeTestTags.PROGRESS_TEXT),
                        )
                        LinearProgressIndicator(
                            progress = { confirmedCount.toFloat() / totalCount },
                            modifier = Modifier.width(130.dp),
                        )
                    }
                }
                Box(Modifier.padding(start = 14.dp)) {
                    Button(
                        onClick = onApply,
                        enabled = canApply,
                        modifier = Modifier.testTag(BangumiMergeTestTags.APPLY_BUTTON),
                    ) {
                        if (isApplying) {
                            CircularProgressIndicator(
                                Modifier.size(ButtonDefaults.IconSize),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(Lang.bangumi_merge_apply))
                        }
                    }
                    // 禁用的 Button 会吞掉点击事件, 所以用覆盖层接管禁用态的点击, 触发滚动到未决定项.
                    if (!canApply) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onApplyBlocked,
                                )
                                .testTag(BangumiMergeTestTags.APPLY_BLOCKED_OVERLAY),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeEmptyState(
    modifier: Modifier = Modifier,
) {
    Box(modifier.testTag(BangumiMergeTestTags.EMPTY_STATE), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.CloudDone,
                null,
                Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(Lang.bangumi_merge_synced),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(Lang.bangumi_merge_synced_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
