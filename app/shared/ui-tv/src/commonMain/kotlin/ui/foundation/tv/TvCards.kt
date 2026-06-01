/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.tv

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.external.placeholder.PlaceholderHighlight
import me.him188.ani.app.ui.external.placeholder.fade
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.foundation.tvLongPressKey
import kotlin.math.pow

/**
 * TV 竖版封面卡片 (探索页 / 追番页共用): 聚焦时主题主色外圈 (外圈与封面之间留一圈空隙,
 * 不需要动态取色). [imageUrl] 为 null 时显示加载占位. 长按 (遥控器确定键长按 / 触屏长按)
 * 弹出 [menu] (用于承载与详情页收藏按钮一致的收藏状态下拉).
 *
 * 焦点请求也可通过 [modifier] 挂 [FocusRequester]: 请求会委托给子树里第一个焦点目标
 * (卡片内容本体), 因此外部可以叠加多枚请求器 (如"首卡"与"恢复焦点"各一枚).
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
    /**
     * [menu] 的展开态变化 (长按弹出 / 关闭). 供调用方在长按期间做整页效果 —— 如时间表把
     * 其余卡片淡掉露出 backdrop. 只在真正变化时回调, 不会在每次组合时空报一次.
     */
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
            .aspectRatio(TV_PORTRAIT_CARD_COVER_RATIO)
            .then(
                if (focused) {
                    Modifier.border(
                        TV_CARD_FOCUS_RING_WIDTH,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(TV_PORTRAIT_CARD_CORNER + TV_CARD_FOCUS_GAP),
                    )
                } else Modifier,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(TV_CARD_FOCUS_GAP),
            shape = RoundedCornerShape(TV_PORTRAIT_CARD_CORNER),
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
                        // 有菜单才接管确认键 (按住途中到阈值立即弹菜单, 短按仍是点击);
                        // 没有菜单时交回下面 combinedClickable 的原生处理.
                        // 长按残余的确认键由弹出的菜单自己吞掉 (调用方负责, 见各页 collectionMenuFor)
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
                } else {
                    // 非完整视觉效果档时骨架不脉动 (highlight=null). 无限 fade 高亮把动画值
                    // 读进组合 (thirdparty placeholder 旧 accompanist 写法), 一屏几十张骨架卡
                    // = 首屏加载最忙时段每帧几十次重组; 搜索页 NSFW/隐藏条目 imageUrl 恒为
                    // null, 不关的话那些卡永远在跑
                    val fullEffects = LocalThemeSettings.current.tvFullVisualEffects
                    Box(
                        Modifier.fillMaxSize()
                            .placeholder(
                                true,
                                shape = RoundedCornerShape(TV_PORTRAIT_CARD_CORNER),
                                highlight = if (fullEffects) ({ PlaceholderHighlight.fade() }) else null,
                            ),
                    )
                }
                // 集数观看进度条: 与详情页选集卡 (FocusEpisodeProgressBar) 同款悬浮胶囊条 —
                // 左右内缩避开圆角, 离底边一点空隙, 圆头, 白 30% 轨道 + 主题色填充
                if (progress != null && progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = TV_CARD_PROGRESS_BAR_INSET)
                            .padding(bottom = TV_CARD_PROGRESS_BAR_BOTTOM_GAP)
                            .fillMaxWidth()
                            .height(TV_CARD_PROGRESS_BAR_HEIGHT)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = TV_CARD_PROGRESS_TRACK_ALPHA)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(TV_CARD_PROGRESS_BAR_HEIGHT)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
        // 菜单以卡片右下角为锚点弹出 (DropdownMenu 默认从锚点向右/上下就近展开): 放一个对齐到
        // 卡片右下角的零尺寸锚点, 菜单即从右下角向右上方向弹出.
        if (menu != null) {
            Box(Modifier.align(Alignment.BottomEnd)) {
                menu(menuExpanded) { setMenuExpanded(false) }
            }
        }
    }
}

