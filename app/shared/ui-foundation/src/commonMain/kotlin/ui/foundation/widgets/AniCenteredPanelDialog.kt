/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.him188.ani.app.ui.foundation.dialogs.DialogWindowDimAmount

/**
 * 半透明居中大面板弹窗: 按窗口比例定尺寸, 下层内容 (视频画面 / 页面) 经系统遮罩隐约透出.
 *
 * 用于大屏上替代贴边侧栏与底部抽屉 —— 贴边面板离视线中心远, 焦点跳到屏幕边缘的过程也难以
 * 看清. 是否改用这个形态由
 * [AniUiBehavior.panelsAsCenteredDialogs][me.him188.ani.app.ui.foundation.AniUiBehavior.panelsAsCenteredDialogs]
 * 决定.
 *
 * 返回键由 [Dialog] 自行消费 (独立窗口), 调用方无需再装 BackHandler.
 */
@Composable
fun AniCenteredPanelDialog(
    onDismissRequest: () -> Unit,
    title: (@Composable () -> Unit)? = null,
    widthFraction: Float = CENTERED_PANEL_WIDTH_FRACTION,
    heightFraction: Float = CENTERED_PANEL_HEIGHT_FRACTION,
    /**
     * 非 null 时高度由 [widthFraction] 推出的宽度按此宽高比算, 忽略 [heightFraction] ——
     * 背景是定比例的图 (如 16:9 剧照) 时用它, 面板与图同比例, 图铺满时不会被裁掉上下或左右.
     */
    aspectRatio: Float? = null,
    /**
     * 宽度上限; 给了它就按 `min(它, 窗口宽)` 定宽, [widthFraction] 不再生效.
     *
     * 内容本身有天然宽度时用它 (如评分弹窗: 十颗星就那么宽, 再宽只是星星两边空一大片) ——
     * 光靠比例, 窗口越宽两边就越松.
     */
    maxWidth: Dp = Dp.Unspecified,
    /**
     * 满幅铺在面板里的背景 (如剧照), 上方自动压一层遮罩保证正文可读.
     *
     * 非 null 时面板底色改为透明 (否则半透明玻璃色会盖住背景), 且内容色固定为白 ——
     * 遮罩之上永远是深底, 不能跟随主题的 onSurface (浅色主题下会变成深字压深底).
     */
    background: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // 面板占掉大半个屏幕, 窗口默认那档 0.6 的压暗会把四周压得几乎全黑 (电视上尤其明显).
        // 0.35 接近 M3 给模态遮罩的 32%, 面板边界照样分得清, 下层页面/视频还透得出来
        DialogWindowDimAmount(CENTERED_PANEL_WINDOW_DIM)
        Surface(
            // maxWidth 必须**在外层**: widthIn 会先服从传进来的约束, 而 fillMaxWidth 给的是
            // min = max = 比例宽度, 放在它后面等于没写 (实测宽度仍是整整那个比例)
            Modifier
                .then(
                    if (maxWidth != Dp.Unspecified) {
                        Modifier.widthIn(max = maxWidth).fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth(widthFraction)
                    },
                )
                .then(
                    if (aspectRatio != null) {
                        Modifier.aspectRatio(aspectRatio)
                    } else {
                        Modifier.fillMaxHeight(heightFraction)
                    },
                ),
            shape = RoundedCornerShape(16.dp),
            color = if (background != null) Color.Transparent else centeredPanelColor,
            // 必须显式给: Surface 默认用 contentColorFor(color) 推内容色, 而这里的底色带了 alpha,
            // 在配色表里查不到对应的 "on" 色 -> 退回 LocalContentColor, 而它的**默认值是纯黑**
            // (material3 的 compositionLocalOf { Color.Black }). 弹窗宿主往往不在任何 Surface 里
            // (如 EditableRatingDialogsHost), 于是深色主题下标题/正文全是黑字压深底.
            contentColor = if (background != null) Color.White else MaterialTheme.colorScheme.onSurface,
        ) {
            Box {
                if (background != null) {
                    background()
                    // 遮罩: 背景图的亮度/花色不可控, 压到足够暗才能保证任意图上正文都读得清
                    Box(
                        Modifier.matchParentSize()
                            .background(Color.Black.copy(alpha = CENTERED_PANEL_SCRIM_ALPHA)),
                    )
                }
                // 内容色由上面 Surface 的 contentColor 供给 (背景图时为白, 否则 onSurface)
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    title?.let {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) { it() }
                        }
                    }
                    content()
                }
            }
        }
    }
}

private const val CENTERED_PANEL_WIDTH_FRACTION = 0.72f
private const val CENTERED_PANEL_HEIGHT_FRACTION = 0.85f

/** 面板不透明度: 半透明玻璃感, 下层 (视频画面 / 页面) 隐约透出. */
private const val CENTERED_PANEL_ALPHA = 0.94f

/**
 * 面板底色 = `surfaceContainerHigh` + 半透明.
 *
 * 角色刻意与 M3 `AlertDialog` 的默认容器色一致: 所有弹窗 (这里的大面板、复用的手机端对话框、
 * 弹幕延迟这类小对话框) 底色于是同出一处, 不会一个偏亮一个偏暗. 只有大面板加半透明 —— 它盖掉
 * 大半个屏幕, 透出一点下层才知道自己没离开播放器; 小对话框不透.
 *
 * 公开是为了 TV 播放器的评论回复弹窗共用: 它为了让播放器根部的唯一按键路由收得到返回键,
 * 不能用真 [Dialog] (独立窗口), 只好自己画一块同窗口的面板.
 */
val centeredPanelColor: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = CENTERED_PANEL_ALPHA)

/**
 * 背景图上的遮罩不透明度: 压住亮色剧照到正文读得清即可, 不必压到只剩轮廓.
 *
 * 0.72 时剧照只剩不到三成亮度, 图基本是个影子; 0.55 下还剩四成半, 看得出画的是哪一幕 ——
 * 这层遮罩之上是白字 (背景态下面板固定给白内容色), 亮部剧照仍撑得住对比. 遇到实在压不住的亮图,
 * 优先给标题/正文加投影 (同 TvHeroBlock 的 titleShadow), 而不是把整张图再压暗一档.
 */
private const val CENTERED_PANEL_SCRIM_ALPHA = 0.55f

/** 弹窗窗口外的系统压暗 (见 [DialogWindowDimAmount]); 系统对话框默认 0.6, 大面板上太黑. */
private const val CENTERED_PANEL_WINDOW_DIM = 0.35f
