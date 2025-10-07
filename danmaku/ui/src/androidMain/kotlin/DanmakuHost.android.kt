/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import android.annotation.SuppressLint
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.annotation.IntRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong
import kotlin.time.Duration.Companion.milliseconds

private class DummyDanmakuGeneratorState(
    private val startTime: Long = currentTimeMillis()
) {
    private var emitted = 0
    private var counter = 0

    fun generate(
        playTimeMillis: Long = currentTimeMillis() - startTime
    ): DanmakuInfo {
        return DanmakuInfo(
            counter++.toString(),
            DanmakuServiceId("dummy"), currentTimeMillis().toString(),
            DanmakuContent(
                playTimeMillis,
                Color.Black.value.toInt(),
                text = LoremIpsum(Random.nextInt(1..5)).values.first(),
                location = DanmakuLocation.entries.random(),
            ),
        )
    }

    fun generateSelf(): DanmakuInfo {
        return DanmakuInfo(
            "self${Random.Default.nextLong(100000000L..999999999L)}",
            DanmakuServiceId("dummy sender"), "2",
            DanmakuContent(
                currentTimeMillis() - startTime,
                0xfe1010,
                text = "this is my danmaku ${currentTimeMillis()}",
                location = DanmakuLocation.entries.random(),
            ),
        )
    }

    fun generateRepopulate(
        lastTimeMillis: Long = Random.nextLong(0L..(1000L * 60 * 25))
    ): List<DanmakuInfo> {
        if (lastTimeMillis < 0) return emptyList()

        val list = mutableListOf<DanmakuInfo>()
        var current = lastTimeMillis

        kotlin.run {
            repeat(Random.nextInt(10..100)) {
                list.add(generate(current))
                current -= Random.nextLong(0L..200L)
                if (current < 0) return@run
            }
        }

        return list.asReversed()
    }

    fun flow() = flow {
        emit(generate())
        emit(generate())
        emit(generate())
        while (true) {
            emit(generate())
            emitted++
            delay(Random.nextLong(5, 10).milliseconds)
        }
    }
}

@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, device = "spec:width=1920px,height=1080px,dpi=240", showSystemUi = false)
internal fun PreviewDanmakuHost() {
    val config = remember {
        mutableStateOf(
            DanmakuConfig(
                displayArea = 1.0f,
                isDebug = true,
                style = DanmakuStyle.Default.copy(
                    strokeWidth = 2f,
                    strokeColor = Color.DarkGray,
                    fontWeight = FontWeight.Normal,
                ),
            ),
        )
    }
    val state = remember { DanmakuHostState(config) }

    val generator = remember { DummyDanmakuGeneratorState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(true) {
        generator.flow().collect {
            state.trySend(DanmakuPresentation(it, isSelf = false))
        }
    }

    @Composable
    fun Editor(modifier: Modifier) {
        LazyRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Button(
                    {
                        scope.launch {
                            state.send(DanmakuPresentation(generator.generateSelf(), isSelf = true))
                        }
                    },
                ) {
                    Text("Send self")
                }
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            val generated = generator.generateRepopulate().map {
                                DanmakuPresentation(it, isSelf = false)
                            }
                            state.repopulate(
                                generated,
                                generated.last().danmaku.playTimeMillis,
                            )
                        }
                    },
                ) {
                    Text("Repopulate")
                }
            }
            item {
                Button(
                    onClick = {
                        val currentConfig = config.value
                        config.value = currentConfig.copy(isDebug = !currentConfig.isDebug)
                    },
                ) {
                    Text("Toggle debug")
                }
            }
        }
    }

    if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.weight(1f)) {
                    DanmakuHost(
                        state,
                        Modifier
                            .fillMaxSize()
                            .background(Color.Transparent),
                    )
                }
                Editor(Modifier.padding(8.dp))
            }
            VerticalDivider()
            DanmakuConfig(
                danmakuConfig = config.value,
                setDanmakuConfig = { config.value = it },
                modifier = Modifier
                    .width(320.dp),
            )
        }
    } else {
        Column {
            DanmakuHost(
                state,
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color.Transparent),
            )
            HorizontalDivider()
            Editor(Modifier.padding(8.dp))
            DanmakuConfig(
                danmakuConfig = config.value,
                setDanmakuConfig = { config.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

        }
    }
}

