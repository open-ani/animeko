/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.comment.BangumiStickers
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.widgets.centeredPanelColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_add_emoji
import me.him188.ani.app.ui.richtext.StickerImage
import me.him188.ani.app.ui.richtext.UIRichElement
import org.jetbrains.compose.resources.stringResource

/** 表情格子的边长 (含四周留白): 一屏放得下八九列, 又不至于小到看不清是哪一枚. */
private val TV_STICKER_CELL_SIZE = 56.dp

/** 表情图本身的边长. 原图最大的也就几十像素, 再放大只会糊. */
private val TV_STICKER_IMAGE_SIZE = 36.dp

/** 左侧表情包列表的宽度 (最长的名字是"Bangumi 娘"). */
private val TV_STICKER_PACK_COLUMN_WIDTH = 132.dp

private const val TV_STICKER_DIALOG_WIDTH_FRACTION = 0.68f

private const val TV_STICKER_DIALOG_HEIGHT_FRACTION = 0.7f

/** 与评论弹窗同一层压暗 (它已经盖在评论弹窗上, 再暗一档就成黑屏了). */
private val TV_STICKER_SCRIM_COLOR = Color.Black.copy(alpha = 0.38f)

/**
 * 表情选择器: 左边选包, 右边选表情, 确定即把表情代码插进输入框.
 *
 * 目录直接用 [BangumiStickers.packs] (六个官方表情包 + 颜文字, 约 470 枚): 它本来就是按
 * "代码 -> 图片地址"的规则算出来的, 加新包只加一项, 不必往安装包里塞图, 也不必另列一份清单.
 * 图走 Coil 现拉 (见 [StickerImage]) —— 随包的那 125 张只是最早的一部分, 且都是静态图.
 *
 * 手机端那套是贴在输入框下方的一整片 FlowRow (点哪枚就是哪枚). 遥控器上不能这么摆: 近五百枚
 * 挤在一片里, 方向键要按上百下才走得到底, 也没法一眼知道自己在哪一包. 所以拆成"包 + 网格",
 * 焦点落在包名上即换包 (与播放器胶囊行浮出面板同一手势), 右键进网格挑.
 *
 * 挑中即关: 遥控器上连挑几枚的代价是每次都要重新走一遍网格, 不如让用户看着输入框决定要不要
 * 再来一枚. 选中的包由调用方记着 ([selectedPackIndex]), 下次开还在那一包.
 */
