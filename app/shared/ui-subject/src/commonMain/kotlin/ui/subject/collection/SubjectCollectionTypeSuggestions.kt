/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ChipColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.lang.*
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.components.SubjectCollectionActions
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import org.jetbrains.compose.resources.*

object SubjectCollectionTypeSuggestions {
    @Composable
    private fun CollectionTypeChip(
        targetType: UnifiedCollectionType,
        state: EditableSubjectCollectionTypeState,
        icon: @Composable (() -> Unit)? = null,
        label: @Composable () -> Unit,
        colors: ChipColors = SuggestionChipDefaults.suggestionChipColors(),
        modifier: Modifier = Modifier,
    ) {
        val scope = rememberCoroutineScope()
        val toaster = LocalToaster.current

        SuggestionChip(
            onClick = {
                scope.launch {
                    val error = state.setSelfCollectionType(targetType)
                    error?.let(toaster::showLoadError)
                }
            },
            icon = icon,
            label = label,
            colors = colors,
            modifier = modifier,
        )
    }

    @Composable
    fun Collect(
        state: EditableSubjectCollectionTypeState,
        modifier: Modifier = Modifier,
    ) = CollectionTypeChip(
        targetType = UnifiedCollectionType.DOING,
        state = state,
        icon = { Icon(Icons.Rounded.Star, null) },
        label = { Text(stringResource(Lang.subject_collection_collect)) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            labelColor = MaterialTheme.colorScheme.primary,
            iconContentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier,
    )

    @Composable
    fun MarkAsDoing(
        state: EditableSubjectCollectionTypeState,
        modifier: Modifier = Modifier,
    ) = CollectionTypeChip(
        targetType = UnifiedCollectionType.DOING,
        state = state,
        icon = SubjectCollectionActions.Doing.icon,
        label = { Text(stringResource(Lang.subject_collection_doing)) },
        modifier = modifier,
    )

    @Composable
    fun MarkAsDropped(
        state: EditableSubjectCollectionTypeState,
        modifier: Modifier = Modifier,
    ) = CollectionTypeChip(
        targetType = UnifiedCollectionType.DROPPED,
        state = state,
        icon = SubjectCollectionActions.Dropped.icon,
        label = { Text(stringResource(Lang.subject_collection_dropped)) },
        modifier = modifier,
    )
}
