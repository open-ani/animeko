/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp

/**
 * [EpisodeScreenLayout] 的布局模式.
 */
enum class EpisodeScreenLayoutMode {
    /**
     * 只有播放器, 占满整个区域. 用于全屏, 以及宽屏布局收起侧边栏时.
     */
    VIDEO_ONLY,

    /**
     * 播放器在上, 高度由播放器自己决定; 次要内容在下, 占满剩余高度.
     */
    COMPACT,

    /**
     * 播放器在左, 次要内容作为侧边栏在右.
     */
    WIDE,
}

/** 桌面端宽屏布局在全屏时也由侧边栏开关决定是否显示次要内容. */
internal fun episodeScreenLayoutMode(
    isFullscreen: Boolean,
    showExpandedUI: Boolean,
    sidebarVisible: Boolean,
    isDesktop: Boolean,
): EpisodeScreenLayoutMode = when {
    isFullscreen && (!isDesktop || !showExpandedUI) -> EpisodeScreenLayoutMode.VIDEO_ONLY
    !showExpandedUI -> EpisodeScreenLayoutMode.COMPACT
    sidebarVisible -> EpisodeScreenLayoutMode.WIDE
    else -> EpisodeScreenLayoutMode.VIDEO_ONLY
}

/**
 * 宽屏布局的侧边栏开关. 窗口和全屏各记一份: 全屏是为了专心看视频, 默认不显示侧边栏;
 * 在全屏里展开侧边栏也不会改变退出全屏后的布局.
 */
@Stable
internal class EpisodeSidebarState(
    private val isFullscreen: () -> Boolean,
) {
    private var visibleInWindow by mutableStateOf(true)
    private var visibleInFullscreen by mutableStateOf(false)

    var isVisible: Boolean
        get() = if (isFullscreen()) visibleInFullscreen else visibleInWindow
        set(value) {
            if (isFullscreen()) visibleInFullscreen = value else visibleInWindow = value
        }
}

/**
 * 播放页布局: 播放器和它旁边的次要内容 (窄屏时是下方的详情与评论, 宽屏时是右侧的侧边栏).
 *
 * 不论 [mode] 如何, [video] 都是同一个子节点, 切换模式只改变它的大小和位置.
 * 播放器节点一旦换了父布局就会被销毁重建: Android 上 SurfaceView 会脱离窗口并销毁 Surface,
 * ExoPlayer 在主线程上等待播放线程放开旧的视频输出, 超时则停止播放.
 * 因此旋转、进出全屏、窗口宽度跨过宽屏阈值都只能改变 [mode], 不能把播放器放进另一个布局.
 *
 * @param secondary 次要内容. [EpisodeScreenLayoutMode.VIDEO_ONLY] 时不组合.
 */
@Composable
fun EpisodeScreenLayout(
    mode: EpisodeScreenLayoutMode,
    video: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondaryContent = if (mode == EpisodeScreenLayoutMode.VIDEO_ONLY) EmptyContent else secondary
    Layout(
        contents = listOf(video, secondaryContent),
        modifier = modifier,
    ) { (videoMeasurables, secondaryMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        when (mode) {
            EpisodeScreenLayoutMode.VIDEO_ONLY -> {
                val videoPlaceables = videoMeasurables.map { it.measure(Constraints.fixed(width, height)) }
                layout(width, height) {
                    videoPlaceables.forEach { it.placeRelative(0, 0) }
                }
            }

            EpisodeScreenLayoutMode.COMPACT -> {
                val videoPlaceables = videoMeasurables.map { it.measure(constraints.copy(minHeight = 0)) }
                val videoHeight = videoPlaceables.maxOfOrNull { it.height } ?: 0
                val secondaryConstraints = Constraints.fixed(width, (height - videoHeight).coerceAtLeast(0))
                val secondaryPlaceables = secondaryMeasurables.map { it.measure(secondaryConstraints) }
                layout(width, height) {
                    videoPlaceables.forEach { it.placeRelative(0, 0) }
                    secondaryPlaceables.forEach { it.placeRelative(0, videoHeight) }
                }
            }

            EpisodeScreenLayoutMode.WIDE -> {
                val sidebarWidth = (width.toDp() * 0.25f).coerceIn(340.dp, 460.dp).roundToPx().coerceAtMost(width)
                val videoWidth = width - sidebarWidth
                val videoPlaceables = videoMeasurables.map { it.measure(Constraints.fixed(videoWidth, height)) }
                val secondaryPlaceables = secondaryMeasurables.map { it.measure(Constraints.fixed(sidebarWidth, height)) }
                layout(width, height) {
                    videoPlaceables.forEach { it.placeRelative(0, 0) }
                    secondaryPlaceables.forEach { it.placeRelative(videoWidth, 0) }
                }
            }
        }
    }
}

private val EmptyContent: @Composable () -> Unit = {}