@Composable
internal fun TvStickerPicker(
    selectedPackIndex: Int,
    onSelectPack: (Int) -> Unit,
    onPick: (token: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val packs = BangumiStickers.packs
    val pack = packs.getOrElse(selectedPackIndex) { packs.first() }
    val gridState = rememberLazyGridState()
    val firstCellFocusRequester = remember { FocusRequester() }
    var firstCellFocused by remember { mutableStateOf(false) }
    val packFocusRequesters = remember(packs.size) { List(packs.size) { FocusRequester() } }
    var focusedPackIndex by remember { mutableIntStateOf(0) }
    var focusedCellIndex by remember { mutableIntStateOf(0) }

    // 初始焦点落网格首格, 不落包列表: 多数时候用户要的就是当前这一包里的某一枚,
    // 换包是少数情形 (左键一步就到)
    LaunchedEffect(Unit) {
        resolveFocusRepeatedly(attempts = 20, arrived = { firstCellFocused }) {
            runCatching { firstCellFocusRequester.requestFocus() }
        }
    }
    // 换包后网格回到顶部: 不归零的话新包会从上一包停的那个位置开始显示 (短包直接是空白)
    LaunchedEffect(selectedPackIndex) {
        gridState.scrollToItem(0)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(TV_STICKER_SCRIM_COLOR)
            // 焦点锁在选择器内: 不锁的话网格边缘的方向键会滑到底下评论弹窗的按钮上
            // (选择器还挡着, 看不见焦点在哪), 而返回键只会关选择器.
            // onExit 要挂在焦点组上才拦得住, 与评论弹窗同一副写法
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier
                .fillMaxWidth(TV_STICKER_DIALOG_WIDTH_FRACTION)
                .fillMaxHeight(TV_STICKER_DIALOG_HEIGHT_FRACTION),
            shape = RoundedCornerShape(20.dp),
            color = centeredPanelColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Text(
                    stringResource(Lang.comment_add_emoji),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxSize()) {
                    // 包列表自己消费上下键 (与新番时间表的日期行同一套路, 见 TvSchedulePage):
                    // 交给默认的空间搜索的话, 首项按上、末项按下会跳出选择器 —— 而选择器盖在
                    // 评论弹窗与评论面板之上, 焦点落到底下那些看不见的卡片上就等于卡死.
                    // 列表可滚 + 逐项显式落点, 顺带也解决了"包多到一屏放不下"时的滚动
                    Column(
                        Modifier
                            .width(TV_STICKER_PACK_COLUMN_WIDTH)
                            .fillMaxHeight()
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val index = focusedPackIndex
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        // 到顶消费掉不动 (聚焦项会被 focusable 自己滚进视口)
                                        if (index > 0) {
                                            runCatching { packFocusRequesters[index - 1].requestFocus() }
                                        }
                                        true
                                    }

                                    Key.DirectionDown -> {
                                        if (index < packs.lastIndex) {
                                            runCatching { packFocusRequesters[index + 1].requestFocus() }
                                        }
                                        true
                                    }

                                    // 左边没有东西, 也消费掉: 交回空间搜索会跑出选择器
                                    Key.DirectionLeft -> true

                                    // 右键交给空间搜索进网格 (由 focusRestorer 落回上次那一格)
                                    else -> false
                                }
                            }
                            .verticalScroll(rememberScrollState()),
                    ) {
                        packs.forEachIndexed { index, item ->
                            TvStickerPackItem(
                                name = item.name,
                                selected = index == selectedPackIndex,
                                onFocused = {
                                    focusedPackIndex = index
                                    onSelectPack(index)
                                },
                                modifier = Modifier.focusRequester(packFocusRequesters[index]),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(TV_STICKER_CELL_SIZE),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            // 网格四周同样不留出口 (理由同上). 只拦"那个方向确实没有格子"的情形,
                            // 其余照常交给网格自己的空间搜索 —— 它会把目标滚进视口
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val columns = gridState.layoutInfo.visibleItemsInfo
                                    .maxOfOrNull { it.column + 1 }
                                    ?.coerceAtLeast(1) ?: return@onPreviewKeyEvent false
                                val index = focusedCellIndex
                                val lastIndex = pack.tokens.lastIndex
                                when (event.key) {
                                    Key.DirectionUp -> index < columns // 首行
                                    Key.DirectionDown -> index / columns == lastIndex / columns // 末行
                                    // 行末按右: 不拦的话空间搜索会绕到别处 (同 TvSchedulePage 的日期行)
                                    Key.DirectionRight -> index % columns == columns - 1 || index == lastIndex
                                    else -> false // 左键回包列表, 交给空间搜索
                                }
                            }
                            .focusRestorer(),
                    ) {
                        itemsIndexed(pack.tokens, key = { _, token -> token }) { index, token ->
                            TvStickerCell(
                                token = token,
                                onClick = { onPick(token) },
                                modifier = Modifier
                                    .onFocusChanged { if (it.isFocused) focusedCellIndex = index }
                                    .ifThen(index == 0) {
                                        focusRequester(firstCellFocusRequester)
                                            .onFocusChanged { firstCellFocused = it.isFocused }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 表情包名: 聚焦即换包 (不必再按一下确定 —— 换包本来就只是"让右边显示这一包").
 * 当前包在失焦时也保持高亮, 否则焦点一进网格就看不出自己在哪一包了.
 */
@Composable
private fun TvStickerPackItem(
    name: String,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { }, // 聚焦即生效, 点击无额外动作 (但要可点才可聚焦)
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = RoundedCornerShape(10.dp),
        color = when {
            focused -> MaterialTheme.colorScheme.primary
            selected -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> Color.Transparent
        },
        contentColor = if (focused) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Text(
            name,
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一枚表情: 聚焦即主题色实底 (图本身多是深色描边, 白底反而看不出焦点). */
@Composable
private fun TvStickerCell(
    token: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrl = BangumiStickers.imageUrlOf(token) ?: return
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(TV_STICKER_CELL_SIZE)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StickerImage(
                UIRichElement.Annotated.Sticker(id = token, resource = null, imageUrl = imageUrl),
                Modifier.size(TV_STICKER_IMAGE_SIZE),
            )
        }
    }
}
