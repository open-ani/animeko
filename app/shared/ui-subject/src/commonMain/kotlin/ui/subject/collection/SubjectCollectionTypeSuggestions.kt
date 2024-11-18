/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionActions
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

object SubjectCollectionTypeSuggestions {
    @Composable
    fun Collect(
        state: EditableSubjectCollectionTypeState,
        modifier: Modifier = Modifier,
    ) {
        SuggestionChip(
            { state.setSelfCollectionType(UnifiedCollectionType.DOING) },
            icon = { Icon(Icons.Rounded.Star, null) },
            label = { Text("追番") },
            colors = SuggestionChipDefaults.suggestionChipColors(
                labelColor = MaterialTheme.colorScheme.primary,
                iconContentColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = modifier,
        )
    }

    @Composable
    fun MarkAsDoing(
        state: EditableSubjectCollectionTypeState,
        modifier: Modifier = Modifier,
    ) {
        SuggestionChip(
            { state.setSelfCollectionType(UnifiedCollectionType.DOING) },
            icon = SubjectCollectionActions.Doing.icon,
            label = { Text("在看") },
            modifier = modifier,
        )
    }

    @Composable
    fun MarkAsDropped(
        state: EditableSubjectCollectionTypeState,
        modifier: Modifier = Modifier,
    ) {
        SuggestionChip(
            { state.setSelfCollectionType(UnifiedCollectionType.DROPPED) },
            icon = SubjectCollectionActions.Dropped.icon,
            label = { Text("抛弃") },
            modifier = modifier,
        )
    }
}
