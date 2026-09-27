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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.user.SelfInfo
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.login_sign_in
import me.him188.ani.app.ui.lang.settings_account_popup_logout
import me.him188.ani.app.ui.lang.settings_tab_account
import me.him188.ani.tv.ui.foundation.focus.tvFocusEnterGate
import org.jetbrains.compose.resources.stringResource

/** 悬浮导航栏的尺寸、内容避让范围与展开动画。 */
object TvNavigationRailDefaults {
    val StartPadding = 16.dp
    val ItemSize = 40.dp
    val ItemSpacing = 8.dp
    val ItemShape = RoundedCornerShape(50)
    val AvatarImageSize = 24.dp
    val IconGlyphSize = 20.dp
    val LabelSpacing = 12.dp
    val LabelEndPadding = 8.dp
    val LabelMaxWidth = 200.dp
    val FocusedContainerColor = Color.White
    val FocusedContentColor = Color(0xFF1A1C1E)
    val BackgroundBlurRadius = 48.dp
    const val BackgroundBlurWidthFraction = .25f
    const val BackgroundDimAlpha = .35f
    const val ExpandDurationMillis = 260

    /** 页面背景可铺满屏幕；前景内容按需使用收起态避让范围，展开时范围保持不变。 */
    val ContentInsets: PaddingValues = PaddingValues(start = StartPadding + ItemSize)
}

/** 导航条目：聚焦导航栏时显示文字，聚焦条目以白色药丸高亮。 */
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
    /** Explicit route-return target, taking precedence over the current page and default entry. */
    val restoreFocus: Boolean = false,
    /** true 时由调用方负责焦点转移，适用于独立页面导航或就地打开弹窗。 */
    val keepFocusOnClick: Boolean = false,
    val modifier: Modifier = Modifier,
    val onClick: () -> Unit,
)

/**
 * 悬浮于页面之上的导航栏。子树持有焦点时显示文字，条目容器随文字显隐变换尺寸。
 *
 * @param selfInfo 头像用户信息；资料未加载时显示默认人物图标。
 * @param isLoggedIn 会话的登录状态，独立于头像资料的加载状态。
 * @param showAvatar false 时保留头像槽位的等高占位, 使其余按钮位置不变.
 * @param onExitFocus 非 null 时: 条目上按返回键/右键调用它并吞掉按键 (如详情页把焦点送回
 *   Hero 播放按钮); null 时不拦截.
 * @param enterFocus 进入落点条目的请求器; 不传则内部自建. 调用方要程序化送焦点进侧边栏
 *   (如全局菜单键) 时, 推荐在 [modifier] 上挂 tvFocusAnchor (容器锚点): requestFocus 会经
 *   进入门控落到 selected 条目, 且 hasFocus 对整个子树上报到位 (解析轮询能确认收敛).
 */
