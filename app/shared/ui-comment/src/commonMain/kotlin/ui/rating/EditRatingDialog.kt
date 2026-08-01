/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.rating

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.foundation.icons.EditSquare
import me.him188.ani.app.ui.foundation.theme.adjustHsv
import me.him188.ani.app.ui.foundation.widgets.AniCenteredPanelDialog
import me.him188.ani.app.ui.foundation.widgets.AniFocusActionButton
import me.him188.ani.app.ui.foundation.widgets.dismissDialogButton
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.rating_comment_hint
import me.him188.ani.app.ui.lang.rating_comment_label
import me.him188.ani.app.ui.lang.rating_comment_optional
import me.him188.ani.app.ui.lang.rating_discard
import me.him188.ani.app.ui.lang.rating_discard_edit_message
import me.him188.ani.app.ui.lang.rating_discard_edit_title
import me.him188.ani.app.ui.lang.rating_edit_title
import me.him188.ani.app.ui.lang.rating_private_only
import me.him188.ani.app.ui.lang.rating_score_class_average
import me.him188.ani.app.ui.lang.rating_score_class_bad
import me.him188.ani.app.ui.lang.rating_score_class_highly_recommended
import me.him188.ani.app.ui.lang.rating_score_class_legendary_caution
import me.him188.ani.app.ui.lang.rating_score_class_masterpiece
import me.him188.ani.app.ui.lang.rating_score_class_okay
import me.him188.ani.app.ui.lang.rating_score_class_poor
import me.him188.ani.app.ui.lang.rating_score_class_recommended
import me.him188.ani.app.ui.lang.rating_score_class_terrible_caution
import me.him188.ani.app.ui.lang.rating_score_class_very_bad
import me.him188.ani.app.ui.lang.settings_danmaku_confirm
import me.him188.ani.app.ui.lang.settings_media_source_continue_editing
import me.him188.ani.app.ui.lang.settings_mediasource_cancel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max

@Stable
class RatingEditorState(
    /** 打开弹窗时**已确认**的评分 (0 = 还没评过); 遥控器形态的星星行要把它衬在下面. */
    val initialScore: Int, // 0 if not rated
    initialComment: String,
    initialIsPrivate: Boolean,
) {
    var score by mutableIntStateOf(initialScore)
    var comment by mutableStateOf(initialComment)
    var isPrivate by mutableStateOf(initialIsPrivate)

    val hasModified by derivedStateOf {
        score != initialScore || comment != initialComment
    }
    val hasModifiedComment by derivedStateOf {
        comment != initialComment
    }
}

class RateRequest(
    val score: Int,
    val comment: String,
    val isPrivate: Boolean,
)

