/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.widgets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.tv.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.tv.ui.foundation.focus.LocalTvFocusMemory
import me.him188.ani.tv.ui.foundation.focus.tvLongPressKey
import kotlin.math.pow

/*
 * TV 沉浸式页面的卡片/按钮/backdrop 体系.
 *
 * 视觉规格对齐上游 PR#3217 实机效果 (material3 + 自研焦点, 不用 tv-material):
 * - 卡片聚焦: 主题主色外圈 + 圈与封面之间留一圈空隙 ("色圈+留白", 无缩放)
 * - hero 按钮: Prime 风格深/浅灰实心, 聚焦整颗变主色反色
 * - backdrop: 平滑曲线采样的多停点渐变 (避免手写折点的马赫带)
 */

/**
 * TV 竖版封面卡片 (探索页 / 追番页共用): 聚焦时主题主色外圈. [imageUrl] 为 null 时显示占位.
 * 长按 (遥控器确定键长按 / 触屏长按) 弹出 [menu].
 */
@Composable
fun TvPortraitCard(
    imageUrl: String?,
    contentDescription: String?,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChangedExtra: ((Boolean) -> Unit)? = null,
    menu: (@Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit)? = null,
    /** 集数观看进度 (0..1): 贴卡片底缘画一条细进度条; null 不显示. */
    progress: Float? = null,
    onMenuExpandedChange: ((Boolean) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    val setMenuExpanded = { value: Boolean ->
        if (menuExpanded != value) {
            menuExpanded = value
            onMenuExpandedChange?.invoke(value)
        }
    }
    Box(
        modifier
            .aspectRatio(TvPortraitCardDefaults.CoverRatio)
            .then(
                if (focused) {
                    Modifier.border(
                        TvFocusDefaults.RingWidth,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(TvPortraitCardDefaults.CornerRadius + TvFocusDefaults.RingInset),
                    )
                } else Modifier,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(TvFocusDefaults.RingInset),
            shape = RoundedCornerShape(TvPortraitCardDefaults.CornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .onFocusChanged {
                        if (it.isFocused) onFocused()
                        onFocusChangedExtra?.invoke(it.isFocused)
                    }
                    .then(
                        // 有菜单才接管确认键 (按住到阈值立即弹菜单, 短按仍是点击)
                        if (menu == null) {
                            Modifier
                        } else {
                            Modifier.tvLongPressKey(
                                onLongPress = { setMenuExpanded(true) },
                                onShortPress = onClick,
                            )
                        },
                    )
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = onClick,
                        onLongClick = menu?.let { { setMenuExpanded(true) } },
                    ),
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        imageUrl,
                        contentDescription = contentDescription,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                // 集数观看进度条: 悬浮胶囊条 — 左右内缩避开圆角, 圆头, 白 30% 轨道 + 主题色填充
                if (progress != null && progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = TvPortraitCardDefaults.ProgressBarInset)
                            .padding(bottom = TvPortraitCardDefaults.ProgressBarBottomGap)
                            .fillMaxWidth()
                            .height(TvPortraitCardDefaults.ProgressBarHeight)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = TvPortraitCardDefaults.ProgressTrackAlpha)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(TvPortraitCardDefaults.ProgressBarHeight)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
        // 菜单以卡片右下角为锚点弹出
        if (menu != null) {
            Box(Modifier.align(Alignment.BottomEnd)) {
                menu(menuExpanded) { setMenuExpanded(false) }
            }
        }
    }
}

/**
 * TV Hero 操作按钮 (立即观看 / 更多详细内容等). 深/浅灰实心 (参考 Prime: 主按钮略亮,
 * 次按钮接近底色), 聚焦时整颗高亮为主题主色、文字/图标反色. [filled] = true 为主按钮.
 */
@Composable
fun TvHeroButton(
    text: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = TvHeroDefaults.ButtonShape,
    focusRequester: FocusRequester? = null,
    onFocusChangedExtra: ((Boolean) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // 聚焦时向内容区焦点记忆上报自身 (壳恢复"进侧边栏前的焦点"用)
    val focusMemory = LocalTvFocusMemory.current
    val selfRequester = remember { FocusRequester() }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val baseContainer = when {
        dark && filled -> Color(0xFF31363D)
        dark -> Color(0xFF17191C)
        filled -> Color(0xFFDBE0E6)
        else -> Color(0xFFF1F3F6)
    }
    val container = if (focused) MaterialTheme.colorScheme.primary else baseContainer
    val content = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val scale = TvHeroDefaults.ButtonScale
    val textStyle = MaterialTheme.typography.titleSmall.let {
        it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale)
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusRequester(selfRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    onFocused()
                    focusMemory?.last = selfRequester
                }
                onFocusChangedExtra?.invoke(it.isFocused)
            },
        shape = shape,
        color = container,
        interactionSource = interactionSource,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp * scale, vertical = 8.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp * scale),
        ) {
            Icon(icon, contentDescription = null, Modifier.size(20.dp * scale), tint = content)
            Text(text, color = content, style = textStyle, maxLines = 1)
        }
    }
}