@Composable
@Preview("Light", showBackground = true)
@Preview("Dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
private fun PreviewDanmakuText() {
    val measurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.bodyMedium
    val density = LocalDensity.current
    val iter = remember { (0..360 step 36).map { with(density) { it.dp.toPx() } } }

    // 这里用 remember 会导致 Android lint bug, 反正是 preview, 就别用了
    val danmaku = dummyDanmaku(measurer, baseStyle, DanmakuStyle.Default)

    Canvas(modifier = Modifier.size(width = 450.dp, height = 360.dp)) {
        iter.forEach { off ->
            with(danmaku) {
                draw(
                    screenPosX = Random.nextFloat() * 100,
                    screenPosY = off,
                )
            }
        }
    }
}

@Composable
private fun DanmakuConfig(
    danmakuConfig: DanmakuConfig,
    setDanmakuConfig: (config: DanmakuConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        item {
            Column(Modifier.fillMaxSize()) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ElevatedFilterChip(
                            selected = danmakuConfig.enableTop,
                            onClick = { setDanmakuConfig(danmakuConfig.copy(enableTop = !danmakuConfig.enableTop)) },
                            leadingIcon = {
                                if (danmakuConfig.enableTop) Icon(Icons.Rounded.Check, contentDescription = null)
                                else Icon(Icons.Rounded.Close, contentDescription = null)
                            },
                            label = { Text("顶部", maxLines = 1) },
                        )
                        ElevatedFilterChip(
                            selected = danmakuConfig.enableFloating,
                            onClick = { setDanmakuConfig(danmakuConfig.copy(enableFloating = !danmakuConfig.enableFloating)) },
                            label = { Text("滚动", maxLines = 1) },
                            leadingIcon = {
                                if (danmakuConfig.enableFloating) Icon(Icons.Rounded.Check, contentDescription = null)
                                else Icon(Icons.Rounded.Close, contentDescription = null)
                            },
                        )
                        ElevatedFilterChip(
                            selected = danmakuConfig.enableBottom,
                            onClick = { setDanmakuConfig(danmakuConfig.copy(enableBottom = !danmakuConfig.enableBottom)) },
                            label = { Text("底部", maxLines = 1) },
                            leadingIcon = {
                                if (danmakuConfig.enableBottom) Icon(Icons.Rounded.Check, contentDescription = null)
                                else Icon(Icons.Rounded.Close, contentDescription = null)
                            },
                        )
                        ElevatedFilterChip(
                            selected = danmakuConfig.enableColor,
                            onClick = {
                                setDanmakuConfig(
                                    danmakuConfig.copy(enableColor = !danmakuConfig.enableColor),
                                )
                            },
                            leadingIcon = {
                                if (danmakuConfig.enableColor) Icon(Icons.Rounded.Check, contentDescription = null)
                                else Icon(Icons.Rounded.Close, contentDescription = null)
                            },
                            label = { Text("彩色", maxLines = 1) },
                        )
                    }
                }
                val fontSize by remember(danmakuConfig) {
                    mutableFloatStateOf(danmakuConfig.style.fontSize.value / DanmakuStyle.Default.fontSize.value)
                }
                SliderItem(
                    value = fontSize,
                    onValueChange = {
                        // 故意每次改都更新, 可以即时预览
                        setDanmakuConfig(
                            danmakuConfig.copy(style = danmakuConfig.style.copy(fontSize = DanmakuStyle.Default.fontSize * it)),
                        )
                    },
                    valueRange = 0.50f..3f,
//                steps = ((3f - 0.50f) / 0.05f).toInt() - 1,
                    title = { Text("弹幕字号") },
                    valueLabel = { Text(remember(fontSize) { "${(fontSize * 100).roundToInt()}%" }) },
                )

                val alpha by remember(danmakuConfig) {
                    mutableFloatStateOf(danmakuConfig.style.alpha)
                }
                SliderItem(
                    value = alpha,
                    onValueChange = {
                        // 故意每次改都更新, 可以即时预览
                        setDanmakuConfig(
                            danmakuConfig.copy(style = danmakuConfig.style.copy(alpha = it)),
                        )
                    },
                    valueRange = 0f..1f,
//                steps = ((1f - 0f) / 0.05f).toInt() - 1,
                    title = { Text("不透明度") },
                    valueLabel = { Text(remember(alpha) { "${(alpha * 100).roundToInt()}%" }) },
                )

                val strokeWidth by remember(danmakuConfig) {
                    mutableFloatStateOf(danmakuConfig.style.strokeWidth / DanmakuStyle.Default.strokeWidth)
                }
                SliderItem(
                    value = strokeWidth,
                    onValueChange = {
                        // 故意每次改都更新, 可以即时预览
                        setDanmakuConfig(
                            danmakuConfig.copy(style = danmakuConfig.style.copy(strokeWidth = it * DanmakuStyle.Default.strokeWidth)),
                        )
                    },
                    valueRange = 0f..2f,
//                steps = ((2f - 0f) / 0.1f).toInt() - 1,
                    title = { Text("描边宽度") },
                    valueLabel = { Text(remember(strokeWidth) { "${(strokeWidth * 100).roundToInt()}%" }) },
                )

                val fontWeight by remember(danmakuConfig) {
                    derivedStateOf {
                        danmakuConfig.style.fontWeight.weight.toFloat()
                    }
                }
                SliderItem(
                    value = fontWeight,
                    onValueChange = {
                        if (it != fontWeight) {
                            // 故意每次改都更新, 可以即时预览
                            setDanmakuConfig(
                                danmakuConfig.copy(style = danmakuConfig.style.copy(fontWeight = FontWeight(it.toInt()))),
                            )
                        }
                    },
                    valueRange = 100f..900f,
//                steps = ((900 - 100) / 100) - 1,
                    title = { Text("弹幕字重") },
                    valueLabel = { Text(remember(fontWeight) { "${fontWeight.toInt()}" }) },
                )

                val speed by remember(danmakuConfig) {
                    mutableFloatStateOf(
                        danmakuConfig.speed / DanmakuConfig.Default.speed,
                    )
                }
                SliderItem(
                    value = speed,
                    onValueChange = {
                        setDanmakuConfig(
                            danmakuConfig.copy(
                                speed = it * DanmakuConfig.Default.speed,
                            ),
                        )
                    },
                    valueRange = 0.2f..3f,
//                steps = ((3f - 0.2f) / 0.1f).toInt() - 1,
                    title = { Text("弹幕速度") },
                    description = { Text("弹幕速度不会跟随视频倍速变化") },
                    valueLabel = { Text(remember(speed) { "${(speed * 100).roundToInt()}%" }) },
                )

                val isDesktop =
                    currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

                @SuppressLint("RememberReturnType")
                val displayDensityRange = remember(isDesktop) {
                    // 100% .. 0%
                    36.dp..if (isDesktop) 720.dp else 240.dp
                }
                var displayDensity by remember(danmakuConfig) {
                    mutableFloatStateOf(
                        1.minus(
                            (danmakuConfig.safeSeparation - displayDensityRange.start) /
                                    (displayDensityRange.endInclusive - displayDensityRange.start + 1.dp),
                        ).div(0.1f).roundToInt().toFloat(),
                    )
                }
                SliderItem(
                    value = displayDensity,
                    onValueChange = {
                        displayDensity = it
                    },
                    // 这个会导致 repopulate, 所以改完了才更新
                    onValueChangeFinished = {
                        setDanmakuConfig(
                            danmakuConfig.copy(
                                safeSeparation = displayDensityRange.start +
                                        (displayDensityRange.endInclusive - displayDensityRange.start + 1.dp)
                                            .times((1 - displayDensity * 0.1f)),
                            ),
                        )
                    },
                    valueRange = 0f..10f,
                    steps = 9,
                    title = { Text("同屏密度") },
                    valueLabel = {
                        when (displayDensity.toInt()) {
                            in 7..10 -> Text("密集")
                            in 4..6 -> Text("适中")
                            in 0..3 -> Text("稀疏")
                        }
                    },
                )


                var displayArea by remember(danmakuConfig) {
                    mutableFloatStateOf(
                        when (danmakuConfig.displayArea) {
                            0.125f -> 1f
                            1f / 6 -> 2f
                            0.25f -> 3f
                            0.50f -> 4f
                            0.75f -> 5f
                            1f -> 6f
                            else -> 2f
                        },
                    )
                }
                SliderItem(
                    value = displayArea,
                    onValueChange = {
                        displayArea = it
                    },
                    // 这个会导致 repopulate, 所以改完了才更新
                    onValueChangeFinished = {
                        setDanmakuConfig(
                            danmakuConfig.copy(
                                displayArea = when (displayArea) {
                                    1f -> 0.125f
                                    2f -> 1f / 6
                                    3f -> 0.25f
                                    4f -> 0.50f
                                    5f -> 0.75f
                                    6f -> 1f
                                    else -> 0.25f
                                },
                            ),
                        )
                    },
                    valueRange = 1f..6f,
                    steps = 4,
                    title = { Text("显示区域") },
                    valueLabel = {
                        when (displayArea) {
                            1f -> Text("1/8 屏")
                            2f -> Text("1/6 屏")
                            3f -> Text("1/4 屏")
                            4f -> Text("半屏")
                            5f -> Text("3/4 屏")
                            6f -> Text("全屏")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SliderItem(
    value: Float,
    onValueChange: (Float) -> Unit,
    title: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0)
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    valueLabel: @Composable (() -> Unit)? = {
        Text(value.toString())
    },
    description: @Composable (() -> Unit)? = null,
    drawTick: DrawScope.(Offset, Color) -> Unit = { offset, color ->
        with(this) { drawCircle(color = color, center = offset, radius = SliderDefaults.TickSize.toPx() / 2f) }
    },

    ) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    title()
                }

                if (valueLabel != null) {
                    Box(Modifier.padding(start = 16.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                            valueLabel()
                        }
                    }
                }
            }
        },
        modifier = modifier,
        supportingContent = {
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                description?.invoke()

                Slider(
                    value,
                    onValueChange,
                    Modifier,
                    enabled,
                    onValueChangeFinished,
                    colors,
                    interactionSource = interactionSource,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = interactionSource,
                            colors = colors,
                            enabled = enabled,
                            thumbSize = DpSize(4.dp, 36.dp),
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            colors = colors, enabled = enabled, sliderState = sliderState,
                            thumbTrackGapSize = 6.dp,
                            drawTick = drawTick,
                        )
                    },
                    valueRange = valueRange,
                    steps = steps,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}