@Composable
fun RatingEditorDialog(
    state: RatingEditorState,
    onDismissRequest: () -> Unit,
    onRate: (RateRequest) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    val discardEditTitle = stringResource(Lang.rating_discard_edit_title)
    val discardEditMessage = stringResource(Lang.rating_discard_edit_message)
    val discardText = stringResource(Lang.rating_discard)
    val continueEditingText = stringResource(Lang.settings_media_source_continue_editing)
    val editRatingText = stringResource(Lang.rating_edit_title)
    val confirmText = stringResource(Lang.settings_danmaku_confirm)
    val cancelText = stringResource(Lang.settings_mediasource_cancel)
    var showConfirmCancelDialog by remember { mutableStateOf(false) }
    if (showConfirmCancelDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmCancelDialog = false },
            title = { Text(discardEditTitle) },
            text = { Text(discardEditMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmCancelDialog = false
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(discardText)
                }
            },
            // "继续编辑" = 关掉这个确认框回去接着写, 遥控器上返回键就是这个意思
            dismissButton = dismissDialogButton(continueEditingText) { showConfirmCancelDialog = false },
        )
    }
    val focusManager = LocalFocusManager.current

    // 遥控器形态: 换成居中大弹窗 (可用方向键调分/写评价), 而不是手机那套 AlertDialog ——
    // 星星只能点、按钮挤在角上, 遥控器上既选不动分也看不清焦点
    if (LocalAniUiBehavior.current.panelsAsCenteredDialogs) {
        TvRatingEditorDialog(
            state = state,
            title = editRatingText,
            confirmText = confirmText,
            isLoading = isLoading,
            onRate = onRate,
            onDismissRequest = {
                // 写过评价才拦一道 (与手机端"取消"按钮同一判据): 只调了分数就直接关
                if (state.hasModifiedComment) showConfirmCancelDialog = true else onDismissRequest()
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.EditSquare, null) },
        title = { Text(editRatingText) },
        text = {
            RatingEditor(
                state.score, { state.score = it },
                state.comment, { state.comment = it },
                state.isPrivate, { state.isPrivate = it },
                enabled = !isLoading,
            )
        },
        confirmButton = {
            if (isLoading) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            } else {
                TextButton(
                    onClick = {
                        onRate(RateRequest(state.score, state.comment, state.isPrivate))
                    },
                ) {
                    Text(confirmText)
                }
            }
        },
        dismissButton = {
            TextButton(
                {
                    if (state.hasModifiedComment) {
                        showConfirmCancelDialog = true
                    } else {
                        onDismissRequest()
                    }
                },
            ) {
                Text(cancelText)
            }
        },
        properties = DialogProperties(
            // 当有修改之后必须点击 "取消" 才能关闭
            dismissOnBackPress = !state.hasModified,
            dismissOnClickOutside = !state.hasModified,
        ),
        modifier = modifier
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                focusManager.clearFocus() // 点击编辑框外面关闭键盘
            },
    )
}

// ============================ 遥控器形态 (居中大弹窗) ============================

/** TV 评分弹窗宽度占屏比 (小屏兜底; 实际宽度多半由 [TV_RATING_DIALOG_MAX_WIDTH] 决定). */
private const val TV_RATING_DIALOG_WIDTH_FRACTION = 0.55f

/**
 * TV 评分弹窗宽度上限: 刚好裹住星星行 (10 × 28dp + 9 × 4dp = 316dp, 加面板左右各 24dp 内边距),
 * 与手机端那个按内容定宽的对话框看起来一致. 再宽只是星星两边空出一大片.
 */
private val TV_RATING_DIALOG_MAX_WIDTH = 380.dp

/** TV 评分弹窗高度占屏比. */
private const val TV_RATING_DIALOG_HEIGHT_FRACTION = 0.8f

/**
 * 评分弹窗的遥控器形态: 居中大弹窗, 全部控件可用方向键上下走一遍
 * (星星行 → 评价正文 → 仅自己可见 → 确认).
 *
 * 版式照手机端 [AlertDialog] 版来: 图标 + 标题居中, 下面依次是居中的分数/评价词、星星行、
 * 评价正文、仅自己可见. 差别只在遥控器必须的那几处:
 * - 星星是**一整行一个焦点目标**, 左右键调分 —— 十颗星各自可聚焦的话遥控器要按十几下,
 *   而且哪颗星上有焦点在电视上根本看不出来;
 * - 示焦一律靠**换高亮色**而不是套框 (星星换色、"仅自己可见"文字换色): 框会把这一栏割成
 *   一块独立的区域, 与手机端版式差得远, 而且十颗星外面套框在电视上比换色还难看出焦点;
 * - 正文输入框的上下键给显式落点 (多行输入框自己会吃掉上下键去挪光标, 走不出去);
 * - 没有"取消"按钮: 返回键就是取消 (与 TV 上其他弹窗一致, 出口只有一个);
 * - 关闭之后由打开它的按钮负责把焦点收回 (见 Modifier.restoreFocusAfter) —— 弹窗是独立
 *   窗口, 关掉后下层窗口不保证还能把焦点还给原来那个节点.
 */
