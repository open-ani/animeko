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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import me.him188.ani.app.ui.foundation.focus.restoreFocusAfter
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.widgets.AniFocusActionButton
import me.him188.ani.app.ui.foundation.widgets.centeredPanelColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_add_emoji
import me.him188.ani.app.ui.lang.comment_ani_only_notice
import me.him188.ani.app.ui.lang.comment_edit
import me.him188.ani.app.ui.lang.comment_image_unavailable
import me.him188.ani.app.ui.lang.comment_new_comment
import me.him188.ani.app.ui.lang.comment_preview
import me.him188.ani.app.ui.lang.comment_rendering
import me.him188.ani.app.ui.lang.comment_reply
import me.him188.ani.app.ui.lang.comment_reply_unsupported
import me.him188.ani.app.ui.lang.comment_send
import me.him188.ani.app.ui.lang.comment_send_comment
import me.him188.ani.app.ui.lang.comment_send_failed_network
import me.him188.ani.app.ui.lang.comment_send_failed_unknown
import me.him188.ani.app.ui.lang.comment_view_comment
import me.him188.ani.app.ui.richtext.StickerImage
import me.him188.ani.app.ui.richtext.UIRichElement
import org.jetbrains.compose.resources.stringResource

/**
 * 弹窗正文块: 文本段与图片按原顺序排列.
 *
 * 只拆到"文本 / 图片"这一层, 不上完整富文本 ([me.him188.ani.app.ui.richtext.RichText]):
 * 那套带遮罩、可点链接, 会在引用区里塞进一堆可点击目标, 与"整块单焦点 + 上下键翻页"
 * 的导航模型冲突. 表情不占焦点, 所以按行内小图出图 (见 [TvInlineText], 与面板卡片一致).
 */
@Immutable
sealed class TvCommentBlock {
    class Text(val text: TvInlineText) : TvCommentBlock()
    class Image(val url: String) : TvCommentBlock()
}

/**
 * 弹窗的目标: 发送上下文 + 被回复的那条评论 (发表新评论时没有).
 */
@Immutable
class TvCommentReplyTarget(
    val context: CommentContext,
    /**
     * 被回复的那条评论; `null` = 发表本集评论 (主楼), 弹窗里不出引用区, 焦点直接落输入框.
     */
    val quoted: TvQuotedComment? = null,
    /**
     * 这条评论到底能不能回复. `false` 时弹窗退化成只读 (不出输入框和发送按钮, 顶部给出提示).
     *
     * 能回复的只有 Ani 源且服务端给了 `canReply` 的**主楼**: Bangumi 评论在 Ani 内只读,
     * 楼中回复也没有对应的写接口 (`createEpisodeReply` 只接受主楼 id).
     * 发表新评论走的是 `createEpisodeComment`, 恒可写.
     */
    val canReply: Boolean = true,
)

/** 引用区里展示的那条评论. */
@Immutable
class TvQuotedComment(
    val authorName: String,
    val timeText: String,
    val blocks: List<TvCommentBlock>,
    /**
     * 别人给这条评论贴的表情 (Bangumi 的"回应"), 按数量降序. 只展示不可点 —— TV 上贴表情
     * 得先做一套选表情的导航, 而看别人贴了什么本身就是评论的一部分信息.
     */
    val reactions: List<TvCommentReaction> = emptyList(),
)

/** 一枚回应: 一个表情 + 贴的人数. */
@Immutable
class TvCommentReaction(
    val sticker: UIRichElement.Annotated.Sticker,
    val count: Int,
)

/** 回复弹窗宽度占屏比 (TV 上 dp 视口约 960x540, 0.62 约合 600dp). */
private const val TV_REPLY_DIALOG_WIDTH_FRACTION = 0.62f

/** 回复弹窗高度占屏比. */
private const val TV_REPLY_DIALOG_HEIGHT_FRACTION = 0.78f

/** 引用区一次翻页的比例 (占可视高度): 留一点重叠, 免得漏读一行. */
private const val TV_REPLY_QUOTE_PAGE_FRACTION = 0.8f

/** 图片加载完成前的占位高度. */
private val TV_REPLY_IMAGE_PLACEHOLDER_HEIGHT = 160.dp

/** 正文与回应行之间的间距. */
private val TV_REPLY_REACTION_GAP = 10.dp

/** 回应里表情图的边长 (与正文行内表情差不多大, 一眼能认出是哪一枚). */
private val TV_REPLY_REACTION_STICKER_SIZE = 22.dp

/**
 * 弹窗外的压暗层: 这层要的就是"把下面的画面按下去", 与主题无关, 用黑.
 *
 * 别只看这个数: 弹窗开着时控制层与评论面板都留在下面 (见调用处), 它自己的上/下渐变 scrim
 * 还要再叠一层, 屏幕上下缘的实际暗度是 `1-(1-本值)(1-那层)`. 所以这层给得比一般弹窗遮罩松.
 */