@Composable
fun TvNavigationSideRail(
    selfInfo: SelfInfo?,
    onAvatarClick: () -> Unit,
    items: List<TvNavRailItem>,
    modifier: Modifier = Modifier,
    showAvatar: Boolean = true,
    onExitFocus: (() -> Unit)? = null,
    enterFocus: FocusRequester? = null,
    /**
     * 点击条目后把焦点还给内容区的方式; null = 空间搜索右移 (moveFocus).
     * 壳传"恢复进入侧边栏前的焦点"实现, 让点击当前页条目回到原位而非几何最近节点.
     */
    returnFocusToContent: (() -> Unit)? = null,
    onLogoutClick: (() -> Unit)? = null,
    avatarModifier: Modifier = Modifier,
    logoutModifier: Modifier = Modifier,
    /** 确认弹窗显示期间保留账号操作入口，关闭后可恢复焦点。 */
    keepAccountActionsVisible: Boolean = false,
    isLoggedIn: Boolean = selfInfo != null,
    /** 账号弹窗关闭后的具体入口；不参与普通导航条目的请求器绑定。 */
    accountRestoreFocus: FocusRequester? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var accountActionsVisible by remember { mutableStateOf(false) }
    val railExpanded = expanded || keepAccountActionsVisible
    val avatarLabel = if (isLoggedIn) {
        selfInfo?.nickname?.takeIf { it.isNotBlank() } ?: stringResource(Lang.settings_tab_account)
    } else {
        stringResource(Lang.login_sign_in)
    }
    val logoutLabel = stringResource(Lang.settings_account_popup_logout)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val labels = items.map { it.label } + if (showAvatar) {
        listOfNotNull(avatarLabel, logoutLabel.takeIf { isLoggedIn && onLogoutClick != null })
    } else {
        emptyList()
    }
    // 隐藏中的账号操作也参与测量，焦点在条目间移动时宽度保持一致。
    val longestLabelWidth = labels.maxOfOrNull {
        textMeasurer.measure(it, style = labelStyle, softWrap = false, maxLines = 1).size.width
    } ?: 0
    val expandedWidth = TvNavigationRailDefaults.ItemSize + TvNavigationRailDefaults.LabelSpacing +
            TvNavigationRailDefaults.LabelEndPadding + with(LocalDensity.current) {
        longestLabelWidth.toDp().coerceAtMost(TvNavigationRailDefaults.LabelMaxWidth)
    }
    val itemWidth by animateDpAsState(
        if (railExpanded) expandedWidth else TvNavigationRailDefaults.ItemSize,
        tween(TvNavigationRailDefaults.ExpandDurationMillis, easing = FastOutSlowInEasing),
        label = "rail-item-width",
    )
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
        // 进入门控 (统一焦点框架的组件级重载): 只有"按左"或编程式聚焦 (全局菜单键) 能进,
        // 上/下/右的空间搜索一律取消; 优先恢复路由入口，否则落当前页或默认条目。
        val defaultEnterFocus = remember { FocusRequester() }
        val restoringItem = items.firstOrNull { it.restoreFocus }
        val enterFocusResolved = enterFocus ?: restoringItem?.focusRequester ?: defaultEnterFocus
        val entryIndex = items.indexOfFirst { it.restoreFocus }.takeIf { it >= 0 }
            ?: items.indexOfFirst { it.selected }.takeIf { it >= 0 }
            ?: items.indexOfFirst { it.defaultFocus }
        Layout(
            modifier = Modifier.fillMaxHeight()
                .onFocusChanged {
                    expanded = it.hasFocus
                    if (!it.hasFocus && !keepAccountActionsVisible) accountActionsVisible = false
                }
                .tvFocusEnterGate(entry = accountRestoreFocus ?: enterFocusResolved)
                .padding(start = TvNavigationRailDefaults.StartPadding),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(TvNavigationRailDefaults.ItemSpacing)) {
                    if (showAvatar) {
                        TvRailAvatar(
                            selfInfo, isLoggedIn, avatarLabel, railExpanded, onExitFocus, onAvatarClick,
                            avatarModifier.width(itemWidth).testTag("tv-navigation-avatar").onFocusChanged {
                                if (it.isFocused) accountActionsVisible = true
                            },
                        )
                    } else {
                        Box(Modifier.size(TvNavigationRailDefaults.ItemSize))
                    }
                    for ((index, item) in items.withIndex()) {
                        TvRailIconItem(
                            icon = item.icon,
                            label = item.label,
                            expanded = railExpanded,
                            onExitFocus = onExitFocus,
                            focusRequester = if (index == entryIndex) enterFocusResolved else item.focusRequester,
                            keepFocusOnClick = item.keepFocusOnClick,
                            returnFocusToContent = returnFocusToContent,
                            onClick = item.onClick,
                            modifier = item.modifier.width(itemWidth).onFocusChanged {
                                if (it.isFocused) accountActionsVisible = false
                            },
                        )
                    }
                }
                if (showAvatar && isLoggedIn && onLogoutClick != null && (accountActionsVisible || keepAccountActionsVisible)) {
                    TvRailIconItem(
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        label = logoutLabel,
                        expanded = railExpanded,
                        onExitFocus = onExitFocus,
                        focusRequester = null,
                        keepFocusOnClick = true,
                        returnFocusToContent = returnFocusToContent,
                        onClick = onLogoutClick,
                        modifier = logoutModifier.width(itemWidth).testTag("tv-navigation-logout"),
                    )
                }
            },
        ) { measurables, constraints ->
            val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val navigation = measurables[0].measure(childConstraints)
            val logout = measurables.getOrNull(1)?.measure(childConstraints)
            val height = constraints.maxHeight
            val navigationY = (height - navigation.height) / 2
            // 主导航独立居中；账号操作占用头像上方的空白，不参与主导航的位置计算。
            layout(maxOf(navigation.width, logout?.width ?: 0), height) {
                navigation.placeRelative(0, navigationY)
                logout?.placeRelative(0, navigationY - logout.height - TvNavigationRailDefaults.ItemSpacing.roundToPx())
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
    loggedIn: Boolean,
    label: String,
    expanded: Boolean,
    onExitFocus: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvRailItem(label, expanded, { if (!loggedIn) onClick() }, modifier.railExitKeys(onExitFocus)) {
        if (loggedIn && selfInfo?.avatarUrl != null) {
            AvatarImage(
                url = selfInfo.avatarUrl,
                modifier = Modifier.size(TvNavigationRailDefaults.AvatarImageSize).clip(CircleShape),
            )
        } else {
            Icon(
                if (loggedIn) Icons.Rounded.Person else Icons.Outlined.AccountCircle, null,
                Modifier.size(TvNavigationRailDefaults.IconGlyphSize),
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
    returnFocusToContent: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    TvRailItem(
        label, expanded,
        onClick = {
            onClick()
            if (!keepFocusOnClick) {
                returnFocusToContent?.invoke() ?: focusManager.moveFocus(FocusDirection.Right)
            }
        },
        modifier = modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .railExitKeys(onExitFocus),
    ) {
        Icon(icon, null, Modifier.size(TvNavigationRailDefaults.IconGlyphSize))
    }
}

/** 图标和文字共享同一颗药丸；尺寸动画同时作用于背景、裁剪和交互范围。 */
@Composable
private fun TvRailItem(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        if (focused) TvNavigationRailDefaults.FocusedContainerColor else Color.Transparent,
        tween(150), label = "rail-item-container",
    )
    val contentColor by animateColorAsState(
        if (focused) TvNavigationRailDefaults.FocusedContentColor else MaterialTheme.colorScheme.onSurface,
        tween(150), label = "rail-item-content",
    )
    Row(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label }
            .clip(TvNavigationRailDefaults.ItemShape)
            .background(containerColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .height(TvNavigationRailDefaults.ItemSize)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box(Modifier.size(TvNavigationRailDefaults.AvatarImageSize), contentAlignment = Alignment.Center) { icon() }
            AnimatedVisibility(
                expanded,
                modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                enter = fadeIn(tween(150)), exit = fadeOut(tween(90)),
            ) {
                Text(
                    label,
                    Modifier.padding(
                        start = TvNavigationRailDefaults.LabelSpacing,
                        end = TvNavigationRailDefaults.LabelEndPadding,
                    ).widthIn(max = TvNavigationRailDefaults.LabelMaxWidth),
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
