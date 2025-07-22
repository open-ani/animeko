/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.adaptive

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.defaultDragHandleSemantics
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.ListDetailAnimatedPane
import me.him188.ani.app.ui.foundation.layout.LocalSharedTransitionScopeProvider
import me.him188.ani.app.ui.foundation.layout.SharedTransitionScopeProvider
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneHorizontalPadding
import me.him188.ani.app.ui.foundation.navigation.BackHandler


/**
 * 自动适应单页模式和双页模式的布局的 paddings
 *
 * Pane 内可以访问 [PaneScope]. 其中有几个非常实用的属性:
 * - [PaneScope.paneContentPadding]: 用于为 pane 增加自动的 content padding. 通常你需要为 pane 的内容直接添加这个 modifier.
 *   如果你不期望为整个容器添加 padding, 可以使用 [PaneScope.listDetailLayoutParameters] [ListDetailLayoutParameters.listPaneContentStartPadding]
 * - [PaneScope.listDetailLayoutParameters] 用于获取当前的布局参数.
 *
 * ### Window Insets
 *
 * [AniListDetailPaneScaffold] 的 Window Insets 行为在 list pane 和 detail pane 之间有所不同.
 * - [detailPane]: 需要自行使用 [PaneScope.paneContentPadding] 和 [PaneScope.paneWindowInsetsPadding] 来处理 window insets. [AniListDetailPaneScaffold] 不会自动 consume 任何 insets.
 * - [listPaneContent]: 如果有 [listPaneTopAppBar], 此页会自动 consume TopAppBar 所使用的 window insets (`AniWindowInsets.forTopAppBar().only(WindowInsetsSides.Top)`).
 * 但你仍然需要使用 [PaneScope.paneContentPadding] 和 [PaneScope.paneWindowInsetsPadding] 来处理其他 window insets.
 *
 * 总之, 你总是需要在 [listPaneContent] 和 [detailPane] 中使用 [PaneScope.paneContentPadding] 和 [PaneScope.paneWindowInsetsPadding] 来处理 window insets.
 * [listPaneContent] 内会自动帮你处理 TopAppBar 的 insets.
 *
 * 如果要在 [detailPane] 内也增加 [AniTopAppBar], 则需要自行处理 insets:
 * ```
 * detailPane = {
 *     AniTopAppBar(windowInsets = paneContentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
 *     Box(Modifier.consumeWindowInsets(paneContentWindowInsets.only(WindowInsetsSides.Top))) {
 *         Column(Modifier.paneContentPadding().paneWindowInsetsPadding()) {
 *             // ...
 *         }
 *     }
 * }
 * ```
 * TODO: 为 [detailPane] 内使用 [AniTopAppBar] 做一个 scaffold
 *
 * @param listPaneTopAppBar 通常可以放 [AniTopAppBar]. 可以为 `null`, 届时不占额外空间 (也不会造成 insets 消耗). 你需要指定 [AniTopAppBar] 的 windowInsets 为 `contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)`.
 * @param listPaneContent 列表内容, 可以是 [Column] 或者 Grid. 需要自行实现 vertical scroll.
 * @param detailPane 详情页内容.
 * @param listPanePreferredWidth See also [androidx.compose.material3.adaptive.layout.PaneScaffoldScope.preferredWidth]
 * @param useSharedTransition 是否在[单页模式][ListDetailLayoutParameters.preferSinglePane]时使用 Container Transform 等 [SharedTransitionLayout] 的动画.
 * 启用后将会调整切换 pane 时的 fade 动画逻辑来支持 Container Transform.
 * @param contentWindowInsets 内容的 [WindowInsets]. 这会影响 [PaneScope.paneContentWindowInsets].
 *
 * @sample me.him188.ani.app.ui.exploration.search.SearchPageLayout
 */
