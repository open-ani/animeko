/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.tv.ui.foundation.focus.tvFocusEnterGate

/*
 * TV 可展开左侧导航栏. 布局/交互对齐上游 PR#3217 的 TvNavigationSideRail:
 * 收起态一列 32dp 图标 (头像置顶), 焦点进入后展开为"图标+文字"并压一层左缘渐变面板,
 * 焦点离开自动收起. 只有"按左"能把焦点移进侧边栏, 进入时焦点落到 selected (当前页)
 * 条目, 无 selected 时回退 defaultFocus 条目.
 */

/** TV 可展开左侧导航栏的默认尺寸. */
object TvNavigationRailDefaults {
    /** 收起态占位宽度 (= start 16 + 图标 32): 调用方据此把内容右移让开收起的图标列. */
    val CollapsedWidth = 48.dp
}

/** 侧边栏单个条目: 图标 + 文字, 聚焦时图标方块反色高亮. */
@Immutable
data class TvNavRailItem(
    val icon: ImageVector,
    val label: String,
    /** 无 selected 条目时焦点进入侧边栏的回退落点 (整栏至多标记一个, 如"探索"). */
    val defaultFocus: Boolean = false,
    /**
     * true = 本条目对应当前显示的页面: 焦点进入侧边栏 (按左/菜单键) 优先落到它上,
     * 用户不用再从固定落点挪到当前页条目. 条目内不画"当前页"高亮 (与聚焦高亮会互相误导).
     */
    val selected: Boolean = false,
    val focusRequester: FocusRequester? = null,
    /** true 时点击后不清焦点 (就地开弹窗的条目需要留住焦点). */
    val keepFocusOnClick: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * TV 可展开左侧导航栏.
 *
 * @param selfInfo 头像用户信息; null 表示未登录 (显示默认人物图标).
 * @param showAvatar false 时保留头像槽位的等高占位, 使其余按钮位置不变.
 * @param onExitFocus 非 null 时: 条目上按返回键/右键调用它并吞掉按键 (如详情页把焦点送回
 *   Hero 播放按钮); null 时不拦截.
 * @param scrimColor 展开面板底色覆盖; null 用 [tvShellBackgroundColor].
 * @param enterFocus 进入侧边栏时聚焦的 defaultFocus 条目的请求器; 传入后调用方可在任意时刻
 *   requestFocus 把焦点直接送进侧边栏 (如全局菜单键), 不传则内部自建.
 */
@Composable
fun TvNavigationSideRail(
    selfInfo: SelfInfo?,
    onAvatarClick: () -> Unit,
    items: List<TvNavRailItem>,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = true,
    onExitFocus: (() -> Unit)? = null,
    scrimColor: Color? = null,
    enterFocus: FocusRequester? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
        AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
            // 展开底衬: 纯色面板 + 右缘多色标平滑羽化融入内容 (消除竖向明暗切线)
            val panelColor = scrimColor ?: tvShellBackgroundColor()
            Box(
                Modifier.fillMaxHeight().width(TV_RAIL_SCRIM_WIDTH).background(
                    Brush.horizontalGradient(
                        0.00f to panelColor,
                        0.82f to panelColor,
                        0.90f to panelColor.copy(alpha = 0.82f),
                        0.96f to panelColor.copy(alpha = 0.38f),
                        1.00f to panelColor.copy(alpha = 0f),
                    ),
                ),
            )
        }
        // 进入门控 (统一焦点框架的组件级重载): 只有"按左"或编程式聚焦 (全局菜单键) 能进,
        // 上/下/右的空间搜索一律取消; 进入落点 = selected (当前页) 条目, 回退 defaultFocus
        val enterFocusResolved = enterFocus ?: remember { FocusRequester() }
        val entryIndex = items.indexOfFirst { it.selected }.takeIf { it >= 0 }
            ?: items.indexOfFirst { it.defaultFocus }
        Column(
            Modifier
                .onFocusChanged { expanded = it.hasFocus }
                .tvFocusEnterGate(entry = enterFocusResolved)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showAvatar) {
                TvRailAvatar(selfInfo, expanded, onExitFocus, onAvatarClick)
            } else {
                Box(Modifier.size(TV_RAIL_ITEM_SIZE))
            }
            for ((index, item) in items.withIndex()) {
                TvRailIconItem(
                    icon = item.icon,
                    label = item.label,
                    expanded = expanded,
                    onExitFocus = onExitFocus,
                    focusRequester = if (index == entryIndex) enterFocusResolved else item.focusRequester,
                    keepFocusOnClick = item.keepFocusOnClick,
                    onClick = item.onClick,
                )
            }
        }
    }
}

