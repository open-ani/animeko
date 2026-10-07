/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.ui.subject.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import me.him188.ani.app.data.models.preference.CollectionSortOrder
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.collection_sort
import me.him188.ani.app.ui.lang.collection_sort_air_date
import me.him188.ani.app.ui.lang.collection_sort_name
import me.him188.ani.app.ui.lang.collection_sort_time
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CollectionSortButton(
    sortOrder: CollectionSortOrder,
    onSortOrderChange: (CollectionSortOrder) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.Sort, contentDescription = stringResource(Lang.collection_sort))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            for (order in CollectionSortOrder.entries) {
                DropdownMenuItem(
                    modifier = Modifier.semantics { selected = order == sortOrder },
                    text = {
                        Text(
                            stringResource(
                                when (order) {
                                    CollectionSortOrder.LAST_UPDATED -> Lang.collection_sort_time
                                    CollectionSortOrder.NAME -> Lang.collection_sort_name
                                    CollectionSortOrder.AIR_DATE -> Lang.collection_sort_air_date
                                },
                            ),
                        )
                    },
                    leadingIcon = {
                        if (order == sortOrder) Icon(Icons.Rounded.Check, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onSortOrderChange(order)
                    },
                )
            }
        }
    }
}