@Composable
private fun TvRatingEditorDialog(
    state: RatingEditorState,
    title: String,
    confirmText: String,
    isLoading: Boolean,
    onRate: (RateRequest) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val scoreLabels = rememberRatingScoreLabels()
    val starsFocusRequester = remember { FocusRequester() }
    val commentFocusRequester = remember { FocusRequester() }
    val privateFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }
    var starsFocused by remember { mutableStateOf(false) }

    // 进来焦点落星星行: 打开这个弹窗的目的九成是调分
    LaunchedEffect(Unit) {
        resolveFocusRepeatedly(attempts = 20, arrived = { starsFocused }) {
            runCatching { starsFocusRequester.requestFocus() }
        }
    }

    AniCenteredPanelDialog(
        onDismissRequest = onDismissRequest,
        // 图标 + 标题居中 (手机端 AlertDialog 的 icon/title 就是这个样子)
        title = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.EditSquare, null)
                Spacer(Modifier.height(8.dp))
                Text(title)
            }
        },
        widthFraction = TV_RATING_DIALOG_WIDTH_FRACTION,
        heightFraction = TV_RATING_DIALOG_HEIGHT_FRACTION,
        maxWidth = TV_RATING_DIALOG_MAX_WIDTH,
    ) {
        // 弹窗高度是按屏比定死的, 内容要正好填满 (否则确认按钮下面空一大片):
        // fillMaxHeight 撑满整个面板, 中间的正文输入框 weight(1f) 吃掉所有多余高度,
        // 按钮行因此贴在面板底部 (只留面板自己那圈内边距)
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 分数 + 评价词 (跟着星星行的左右键实时变), 与标题、星星一样居中
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.score == 0) {
                    RatingScoreText(
                        "—",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // 分数与评价词与手机端同一套取色 (见 scoreColor)
                    RatingScoreText(
                        state.score.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = scoreColor(state.score.toFloat()),
                    )
                    RatingScoreText(
                        remember(state.score, scoreLabels) {
                            renderScoreClass(state.score.toFloat(), scoreLabels)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scoreColor(state.score.toFloat()),
                    )
                }
            }

            TvRatingStarsRow(
                score = state.score,
                confirmedScore = state.initialScore,
                onScoreChange = { if (!isLoading) state.score = it },
                enabled = !isLoading,
                focusRequester = starsFocusRequester,
                downFocusRequester = commentFocusRequester,
                onFocusChanged = { starsFocused = it },
            )

            val commentLabelText = stringResource(Lang.rating_comment_label)
            val commentOptionalText = stringResource(Lang.rating_comment_optional)
            OutlinedTextField(
                state.comment,
                { state.comment = it },
                Modifier
                    .fillMaxWidth()
                    // 吃掉面板里所有多余高度: 评价正文本来就是这里最该给空间的东西
                    .weight(1f)
                    .heightIn(min = 88.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> {
                                runCatching { starsFocusRequester.requestFocus() }
                                true
                            }

                            Key.DirectionDown -> {
                                runCatching { privateFocusRequester.requestFocus() }
                                true
                            }

                            else -> false
                        }
                    }
                    .focusRequester(commentFocusRequester),
                label = { Text(commentLabelText) },
                placeholder = { Text(commentOptionalText) },
                singleLine = false,
                shape = MaterialTheme.shapes.medium,
                readOnly = isLoading,
            )

            TvRatingPrivateToggle(
                isPrivate = state.isPrivate,
                onToggle = { if (!isLoading) state.isPrivate = !state.isPrivate },
                focusRequester = privateFocusRequester,
                downFocusRequester = confirmFocusRequester,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 只有"确认": 取消 = 返回键 (写过评价时仍会问一句是否放弃)
                AniFocusActionButton(
                    onClick = {
                        if (!isLoading) onRate(RateRequest(state.score, state.comment, state.isPrivate))
                    },
                    modifier = Modifier.focusRequester(confirmFocusRequester),
                    loading = isLoading,
                ) {
                    Text(confirmText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * 星星行: 整行一个焦点目标, 左右键加减一分 (可减到 0 = 撤销评分).
 *
 * 不套框. 颜色只有一种 (主题色), 状态靠**同色的深浅** (HSV 里只动饱和度/明度, 色相不变):
 * - 焦点不在这一栏时: 星星就是主题色, 表示"会保存的分数";
 * - 正在改分 (本行聚焦) 时: 新分数换成**深一档**的同色盖在上面, 旧评分 ([confirmedScore])
 *   那截仍是主题色 —— 改低了就露出原来的浅色, 一眼看出"从几分改到几分".
 *
 * 焦点另外还有空心星的一点主色底光 —— 打满十分时没有空心星可看, 深色覆盖本身也说明了焦点在这.
 */
@Composable
private fun TvRatingStarsRow(
    score: Int,
    /** 打开弹窗时已确认的评分; 改分期间衬在下面 (0 = 还没评过). */
    confirmedScore: Int,
    onScoreChange: (Int) -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onScoreChange((score - 1).coerceIn(0, 10))
                        true
                    }

                    Key.DirectionRight -> {
                        onScoreChange((score + 1).coerceIn(0, 10))
                        true
                    }

                    // 确认键 = 往下走 (行内没有可确认的东西, 但用户按了总得有反应)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        runCatching { downFocusRequester.requestFocus() }
                        true
                    }

                    else -> false
                }
            }
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .focusable(interactionSource = interactionSource)
            .padding(vertical = 8.dp),
        // 与标题、分数一样居中
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val primary = MaterialTheme.colorScheme.primary
        /** 焦点不在这一栏时看到的就是这个: 主题色的星星 = 当前会保存的分数. */
        val confirmedColor = primary
        /**
         * 正在改分 (本行聚焦) 时新分数用的色: 同一个色**加深一档** (饱和度提上去、明度压下来),
         * 盖在旧评分上面, 改低了那截就露出旧评分原本的主题色.
         *
         * 往深走而不是掺白: 深色主题下的主色本身就是高明度低饱和的淡色, 再往白走直接就成了白,
         * 看不出还是同一个颜色.
         */
        val pendingColor = if (focused) {
            primary.adjustHsv(saturationFactor = 1.8f, valueFactor = 0.72f)
        } else {
            primary
        }
        // 空心星平时几乎不可见, 聚焦时点上一点主色底光
        val emptyColor = if (focused) {
            primary.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        }
        repeat(10) { i ->
            val inPending = score >= i + 1
            // 旧评分只在改分期间衬着: 离开这一栏后星星就是"会保存的分数"本身, 不再留残影
            val inConfirmed = focused && confirmedScore >= i + 1
            val color = when {
                inPending -> pendingColor
                inConfirmed -> confirmedColor
                else -> emptyColor
            }
            CompositionLocalProvider(LocalContentColor provides color) {
                // 尺寸不跟着焦点变: 星星行的高度一变, 下面的正文框会跟着跳一下
                Icon(
                    if (inPending || inConfirmed) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = null,
                    Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * "仅自己可见": 整行可聚焦, 确认键切换 (Checkbox 本身在遥控器上太小, 也看不出焦点).
 *
 * 同样不套框: 聚焦时文字换成主色并加粗一档 (勾选框跟着 [LocalContentColor] 一起变).
 */
@Composable
private fun TvRatingPrivateToggle(
    isPrivate: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester,
    downFocusRequester: FocusRequester,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.DirectionDown) {
                    runCatching { downFocusRequester.requestFocus() }
                    return@onPreviewKeyEvent true
                }
                false
            }
            .focusRequester(focusRequester)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // onCheckedChange = null: 勾选框本身不可交互/不可聚焦, 整行才是焦点目标 ——
        // 否则遥控器焦点会落进这个 20dp 的小方块里, 看不出焦点在哪
        val colorScheme = MaterialTheme.colorScheme
        Checkbox(
            checked = isPrivate,
            onCheckedChange = null,
            // 勾选框跟文字一起亮: 聚焦是主色, 没聚焦压成中性色 (只有一个变色的话像是漏了)
            colors = CheckboxDefaults.colors(
                checkedColor = if (focused) colorScheme.primary else colorScheme.onSurfaceVariant,
                checkmarkColor = if (focused) colorScheme.onPrimary else colorScheme.surface,
                uncheckedColor = if (focused) colorScheme.primary else colorScheme.onSurfaceVariant,
            ),
        )
        Text(
            stringResource(Lang.rating_private_only),
            Modifier.padding(start = 8.dp),
            color = if (focused) colorScheme.primary else Color.Unspecified,
            fontWeight = if (focused) FontWeight.Bold else null,
        )
    }
}

