/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.domain.comment.CommentSendResult
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.widgets.AniFocusActionButton
import me.him188.ani.app.ui.foundation.widgets.centeredPanelColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_ani_only_notice
import me.him188.ani.app.ui.lang.comment_reply
import me.him188.ani.app.ui.lang.comment_reply_unsupported
import me.him188.ani.app.ui.lang.comment_send
import me.him188.ani.app.ui.lang.comment_send_comment
import me.him188.ani.app.ui.lang.comment_send_failed_network
import me.him188.ani.app.ui.lang.comment_send_failed_unknown
import me.him188.ani.app.ui.lang.comment_view_comment
import org.jetbrains.compose.resources.stringResource

/**
 * 弹窗正文块: 文本段与图片按原顺序排列.
 *
 * 只拆到"文本 / 图片"这一层, 不上完整富文本 ([me.him188.ani.app.ui.richtext.RichText]):
 * 那套带遮罩、可点链接、行内表情, 会在引用区里塞进一堆可点击目标, 与"整块单焦点 + 上下键翻页"
 * 的导航模型冲突. 表情按 `[表情]` 压进文本 (与面板卡片一致).
 */
@Immutable
sealed class TvCommentBlock {
    class Text(val text: String) : TvCommentBlock()
    class Image(val url: String) : TvCommentBlock()
}

/**
 * 回复目标: 被回复的那条评论的展示内容 + 发送上下文.
 */
@Immutable
class TvCommentReplyTarget(
    val context: CommentContext,
    val authorName: String,
    val timeText: String,
    val blocks: List<TvCommentBlock>,
    /**
     * 这条评论到底能不能回复. `false` 时弹窗退化成只读 (不出输入框和发送按钮, 顶部给出提示).
     *
     * 能回复的只有 Ani 源且服务端给了 `canReply` 的**主楼**: Bangumi 评论在 Ani 内只读,
     * 楼中回复也没有对应的写接口 (`createEpisodeReply` 只接受主楼 id).
     */
    val canReply: Boolean,
)

/** 回复弹窗宽度占屏比 (TV 上 dp 视口约 960x540, 0.62 约合 600dp). */
private const val TV_REPLY_DIALOG_WIDTH_FRACTION = 0.62f

/** 回复弹窗高度占屏比. */
private const val TV_REPLY_DIALOG_HEIGHT_FRACTION = 0.78f

/** 引用区一次翻页的比例 (占可视高度): 留一点重叠, 免得漏读一行. */
private const val TV_REPLY_QUOTE_PAGE_FRACTION = 0.8f

/** 图片加载完成前的占位高度. */
private val TV_REPLY_IMAGE_PLACEHOLDER_HEIGHT = 160.dp

/** 弹窗外的压暗层: 这层要的就是"把下面的画面按下去", 与主题无关, 用黑. */
private val TV_REPLY_SCRIM_COLOR = Color.Black.copy(alpha = 0.55f)

/**
 * 弹窗内取色一律走配色表, 与其他弹窗 (更换弹幕、评分等) 同一套语言:
 * - 分区块 (引用区 / 输入框) 的底比面板高一档, 浮起来;
 * - 示焦 = 主题色 (描边、光标、按钮实底), 未聚焦是中性色.
 */
private val quoteContainerColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest

private val fieldContainerColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest

/** 未聚焦描边. */
private val idleBorderColor: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

/** 说明文字 (发送去向提示) 与时间戳: 压一档, 不与标题和正文抢. */
private val hintColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * TV 评论回复弹窗.
 *
 * 与手机端的底部 sheet ([me.him188.ani.app.ui.comment.EditComment]) 相比:
 * - 大弹窗居中, 被回复的评论正文在**输入框上方**, 放不下时可用上下键翻 (翻到底再按下键
 *   才进输入框), 而不是把正文挤成两行贴在输入框下面;
 * - 没有右上角关闭按钮, 纯弹窗: 出口只有返回键 (由 TvEpisodeScreen 的唯一按键路由处理);
 * - 焦点锁在弹窗内 (方向键走到边界即取消这次焦点搜索), 不会滑到底下还在场的播放器控件上.
 *
 * 不用真 `Dialog`: 那是独立窗口, 播放器根部的唯一按键路由收不到它的按键, 且本窗口会失去
 * 窗口焦点 —— 与详情层/侧边 sheet 一样, 这里也是同窗口内的全屏 Box.
 */
