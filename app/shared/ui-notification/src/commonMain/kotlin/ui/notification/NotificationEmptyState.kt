/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 没有内容可显示时的提示: 圆形底色中的图标, 下面是标题和说明, 文本居中. 读屏时作为一个整体朗读.
 *
 * 它只是提示本身, 由调用方放在可用区域的中央: 列表中用 [notificationEmptyStateItem], 详情栏等区域中用 [Box] 居中.
 *
 * @param title 标题. 为 `null` 时 [supportingText] 是唯一的文本, 使用更大的字号, 例如宽屏中尚未选择条目时的提示.
 * @param modifier 作用于提示本身 (图标和文本), 测试标签也应加在这里.
 * @param supportingText 标题下方的说明.
 */
@Composable
internal fun NotificationEmptyState(
    icon: ImageVector,
    title: String?,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Column(
        modifier
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .widthIn(max = 320.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .testTag(NotificationTestTags.EMPTY_STATE_ICON)
                .size(64.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(32.dp), tint = colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        if (title != null) {
            Text(
                title,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
                style = typography.titleMedium,
            )
        }
        if (supportingText != null) {
            if (title != null) {
                Spacer(Modifier.height(4.dp))
            }
            Text(
                supportingText,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = if (title != null) typography.bodyMedium else typography.bodyLarge,
            )
        }
    }
}

/**
 * 列表为空时的 [NotificationEmptyState], 在列表的可见区域 (不含列表的内边距) 中居中.
 * 可见区域放不下时, 这一项随内容变高, 从列表顶部开始并可以滚动.
 *
 * 提示是列表中的一项, 所以空列表仍然可以下拉刷新.
 *
 * @param testTag 提示本身的测试标签.
 */
internal fun LazyListScope.notificationEmptyStateItem(
    icon: ImageVector,
    title: StringResource,
    supportingText: StringResource,
    testTag: String,
) {
    item(key = "empty", contentType = "empty") {
        Box(Modifier.fillParentMaxWidth(), contentAlignment = Alignment.Center) {
            // 让这一项至少与可见区域一样高
            Spacer(Modifier.fillParentMaxHeight())
            NotificationEmptyState(
                icon,
                stringResource(title),
                Modifier.testTag(testTag),
                supportingText = stringResource(supportingText),
            )
        }
    }
}
