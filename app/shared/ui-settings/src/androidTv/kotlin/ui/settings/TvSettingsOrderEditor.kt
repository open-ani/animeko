/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_priority_position
import me.him188.ani.app.ui.lang.settings_priority_reorder
import me.him188.ani.app.ui.lang.settings_priority_reorder_done
import me.him188.ani.app.ui.lang.settings_priority_selected_description
import me.him188.ani.app.ui.lang.tv_settings_moving_hint
import me.him188.ani.app.ui.lang.tv_settings_reorder_hint
import me.him188.ani.leanback.ui.foundation.focus.TvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.layout.TvModalOverlay
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionModal
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

/** Selection and order are drafts until Save. Moving uses the axis of the visible list. */
@Composable
internal fun TvSettingsOrderEditor(dialog: TvSettingsDialog.Order, focus: TvFocusScope, close: () -> Unit) {
    var order by remember(dialog) {
        val chosen = dialog.selected ?: dialog.values.map { it.id }
        mutableStateOf(chosen + dialog.values.map { it.id }.filterNot { it in chosen })
    }
    var selected by remember(dialog) { mutableStateOf((dialog.selected ?: order).toSet()) }
    var reordering by remember { mutableStateOf(false) }
    var enteredReorder by remember { mutableStateOf(false) }
    var movingId by remember { mutableStateOf<String?>(null) }
    var beforeMove by remember { mutableStateOf(order) }
    val selectedOrder = order.filter { it in selected }
    LaunchedEffect(reordering) {
        focus.request(editorKey(when {
            reordering -> "order-${selectedOrder.first()}"
            enteredReorder -> "reorder"
            else -> "entry"
        }))
    }
    val backState = reordering to movingId
    val back = {
        if (backState == (reordering to movingId)) {
            when {
                movingId != null -> { order = beforeMove; movingId = null }
                reordering -> reordering = false
                else -> close()
            }
        }
    }
    TvModalOverlay(onClose = back, background = {}) {
        TvOptionModal(
            dialog.title, Modifier.testTag("tv-settings-editor"),
            subtitle = stringResource(when {
                movingId != null -> Lang.tv_settings_moving_hint
                reordering -> Lang.tv_settings_reorder_hint
                else -> Lang.settings_priority_selected_description
            }),
            footer = {
                if (reordering) {
                    if (movingId == null) TvOptionRow(
                        stringResource(Lang.settings_priority_reorder_done),
                        modifier = Modifier.testTag("tv-settings-reorder-done"),
                    ) { reordering = false }
                } else {
                    TvOptionRow(
                        stringResource(Lang.settings_priority_reorder), enabled = selected.size > 1,
                        icon = Icons.Rounded.UnfoldMore,
                        modifier = Modifier.tvFocusAnchor(focus, editorKey("reorder")).testTag("tv-settings-reorder"),
                    ) { enteredReorder = true; reordering = true }
                    EditorActions(true, { dialog.onSave(selectedOrder); close() }, close)
                }
            },
        ) {
            val visible = if (reordering) selectedOrder else order
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                visible.forEachIndexed { index, id ->
                    key(id) {
                        val moving = movingId == id
                        TvOptionRow(
                            dialog.values.find { it.id == id }?.title ?: id,
                            value = if (id in selected) stringResource(Lang.settings_priority_position, selectedOrder.indexOf(id) + 1) else "",
                            checked = if (reordering) null else id in selected,
                            toggleRole = Role.Checkbox,
                            valueIcon = if (moving) Icons.Rounded.UnfoldMore else null,
                            modifier = Modifier.testTag("tv-settings-order-$id")
                                .tvFocusAnchor(focus, editorKey("order-$id"))
                                .then(if (index == 0) Modifier.tvFocusAnchor(focus, editorKey("entry")) else Modifier)
                                .focusProperties { canFocus = movingId == null || moving }
                                .onPreviewKeyEvent { event ->
                                    if (!moving || event.key !in listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)) {
                                        false
                                    } else {
                                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                                            val direction = when (event.key) {
                                                Key.DirectionUp -> -1
                                                Key.DirectionDown -> 1
                                                else -> 0
                                            }
                                            order = moveSettingsPreference(selectedOrder, id, direction) + order.filterNot { it in selected }
                                        }
                                        true
                                    }
                                },
                        ) {
                            if (!reordering) selected = if (id in selected) selected - id else selected + id
                            else if (moving) movingId = null
                            else { beforeMove = order; movingId = id }
                        }
                    }
                }
            }
        }
    }
}

internal fun moveSettingsPreference(order: List<String>, id: String, direction: Int): List<String> {
    val index = order.indexOf(id)
    if (index < 0) return order
    val destination = (index + direction).coerceIn(0, order.lastIndex)
    return order.toMutableList().apply { add(destination, removeAt(index)) }
}
