/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.layout.TvModalOverlay
import me.him188.ani.tv.ui.foundation.widgets.TvOptionModal
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow

/**
 * 时区选择对话框. 列表是唯一的交互区域, 滚动与焦点都由方向键驱动.
 *
 * 打开时列表从当前选中项开始显示并把焦点交给它; 关闭后由设置页把焦点还给时区条目.
 */
@Composable
internal fun TvSettingsTimeZoneEditor(
    dialog: TvSettingsDialog.TimeZone,
    focus: TvFocusScope,
    onClose: () -> Unit,
) {
    val entryIndex = remember(dialog) {
        dialog.rows.indexOfFirst { it is TvTimeZoneRow.Option && it.zoneId == dialog.selectedZoneId }
            .coerceAtLeast(0)
    }
    // 初始可见位置与焦点落点都取打开时的选中项, 之后的状态更新不改变它们
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = entryIndex)
    val entryKey = remember(dialog) { dialog.rows.getOrNull(entryIndex)?.key }
    LaunchedEffect(dialog, entryKey) { entryKey?.let { focus.request(editorKey(it)) } }

    TvModalOverlay(onClose = onClose, background = {}) {
        TvOptionModal(dialog.title, Modifier.testTag("tv-settings-editor"), width = 640.dp) {
            LazyColumn(
                state = listState,
                modifier = Modifier.testTag("tv-settings-time-zone-list"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(dialog.rows, key = { it.key }) { row ->
                    when (row) {
                        is TvTimeZoneRow.Section -> Text(
                            row.region,
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        is TvTimeZoneRow.Option -> TvOptionRow(
                            title = row.title,
                            value = row.offsetLabel.orEmpty(),
                            selected = row.selected,
                            showSelectionIndicator = false,
                            trailingIcon = if (row.selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                            modifier = Modifier.testTag("tv-settings-time-zone-${row.zoneId ?: "system"}")
                                .semantics { role = Role.RadioButton }
                                .tvFocusAnchor(focus, editorKey(row.key)),
                        ) {
                            dialog.onSelect(row.zoneId)
                            onClose()
                        }
                    }
                }
            }
        }
    }
}
