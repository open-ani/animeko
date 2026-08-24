/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium

/**
 * 记录 container transform「从内容的哪一点开始向外扩」的锚点.
 *
 * `sharedBounds` 的 `resizeMode = scaleToBounds(ContentScale.None, alignment)` 会把内容按最终尺寸
 * 布局、再按 [alignment] 塞进动画中的窗口里, 所以 [alignment] 决定了形变刚开始时窗口里露出的是内容的
 * 哪一块. 默认的 `Alignment.TopCenter` 意味着从页面顶部中间开始扩; 想改成从条目封面开始扩, 就得把封面
 * 在页面里的比例位置换算成 [BiasAlignment].
 *
 * 用法:
 * 1. 在详情页入口 `rememberBoundOffsetAlignment()` 建一个 state;
 * 2. 把它传给 [subjectContainerTransform], 容器节点的尺寸由那边内部记录;
 * 3. 在封面上挂 [Modifier.boundOffsetAlignment], 量出封面位置;
 * 4. [alignment] 会自动更新.
 *
 * 首帧读到的是构造时传入的估算值 —— `onPlaced` 要等布局结束才回调, 而那时形变动画已经开始了.
 * 见 [BoundOffsetAlignmentDefaults].
 */
@Stable
class BoundOffsetAlignmentState internal constructor(
    private val defaultAlignment: BiasAlignment,
) {
    /**
     * 当前锚点. 量到封面之前是构造时传入的估算值.
     */
    var alignment: BiasAlignment by mutableStateOf(defaultAlignment)
        private set

    // 故意不用 mutableStateOf: 这两个值只是计算 [alignment] 的中间量, 不该直接触发重组.
    private var containerCoordinates: LayoutCoordinates? = null
    private var anchorCoordinates: LayoutCoordinates? = null

    internal fun onContainerPlaced(coordinates: LayoutCoordinates) {
        containerCoordinates = coordinates
        recalculate()
    }

    internal fun onAnchorPlaced(coordinates: LayoutCoordinates) {
        anchorCoordinates = coordinates
        recalculate()
    }

    private fun recalculate() {
        val container = containerCoordinates?.takeIf { it.isAttached } ?: return
        val anchor = anchorCoordinates?.takeIf { it.isAttached } ?: return
        val containerSize = container.size
        if (containerSize.width <= 0 || containerSize.height <= 0) return

        // 两个节点都在 sharedBounds 的 SkipToLookaheadSizeNode 子树内, 该子树始终按 lookahead
        // (最终) constraints 布局, 所以这里量到的相对位置就是最终布局的位置, 不会被形变动画污染,
        // 也就不会出现「用动画中的坐标算 alignment -> 摆放变化 -> 坐标又变」的自反馈.
        val anchorTopLeft = container.localPositionOf(anchor, Offset.Zero)
        val anchorCenter = anchorTopLeft + Offset(anchor.size.width / 2f, anchor.size.height / 2f)

        val new = fractionAlignment(
            horizontal = (anchorCenter.x / containerSize.width).coerceIn(0f, 1f),
            vertical = (anchorCenter.y / containerSize.height).coerceIn(0f, 1f),
        )
        // 必须判等: onPlaced 每帧都会回调, 不判等就是每帧在 layout 阶段写一个会被 composition 读的
        // state, 会导致持续重组.
        if (new != alignment) {
            alignment = new
        }
    }
}

/**
 * @param defaultAlignment 量到锚点之前使用的估算值, 见 [BoundOffsetAlignmentDefaults].
 */
@Composable
fun rememberBoundOffsetAlignment(
    defaultAlignment: BiasAlignment = BoundOffsetAlignmentDefaults.subjectDetailsCover(),
): BoundOffsetAlignmentState = remember(defaultAlignment) { BoundOffsetAlignmentState(defaultAlignment) }

/**
 * 把挂载的节点作为 container transform 向外扩张的锚点, 算成 [BiasAlignment] 存进 [state].
 *
 * [state] 为 `null` (没有 [LocalBoundOffsetAlignmentState], 例如 preview、搜索页的详情栏、播放页的
 * 条目详情 sheet) 时是空实现.
 */
@Composable
fun Modifier.boundOffsetAlignment(
    state: BoundOffsetAlignmentState? = LocalBoundOffsetAlignmentState.current,
): Modifier = if (state == null) this else onPlaced { state.onAnchorPlaced(it) }

/**
 * 供 [Modifier.boundOffsetAlignment] 就近读取, 免得从详情页入口一路把 state 传到封面那一层.
 */
@Stable
val LocalBoundOffsetAlignmentState: ProvidableCompositionLocal<BoundOffsetAlignmentState?> =
    staticCompositionLocalOf { null }

/**
 * 把「内容里的比例位置」(0 = 左/顶, 1 = 右/底) 换算成 [BiasAlignment].
 *
 * `BiasAlignment.align` 是 `x = (space - size) / 2 * (1 + bias)`, 代进去可以推出: 窗口很小时落在
 * 窗口中心的内容坐标是 `size * (1 + bias) / 2`, 也就是内容的 `(1 + bias) / 2` 比例处.
 */
@Stable
fun fractionAlignment(horizontal: Float, vertical: Float): BiasAlignment =
    BiasAlignment(horizontalBias = 2f * horizontal - 1f, verticalBias = 2f * vertical - 1f)

@Stable
object BoundOffsetAlignmentDefaults {
    /**
     * 单列 (COMPACT) 布局下条目封面的估算位置, 按 360 x 800dp 手机推算:
     *
     * - `SubjectDetailsHeaderCompact` 的封面宽 140dp, 高 `140 / (849/1200)` ≈ 198dp;
     * - 左边是 `paneHorizontalPadding` = 16dp;
     * - 上面是状态栏 + `AniTopAppBar` ≈ 88dp.
     *
     * 中心 ≈ `(16 + 70, 88 + 99)` = `(86, 187)`, 即 `(0.24, 0.23)`.
     */
    val SingleColumnSubjectCover: BiasAlignment = fractionAlignment(0.24f, 0.23f)

    /**
     * 多列 (MEDIUM / EXPANDED) 布局下条目封面的估算位置, 按 1400 x 900dp 窗口推算:
     *
     * - `SubjectSidebar` 的封面占满 `sidebarWidth` = 340dp, 高 `340 / (849/1200)` ≈ 481dp;
     * - 左边是 `contentHorizontalPadding` = 40dp;
     * - 上面是 `AniTopAppBar` (64dp) + `contentTopPadding` (12dp) ≈ 76dp.
     *
     * 中心 ≈ `(40 + 170, 76 + 240)` = `(210, 316)`, 即 `(0.15, 0.35)`.
     */
    val MultiColumnSubjectCover: BiasAlignment = fractionAlignment(0.15f, 0.35f)

    /**
     * 条目详情页封面的首帧估算锚点. 单列和多列的封面位置差很多, 所以按窗口宽度分档 —— 与
     * `SubjectDetailsHeader` 内部选 Compact / Wide 的判断保持一致.
     */
    @Composable
    fun subjectDetailsCover(): BiasAlignment =
        if (currentWindowAdaptiveInfo1().isWidthAtLeastMedium) {
            MultiColumnSubjectCover
        } else {
            SingleColumnSubjectCover
        }
}