/** 侧边栏统一的图标方块: 32dp 容器 + 20dp 字形, 聚焦时主色底 + 反色图标 (带颜色过渡动画). */
@Composable
private fun TvRailGlyphBox(
    focused: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
    )
    Box(
        modifier.size(TV_RAIL_ITEM_SIZE).clip(RoundedCornerShape(TV_RAIL_ITEM_CORNER)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (focused) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ) {
            Box(Modifier.size(TV_RAIL_ICON_GLYPH_SIZE), contentAlignment = Alignment.Center) {
                Icon(icon, null)
            }
        }
    }
}

/** 返回键/右键回退焦点的按键处理 (仅 [onExitFocus] 非 null 时拦截). */
private fun Modifier.railExitKeys(onExitFocus: (() -> Unit)?): Modifier {
    if (onExitFocus == null) return this
    return this.onPreviewKeyEvent { event ->
        when (event.key) {
            Key.Back, Key.Escape -> {
                if (event.type == KeyEventType.KeyUp) onExitFocus()
                true
            }

            Key.DirectionRight -> {
                if (event.type == KeyEventType.KeyDown) onExitFocus()
                true
            }

            else -> false
        }
    }
}

@Composable
private fun TvRailAvatar(
    selfInfo: SelfInfo?,
    expanded: Boolean,
    onExitFocus: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loggedIn = selfInfo != null
    var avatarFocused by remember { mutableStateOf(false) }
    // 聚焦高亮: 已登录画圆环 (头像是圆的), 未登录用图标块反色底
    val focusHighlight by animateColorAsState(
        if (avatarFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
    )
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (loggedIn) {
            Box(
                Modifier.size(TV_RAIL_ITEM_SIZE)
                    .onFocusChanged { avatarFocused = it.isFocused }
                    .railExitKeys(onExitFocus)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                    .border(2.dp, focusHighlight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // 照片比高亮框略小并居中, 使聚焦圆环成为其外圈
                AvatarImage(
                    url = selfInfo?.avatarUrl,
                    modifier = Modifier.size(TV_RAIL_AVATAR_IMAGE_SIZE).clip(CircleShape),
                )
            }
        } else {
            TvRailGlyphBox(
                focused = avatarFocused,
                icon = Icons.Outlined.AccountCircle,
                modifier = Modifier
                    .onFocusChanged { avatarFocused = it.isFocused }
                    .railExitKeys(onExitFocus)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ),
            )
        }
        if (expanded) {
            Text(
                selfInfo?.nickname?.takeIf { it.isNotBlank() } ?: "登录",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun TvRailIconItem(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    onExitFocus: (() -> Unit)?,
    focusRequester: FocusRequester?,
    keepFocusOnClick: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Row(
        modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .railExitKeys(onExitFocus)
            // 自绘聚焦指示 (图标方块反色); 不标记"当前页", 两处高亮会误导用户
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    onClick()
                    // 点击后清空焦点, 让全局兜底把焦点送入当前页面左上角可聚焦项
                    if (!keepFocusOnClick) focusManager.clearFocus()
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvRailGlyphBox(focused, icon)
        if (expanded) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                softWrap = false,
            )
        }
    }
}

/** 侧边栏展开时的渐变面板宽度. */
private val TV_RAIL_SCRIM_WIDTH = 180.dp

/** 单个图标按钮 (聚焦反色方块 / 头像) 的边长. */
private val TV_RAIL_ITEM_SIZE = 32.dp

/** 图标按钮聚焦方块的圆角. */
private val TV_RAIL_ITEM_CORNER = 6.dp

/** 头像照片尺寸: 比高亮框略小, 使聚焦圆环成为其外圈. */
private val TV_RAIL_AVATAR_IMAGE_SIZE = 24.dp

/** 图标字形尺寸. */
private val TV_RAIL_ICON_GLYPH_SIZE = 20.dp
