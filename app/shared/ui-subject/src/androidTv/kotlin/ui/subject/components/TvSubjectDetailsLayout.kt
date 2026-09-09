/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.leanback.ui.foundation.focus.TvAnchoredBringIntoViewSpec
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal

/**
 * 详情页骨架 (对齐手机 SubjectDetailsPageLayout 的 slot 模式):
 * 统一焦点接线 + 全屏 [backdrop] 背景层 + 纵向滚动内容列.
 * heroHeight 保留首屏底部的下一区块预览，具体内容由 slot 提供。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvSubjectDetailsPageLayout(
    focus: TvFocusScope,
    scrollState: ScrollState,
    bringIntoViewSpec: BringIntoViewSpec,
    scrollContentModifier: Modifier,
    backdrop: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(heroHeight: Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().tvFocusNavSignal(focus)) {
        val heroHeight = maxHeight * TvSubjectDetailsDefaults.OverviewHeightFraction
        val actionBackdrop = rememberHazeState()
        // Capture only the backdrop, as a sibling of the buttons that sample it.
        Box(Modifier.matchParentSize().hazeSource(actionBackdrop)) { backdrop() }
        // 首屏回到页顶；人物与关联条目沿用默认纵向策略。
        // 横向列表独立使用行首锚点，避免继承外层纵向距离。
        val rowStartPaddingPx = with(LocalDensity.current) { TvSubjectDetailsDefaults.HorizontalPadding.toPx() }
        val rowSpec = remember(rowStartPaddingPx) { TvAnchoredBringIntoViewSpec { rowStartPaddingPx } }
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides bringIntoViewSpec,
            LocalTvDetailsActionBackdrop provides actionBackdrop,
        ) {
            Column(scrollContentModifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = 48.dp.toPx()
                    if (scrollState.value > 0) drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = fade),
                        size = Size(size.width, fade), blendMode = BlendMode.DstIn,
                    )
                }
                .verticalScroll(scrollState)) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                    content(heroHeight)
                }
            }
        }
    }
}

/**
 * 详情页滚动锚点登记: 首屏的根节点上报自己在滚动
 * 内容里的上边缘, 以及子树是否持焦; BringIntoView 就把"聚焦项所在区块的上边缘"对齐到视口
 * 上边缘 —— 锚点是布局的边, 不是 30% 这种无依据的比例. hero 的上边缘 = 0, 播放钮聚焦即回页顶.
 *
 * 区块上边缘 = 区块 positionInRoot.y - 滚动内容 positionInRoot.y + 当时的滚动偏移
 * (每次布局都重新上报, 滚动中也一致; 嵌套在子容器里的区块同样成立).
 */
@Stable
internal class TvDetailsScrollAnchors {
    var contentTopInRoot by mutableFloatStateOf(0f)

    /** 顶边锚点区块: key -> 锚点在滚动内容里的位置 (区块上边缘 - 内缩量). */
    val sectionTops = mutableStateMapOf<String, Float>()

    /** 各区块子树是否持焦。 */
    val focusedKeys = mutableStateMapOf<String, Boolean>()

    fun focusedSectionTop(): Float? = sectionTops.entries.firstOrNull { focusedKeys[it.key] == true }?.value
}

/**
 * 标注本节点为滚动区块 [key] (几何上报 + 子树持焦上报; 挂在区块根节点).
 * [anchorInsetPx]: 锚点在区块上边缘之上多少 —— 对齐后区块顶与视口顶留这段距离 (锚点位置的
 * 一部分, 不是布局 padding); 0 = 区块顶贴视口顶.
 */
internal fun Modifier.tvDetailsScrollSection(
    anchors: TvDetailsScrollAnchors,
    key: String,
    anchorInsetPx: Float,
    scrollOffset: () -> Int,
): Modifier = this
    .onGloballyPositioned { coords ->
        val anchor = coords.positionInRoot().y - anchors.contentTopInRoot + scrollOffset() - anchorInsetPx
        if (anchors.sectionTops[key] != anchor) anchors.sectionTops[key] = anchor
    }
    .onFocusChanged { if (anchors.focusedKeys[key] != it.hasFocus) anchors.focusedKeys[key] = it.hasFocus }

/**
 * 详情页纵向滚动策略: 首屏按顶边锚点。
 * 角色及后续区块不登记纵向锚点, 直接交给页面覆盖前的 [defaultSpec], 保留平台默认行为.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class TvDetailsBringIntoViewSpec(
    private val anchors: TvDetailsScrollAnchors,
    private val scrollOffset: () -> Int,
    private val defaultSpec: BringIntoViewSpec,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // 首屏按布局顶边对齐视口。
        anchors.focusedSectionTop()?.let { top -> return top - scrollOffset() }
        return defaultSpec.calculateScrollDistance(offset, size, containerSize)
    }
}

internal data class TvDetailsBackdropImage(val url: String, val bitmap: ImageBitmap)

/** Scoped to this page's overlays, so they can draw the already decoded backdrop on their first frame. */
internal val LocalTvDetailsBackdropImage = staticCompositionLocalOf<TvDetailsBackdropImage?> { null }

/** Only the overview actions sample this page's backdrop; overlays already have their own blur. */
internal val LocalTvDetailsActionBackdrop = staticCompositionLocalOf<HazeState?> { null }

/** 全屏背景：左侧和底部线性遮罩，随页面滚动增强模糊与压暗。 */
@Composable
internal fun TvDetailsBackdrop(
    url: String,
    blurProgress: () -> Float,
    modifier: Modifier = Modifier,
    crossfade: Boolean? = null,
    onImageLoaded: ((ImageBitmap) -> Unit)? = null,
) {
    val progress = blurProgress().coerceIn(0f, 1f)
    val loadedImage = LocalTvDetailsBackdropImage.current?.takeIf { it.url == url }?.bitmap
    Box(modifier.fillMaxSize().background(TvSubjectDetailsDefaults.Background)) {
        val imageModifier = Modifier.fillMaxSize().blur(TvSubjectDetailsDefaults.ReaderBlurRadius * progress)
        if (loadedImage != null) {
            Image(loadedImage, contentDescription = null, imageModifier,
                contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, -.5f))
        } else {
            AsyncImage(
                url, contentDescription = null, imageModifier,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -.5f),
                crossfade = crossfade,
                onSuccess = { result -> result.bitmap?.let { onImageLoaded?.invoke(it) } },
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f + .20f * progress)))
        Box(Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = .60f),
                .55f to Color.Black.copy(alpha = .16f),
                1f to Color.Transparent,
            ),
        ))
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                .45f to Color.Transparent,
                1f to Color.Black.copy(alpha = .42f),
            ),
        ))
    }
}
