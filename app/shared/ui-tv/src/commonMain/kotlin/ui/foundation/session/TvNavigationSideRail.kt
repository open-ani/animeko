/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SyncAlt
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.getIcon
import me.him188.ani.app.navigation.getText
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.watchtogether.LocalWatchTogetherEntry
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_search
import me.him188.ani.app.ui.lang.login_sign_in
import me.him188.ani.app.ui.lang.settings
import me.him188.ani.app.ui.lang.watch_together_title
import me.him188.ani.app.ui.user.SelfInfoUiState
import org.jetbrains.compose.resources.stringResource

/** TV 可展开左侧导航栏的默认尺寸. */
object TvNavigationRailDefaults {
    /**
     * 收起态占位宽度 (= start 16 + 图标 32): 调用方据此把内容右移让开收起的图标列.
     * 取 48dp 使内容左缘与详情页内容 (TV contentHorizontalPadding = 48dp) 对齐.
     */
    val CollapsedWidth = 48.dp
}

/** 侧边栏单个条目: 图标 + 文字, [selected] 时以次要容器色标记当前项. */
@Immutable
data class TvNavRailItem(
    val icon: ImageVector,
    val label: String,
    /** true 时焦点进入侧边栏总是落到该条目上 (整栏至多标记一个, 如"探索"). */
    val defaultFocus: Boolean = false,
    /** 非 null 时把此 FocusRequester 挂到该条目 (如初始/切页后把焦点落到当前项). */
    val focusRequester: FocusRequester? = null,
    /**
     * true 时点击后**不**清焦点. 默认清是为了切页 (见 [TvRailIconItem] 里的注释);
     * 不切页、只是就地开个弹窗的条目 (如"一起看") 必须留住焦点, 否则弹窗关掉后
     * 焦点回不到本条目上.
     */
    val keepFocusOnClick: Boolean = false,
    val onClick: () -> Unit,
)

/** 头像的关联动作 (编辑资料/播放记录/退出登录): 焦点在头像簇上时浮现于头像上方. */
@Immutable
data class TvRailAvatarAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * 组装主页与详情页侧边栏共用的条目列表 (搜索 + 主页各 tab + 设置), 两处只差点击行为
 * (主页直接切 tab, 详情页先弹回主页再切). 焦点进入侧边栏时总是落到"探索"上.
 */
@Composable
fun buildTvRailItems(
    onSearch: () -> Unit,
    onNavigateToPage: (MainScreenPage) -> Unit,
    onSettings: () -> Unit,
): List<TvNavRailItem> = buildList {
    add(
        TvNavRailItem(
            icon = Icons.Rounded.Search,
            label = stringResource(Lang.exploration_search),
            onClick = onSearch,
        ),
    )
    for (entry in MainScreenPage.visibleEntries) {
        add(
            TvNavRailItem(
                icon = entry.getIcon(),
                label = entry.getText(),
                defaultFocus = entry == MainScreenPage.Exploration,
                onClick = { onNavigateToPage(entry) },
            ),
        )
    }
    add(
        TvNavRailItem(
            icon = Icons.Rounded.Settings,
            label = stringResource(Lang.settings),
            onClick = onSettings,
        ),
    )
    // "一起看": 只在设置里打开了功能时出现 —— 遥控器上没有可拖的悬浮气泡, 这颗常驻图标
    // 与播放器胶囊行末尾那颗一起承担气泡原本的入口作用, 显隐条件与气泡完全一致.
    val watchTogether = LocalWatchTogetherEntry.current
    if (watchTogether.enabled) {
        add(
            TvNavRailItem(
                icon = Icons.Rounded.SyncAlt,
                label = stringResource(Lang.watch_together_title),
                keepFocusOnClick = true,
                onClick = { watchTogether.open() },
            ),
        )
    }
}