@Composable
fun RatingEditor(
    score: Int,
    onScoreChange: (Int) -> Unit,
    comment: String,
    onCommentChange: (String) -> Unit,
    isPrivate: Boolean,
    onIsPrivateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scoreLabels = rememberRatingScoreLabels()
    val commentLabelText = stringResource(Lang.rating_comment_label)
    val commentHintText = stringResource(Lang.rating_comment_hint)
    val commentOptionalText = stringResource(Lang.rating_comment_optional)
    val privateOnlyText = stringResource(Lang.rating_private_only)
    Column(modifier) {
        Column(
            Modifier.align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (score == 0) {
                    RatingScoreText(
                        "",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    RatingScoreText(
                        score.toString(),
                        color = scoreColor(score.toFloat()),
                    )
                    RatingScoreText(
                        remember(score, scoreLabels) { renderScoreClass(score.toFloat(), scoreLabels) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = scoreColor(score.toFloat()),
                    )
                }
            }

            Row {
                TenRatingStars(
                    score,
                    onScoreChange = onScoreChange,
                    scoreLabels = scoreLabels,
                    enabled = enabled,
                )
            }
        }

        Column(
            Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row {
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                OutlinedTextField(
                    comment,
                    onCommentChange,
                    Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    singleLine = false,
                    shape = MaterialTheme.shapes.medium,
                    label = {
                        if (isFocused || comment.isNotEmpty()) {
                            Text(commentLabelText)
                        } else {
                            Text(commentHintText)
                        }
                    },
                    interactionSource = interactionSource,
                    placeholder = { Text(commentOptionalText) },
                    readOnly = !enabled,
                )
            }
        }

        Row(
            Modifier.clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = { onIsPrivateChange(!isPrivate) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isPrivate,
                onCheckedChange = onIsPrivateChange,
                enabled = enabled,
            )
            Text(privateOnlyText)
        }
    }
}

@Composable
private fun TenRatingStars(
    score: Int, // range 1..10
    onScoreChange: (Int) -> Unit,
    scoreLabels: RatingScoreLabels,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides color) {
            val icon = @Composable { index: Int ->
                Icon(
                    if (score >= index) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = renderScoreClass(index.toFloat(), scoreLabels),
                    Modifier
                        .clip(CircleShape)
                        .clickable(
                            remember { MutableInteractionSource() },
                            enabled = enabled,
                            indication = ripple(),
                        ) { onScoreChange(index) }
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val size = max(placeable.width, placeable.height)
                            layout(size, size) {
                                placeable.place((size - placeable.width) / 2, (size - placeable.height) / 2)
                            }
                        }
                        .height(32.dp)
                        .weight(1f),
                )
            }

            repeat(10) {
                icon(it + 1)
            }
        }
    }
}