/**
 * TV hero 区标题/正文文字色: 黑夜 #F1F1F1 亮中性白 (M3 onSurface 偏暗带紫色相, 深色
 * backdrop 上发糊); 白天等效中性深灰.
 */
@Composable
fun tvHeroContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFF1F1F1) else Color(0xFF1A1C1E)

/** TV hero 区次要信息文字色 (连载信息/日期等): 黑夜 #B4B5B7 中性灰; 白天等效. */
@Composable
fun tvHeroSecondaryContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFB4B5B7) else Color(0xFF5B5D60)

/**
 * TV 沉浸式外壳背景色 (外壳统一背景与侧边栏展开面板底色共用):
 * 深色主题用最低层容器色; 浅色主题用 surface (带主题色而非死白).
 */
@Composable
fun tvShellBackgroundColor(): Color {
    val surface = MaterialTheme.colorScheme.surface
    return if (surface.luminance() < 0.5f) MaterialTheme.colorScheme.surfaceContainerLowest else surface
}

/**
 * backdrop 下缘渐隐停点: alpha 在 [start]..[end] 内从 0 平滑升到 1.
 * quintic smootherstep 经 1-(1-s)^power 反变换: 起点零斜率极缓进入, 尾段指数级放缓渐近全遮
 * —— 两端都看不到"渐变开始"的分界线. 采样多段生成停点, 避免手写折点产生马赫带.
 */
fun tvBackdropFadeToBlackStops(
    start: Float,
    end: Float,
    power: Float = 2.5f,
    samples: Int = 20,
    color: Color = Color.Black,
): Array<Pair<Float, Color>> = Array(samples + 1) { i ->
    val f = i / samples.toFloat()
    val s5 = f * f * f * (f * (f * 6f - 15f) + 10f)
    (start + (end - start) * f) to color.copy(alpha = 1f - (1f - s5).pow(power))
}

/**
 * backdrop 边缘渐隐停点: alpha 在 [start]..[end] 内从 [maxAlpha] 平滑降到 0.
 * [maxAlpha] < 1 时是"压暗"而非完全遮盖 (如顶缘给悬浮文字提高可读性).
 */
fun tvBackdropFadeFromBlackStops(
    start: Float,
    end: Float,
    maxAlpha: Float = 1f,
    samples: Int = 14,
    color: Color = Color.Black,
): Array<Pair<Float, Color>> = Array(samples + 1) { i ->
    val f = i / samples.toFloat()
    val s = f * f * (3f - 2f * f)
    (start + (end - start) * f) to color.copy(alpha = maxAlpha * (1f - s))
}

/**
 * TV 页面背景 backdrop 层 (追番页 / 搜索页共用): 16:9 贴右上角, 高度为屏高固定比例,
 * 顶缘轻度压暗 + 左缘/下缘渐隐入页面背景. 调用方通常 `Modifier.align(Alignment.TopEnd)`.
 *
 * [fadeColor] 必须传图层正下方的实际页面底色: 渐隐是直接在图上叠画该色渐变 (SrcOver),
 * 避免 DstOut 擦除所需的离屏合成 (低端 GPU 上是白付的填充率).
 *
 * [backdropUrl] 用 lambda 传入: URL 由"聚焦条目"状态推导, 状态读取发生在本组件内部 ——
 * 遥控器每移一格只重组这一小块.
 */
@Composable
fun TvPageBackdropLayer(
    backdropUrl: () -> String?,
    fadeColor: Color,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        backdropUrl(),
        modifier,
        animationSpec = tween(TvBackdropDefaults.CrossfadeMillis),
    ) { url ->
        if (url != null) {
            Box(
                Modifier
                    .fillMaxHeight(TvBackdropDefaults.HeightFraction)
                    .aspectRatio(TvBackdropDefaults.AspectRatio, matchHeightConstraintsFirst = true)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                *tvBackdropFadeFromBlackStops(
                                    start = 0f, end = TvBackdropDefaults.TopScrimEnd,
                                    maxAlpha = TvBackdropDefaults.TopScrimAlpha,
                                    color = fadeColor,
                                ),
                            ),
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                *tvBackdropFadeFromBlackStops(
                                    start = TvBackdropDefaults.LeftFadeStart,
                                    end = TvBackdropDefaults.LeftFadeEnd,
                                    color = fadeColor,
                                ),
                            ),
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                *tvBackdropFadeToBlackStops(
                                    start = TvBackdropDefaults.BottomFadeStart,
                                    end = 1f,
                                    color = fadeColor,
                                ),
                            ),
                        )
                    },
            ) {
                AsyncImage(
                    url,
                    contentDescription = null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/**
 * TV 全屏背景 backdrop 层 (新番时间表用): 横版图铺满全屏 (Crop) + 一层整屏压暗, 换图
 * crossfade. 四缘不做渐隐 (横向渐变收尾总会留一条可见边界; 整页铺内容时均匀压暗即可).
 */
@Composable
fun TvFullScreenBackdropLayer(
    backdropUrl: () -> String?,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    Crossfade(
        backdropUrl(),
        modifier,
        animationSpec = tween(TvBackdropDefaults.CrossfadeMillis),
    ) { url ->
        if (url != null) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    url,
                    contentDescription = null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize()
                        .background(background.copy(alpha = TvBackdropDefaults.FullScreenDimAlpha)),
                )
            }
        }
    }
}