/**
 * TV Hero 操作按钮 (立即观看 / 更多详细内容 / 继续观看等). 两枚都是深/浅灰实心
 * (参考 Prime: 主按钮略亮, 次按钮接近底色), 按白天/黑夜主题分别取色. 聚焦时整颗按钮
 * 高亮为主题主色、文字/图标反色 (onPrimary), 与侧边栏选中一致.
 * [filled] = true 为主按钮 (略亮一档). 图标 + 文字单行.
 */
@Composable
fun TvHeroButton(
    text: String,
    icon: ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChangedExtra: ((Boolean) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // 按当前主题明暗取底色 (由 surface 亮度判定, 兼容手动日夜切换):
    // 黑夜: 主按钮 rgb(49,54,61), 次按钮接近黑; 白天: 对应的浅灰两档.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val baseContainer = when {
        dark && filled -> Color(0xFF31363D)
        dark -> Color(0xFF17191C)
        filled -> Color(0xFFDBE0E6)
        else -> Color(0xFFF1F3F6)
    }
    val container = if (focused) MaterialTheme.colorScheme.primary else baseContainer
    val content = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    // 按 TV_HERO_BUTTON_SCALE 整体缩放内边距/图标/字号
    val scale = TV_HERO_BUTTON_SCALE
    val textStyle = MaterialTheme.typography.titleSmall.let {
        it.copy(fontSize = it.fontSize * scale, lineHeight = it.lineHeight * scale)
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged {
                if (it.isFocused) onFocused()
                onFocusChangedExtra?.invoke(it.isFocused)
            },
        shape = RoundedCornerShape(TV_HERO_BUTTON_CORNER),
        color = container,
        interactionSource = interactionSource,
    ) {
        Row(
            // 内边距对齐 Prime 实测 (单行按钮 30.5dp 高, 水平留白 ~13dp, 垂直墨迹留白 ~9dp)
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
 * hero 区常驻文本跑马灯的迭代次数: [ThemeSettings.tvFullVisualEffects] 关闭时滚固定次数后
 * 停在行首 —— 无限迭代让页面永远无法进入"无脏区"静止态 (溢出的文字行每帧重绘 + 整帧重合成,
 * 也阻止合成器跳帧省电), 是低端设备的常驻底噪. 换条目时文本重建, 会重新滚够次数, 信息不丢失.
 * 聚焦才出现的跑马灯 (单实例、用户明确在看) 不受此限.
 */
@Composable
fun tvHeroMarqueeIterations(): Int =
    if (LocalThemeSettings.current.tvFullVisualEffects) Int.MAX_VALUE else TV_HERO_MARQUEE_REDUCED_ITERATIONS

/** 非完整视觉效果档时 hero 跑马灯的滚动次数. */
private const val TV_HERO_MARQUEE_REDUCED_ITERATIONS = 3

/**
 * TV hero 区标题/正文文字色 (对齐 Prime 实测): 黑夜 #F1F1F1 —— 亮中性白, 无色相、无投影
 * (实测字形边缘无暗晕, 可读性靠文字够亮 + backdrop 渐隐压暗). M3 的 onSurface/onSurfaceVariant
 * 偏暗且带紫色相, 在深色 backdrop 上显得发糊. 白天用等效中性深灰.
 */
@Composable
fun tvHeroContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFF1F1F1) else Color(0xFF1A1C1E)

/** TV hero 区次要信息文字色 (连载信息/日期等, 对齐 Prime 实测): 黑夜 #B4B5B7 中性灰; 白天等效. */
@Composable
fun tvHeroSecondaryContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFB4B5B7) else Color(0xFF5B5D60)

/**
 * TV backdrop 下缘渐隐的渐变停点: 遮盖 alpha 在 [start]..[end] (绘制坐标 0..1)
 * 内从 0 平滑升到 1. 曲线 = quintic smootherstep (两端一、二阶导都为 0) 经 1-(1-s)^power
 * 反变换: 起点以零斜率极缓进入 (看不到"渐变开始"的分界线), 中段较快压暗, 尾段以指数级
 * 放缓渐近全遮、一直渐变到 [end] (通常传图的底边 1.0) —— 终点同样无分界线.
 * [power] 越大前段越快、尾巴越长. 采样多段生成停点, 避免手写折点产生马赫带.
 *
 * [color] 传页面底色时用普通 SrcOver 叠画即可 (图下面恰是该纯色时与 DstOut 擦除逐像素
 * 等价, 且不需要离屏合成); 传默认黑 + BlendMode.DstOut 则是擦除语义 (底下不是纯色时用).
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
 * TV backdrop 边缘渐隐的渐变停点: 遮盖 alpha 在 [start]..[end] 内从 [maxAlpha]
 * 平滑降到 0 ([start] 之前按 [maxAlpha] 遮盖, [end] 之后图完全清晰). smoothstep 采样,
 * 理由同上. [maxAlpha] < 1 时是"压暗"而非完全遮盖 (如顶缘给悬浮文字提高可读性).
 * [color] 语义见 [tvBackdropFadeToBlackStops].
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
 * TV 页面背景 backdrop 层 (追番页 / 搜索页共用): 16:9 贴右上角, 高度为屏高固定比例
 * ([TV_BACKDROP_HEIGHT_FRACTION]), 顶缘轻度压暗 + 左缘/下缘渐隐入页面背景
 * (恒用探索页"卡片态"渐变). 调用方通常 `Modifier.align(Alignment.TopEnd)`.
 *
 * [fadeColor] 必须传**图层正下方的实际页面底色** (追番页是 shellBackgroundColor, 搜索页是
 * colorScheme.background): 渐隐是直接在图上叠画该色的渐变 (SrcOver). 旧实现用
 * DstOut 擦除露底色, 视觉等价但要求整块图层先渲染进离屏缓冲
 * (CompositingStrategy.Offscreen, 4K 下 ~14MB、每次换图重光栅化) —— 低端 GPU 上是
 * 白付的填充率 (2026-07-31 性能整改).
 *
 * [backdropUrl] 用 lambda 而非值传入: URL 由"聚焦条目"状态推导, 状态读取发生在本组件
 * 内部 —— 遥控器每移一格只重组这一小块, 不连带调用方整个页面作用域重组.
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
        animationSpec = tween(TV_BACKDROP_CROSSFADE_MILLIS),
    ) { url ->
        if (url != null) {
            Box(
                Modifier
                    .fillMaxHeight(TV_BACKDROP_HEIGHT_FRACTION)
                    .aspectRatio(TV_BACKDROP_ASPECT_RATIO, matchHeightConstraintsFirst = true)
                    .drawWithContent {
                        drawContent()
                        // 停点由平滑曲线采样生成 (无折点, 避免暗色端可见的马赫带分界线);
                        // 顶缘轻度压暗 (非全遮): 给悬浮在 backdrop 上的顶部文字一层可读性 scrim
                        drawRect(
                            brush = Brush.verticalGradient(
                                *tvBackdropFadeFromBlackStops(
                                    start = 0f, end = TV_BACKDROP_TOP_SCRIM_END,
                                    maxAlpha = TV_BACKDROP_TOP_SCRIM_ALPHA,
                                    color = fadeColor,
                                ),
                            ),
                        )
                        drawRect(
                            brush = Brush.horizontalGradient(
                                *tvBackdropFadeFromBlackStops(
                                    start = TV_BACKDROP_LEFT_FADE_START,
                                    end = TV_BACKDROP_LEFT_FADE_END,
                                    color = fadeColor,
                                ),
                            ),
                        )
                        // 下缘渐隐: 零斜率极缓起步 + 指数级长尾渐近全遮, 一直渐变到图底
                        drawRect(
                            brush = Brush.verticalGradient(
                                *tvBackdropFadeToBlackStops(
                                    start = TV_BACKDROP_BOTTOM_FADE_START,
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
 * TV 全屏背景 backdrop 层 (新番时间表用): 横版图铺满全屏 (Crop) + 一层整屏压暗
 * ([TV_FULLSCREEN_BACKDROP_DIM_ALPHA]), 换图 crossfade. 四缘都不做渐隐 —— 见下.
 *
 * 与 [TvPageBackdropLayer] (16:9 贴右上, 只占屏顶七成) 的区别: 本页整屏都铺着卡片与小字,
 * 因此整屏压暗; 也正因为压暗是均匀的一层, 左缘不再额外补 scrim —— 横向渐变收尾处总会留下
 * 一条肉眼可见的边界, 而侧边栏的白图标压在整屏压暗上本来就够清楚.
 *
 * 遮罩一律用页面背景色而非黑色: 黑色遮罩在浅色主题下会把整页压暗, 迫使文字改用白色 (详情页
 * 就是这么做的 —— 它首屏只有一个标题浮在图上); 而本页文字铺满全屏、焦点每移一格就换图,
 * 逐图切换文字明暗会闪. 用背景色遮罩后, 深色主题下等效于原来的黑色遮罩, 浅色主题下是一层白纱,
 * 两种主题都能直接用 [tvHeroContentColor] 那套随主题取色的文字色.
 *
 * [backdropUrl] 用 lambda 传入, 理由同 [TvPageBackdropLayer].
 *
 * 只能用在**整屏归自己**的页面上 (新番时间表是独立目的地): 主壳内的页面被让开了侧边栏那一条,
 * 而主页三个 tab 的 AnimatedContent 会把内容裁在这个边界上 (MainScreen 的 topLevelTransition
 * 用的是默认 SizeTransform, clip = true), 从内容侧无论怎么向左出血都会被裁掉.
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
        animationSpec = tween(TV_BACKDROP_CROSSFADE_MILLIS),
    ) { url ->
        if (url != null) {
            // 不做底缘渐隐: 本页整屏都是内容, 渐隐带那一段会被擦成纯背景色 —— 在实机上就是
            // 屏幕最下面横着一条黑边. 它原本是为了托住右下角那行遥控提示, 提示已经去掉了.
            // 图铺满整屏, 均匀压暗一层就够 (与 16:9 那版不同: 那版图只占屏顶七成, 渐隐带落在
            // 屏幕中段, 是图与背景之间的过渡, 不是一条贴着屏底的边)
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    url,
                    contentDescription = null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // 整屏基础压暗: 亮部海报上压不住灰色小字. 这是唯一一层压暗 —— 左缘不再额外补
                // scrim: 任何"从左缘衰减到透明"的横向渐变都会在收尾处留下一条肉眼可见的边界,
                // 而侧边栏图标压在这层整屏压暗上本来就足够清楚 (白图标 + 深底)
                Box(
                    Modifier.fillMaxSize()
                        .background(background.copy(alpha = TV_FULLSCREEN_BACKDROP_DIM_ALPHA)),
                )
            }
        }
    }
}

// ============ TV 沉浸式页面 (探索/追番/搜索) 共享调参 ============
// 探索页轮播 (hero) 态的参数不在此列, 单独放在 TvExplorationPage 里.

// ---- backdrop ----

/** backdrop 宽高比. */
const val TV_BACKDROP_ASPECT_RATIO = 16f / 9f

/** backdrop 高度占屏高比例 (追番/搜索; 探索页因轮播布局单独一档). */
const val TV_BACKDROP_HEIGHT_FRACTION = 0.70f

/** backdrop 换图的淡入淡出时长 (毫秒). */
const val TV_BACKDROP_CROSSFADE_MILLIS = 600

/** backdrop 顶缘压暗带终点 (图片高度坐标 0..1; 顶部悬浮文字的可读性 scrim). */
const val TV_BACKDROP_TOP_SCRIM_END = 0.16f

/** backdrop 顶缘压暗强度 (1 = 完全擦除). */
const val TV_BACKDROP_TOP_SCRIM_ALPHA = 0.7f

/**
 * backdrop 下缘渐隐起点 (图片高度坐标 0..1, 此处开始向下渐暗, 一直渐变到图底).
 * 卡片聚焦态共用; 探索页轮播态另有自己的一档.
 */
const val TV_BACKDROP_BOTTOM_FADE_START = 0.78f

/**
 * TV hero backdrop 左缘渐隐窗口起点 (图片宽度坐标 0..1, 此前全擦除).
 * 探索 (卡片态) / 追番 / 搜索三页共用, 改这里三页一起变.
 */
const val TV_BACKDROP_LEFT_FADE_START = 0.02f

// ---- 全屏 backdrop (新番时间表; 见 [TvFullScreenBackdropLayer]) ----

/** 全屏 backdrop 的整屏基础压暗强度 (页面背景色的不透明度). 调大 = 图更淡、文字更清楚. */
const val TV_FULLSCREEN_BACKDROP_DIM_ALPHA = 0.46f

/** TV hero backdrop 左缘渐隐窗口终点 (此处起图完全清晰). 三页共用. */
const val TV_BACKDROP_LEFT_FADE_END = 0.3f

// ---- hero 文字 ----

/** TV hero 标题占屏宽比例 (右侧留给 backdrop 清晰区). */
const val TV_HERO_TITLE_WIDTH_FRACTION = 0.5f

/** TV hero 简介/状态行文字占内容列宽比例 (右边界之外留给 backdrop 清晰区). 三页共用. */
const val TV_HERO_SUMMARY_WIDTH_FRACTION = 0.4f

/** TV hero 信息块换条目时文字的渐隐渐现时长 (毫秒). */
const val TV_HERO_TEXT_FADE_MILLIS = 500

/** TV hero 媒体 (backdrop/简介等) 请求防抖: 焦点在卡片间快速划过时不发请求. */
const val TV_HERO_MEDIA_DEBOUNCE_MILLIS = 300L

// ---- 卡片网格 ----

/** 竖版海报卡片宽度 (Adaptive 网格按此为最小宽度自动决定列数). */
val TV_PAGE_CARD_WIDTH: Dp = 112.dp

/** 卡片间距. */
val TV_PAGE_CARD_SPACING = 10.dp

// ---- 底部遮罩 / 右下角提示 / 页面留白 ----

/** 底缘渐变遮罩高度 (覆盖被视口截断的下一行卡片露出的整段). */
val TV_PAGE_BOTTOM_SCRIM_HEIGHT = 90.dp

/** 底缘遮罩在最底边的不透明度 (1 = 底边完全融入页面背景). */
const val TV_PAGE_BOTTOM_SCRIM_MAX_ALPHA = 0.95f

/** 右下角遥控键提示的底部留白. */
val TV_PAGE_HINT_BOTTOM_PAD = 12.dp

/** 右下角遥控键提示的图标尺寸. */
val TV_PAGE_HINT_ICON_SIZE = 14.dp

/** 内容右侧留白. */
val TV_PAGE_END_PAD = 48.dp

/** 竖版封面宽高比 (与详情页封面一致; 网格行高估算也用它). */
const val TV_PORTRAIT_CARD_COVER_RATIO = 0.72f

/** 卡片圆角. */
private val TV_PORTRAIT_CARD_CORNER = 8.dp

/** 聚焦外圈描边宽度 (主题主色). */
private val TV_CARD_FOCUS_RING_WIDTH = 2.5.dp

/** 聚焦外圈与封面之间的空隙 (卡片内容常驻内缩此值, 聚焦时空隙处露出底色形成"色圈+留白"). */
private val TV_CARD_FOCUS_GAP = 3.dp

/** 继续观看卡片底部集数进度条 (样式对齐详情页 FocusEpisodeProgressBar): 条厚. */
private val TV_CARD_PROGRESS_BAR_HEIGHT = 2.5.dp

/** 进度条左右内缩 (避开卡片圆角). */
private val TV_CARD_PROGRESS_BAR_INSET = 10.dp

/** 进度条与卡片底边的空隙. */
private val TV_CARD_PROGRESS_BAR_BOTTOM_GAP = 4.dp

/** 进度条轨道 (未看部分) 的白色不透明度. */
private const val TV_CARD_PROGRESS_TRACK_ALPHA = 0.3f

/** Hero 操作按钮圆角. */
private val TV_HERO_BUTTON_CORNER = 8.dp

/** 操作按钮整体缩放比例 (内边距/图标/字号统一乘此值). 调小让按钮更紧凑. */
private const val TV_HERO_BUTTON_SCALE = 0.9f