@Composable
fun <T> AniListDetailPaneScaffold(
    navigator: ThreePaneScaffoldNavigator<T>,
    listPaneTopAppBar: @Composable (PaneScope.() -> Unit)? = null,
    listPaneContent: @Composable (PaneScope.() -> Unit),
    detailPane: @Composable (PaneScope.() -> Unit),
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = ListDetailPaneScaffoldDefaults.windowInsets,
    useSharedTransition: Boolean = false,
    listPanePreferredWidth: Dp = calculateMinimumPaneWidth(),
    minListPaneWidth: Dp = calculateMinimumPaneWidth(),
    minDetailPaneWidth: Dp = minListPaneWidth,
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? = { state ->
        val interactionSource = remember { MutableInteractionSource() }
        VerticalDragHandle(
            modifier =
                Modifier.paneExpansionDraggable(
                    state,
                    LocalMinimumInteractiveComponentSize.current,
                    interactionSource,
                    state.defaultDragHandleSemantics(),
                ),
            interactionSource = interactionSource,
        )
    },
    scaffoldValue: ThreePaneScaffoldValue = navigator.scaffoldValue,
    layoutParameters: ListDetailLayoutParameters = ListDetailLayoutParameters.calculate(navigator.scaffoldDirective),
) {
    val coroutineScope = rememberCoroutineScope()
    BackHandler(navigator.canNavigateBack()) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { // start immediately to change state
            navigator.navigateBack()
        }
    }
    val layoutParametersState by rememberUpdatedState(layoutParameters)
    val contentWindowInsetsState by rememberUpdatedState(contentWindowInsets)
    val scaffoldValueState by rememberUpdatedState(scaffoldValue)

    SharedTransitionLayout {
        ListDetailPaneScaffold(
            navigator.scaffoldDirective,
            scaffoldValue,
            listPane = {
                val threePaneScaffoldScope = this
                ListDetailAnimatedPane(
                    Modifier
                        .requiredWidthIn(min = minListPaneWidth)
                        .preferredWidth(listPanePreferredWidth),
                    useSharedTransition,
                ) {
                    Column {
                        val scope =
                            remember(threePaneScaffoldScope, this@ListDetailAnimatedPane) {
                                object : PaneScope {
                                    override val listDetailLayoutParameters: ListDetailLayoutParameters
                                        get() = layoutParametersState

                                    override val isSinglePane: Boolean
                                        get() = scaffoldValueState.isSinglePane

                                    override val paneContentWindowInsets: WindowInsets
                                        get() = when {
                                            isSinglePane -> contentWindowInsetsState
                                            else -> contentWindowInsetsState.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
                                        }

                                    override fun Modifier.paneContentPadding(
                                        extraStart: Dp,
                                        extraEnd: Dp,
                                    ): Modifier {
                                        val endPadding = if (isSinglePane) {
                                            layoutParametersState.listPaneContentEndPadding
                                        } else {
                                            0.dp // ListDetail 两个 pane 之间自带 24.dp
                                        }
                                        return Modifier
                                            .padding(
                                                PaddingValues(
                                                    start = (layoutParametersState.listPaneContentStartPadding + extraStart)
                                                        .coerceAtLeast(0.dp),
                                                    end = (endPadding + extraEnd)
                                                        .coerceAtLeast(0.dp),
                                                ),
                                            )
                                            .consumeWindowInsets(
                                                PaddingValues(
                                                    start = (layoutParametersState.listPaneContentStartPadding + extraStart)
                                                        .coerceAtLeast(layoutParametersState.listPaneContentStartPadding),
                                                    end = (endPadding + extraEnd)
                                                        .coerceAtLeast(endPadding),
                                                ),
                                            )
                                    }
                                }
                            }

                        CompositionLocalProvider(
                            LocalSharedTransitionScopeProvider provides remember(
                                this@SharedTransitionLayout,
                                this@ListDetailAnimatedPane,
                            ) {
                                SharedTransitionScopeProvider(
                                    this@SharedTransitionLayout, this@ListDetailAnimatedPane,
                                )
                            },
                        ) {
                            val decoratedPaneContent = @Composable {
                                Column(Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = 1300.dp)) {
                                    listPaneContent(scope)
                                }
                            }

                            if (listPaneTopAppBar == null) {
                                decoratedPaneContent()
                            } else {
                                listPaneTopAppBar(scope)
                                Column(
                                    Modifier.consumeWindowInsets(
                                        contentWindowInsets.only(WindowInsetsSides.Top),
                                    ),
                                ) {
                                    decoratedPaneContent()
                                }
                            }
                        }
                    }
                }
            },
            detailPane = {
                val threePaneScaffoldScope = this
                ListDetailAnimatedPane(
                    Modifier.requiredWidthIn(min = minDetailPaneWidth),
                    useSharedTransition = useSharedTransition,
                ) {
                    Card(
                        shape = layoutParameters.detailPaneShape,
                        colors = layoutParameters.detailPaneColors,
                    ) {
                        val scope =
                            remember(threePaneScaffoldScope, this@ListDetailAnimatedPane) {
                                object : PaneScope {
                                    override val listDetailLayoutParameters: ListDetailLayoutParameters
                                        get() = layoutParametersState

                                    override val isSinglePane: Boolean
                                        get() = scaffoldValueState.isSinglePane

                                    override val paneContentWindowInsets: WindowInsets
                                        get() = when {
                                            isSinglePane -> contentWindowInsetsState
                                            else -> contentWindowInsetsState.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)
                                        }

                                    override fun Modifier.paneContentPadding(
                                        extraStart: Dp,
                                        extraEnd: Dp,
                                    ): Modifier {
                                        return Modifier
                                            .padding(
                                                PaddingValues(
                                                    start = (layoutParametersState.detailPaneContentStartPadding + extraStart)
                                                        .coerceAtLeast(0.dp),
                                                    end = (layoutParametersState.detailPaneContentEndPadding + extraEnd)
                                                        .coerceAtLeast(0.dp),
                                                ),
                                            )
                                            .consumeWindowInsets(
                                                PaddingValues(
                                                    start = (layoutParametersState.detailPaneContentStartPadding + extraStart)
                                                        .coerceAtLeast(layoutParametersState.detailPaneContentStartPadding),
                                                    end = (layoutParametersState.detailPaneContentEndPadding + extraEnd)
                                                        .coerceAtLeast(layoutParametersState.detailPaneContentEndPadding),
                                                ),
                                            )
                                    }
                                }
                            }
                        detailPane(scope)
                    }
                }
            },
            modifier,
            // singlePane 时不显示 handle 之类的. 否则会在切换页面时有动画问题. 应该是 CMP bug
            paneExpansionState = if (layoutParameters.preferSinglePane) {
                null
            } else {
                rememberPaneExpansionState(
                    keyProvider = scaffoldValue,
                    anchors = calculatePaneAnchors(minListPaneWidth, listPanePreferredWidth, minDetailPaneWidth),
                )
            },
            paneExpansionDragHandle = if (layoutParameters.preferSinglePane) null else paneExpansionDragHandle,
        )
    }
}

