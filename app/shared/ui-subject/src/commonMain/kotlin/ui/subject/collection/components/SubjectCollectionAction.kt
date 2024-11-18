/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * 收藏类型的展示图标和标题. 用于给各种需要展示收藏类型的地方提供一致的展示方式.
 */
@Immutable
class SubjectCollectionAction(
    val title: @Composable () -> Unit,
    val icon: @Composable () -> Unit,
    val type: UnifiedCollectionType,
)

@Immutable
object SubjectCollectionActions {
    @Stable
    val Wish = SubjectCollectionAction(
        { Text("想看") },
        { Icon(Icons.AutoMirrored.Rounded.EventNote, null) },
        UnifiedCollectionType.WISH,
    )

    @Stable
    val Doing = SubjectCollectionAction(
        { Text("在看") },
        { Icon(Icons.Rounded.PlayCircleOutline, null) },
        UnifiedCollectionType.DOING,
    )

    @Stable
    val Done = SubjectCollectionAction(
        { Text("看过") },
        { Icon(Icons.Rounded.TaskAlt, null) },
        UnifiedCollectionType.DONE,
    )

    @Stable
    val OnHold = SubjectCollectionAction(
        { Text("搁置") },
        { Icon(Icons.Rounded.AccessTime, null) },
        UnifiedCollectionType.ON_HOLD,
    )

    @Stable
    val Dropped = SubjectCollectionAction(
        { Text("抛弃") },
        { Icon(Icons.Rounded.Block, null) },
        UnifiedCollectionType.DROPPED,
    )

    @Stable
    val DeleteCollection = SubjectCollectionAction(
        { Text("取消追番", color = MaterialTheme.colorScheme.error) },
        { Icon(Icons.Rounded.DeleteOutline, null) },
        type = UnifiedCollectionType.NOT_COLLECTED,
    )

    @Stable
    val Collect = SubjectCollectionAction(
        { Text("追番") },
        { Icon(Icons.Rounded.Star, null) },
        type = UnifiedCollectionType.NOT_COLLECTED,
    )
}

private val SubjectCollectionActionsCommon
    get() = listOf(
        SubjectCollectionActions.Wish,
        SubjectCollectionActions.Doing,
        SubjectCollectionActions.Done,
        SubjectCollectionActions.OnHold,
        SubjectCollectionActions.Dropped,
    )

@Stable
val SubjectCollectionActionsForEdit = SubjectCollectionActionsCommon + listOf(
    SubjectCollectionActions.DeleteCollection,
)

@Stable
val SubjectCollectionActionsForCollect = SubjectCollectionActionsCommon + listOf(
    SubjectCollectionActions.Collect,
)