private val TV_REPLY_SCRIM_COLOR = Color.Black.copy(alpha = 0.38f)

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
 * 回应胶囊的底: 在引用区的底色上再蒙一层内容色, 比引用区**浅**一档.
 *
 * 不用 surfaceContainer 那一档档的容器色: 引用区用的已经是最亮的 surfaceContainerHighest,
 * 往下取任何一档在深色配色里都是更暗 (surfaceContainerLow 直接黑成一块), 而 surfaceBright
 * 与 highest 只差几个灰度、看不出层次. 蒙一层半透明的 onSurface 才能保证"比底下那层浅".
 */
private val reactionChipColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

/**
 * TV 评论弹窗: 回复某条评论, 或发表本集评论 (`target.quoted == null`, 见 [TvCommentReplyTarget]).
 *
 * 发表新评论没有引用区, 弹窗高度按内容收 (撑满 78% 屏高的话输入框会吊在一大片空白上面),
 * 初始焦点也直接落输入框 —— 那时没有"先看完被回复的那条"这一步.
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
    /**
     * 引用区聚焦时按左右键: 原地翻到相邻的一条评论 (`delta` = -1/+1), 由调用方换 [target]
     * 并同步滚动背后的评论面板. 到端点等无效场合由调用方忽略.
     *
     * 输入框里有草稿时不发这个回调 (换一条评论会连草稿一起清掉, 见按键处理).
     *
     * 用左右键而不是上下键: 上下键在这个弹窗里是"翻长评论正文 / 翻到底进输入框", 抢不得.
     */
    onNavigate: ((delta: Int) -> Unit)? = null,
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

    val quoted = target.quoted
    // 初始焦点落引用区 (阅读态), 不直接进输入框: 一进来就弹系统键盘会把弹窗下半遮掉,
    // 而用户多半要先看完被回复的那条. 发表新评论没有引用区, 直接进输入框
    LaunchedEffect(Unit) {
        if (quoted == null) {
            resolveFocusRepeatedly(attempts = 20, arrived = { fieldFocused }) {
                runCatching { fieldFocusRequester.requestFocus() }
            }
            return@LaunchedEffect
        }
        resolveFocusRepeatedly(attempts = 20, arrived = { quoteFocused }) {
            runCatching { quoteFocusRequester.requestFocus() }
        }
    }
    // 焦点进出输入框时开合软键盘 (TV 上没有物理键盘, 不主动弹就没法打字)
    LaunchedEffect(fieldFocused) {
        if (fieldFocused) keyboard?.show() else keyboard?.hide()
    }
    // 左右键原地换了一条评论 (见 [onNavigate]): 正文回到顶部. 不归零的话新评论会从上一条
    // 停在的那个像素位置开始显示 —— 短评论直接看起来是空白
    LaunchedEffect(target) {
        scrollState.scrollTo(0)
    }

    val send: () -> Unit = send@{
        if (sending || editorState.content.text.isBlank()) return@send
        scope.launch {
            if (editorState.send()) onSent()
        }
    }

    // 预览: 与手机端同一套 (renderPreview 把 BBCode 渲染成 UIRichText), 只是渲染结果改用
    // 引用区那套逐块渲染 —— RichText 会在里面塞进一堆可点链接, 与"整块单焦点"的导航模型冲突
    val previewing = editorState.previewing
    LaunchedEffect(previewing) {
        if (previewing) editorState.renderPreview()
    }
    val previewBlocks = editorState.previewContent?.toCommentBlocks()
    val stickerPickerOpen = editorState.showStickerPanel
    // 选中的表情包: 记在弹窗这一层, 关掉选择器再开还在原来那一包
    var stickerPackIndex by remember { mutableIntStateOf(0) }

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
                // 没有引用区就按内容收高: 撑满屏高的话输入框和发送按钮会吊在一大片空白下面
                .then(if (quoted != null) Modifier.fillMaxHeight(TV_REPLY_DIALOG_HEIGHT_FRACTION) else Modifier),
            shape = RoundedCornerShape(20.dp),
            // 与其他弹窗同一个底色与内容色 (见 centeredPanelColor / AniCenteredPanelDialog)
            color = centeredPanelColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (quoted != null) Modifier.fillMaxHeight() else Modifier)
                    .padding(28.dp),
            ) {
                Text(
                    stringResource(
                        when {
                            quoted == null -> Lang.comment_new_comment
                            // 只读态标题不能还写"回复评论": 下面没有输入框, 两者对不上
                            target.canReply -> Lang.comment_reply
                            else -> Lang.comment_view_comment
                        },
                    ),
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

                // ---- 被回复的评论 (可上下键翻); 发表新评论时整块不出 ----
                if (quoted != null) Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // 翻到底 (canScrollForward = false) 才把下键放行, 由 down 指定落点进输入框;
                        // 短评论一按即穿透. 上键对称: 翻到顶再往上没有目标, 焦点组会拦住.
                        //
                        // 左右键交给 [onNavigate] 换相邻评论: 引用区里左右无处可去, 原本只是被
                        // 焦点组拦住. 恒返回 true (没给回调时也吞掉), 免得焦点飘出弹窗
                        .onPreviewKeyEvent { event ->
                            // 焦点搜索只发生在 KeyDown, 所以 KeyUp 一律放行即可
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val page = (scrollState.viewportSize * TV_REPLY_QUOTE_PAGE_FRACTION)
                                .coerceAtLeast(1f)
                            // 有草稿就不翻: 调用方换 target 时会按新评论重开编辑态
                            // ([CommentEditorState.startEdit] 一换 target 就清空输入框),
                            // 在软键盘上敲了半天的字会无声消失, 翻回来也只是再覆盖一次空串.
                            // 键仍然吞掉 (下同), 免得焦点飘出弹窗
                            val draftEmpty = editorState.content.text.isBlank()
                            when {
                                event.key == Key.DirectionDown && scrollState.canScrollForward -> {
                                    scope.launch { scrollState.animateScrollBy(page) }
                                    true
                                }

                                event.key == Key.DirectionUp && scrollState.canScrollBackward -> {
                                    scope.launch { scrollState.animateScrollBy(-page) }
                                    true
                                }

                                event.key == Key.DirectionLeft -> {
                                    if (draftEmpty) onNavigate?.invoke(-1)
                                    true
                                }

                                event.key == Key.DirectionRight -> {
                                    if (draftEmpty) onNavigate?.invoke(1)
                                    true
                                }

                                else -> false
                            }
                        }
                        // 只读态与预览态下面都没有输入框 (预览把 BasicTextField 整个换掉了),
                        // 不能给 down 指一个未附着的请求器: 焦点搜索命中自定义落点后就不再退回
                        // 空间搜索, 而未附着的请求器只是静默返回 false, 下键当场变死键.
                        // 不给 down, 空间搜索会自己落到下面的按钮行 (与 [actionUpFocus] 对称)
                        .then(
                            if (target.canReply && !previewing) {
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
                        .padding(16.dp),
                ) {
                    // 回应行钉在框底 (不随正文滚): 正文可能很长, 跟在末尾就得翻到底才看得见,
                    // 而它是"这条评论收到了什么反响"的概览, 该和作者行一样一直在
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(scrollState)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    quoted.authorName,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    quoted.timeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = hintColor,
                                )
                            }
                            quoted.blocks.forEach { block ->
                                Spacer(Modifier.height(8.dp))
                                when (block) {
                                    is TvCommentBlock.Text -> Text(
                                        block.text.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        inlineContent = tvStickerInlineContent(block.text.stickers),
                                    )

                                    is TvCommentBlock.Image -> TvCommentQuoteImage(block.url)
                                }
                            }
                        }
                        if (quoted.reactions.isNotEmpty()) {
                            Spacer(Modifier.height(TV_REPLY_REACTION_GAP))
                            TvCommentReactionRow(quoted.reactions)
                        }
                    }
                }

                // 只读态到这里就结束: 引用区已经 weight(1f) 撑满剩余高度
                if (!target.canReply) {
                    return@Column
                }

                Spacer(Modifier.height(16.dp))

                // ---- 预览 (与输入框换位, 不是并排): 弹窗里没有第二块地方摆得下 ----
                if (previewing) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = fieldContainerColor,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, idleBorderColor),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            if (previewBlocks == null) {
                                // 渲染是挂起的 (BBCode -> UIRichText), 慢一拍时给个说法
                                Text(
                                    stringResource(Lang.comment_rendering),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = hintColor,
                                )
                            } else {
                                previewBlocks.forEachIndexed { index, block ->
                                    if (index > 0) Spacer(Modifier.height(8.dp))
                                    when (block) {
                                        is TvCommentBlock.Text -> Text(
                                            block.text.text,
                                            style = MaterialTheme.typography.bodyLarge,
                                            inlineContent = tvStickerInlineContent(block.text.stickers),
                                        )

                                        is TvCommentBlock.Image -> TvCommentQuoteImage(block.url)
                                    }
                                }
                            }
                        }
                    }
                } else Surface(
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
                                    // 没有引用区时上键就地吞掉: 焦点留在输入框 (往上没有第二个目标,
                                    // 放行的话会飘出弹窗去撞焦点组的边界)
                                    Key.DirectionUp -> {
                                        if (quoted != null) runCatching { quoteFocusRequester.requestFocus() }
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
                // 预览态下输入框不在场, 上键就没有落点可指 (指一个未附着的请求器不会抛,
                // 只是静默吃掉这次焦点搜索, 上键变死键): 不给 up, 交回空间搜索 —— 预览块
                // 不可聚焦, 于是落到引用区; 没有引用区时焦点组会把这一下拦在弹窗内
                val actionUpFocus = fieldFocusRequester.takeIf { !previewing }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvReplyActionButton(
                        text = stringResource(Lang.comment_add_emoji),
                        icon = Icons.Outlined.SentimentSatisfied,
                        onClick = {
                            // 预览态下没法插字, 先退回编辑态 (手机端是把这些动作禁用掉, 但
                            // 禁用的按钮在遥控器上不可聚焦, 焦点会当场丢在弹窗里)
                            if (previewing) editorState.togglePreview()
                            editorState.toggleStickerPanelState(true)
                        },
                        upFocusRequester = actionUpFocus,
                        // 选择器关掉后焦点还给本按钮: 它抢焦点时本按钮还在场, 但 Compose 不会自己还
                        modifier = Modifier.restoreFocusAfter(stickerPickerOpen),
                    )
                    TvReplyActionButton(
                        text = stringResource(if (previewing) Lang.comment_edit else Lang.comment_preview),
                        icon = if (previewing) Icons.Rounded.Edit else Icons.Rounded.Visibility,
                        onClick = { editorState.togglePreview() },
                        upFocusRequester = actionUpFocus,
                    )
                    TvReplySendButton(
                        onClick = send,
                        sending = sending,
                        focusRequester = sendFocusRequester,
                        upFocusRequester = actionUpFocus,
                    )
                }
            }
        }

        // 表情选择器: 盖在本弹窗之上 (同一个全屏 Box 里的第二个孩子). 返回键先关它再关本弹窗,
        // 由根路由处理 (见 TvEpisodeScreen) —— 它在弹窗之上, 但按键仍走那唯一一条路由
        if (stickerPickerOpen) {
            TvStickerPicker(
                selectedPackIndex = stickerPackIndex,
                onSelectPack = { stickerPackIndex = it },
                onPick = { token ->
                    editorState.insertTextAt(token)
                    // 挑中即关: 见 TvStickerPicker 的注释
                    editorState.toggleStickerPanelState(false)
                },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * 回应行: 一枚表情 + 人数, 一行放不下就折行.
 *
 * 折出来的行往上长 (整块钉在框底, 变高就把上面的正文区压小), 与 bgm 网页端"回应堆在评论下方"
 * 的观感一致. 不设行数上限: 回应只能取自那一包里 Bangumi 放开的二十来枚, 折不出几行.
 *
 * 不可聚焦、不可点: 见 [TvCommentReplyTarget.reactions].
 */
@Composable
private fun TvCommentReactionRow(reactions: List<TvCommentReaction>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        reactions.forEach { reaction ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(reactionChipColor)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StickerImage(
                    reaction.sticker,
                    Modifier.size(TV_REPLY_REACTION_STICKER_SIZE),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    reaction.count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = hintColor,
                )
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
    // 0: 加载中, 1: 出图了, 2: 失败
    var state by remember(url) { mutableIntStateOf(0) }

    // 评论里贴的是外部图床, 挂掉/被删是常态 (实测有评论贴的是已经没有 A 记录的域名).
    // 原来失败后整块塌成 0 高度, 观感是"骨架屏闪一下然后凭空消失", 而卡片上明明写着 [图片];
    // 退化成一行说明: 交代这里原本有张图且它已经取不回来了, 同时不再让正文高度跳变
    if (state == 2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.BrokenImage,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = hintColor,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(Lang.comment_image_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = hintColor,
            )
        }
        return
    }

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
            .ifThen(state != 1) { heightIn(min = TV_REPLY_IMAGE_PLACEHOLDER_HEIGHT) }
            .animateContentSize()
            .placeholder(state == 0)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.FillWidth,
        onSuccess = { state = 1 },
        onError = { state = 2 },
    )
}

/** 上键落点; 传 null = 不指定 (预览态下输入框不在场, 指一个未附着的请求器会静默吃掉这次焦点搜索). */
private fun Modifier.upFocus(requester: FocusRequester?): Modifier =
    if (requester == null) this else focusProperties { up = requester }

/** 弹窗底部那一排里除发送之外的动作 (表情 / 预览): 与发送同一款示焦, 图标在文字左边. */
@Composable
private fun TvReplyActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    upFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    AniFocusActionButton(
        onClick = onClick,
        modifier = modifier.upFocus(upFocusRequester),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 发送按钮 (示焦规则见 [AniFocusActionButton]). */
@Composable
private fun TvReplySendButton(
    onClick: () -> Unit,
    sending: Boolean,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
) {
    AniFocusActionButton(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .upFocus(upFocusRequester),
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