@Composable
internal fun TvCommentReplyDialog(
    target: TvCommentReplyTarget,
    editorState: CommentEditorState,
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val quoteFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }
    val sendFocusRequester = remember { FocusRequester() }
    var quoteFocused by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val sending by editorState.sending.collectAsStateWithLifecycle()

    // 初始焦点落引用区 (阅读态), 不直接进输入框: 一进来就弹系统键盘会把弹窗下半遮掉,
    // 而用户多半要先看完被回复的那条
    LaunchedEffect(Unit) {
        resolveFocusRepeatedly(attempts = 20, arrived = { quoteFocused }) {
            runCatching { quoteFocusRequester.requestFocus() }
        }
    }
    // 焦点进出输入框时开合软键盘 (TV 上没有物理键盘, 不主动弹就没法打字)
    LaunchedEffect(fieldFocused) {
        if (fieldFocused) keyboard?.show() else keyboard?.hide()
    }

    val send: () -> Unit = send@{
        if (sending || editorState.content.text.isBlank()) return@send
        scope.launch {
            if (editorState.send()) onSent()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(TV_REPLY_SCRIM_COLOR)
            // 焦点锁在弹窗内: 不锁的话引用区按上键/按钮按下键会滑到底下仍在场的胶囊行与
            // 进度条上 (弹窗还挡着, 看不见焦点在哪), 返回键又只会关弹窗
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier
                .fillMaxWidth(TV_REPLY_DIALOG_WIDTH_FRACTION)
                .fillMaxHeight(TV_REPLY_DIALOG_HEIGHT_FRACTION),
            shape = RoundedCornerShape(20.dp),
            // 与其他弹窗同一个底色与内容色 (见 centeredPanelColor / AniCenteredPanelDialog)
            color = centeredPanelColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.fillMaxSize().padding(28.dp)) {
                Text(
                    // 只读态标题不能还写"回复评论": 下面没有输入框, 两者对不上
                    stringResource(if (target.canReply) Lang.comment_reply else Lang.comment_view_comment),
                    // 标题字号与其他居中大弹窗一致 (AniCenteredPanelDialog 的 title 用 titleLarge)
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                // 可回复时给的是与手机端编辑框同一句提示 (评论发到 Ani, Bangumi 评论只读):
                // 这个弹窗是 TV 上唯一的发评论入口, 不写在这里就没有别处能看到.
                // 不可回复时换成"仅可查看", 说明为什么下面没有输入框
                Text(
                    stringResource(
                        if (target.canReply) Lang.comment_ani_only_notice else Lang.comment_reply_unsupported,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = hintColor,
                )
                Spacer(Modifier.height(12.dp))

                // ---- 被回复的评论 (可上下键翻) ----
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // 翻到底 (canScrollForward = false) 才把下键放行, 由 down 指定落点进输入框;
                        // 短评论一按即穿透. 上键对称: 翻到顶再往上没有目标, 焦点组会拦住
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val page = (scrollState.viewportSize * TV_REPLY_QUOTE_PAGE_FRACTION)
                                .coerceAtLeast(1f)
                            when {
                                event.key == Key.DirectionDown && scrollState.canScrollForward -> {
                                    scope.launch { scrollState.animateScrollBy(page) }
                                    true
                                }

                                event.key == Key.DirectionUp && scrollState.canScrollBackward -> {
                                    scope.launch { scrollState.animateScrollBy(-page) }
                                    true
                                }

                                else -> false
                            }
                        }
                        // 只读态下面没有输入框, 不能给 down 指一个未附着的请求器
                        .then(
                            if (target.canReply) {
                                Modifier.focusProperties { down = fieldFocusRequester }
                            } else {
                                Modifier
                            },
                        )
                        .focusRequester(quoteFocusRequester)
                        .onFocusChanged { quoteFocused = it.isFocused }
                        .focusable()
                        .clip(RoundedCornerShape(12.dp))
                        .background(quoteContainerColor)
                        // 聚焦即主题色描边 (与更换弹幕等弹窗同一套示焦)
                        .border(
                            width = if (quoteFocused) 2.dp else 1.dp,
                            color = if (quoteFocused) MaterialTheme.colorScheme.primary else idleBorderColor,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                target.authorName,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                target.timeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = hintColor,
                            )
                        }
                        target.blocks.forEach { block ->
                            Spacer(Modifier.height(8.dp))
                            when (block) {
                                is TvCommentBlock.Text -> Text(
                                    block.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                is TvCommentBlock.Image -> TvCommentQuoteImage(block.url)
                            }
                        }
                    }
                }

                // 只读态到这里就结束: 引用区已经 weight(1f) 撑满剩余高度
                if (!target.canReply) {
                    return@Column
                }

                Spacer(Modifier.height(16.dp))

                // ---- 输入框 ----
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = fieldContainerColor,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    // 聚焦即主题色描边 (与 M3 输入框聚焦态、更换弹幕弹窗一致)
                    border = BorderStroke(
                        width = if (fieldFocused) 2.dp else 1.dp,
                        color = if (fieldFocused) MaterialTheme.colorScheme.primary else idleBorderColor,
                    ),
                ) {
                    BasicTextField(
                        value = editorState.content,
                        onValueChange = { editorState.setContent(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            // 上下键给显式落点: 输入框是多行的, 交给空间搜索会在换行之间
                            // 打转 (文本框自己也会吃掉上下键去挪光标), 走不出去
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        runCatching { quoteFocusRequester.requestFocus() }
                                        true
                                    }

                                    Key.DirectionDown -> {
                                        runCatching { sendFocusRequester.requestFocus() }
                                        true
                                    }

                                    else -> false
                                }
                            }
                            .focusRequester(fieldFocusRequester)
                            .onFocusChanged { fieldFocused = it.isFocused },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        // 光标与 M3 输入框一样用主题色
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        decorationBox = { inner ->
                            if (editorState.content.text.isEmpty()) {
                                Text(
                                    stringResource(Lang.comment_send_comment),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = hintColor,
                                )
                            }
                            inner()
                        },
                    )
                }

                // 发送失败原因 (成功即关窗, 不需要成功提示)
                (editorState.sendResult as? CommentSendResult.Error)?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (error) {
                            CommentSendResult.NetworkError ->
                                stringResource(Lang.comment_send_failed_network)

                            is CommentSendResult.UnknownError ->
                                stringResource(Lang.comment_send_failed_unknown, error.message)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TvReplySendButton(
                        onClick = send,
                        sending = sending,
                        focusRequester = sendFocusRequester,
                        upFocusRequester = fieldFocusRequester,
                    )
                }
            }
        }
    }
}

