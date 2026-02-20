/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

@Stable
inline val ProgressIndicatorHeight get() = 4.dp

enum class Mode {
    Indefinite,
    Definite,
}

private val ScaleAnimation: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMedium)

@Stable
class FastLinearProgressState(
    uiScope: CoroutineScope,
) {
    private val tasker = MonoTasker(uiScope)
    private var targetVisible by mutableStateOf(false)

    internal var mode: Mode by mutableStateOf(Mode.Indefinite)
    internal var scale: Float by mutableFloatStateOf(0f)
        private set
    internal var progress: Float by mutableFloatStateOf(0f)
        private set

    /**
     * 设置进度条是否可见.
     *
     * @param visible 是否期望展示进度条
     * @param delayMillis 延迟 [delayMillis] 毫秒后才开始显示进度条. 如果在这个时间内 [visible] 变为 `false`, 则不会显示进度条.
     * @param minimumDurationMillis 在延迟 [delayMillis] 后开始显示进度条的最短显示时间.
     * 即使 [visible] 变为 `false`, 也会显示至少 [minimumDurationMillis] 毫秒. 不包括 [delayMillis].
     */
    fun setVisible(
        visible: Boolean,
        delayMillis: Long = 100L,
        minimumDurationMillis: Int = 1500,
    ) {
        if (targetVisible == visible) return
        targetVisible = visible

        if (visible) {
            this.mode = Mode.Definite
            tasker.launch {
                delay(delayMillis) // 超级快的动作, 不展示进度条
                launch {
                    animate(
                        scale, 1f,
                        animationSpec = ScaleAnimation,
                    ) { value, _ ->
                        scale = value
                    }
                }
                animate(
                    0f, 1f,
                    animationSpec = tween(minimumDurationMillis),
                ) { value, _ ->
                    progress = value
                }

                if (targetVisible) {
                    mode = Mode.Indefinite
                } else {
                    // 其他线程已经修改, 期望提前结束
                }
            }
        } else {
            tasker.launchNext {
                animate(
                    scale, 0f,
                    animationSpec = ScaleAnimation,
                ) { value, _ ->
                    scale = value
                }
                mode = Mode.Definite // 恢复状态, 避免下次显示时会有一点点
            }
        }
    }

    suspend fun awaitCompletion() {
        withContext(Dispatchers.Main.immediate) {
            if (!targetVisible) return@withContext // fast path
            snapshotFlow { targetVisible }.filter { !it }.first()
            tasker.join()
        }
    }
}


/**
 * 适用于加载很快的小动作
 *
 * 延迟 [delayMillis] 后, 如果 [visible] 仍然为 `true`, 就展示 [minimumDurationMillis] 秒动画.
 *
 * 如果超过 [minimumDurationMillis] 秒后 [visible] 仍然为 `true`, 进度条将会转为 indefinite 模式.
 */
@Composable
fun FastLinearProgressIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
    delayMillis: Long = 100L,
    minimumDurationMillis: Int = 1500,
) {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { FastLinearProgressState(scope) }
    SideEffect {
        state.setVisible(visible, delayMillis, minimumDurationMillis)
    }
    return FastLinearProgressIndicator(state, modifier)
}

@Composable
fun FastLinearProgressIndicator(
    state: FastLinearProgressState,
    modifier: Modifier = Modifier,
) {
    val progressModifier = Modifier.fillMaxWidth().graphicsLayer { scaleY = state.scale }
    val trackColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    val cap = StrokeCap.Round

    Crossfade(state.mode, modifier = modifier.height(4.dp).fillMaxWidth()) { mode ->
        if (mode == Mode.Definite) {
            LinearProgressIndicator(
                { state.progress },
                progressModifier,
                trackColor = trackColor,
                strokeCap = cap,
            )
        } else {
            LinearProgressIndicator(
                progressModifier,
                trackColor = trackColor,
                strokeCap = cap,
            )
        }
    }
}
//
//@Composable
//fun AnimatedLinearProgressIndicator(
//    visible: Boolean,
//    modifier: Modifier = Modifier,
//    progress: (() -> Float)? = null,
//) {
//    val scale by animateFloatAsState(
//        if (visible) 1f else 0f,
//        spring(stiffness = Spring.StiffnessMedium),
//    )
//    val progressModifier = Modifier.fillMaxWidth().graphicsLayer { scaleY = scale }
//    val trackColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
//    val cap = StrokeCap.Round
//    Crossfade(progress, modifier = modifier.height(4.dp).fillMaxWidth()) { pro ->
//        if (pro == null) {
//            var indefinite by remember(visible) { mutableStateOf(false) }
//            LaunchedEffect(true) {
//                delay(1500) // 一秒后转为无限模式
//                indefinite = true
//            }
//            if (indefinite) {
//                LinearProgressIndicator(
//                    progressModifier,
//                    trackColor = trackColor,
//                    strokeCap = cap,
//                )
//            } else {
//                val scope = rememberCoroutineScope()
//                var prog by remember {
//                    mutableFloatStateOf(0f)
//                }
//                SideEffect {
//                    scope.launch {
//                        animate(
//                            0f, 1f,
//                            animationSpec = tween(1500)
//                        ) { value, _ ->
//                            prog = value
//                        }
//                    }
//                }
//
//                LinearProgressIndicator(
//                    { prog },
//                    progressModifier,
//                    trackColor = trackColor,
//                    strokeCap = cap,
//                )
//            }
//        } else {
//            LinearProgressIndicator(
//                pro,
//                progressModifier,
//                trackColor = trackColor,
//                strokeCap = cap,
//            )
//        }
//    }
//}

@PreviewLightDark
@Composable
fun PreviewAnimatedLinearProgressIndicatorIndefiniteLonger() = ProvideCompositionLocalsForPreview {
    var visible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(key1 = true) {
        while (isActive) {
            visible = !visible
            delay(2000)
        }
    }
    Surface {
        Box(
            Modifier
                .height(64.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            FastLinearProgressIndicator(visible)
        }
    }
}

@PreviewLightDark
@Composable
fun PreviewAnimatedLinearProgressIndicatorIndefiniteShorter() = ProvideCompositionLocalsForPreview {
    var visible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(key1 = true) {
        while (isActive) {
            visible = !visible
            delay(1000)
        }
    }
    Surface {
        Box(
            Modifier
                .height(64.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            FastLinearProgressIndicator(visible)
        }
    }
}
