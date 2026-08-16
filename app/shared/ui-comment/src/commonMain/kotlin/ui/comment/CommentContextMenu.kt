/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_block_user
import me.him188.ani.app.ui.lang.comment_copy_content
import me.him188.ani.app.ui.lang.comment_open_in_bangumi
import me.him188.ani.app.ui.lang.comment_report
import org.jetbrains.compose.resources.stringResource

object CommentContextMenuTestTags {
    const val CopyContent = "CommentContextMenu:copy"
    const val OpenOriginal = "CommentContextMenu:openOriginal"
    const val BlockAuthor = "CommentContextMenu:block"
    const val Report = "CommentContextMenu:report"
}

/**
 * 评论上下文菜单, 对应 Figma 设计 "CommentContextMenu".
 *
 * 由 ⋮ 按钮 / 长按 (移动端) / 右键 (桌面端) 唤出.
 *
 * @param onCopyContent 复制评论内容. 恒显示.
 * @param onOpenOriginal 在来源平台 (Bangumi) 打开. `null` 时隐藏, 仅 Bangumi 源评论应显示.
 * @param onBlockAuthor 拉黑评论作者 (本地屏蔽). `null` 时隐藏.
 * @param onReport 举报评论. `null` 时隐藏, 以 error 色展示.
 */
@Composable
fun CommentContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onCopyContent: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenOriginal: (() -> Unit)? = null,
    onBlockAuthor: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    offset: DpOffset = DpOffset.Zero,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = MaterialTheme.shapes.medium,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(Lang.comment_copy_content)) },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
            modifier = Modifier.testTag(CommentContextMenuTestTags.CopyContent),
            onClick = {
                onDismissRequest()
                onCopyContent()
            },
        )
        if (onOpenOriginal != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Lang.comment_open_in_bangumi)) },
                leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) },
                modifier = Modifier.testTag(CommentContextMenuTestTags.OpenOriginal),
                onClick = {
                    onDismissRequest()
                    onOpenOriginal()
                },
            )
        }
        if (onBlockAuthor != null || onReport != null) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        if (onBlockAuthor != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Lang.comment_block_user)) },
                leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                modifier = Modifier.testTag(CommentContextMenuTestTags.BlockAuthor),
                onClick = {
                    onDismissRequest()
                    onBlockAuthor()
                },
            )
        }
        if (onReport != null) {
            DropdownMenuItem(
                text = { Text(stringResource(Lang.comment_report)) },
                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                modifier = Modifier.testTag(CommentContextMenuTestTags.Report),
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                onClick = {
                    onDismissRequest()
                    onReport()
                },
            )
        }
    }
}