@Composable
private fun calculateMinimumPaneWidth(
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
): Dp {
    return when {
        windowSizeClass.containsWidthDp(1200) -> 412.dp // Large, M3 spec 
        windowSizeClass.containsWidthDp(840) -> 360.dp // Expanded, M3 spec
        else -> {
            // M3 spec
            (((windowSizeClass.minWidthDp - 24 * 3).toFloat() / 2).dp).coerceAtLeast(360.dp)
        }
    }
}

@Stable
interface PaneScope {
    /**
     * 获取当前的布局参数.
     *
     * 若要为 pane 增加 padding, 可优先使用 [paneContentPadding].
     */
    @Stable
    val listDetailLayoutParameters: ListDetailLayoutParameters

    /**
     * @see ListDetailLayoutParameters.preferSinglePane
     */
    @Stable
    val isSinglePane get() = listDetailLayoutParameters.preferSinglePane

    /**
     * 此 Pane 需要 consume 的 [WindowInsets].
     *
     * - 对于单页模式, 这就是传入 [ListDetailPaneScaffold] 的 `contentWindowInsets`,
     * 通常是 [ListDetailPaneScaffoldDefaults.windowInsets].
     *
     * - 对于多页模式, 每个 pane 只 consume 一部分 insets: 左侧 pane 只 consume `Start + Vertical`, 右侧 pane 只 consume `End + Vertical`.
     *
     * 推荐使用 [paneWindowInsetsPadding].
     * 如果需要同时使用 [paneContentPadding], 顺序必须是 [paneContentPadding] 在前:
     *
     * ```kotlin
     * Column(
     *     Modifier
     *         .paneContentPadding() // 必须在最前
     *         .paneWindowInsetsPadding()
     *         .padding(top = searchBarHeight) // other paddings
     * ) {
     *     searchResultList()
     * }
     * ```
     */
    @Stable
    val paneContentWindowInsets: WindowInsets

