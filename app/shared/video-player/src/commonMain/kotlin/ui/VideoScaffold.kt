/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.layout.desktopTitleBar
import me.him188.ani.app.ui.foundation.theme.slightlyWeaken
import me.him188.ani.app.videoplayer.ui.gesture.PlayerGestureHost
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerBar
import me.him188.ani.app.videoplayer.ui.top.PlayerTopBar

/**
 * 视频播放器框架, 可以自定义组合控制器等部分.
 *
 * 视频播放器框架由以下层级组成, 由上至下:
 *
 * - 悬浮消息: [floatingMessage], 例如正在缓冲
 * - 控制器: [topBar], [rhsBar] 和 [bottomBar]
 * - 手势: [gestureHost]
 * - 弹幕: [danmakuHost]
 * - 视频: [video]
 * - 右侧侧边栏: [rhsSheet]
 *
 * @param topBar [PlayerTopBar]
 * @param video [VideoPlayer]. video 不会接受到点击事件.
 * @param danmakuHost 为 `DanmakuHost` 留的区域
 * @param gestureHost 手势区域, 例如快进/快退, 音量调节等. See [PlayerGestureHost]
 * @param floatingMessage 悬浮消息, 例如正在缓冲. 将会对齐到中央
 * @param rhsBar 右侧控制栏, 锁定手势等.
 * @param bottomBar [PlayerControllerBar]
 * @param expanded 当前是否处于全屏模式. 全屏时此框架会 [Modifier.fillMaxSize], 否则会限制为一个 16:9 的框.
 */