/**
 * 引用区里的图片: 按引用区宽度整宽显示 (与下面的输入框同宽), 高度按原图比例.
 *
 * 加载完成前给个最小高度占位 —— 固有尺寸未知时高度是 0, 图片到位时整块正文会往下弹一大截,
 * 正在翻页的话会当场跳位.
 *
 * 不做可点放大: 引用区是"整块一个焦点 + 上下键翻页", 多一个可聚焦目标就得重做那套导航.
 */
@Composable
private fun TvCommentQuoteImage(url: String) {
    val context = LocalPlatformContext.current
    var loaded by remember(url) { mutableStateOf(false) }
    AsyncImage(
        model = remember(url, context) {
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(false)
                .build()
        },
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .ifThen(!loaded) { heightIn(min = TV_REPLY_IMAGE_PLACEHOLDER_HEIGHT) }
            .animateContentSize()
            .placeholder(!loaded)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.FillWidth,
        onSuccess = { loaded = true },
        // 失败也要收掉占位: 否则那块 160dp 的骨架屏会一直闪着, 看起来像还在加载
        onError = { loaded = true },
    )
}

/** 发送按钮 (示焦规则见 [AniFocusActionButton]). */
@Composable
private fun TvReplySendButton(
    onClick: () -> Unit,
    sending: Boolean,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
) {
    AniFocusActionButton(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusProperties { up = upFocusRequester },
        loading = sending,
    ) {
        // 文字 + 纸飞机 (与手机端发送按钮同一枚图标, 在文字右侧)
        Text(
            stringResource(Lang.comment_send),
            style = MaterialTheme.typography.labelLarge,
        )
        Icon(
            Icons.AutoMirrored.Rounded.Send,
            contentDescription = null,
            Modifier.size(18.dp),
        )
    }
}