    /**
     * @see paneContentWindowInsets
     */
    @Stable
    fun Modifier.paneWindowInsetsPadding(): Modifier = windowInsetsPadding(paneContentWindowInsets)

    /**
     * 为 pane 增加自动的 content padding 并 consume 等量的 [WindowInsets]. 通常应用于 pane 的最外层容器:
     *
     * [extraStart] 为额外增加多少 start padding. 可以为负数, 则表示减少一些 padding.
     * 即使减少了 padding, 此函数仍然会 consume 完整的 [ListDetailLayoutParameters.listPaneContentStartPadding] 大小的 window insets.
     *
     * 适合搭配 [ListItem] 等自带 content padding 的组件使用 - 传入 `extraStart = (-16).dp`.
     */
    @Stable
    fun Modifier.paneContentPadding(
        extraStart: Dp = 0.dp,
        extraEnd: Dp = 0.dp,
    ): Modifier
}

@Immutable
data class ListDetailLayoutParameters(
    /**
     * 通常不要使用这个. 而是使用 [PaneScope.paneContentPadding]
     */
    val listPaneContentStartPadding: Dp,
    /**
     * 通常不要使用这个. 而是使用 [PaneScope.paneContentPadding]
     */
    val listPaneContentEndPadding: Dp,
    /**
     * 通常不要使用这个. 而是使用 [PaneScope.paneContentPadding]
     */
    val detailPaneContentStartPadding: Dp,
    /**
     * 通常不要使用这个. 而是使用 [PaneScope.paneContentPadding]
     */
    val detailPaneContentEndPadding: Dp,

    val detailPaneShape: Shape,
    val detailPaneColors: CardColors,
    /**
     * 是否为单页模式, 即整个屏幕上只会同时出现一个 pane. 通常在一个 COMPACT 设备上.
     */
    val preferSinglePane: Boolean,
    /**
     * 在 list pane 中高亮选中的 item.
     */
    val highlightSelectedItem: Boolean = !preferSinglePane,
) {
    companion object {
        @Composable
        fun calculate(directive: PaneScaffoldDirective): ListDetailLayoutParameters {
            val isTwoPane = directive.maxHorizontalPartitions > 1
            val windowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass
            return if (isTwoPane) {
                ListDetailLayoutParameters(
                    listPaneContentStartPadding = windowSizeClass.paneHorizontalPadding,
                    listPaneContentEndPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneContentStartPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneContentEndPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneShape = MaterialTheme.shapes.extraLarge.copy(
                        topEnd = ZeroCornerSize,
                        bottomEnd = ZeroCornerSize,
                    ),
                    detailPaneColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    preferSinglePane = false,
                )
            } else {
                ListDetailLayoutParameters(
                    listPaneContentStartPadding = windowSizeClass.paneHorizontalPadding,
                    listPaneContentEndPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneContentStartPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneContentEndPadding = windowSizeClass.paneHorizontalPadding,
                    detailPaneShape = RectangleShape,
                    detailPaneColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    preferSinglePane = true,
                )
            }
        }
    }
}