/**
 * TV 可展开左侧导航栏 (主页与详情页共用同一实现):
 * 收起态是一列图标 (头像置顶 + 若干图标条目); 焦点进入后展开为"图标 + 文字"并压一层左缘渐变遮罩,
 * 焦点离开自动收起. 头像点击进入设置的用户信息页 (由 [onAvatarClick] 决定); 未登录时头像退化成
 * 设置里那个默认人物符号 (AccountCircle), 尺寸/对齐与其他图标完全一致.
 *
 * @param selfInfo 头像用户信息; 传 null 则不显示头像/用户名, 但仍保留头像槽位的等高占位,
 *   使其余按钮位置不变 (如详情页不需要头像).
 * @param onExitFocus 非 null 时: 条目上按返回键/右键调用它 (如详情页把焦点送回 Hero 播放按钮),
 *   并吞掉该按键; null 时不拦截 (返回键正常逐层退, 右键交给空间焦点搜索进入右侧内容).
 */
@Composable
fun TvNavigationSideRail(
    selfInfo: SelfInfoUiState?,
    onAvatarClick: () -> Unit,
    items: List<TvNavRailItem>,
    modifier: Modifier = Modifier,
    onExitFocus: (() -> Unit)? = null,
    /** 头像关联动作 (按登录态由调用方组装); 焦点在头像簇上时于其上方浮现这些图标+文字按钮. */
    avatarActions: List<TvRailAvatarAction> = emptyList(),
    /** 展开遮罩面板底色覆盖 (如详情页按封面调色板取色, 使遮罩跟随背景/主题); null 用默认 surface. */
    scrimColor: Color? = null,
) {
    // hasFocus (含子节点): 任一条目聚焦即展开
    var expanded by remember { mutableStateOf(false) }
    // 垂直居中
    Box(modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
        AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
            // 展开底衬: 纯色面板 (取 TV 全屏背景色, 与主壳背景一致, 日夜自适应)
            // + 右缘多色标平滑羽化融入内容.
            // 不再用"加深/变暗"的半屏遮罩 —— 那在浅色下会像一块脏阴影压暗白底与标题;
            // 现在浅色是干净的白/浅灰面板, 深色是干净的深色面板, 都与主背景无缝衔接.
            val panelColor = scrimColor ?: AniThemeDefaults.shellBackgroundColor
            Box(
                Modifier.fillMaxHeight().width(TV_RAIL_SCRIM_WIDTH).background(
                    // 前 ~82% 纯色实心, 末段用多色标近似缓动曲线羽化到透明, 消除竖向明暗切线
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
        // 进入门控: 只有"按左"才能把焦点移进侧边栏 (从上/下/右方向的空间焦点搜索一律取消,
        // 否则详情页最上方按钮按上也会误入); 进入时焦点总是落到 defaultFocus 标记的条目
        // (如"探索"), 不随进入位置变化.
        val enterFocus = remember { FocusRequester() }
        val hasDefaultFocusItem = items.any { it.defaultFocus }
        Column(
            Modifier
                .onFocusChanged { expanded = it.hasFocus }
                .focusProperties {
                    onEnter = {
                        if (requestedFocusDirection == FocusDirection.Left) {
                            if (hasDefaultFocusItem) enterFocus.requestFocus()
                        } else {
                            cancelFocus()
                        }
                    }
                }
                .focusGroup()
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selfInfo != null) {
                TvRailAvatar(selfInfo, expanded, onExitFocus, onAvatarClick, avatarActions)
            } else {
                // 不显示头像时保留等高占位, 使其余按钮位置不变
                Box(Modifier.size(TV_RAIL_ITEM_SIZE))
            }
            for (item in items) {
                TvRailIconItem(
                    icon = item.icon,
                    label = item.label,
                    expanded = expanded,
                    onExitFocus = onExitFocus,
                    focusRequester = if (item.defaultFocus) {
                        enterFocus
                    } else {
                        item.focusRequester
                    },
                    keepFocusOnClick = item.keepFocusOnClick,
                    onClick = item.onClick,
                )
            }
        }
    }
}

/**
 * 侧边栏统一的图标方块: 32dp 容器 + 20dp 字形, 聚焦时主色底 + 反色图标 (带颜色过渡动画).
 * 普通条目 / 头像动作按钮 / 未登录头像共用此视觉.
 */
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
            // 返回键不退出页面, 把焦点还给调用方指定的目标 (如 Hero 播放按钮)
            Key.Back, Key.Escape -> {
                if (event.type == KeyEventType.KeyUp) onExitFocus()
                true
            }

            // 右键直接回目标, 不走空间焦点搜索
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
    selfInfo: SelfInfoUiState,
    expanded: Boolean,
    onExitFocus: (() -> Unit)?,
    onClick: () -> Unit,
    avatarActions: List<TvRailAvatarAction>,
    modifier: Modifier = Modifier,
) {
    val loggedIn = selfInfo.selfInfo != null && selfInfo.isSessionValid != false
    var avatarFocused by remember { mutableStateOf(false) }
    // 头像簇 (头像 + 上方动作按钮) 任一有焦点即展开动作按钮
    var clusterFocused by remember { mutableStateOf(false) }
    // 聚焦高亮: 已登录画圆环 (头像是圆的), 未登录用图标块反色底
    val focusHighlight by animateColorAsState(
        if (avatarFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
    )
    Box(modifier.onFocusChanged { clusterFocused = it.hasFocus }) {
        // 浮现的动作按钮: 用负偏移置于头像正上方, 不占布局 (不推挤其余条目), 焦点离开头像簇即隐藏.
        // 按上键从头像即落到最下面那个按钮 (空间焦点搜索, 因其 bounds 在头像上方).
        if (avatarActions.isNotEmpty()) {
            AnimatedVisibility(
                visible = clusterFocused,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // 测量后向父级上报 0 尺寸 (不占布局): 否则会撑高头像簇, 使垂直居中的整栏
                    // 重新居中导致头像上移. 内容放到头像正上方 (y = -自身高度).
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(0, 0) { placeable.place(0, -placeable.height) }
                    },
            ) {
                Column(
                    Modifier.padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (action in avatarActions) {
                        TvRailAvatarActionButton(action, onExitFocus)
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (loggedIn) {
                // 圆形头像照片, 聚焦画圆环
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
                    // 照片比高亮框 (32dp) 略小并居中, 使聚焦圆环成为其外圈, 不超出高亮尺寸
                    AvatarImage(
                        url = selfInfo.selfInfo?.avatarUrl,
                        modifier = Modifier.size(TV_RAIL_AVATAR_IMAGE_SIZE).clip(CircleShape),
                    )
                }
            } else {
                // 未登录: 退化成默认人物符号图标块 (与其它条目一致的反色高亮)
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
                    if (loggedIn) {
                        selfInfo.selfInfo?.nickname ?: stringResource(Lang.login_sign_in)
                    } else {
                        stringResource(Lang.login_sign_in)
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    softWrap = false,
                )
            }
        }
    }
}

/** 头像上方浮现的单个动作按钮 (图标方块 + 文字), 聚焦反色, 与普通条目视觉一致. */
@Composable
private fun TvRailAvatarActionButton(
    action: TvRailAvatarAction,
    onExitFocus: (() -> Unit)?,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .railExitKeys(onExitFocus)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = action.onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvRailGlyphBox(focused, action.icon)
        Text(
            action.label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            softWrap = false,
        )
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
            // 自绘聚焦指示 (图标方块反色), 关掉默认 indication 避免整行水波.
            // 只保留焦点高亮: 不标记"当前页", 否则聚焦项与当前页两处高亮会误导用户.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    onClick()
                    // 点击后清空焦点, 让 AniAppContent 全局兜底把焦点送入(可能刚切换/弹回的)当前页面
                    // 左上角可聚焦项; 不用 moveFocus(Right): 切页瞬间新内容还没组合出来, moveFocus 会
                    // 落到正在退场的旧页面或失败, 导致丢焦点.
                    // 就地开弹窗的条目不清 (见 TvNavRailItem.keepFocusOnClick).
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

/** 侧边栏展开时的渐变遮罩宽度. */
private val TV_RAIL_SCRIM_WIDTH = 180.dp

/** 单个图标按钮 (聚焦反色方块 / 头像) 的边长. */
private val TV_RAIL_ITEM_SIZE = 32.dp

/** 图标按钮聚焦方块的圆角 (偏方, 不要太圆). */
private val TV_RAIL_ITEM_CORNER = 6.dp

/** 头像照片尺寸: 比高亮框 (32dp) 略小并居中, 使聚焦圆环成为其外圈, 不超出高亮尺寸. */
private val TV_RAIL_AVATAR_IMAGE_SIZE = 24.dp

/** 图标字形尺寸 (32dp 容器 / 20dp 字形). */
private val TV_RAIL_ICON_GLYPH_SIZE = 20.dp