private data class RatingScoreLabels(
    val terribleCaution: String,
    val veryBad: String,
    val bad: String,
    val poor: String,
    val average: String,
    val okay: String,
    val recommended: String,
    val highlyRecommended: String,
    val masterpiece: String,
    val legendaryCaution: String,
)

@Composable
private fun rememberRatingScoreLabels(): RatingScoreLabels = RatingScoreLabels(
    terribleCaution = stringResource(Lang.rating_score_class_terrible_caution),
    veryBad = stringResource(Lang.rating_score_class_very_bad),
    bad = stringResource(Lang.rating_score_class_bad),
    poor = stringResource(Lang.rating_score_class_poor),
    average = stringResource(Lang.rating_score_class_average),
    okay = stringResource(Lang.rating_score_class_okay),
    recommended = stringResource(Lang.rating_score_class_recommended),
    highlyRecommended = stringResource(Lang.rating_score_class_highly_recommended),
    masterpiece = stringResource(Lang.rating_score_class_masterpiece),
    legendaryCaution = stringResource(Lang.rating_score_class_legendary_caution),
)

@Stable
private fun renderScoreClass(score: Float, labels: RatingScoreLabels): String {
    return when (score) {
        in 0f..1f -> labels.terribleCaution
        in 1f..2f -> labels.veryBad
        in 2f..3f -> labels.bad
        in 3f..4f -> labels.poor
        in 4f..5f -> labels.average
        in 5f..6f -> labels.okay
        in 6f..7f -> labels.recommended
        in 7f..8f -> labels.highlyRecommended
        in 8f..9f -> labels.masterpiece
        in 9f..10f -> labels.legendaryCaution
        else -> ""
    }
}