// ============ TV 沉浸式页面共享 Defaults (对齐上游 PR 实机验证值) ============

/** TV 页面 backdrop 层的默认值 (页面/全屏两种 backdrop 与探索页 hero backdrop 共用). */
object TvBackdropDefaults {
    /** backdrop 宽高比. */
    const val AspectRatio: Float = 16f / 9f

    /** backdrop 高度占屏高比例 (追番/搜索; 探索页因轮播布局单独一档). */
    const val HeightFraction: Float = 0.70f

    /** backdrop 换图的淡入淡出时长 (毫秒). */
    const val CrossfadeMillis: Int = 600

    /** backdrop 顶缘压暗带终点 (顶部悬浮文字的可读性 scrim). */
    const val TopScrimEnd: Float = 0.16f

    /** backdrop 顶缘压暗强度. */
    const val TopScrimAlpha: Float = 0.7f

    /** backdrop 下缘渐隐起点 (卡片态; 探索页轮播态另有自己的一档). */
    const val BottomFadeStart: Float = 0.78f

    /** backdrop 左缘渐隐窗口起点 (此前全擦除). */
    const val LeftFadeStart: Float = 0.02f

    /** backdrop 左缘渐隐窗口终点 (此处起图完全清晰). */
    const val LeftFadeEnd: Float = 0.3f

    /** 全屏 backdrop 的整屏基础压暗强度. */
    const val FullScreenDimAlpha: Float = 0.46f
}

/** TV hero 区 (标题/简介/操作按钮) 的默认值. */
object TvHeroDefaults {
    /** hero 标题占屏宽比例 (右侧留给 backdrop 清晰区). */
    const val TitleWidthFraction: Float = 0.5f

    /** hero 简介/状态行文字占内容列宽比例. */
    const val SummaryWidthFraction: Float = 0.4f

    /** hero 信息块换条目时文字的渐隐渐现时长 (毫秒). */
    const val TextFadeMillis: Int = 500

    /** hero 媒体 (backdrop/简介等) 请求防抖: 焦点快速划过时不发请求. */
    const val MediaDebounceMillis: Long = 300L

    /** [TvHeroButton] 圆角. */
    val ButtonShape: Shape = RoundedCornerShape(8.dp)

    /** [TvHeroButton] 整体缩放比例 (内边距/图标/字号统一乘此值). */
    const val ButtonScale: Float = 0.9f
}

/** TV 沉浸式页面级布局的默认值 (卡片网格/留白/底缘遮罩). */
object TvPageDefaults {
    /** 卡片间距. */
    val CardSpacing: Dp = 10.dp

    /** 内容右侧留白. */
    val EndPadding: Dp = 48.dp

    /** Adaptive 海报网格的最小格宽 (追番/搜索页同规格). */
    val PosterGridCellMinWidth: Dp = 124.dp

    /** 海报网格内容边距 (追番/搜索页同规格; 水平 = overscan 安全边距 48). */
    val PosterGridContentPadding: PaddingValues =
        PaddingValues(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 24.dp)

    /** 底缘渐变遮罩高度 (覆盖被视口截断的下一行卡片露出的整段). */
    val BottomScrimHeight: Dp = 90.dp

    /** 底缘遮罩在最底边的不透明度. */
    const val BottomScrimMaxAlpha: Float = 0.95f
}

/** [TvPortraitCard] 默认值 (聚焦外圈规格统一走 [TvFocusDefaults]). */
object TvPortraitCardDefaults {
    /** 竖版封面宽高比 (与详情页封面一致). */
    const val CoverRatio: Float = 0.72f

    /** 卡片圆角. */
    val CornerRadius: Dp = 8.dp

    /** 集数进度条 (悬浮胶囊条) 高度. */
    val ProgressBarHeight: Dp = 2.5.dp

    /** 集数进度条左右内缩 (避开卡片圆角). */
    val ProgressBarInset: Dp = 10.dp

    /** 集数进度条距卡片底缘间距. */
    val ProgressBarBottomGap: Dp = 4.dp

    /** 集数进度条轨道 (白色) 不透明度. */
    const val ProgressTrackAlpha: Float = 0.3f
}