@Composable
fun VideoScaffold(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets.safeContent, // TODO: 目前只对部分元素有效
    maintainAspectRatio: Boolean = !expanded,
    controllerState: PlayerControllerState,
    gestureLocked: Boolean = false,
    topBar: @Composable RowScope.() -> Unit = {},
    /**
     * @see VideoPlayer
     */
    video: @Composable BoxScope.() -> Unit = {},
    danmakuHost: @Composable BoxScope.() -> Unit = {},
    gestureHost: @Composable BoxWithConstraintsScope.() -> Unit = {},
    floatingMessage: @Composable BoxScope.() -> Unit = {},
    rhsButtons: @Composable ColumnScope.() -> Unit = {},
    gestureLock: @Composable ColumnScope.() -> Unit = {},
    bottomBar: @Composable RowScope.() -> Unit = {},
    detachedProgressSlider: @Composable () -> Unit = {},
    floatingBottomEnd: @Composable RowScope.() -> Unit = {},
    rhsSheet: @Composable () -> Unit = {},
    leftBottomTips: @Composable () -> Unit = {},
) {
    val controllerVisibility = controllerState.visibility
        .withGestureLocked(gestureLocked)
        .withExpanded(expanded)

    val enterTransition = LocalAniMotionScheme.current.animatedVisibility.standardEnter
    val exitTransition = LocalAniMotionScheme.current.animatedVisibility.standardExit
    BoxWithConstraints(
        modifier.then(if (expanded) Modifier.fillMaxHeight() else Modifier.fillMaxWidth()),
        contentAlignment = Alignment.Center,
    ) { // 16:9 box
        Box(
            Modifier
                .then(
                    if (!maintainAspectRatio) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth().height(maxWidth * 9 / 16) // 16:9 box
                    },
                ),
        ) {
            Box(
                Modifier
                    .background(Color.Transparent)
                    .matchParentSize(), // no window insets for video
            ) {
                video()
                Box(Modifier.matchParentSize()) // 防止点击事件传播到 video 里
            }

            // 弹幕
            Box(
                Modifier
                    .matchParentSize()
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Vertical)),
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    danmakuHost()
                }
            }

            // 控制手势
            BoxWithConstraints(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                gestureHost()
            }

            Box(Modifier) {
                Column(Modifier.fillMaxSize().background(Color.Transparent)) {
                    // 顶部控制栏: 返回键, 标题, 设置
                    me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility(
                        visible = controllerVisibility.topBar,
                        enter = enterTransition,
                        exit = exitTransition,
                    ) {
                        Box {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent.copy(0.72f),
                                            0.32f to Color.Transparent.copy(0.45f),
                                            1f to Color.Transparent,
                                        ),
                                    ),
                            )
                            val alwaysOnRequester = rememberAlwaysOnRequester(controllerState, "topBar")

                            Column(
                                Modifier
                                    .hoverToRequestAlwaysOn(alwaysOnRequester)
                                    .fillMaxWidth(),
                            ) {
                                //force skip layout hit test for windows
                                val desktopTitleBarInsets = WindowInsets.desktopTitleBar.only(WindowInsetsSides.Top)
                                Spacer(
                                    modifier = Modifier.fillMaxWidth()
                                        .pointerInput(Unit) {}
                                        .windowInsetsPadding(desktopTitleBarInsets),

                                    )
                                Row(
                                    Modifier.fillMaxWidth()
                                        .consumeWindowInsets(desktopTitleBarInsets)
                                        .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                                        topBar()
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }

                        }
                    }

                    Box(Modifier.weight(1f, fill = true).fillMaxWidth())

                    Column {
                        // 底部控制栏: 播放/暂停, 进度条, 切换全屏
                        AniAnimatedVisibility(
                            visible = controllerVisibility.bottomBar,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            val alwaysOnRequester = rememberAlwaysOnRequester(controllerState, "bottomBar")
                            Column(
                                Modifier
                                    .hoverToRequestAlwaysOn(alwaysOnRequester)
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val event = awaitPointerEvent()
                                            if (event.changes.all { it.pressed }) {
                                                //点击 bottom bar 里的按钮时 请求 always on
                                                alwaysOnRequester.request()
                                            }
                                            var releaseEvent = awaitPointerEvent()
                                            while (releaseEvent.changes.any { it.pressed }) {
                                                releaseEvent = awaitPointerEvent()
                                            }
                                            alwaysOnRequester.cancelRequest()
                                        }
                                    }
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            1 - 0.32f to Color.Transparent.copy(0.45f),
                                            1f to Color.Transparent.copy(0.72f),
                                        ),
                                    ),
                            ) {
                                Spacer(Modifier.height(if (expanded) 12.dp else 6.dp))
                                Row(
                                    Modifier.fillMaxWidth()
                                        .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                                        bottomBar()
                                    }
                                }
                            }

                        }
                        AniAnimatedVisibility(
                            visible = controllerVisibility.detachedSlider,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                            ) {
                                detachedProgressSlider()
                            }
                        }
                    }
                }
                AniAnimatedVisibility(
                    controllerVisibility.floatingBottomEnd && !expanded,
                    Modifier.align(Alignment.BottomEnd),
                    enter = enterTransition,
                    exit = exitTransition,
                ) {
                    Row(
                        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.End)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            floatingBottomEnd()
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxSize().background(Color.Transparent)
                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.End)),
            ) {
                Box(Modifier.weight(1f, fill = true).fillMaxWidth()) {
                    Column(
                        Modifier.padding(end = 16.dp).align(Alignment.CenterEnd),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AniAnimatedVisibility(
                            visible = controllerVisibility.rhsBar,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            rhsButtons()
                        }

                        // Separate from controllers, to fix position when controllers are/aren't hidden
                        AniAnimatedVisibility(
                            visible = controllerVisibility.gestureLock,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            gestureLock()
                        }
                    }
                }
            }

            Box(Modifier.matchParentSize()) {
                Column(Modifier.windowInsetsPadding(contentWindowInsets)) {
                    Box(Modifier.weight(0.5f))
                    Row(
                        Modifier.weight(0.5f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        leftBottomTips()
                    }
                }
            }
            // 悬浮消息, 例如正在缓冲
            Box(
                Modifier.matchParentSize().windowInsetsPadding(contentWindowInsets),
                contentAlignment = Alignment.Center,
            ) {
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground.slightlyWeaken()) {
                        floatingMessage()
                    }
                }
            }

            // 右侧 sheet
            Box(Modifier.matchParentSize().windowInsetsPadding(contentWindowInsets)) {
                rhsSheet()
            }
        }
    }
}


@Stable
private fun ControllerVisibility.withGestureLocked(gestureLocked: Boolean): ControllerVisibility {
    return if (gestureLocked) {
        copy(
            topBar = false,
            bottomBar = false,
            detachedSlider = false,
            rhsBar = false,
        )
    } else {
        this
    }
}

@Stable
private fun ControllerVisibility.withExpanded(isExpanded: Boolean): ControllerVisibility {
    return if (isExpanded) {
        copy(floatingBottomEnd = false)
    } else {
        this
    }
}