@Suppress("UnusedReceiverParameter")
val ListDetailPaneScaffoldDefaults.windowInsets
    @Composable
    get() = AniWindowInsets.forPageContent()

private val ThreePaneScaffoldValue.isSinglePane: Boolean
    get() {
        var count = 0
        if (this[ThreePaneScaffoldRole.Primary] == PaneAdaptedValue.Expanded) count++
        if (this[ThreePaneScaffoldRole.Secondary] == PaneAdaptedValue.Expanded) count++
        if (this[ThreePaneScaffoldRole.Tertiary] == PaneAdaptedValue.Expanded) count++
        return count <= 1
    }

@Composable
private fun calculatePaneAnchors(
    minListPaneWidth: Dp = Dp.Unspecified,
    preferredListPaneWidth: Dp = Dp.Unspecified,
    minDetailPaneWidth: Dp = Dp.Unspecified,
    stepDp: Dp = 32.dp // 中间每间隔这么多加一个 anchor
): List<PaneExpansionAnchor> {
    val screenWidthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current

    return remember(density, screenWidthPx, minListPaneWidth, preferredListPaneWidth, minDetailPaneWidth, stepDp) {
        if (screenWidthPx <= 0) return@remember emptyList()

        // 计算下界：minListRatio（若未指定则为 0）
        val minRatio = if (minListPaneWidth != Dp.Unspecified) {
            val px = with(density) { minListPaneWidth.roundToPx() }
            (px / screenWidthPx.toFloat()).coerceIn(0f, 1f)
        } else 0f

        // 计算上界：maxRatio（若未指定 minDetailPaneWidth 则为 1）
        val maxRatio = if (minDetailPaneWidth != Dp.Unspecified) {
            val px = with(density) { minDetailPaneWidth.roundToPx() }
            ((screenWidthPx - px) / screenWidthPx.toFloat()).coerceIn(0f, 1f)
        } else 1f

        // 收集所有比例值
        val ratios = mutableListOf<Float>()

        // 1. 每隔 stepDp 加一个锚点
        val stepPx = with(density) { stepDp.roundToPx() }
        if (stepPx > 0) {
            val count = (screenWidthPx / stepPx).coerceAtLeast(1)
            for (i in 1..count) {
                val p = (i * stepPx / screenWidthPx.toFloat()).coerceIn(0f, 1f)
                ratios += p
            }
        }

        // 2. 原 minListPaneWidth
        if (minListPaneWidth != Dp.Unspecified) {
            val px = with(density) { minListPaneWidth.roundToPx() }
            ratios += (px / screenWidthPx.toFloat()).coerceIn(0f, 1f)
        }
        // 3. 原 preferredListPaneWidth
        if (preferredListPaneWidth != Dp.Unspecified) {
            val px = with(density) { preferredListPaneWidth.roundToPx() }
            ratios += (px / screenWidthPx.toFloat()).coerceIn(0f, 1f)
        }
        // 4. 原 minDetailPaneWidth
        if (minDetailPaneWidth != Dp.Unspecified) {
            val px = with(density) { minDetailPaneWidth.roundToPx() }
            ratios += ((screenWidthPx - px) / screenWidthPx.toFloat()).coerceIn(0f, 1f)
        }

        // 去重、过滤（在 [minRatio, maxRatio] 之间）、排序并映射回 Anchor
        ratios
            .distinct()
            .filter { it in minRatio..maxRatio }
            .sorted()
            .map { PaneExpansionAnchor.Proportion(it) }
    }
}
