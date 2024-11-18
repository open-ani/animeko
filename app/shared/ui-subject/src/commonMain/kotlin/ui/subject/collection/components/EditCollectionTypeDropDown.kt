/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import me.him188.ani.datasources.api.topic.UnifiedCollectionType


@Composable
fun EditCollectionTypeDropDown(
    state: EditableSubjectCollectionTypeState,
    modifier: Modifier = Modifier,
) {
    EditCollectionTypeDropDown(
        currentType = state.selfCollectionType,
        expanded = state.showDropdown,
        onDismissRequest = { state.showDropdown = false },
        onClick = {
            state.showDropdown = false
            state.setSelfCollectionType(it.type)
        },
        modifier = modifier,
    )
}

/**
 * A drop down menu to edit the collection type of a subject.
 * Also includes a dialog to set all episodes as watched when the user attempts to mark the subject as [UnifiedCollectionType.DONE].
 */
@Composable
fun EditCollectionTypeDropDown(
    currentType: UnifiedCollectionType?,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onClick: (action: SubjectCollectionAction) -> Unit,
    modifier: Modifier = Modifier,
    actions: List<SubjectCollectionAction> = SubjectCollectionActionsForEdit,
    showDelete: Boolean = currentType != UnifiedCollectionType.NOT_COLLECTED,
) {
    DropdownMenu(
        expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(x = 0.dp, y = 4.dp),
        modifier = modifier,
    ) {
        for (action in actions) {
            if (!showDelete && action == SubjectCollectionActions.DeleteCollection) continue

            val onClickState by rememberUpdatedState(onClick)
            val onDismissRequestState by rememberUpdatedState(onDismissRequest)
            val color = action.colorForCurrent(currentType)
            DropdownMenuItem(
                text = {
                    CompositionLocalProvider(LocalContentColor provides color) {
                        action.title()
                    }
                },
                leadingIcon = {
                    CompositionLocalProvider(LocalContentColor provides color) {
                        action.icon()
                    }
                },
                onClick = {
                    onClickState(action)
                    onDismissRequestState()
                },
            )
        }
    }
}

@Composable
private fun SubjectCollectionAction.colorForCurrent(
    currentType: UnifiedCollectionType?
) = if (currentType == type) {
    MaterialTheme.colorScheme.primary
} else {
    LocalContentColor.current
}
