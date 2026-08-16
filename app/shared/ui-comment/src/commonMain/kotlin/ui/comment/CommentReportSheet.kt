/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_report_cancel
import me.him188.ani.app.ui.lang.comment_report_detail_hint
import me.him188.ani.app.ui.lang.comment_report_reason_harassment
import me.him188.ani.app.ui.lang.comment_report_reason_illegal
import me.him188.ani.app.ui.lang.comment_report_reason_nsfw
import me.him188.ani.app.ui.lang.comment_report_reason_other
import me.him188.ani.app.ui.lang.comment_report_reason_spam
import me.him188.ani.app.ui.lang.comment_report_reason_spoiler
import me.him188.ani.app.ui.lang.comment_report_submit
import me.him188.ani.app.ui.lang.comment_report_subtitle
import me.him188.ani.app.ui.lang.comment_report_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 举报理由分类, 与服务端 `CommentReportReason` 对应.
 */
enum class CommentReportReason {
    /** 垃圾广告或引流 */
    SPAM,

    /** 人身攻击、骚扰 */
    HARASSMENT,

    /** 剧透 */
    SPOILER,

    /** 色情、血腥或令人不适 */
    NSFW,

    /** 违法违规内容 */
    ILLEGAL,

    /** 其他 */
    OTHER,
}

private val CommentReportReason.titleRes: StringResource
    get() = when (this) {
        CommentReportReason.SPAM -> Lang.comment_report_reason_spam
        CommentReportReason.HARASSMENT -> Lang.comment_report_reason_harassment
        CommentReportReason.SPOILER -> Lang.comment_report_reason_spoiler
        CommentReportReason.NSFW -> Lang.comment_report_reason_nsfw
        CommentReportReason.ILLEGAL -> Lang.comment_report_reason_illegal
        CommentReportReason.OTHER -> Lang.comment_report_reason_other
    }

/**
 * 举报评论弹层, 对应 Figma 设计 "ReportSheet".
 *
 * 移动端 (紧凑宽度) 为 bottom sheet, 桌面端为对话框.
 *
 * @param snapshotText 被举报评论的快照预览, 一般为 "作者名：评论内容".
 * @param onSubmit 提交举报. 参数为选择的理由与补充说明 (可能为空字符串). 调用方负责关闭弹层与提示.
 */
@Composable
fun CommentReportSheet(
    snapshotText: String,
    onSubmit: (reason: CommentReportReason, detail: String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (currentWindowAdaptiveInfo1().isWidthCompact) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
        ) {
            CommentReportSheetContent(
                snapshotText = snapshotText,
                onSubmit = onSubmit,
                onCancel = onDismissRequest,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp),
            )
        }
    } else {
        Dialog(onDismissRequest = onDismissRequest) {
            Surface(
                modifier = modifier.widthIn(max = 400.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                CommentReportSheetContent(
                    snapshotText = snapshotText,
                    onSubmit = onSubmit,
                    onCancel = onDismissRequest,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

@Composable
internal fun CommentReportSheetContent(
    snapshotText: String,
    onSubmit: (reason: CommentReportReason, detail: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedReason by rememberSaveable { mutableStateOf<CommentReportReason?>(null) }
    var detail by rememberSaveable { mutableStateOf("") }

    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(Lang.comment_report_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Lang.comment_report_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        // 被举报评论快照
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = snapshotText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))

        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
        ) {
            CommentReportReason.entries.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedReason == reason,
                        onClick = null,
                    )
                    Text(
                        text = stringResource(reason.titleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))

        // 补充说明 (选填), 与理由文本左对齐
        Row(Modifier.fillMaxWidth().padding(start = 30.dp)) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                BasicTextField(
                    value = detail,
                    onValueChange = { detail = it.take(1000) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    decorationBox = { innerTextField ->
                        if (detail.isEmpty()) {
                            Text(
                                text = stringResource(Lang.comment_report_detail_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(Lang.comment_report_cancel))
            }
            Button(
                onClick = {
                    selectedReason?.let { onSubmit(it, detail.trim()) }
                },
                enabled = selectedReason != null,
            ) {
                Text(stringResource(Lang.comment_report_submit))
            }
        }
    }
}