@Composable
fun scoreColor(score: Float): Color {
    return when (score) {
        in 0f..1f -> MaterialTheme.colorScheme.error
        in 1f..4f -> MaterialTheme.colorScheme.onSurface
        in 4f..6f -> MaterialTheme.colorScheme.onSurface
        in 6f..9f -> MaterialTheme.colorScheme.onSurface
        in 9f..10f -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
}


@Composable
@Preview
private fun PreviewEditRatingDialog() {
    ProvideCompositionLocalsForPreview {
        RatingEditorDialog(
            remember {
                RatingEditorState(
                    initialScore = 0,
                    initialComment = "",
                    initialIsPrivate = false,
                )
            },
            onDismissRequest = {},
            onRate = {},
        )
    }
}

@Composable
@Preview
private fun PreviewEditRatingDialogLoading() {
    ProvideCompositionLocalsForPreview {
        RatingEditorDialog(
            remember {
                RatingEditorState(
                    initialScore = 0,
                    initialComment = "",
                    initialIsPrivate = false,
                )
            },
            onDismissRequest = {},
            onRate = {},
            isLoading = true,
        )
    }
}

@Composable
@Preview
private fun PreviewEditRating() {
    ProvideCompositionLocalsForPreview {
        val state = remember {
            RatingEditorState(
                initialScore = 4,
                initialComment = "",
                initialIsPrivate = false,
            )
        }
        Surface {
            RatingEditor(
                score = state.score,
                onScoreChange = { state.score = it },
                comment = state.comment,
                onCommentChange = { state.comment = it },
                isPrivate = state.isPrivate,
                onIsPrivateChange = { state.isPrivate = it },
            )
        }
    }
}

@Composable
@Preview
private fun PreviewEditRatingDisabled() {
    ProvideCompositionLocalsForPreview {
        val state = remember {
            RatingEditorState(
                initialScore = 0,
                initialComment = "",
                initialIsPrivate = false,
            )
        }
        Surface {
            RatingEditor(
                score = state.score,
                onScoreChange = { state.score = it },
                comment = state.comment,
                onCommentChange = { state.comment = it },
                isPrivate = state.isPrivate,
                onIsPrivateChange = { state.isPrivate = it },
                enabled = false,
            )
        }
    }
}
