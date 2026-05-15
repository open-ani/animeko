package me.him188.ani.app.ui.settings.framework.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_danmaku_cancel
import me.him188.ani.app.ui.lang.settings_media_source_save_button
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.jetbrains.compose.resources.stringResource

@Stable
class SelectableItem<T>(
    val item: T,
    val selected: Boolean
)

/**
 * 支持弹出对话框让用户排序.
 *
 * @param exposed 未展开时显示在项目右侧的标签, 来表示当前的排序
 * @param key 用于区分每个项目的唯一键, 必须快速且稳定
 */
@SettingsDsl
@Composable
fun <T> SettingsScope.SorterItem(
    values: () -> List<SelectableItem<T>>,
    onSort: (List<SelectableItem<T>>) -> Unit,
    exposed: @Composable (List<SelectableItem<T>>) -> Unit,
    item: @Composable (T) -> Unit,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    description: @Composable (() -> Unit)? = null,
    dialogDescription: @Composable (() -> Unit)? = description,
    dialogItemDescription: @Composable ((T) -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    title: @Composable () -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    TextItem(
        modifier = modifier.clickable { showDialog = true },
        description = description,
        icon = icon,
        action = {
            val valuesState by remember {
                derivedStateOf { values() }
            }
            TextButton(onClick = { showDialog = true }, Modifier.widthIn(max = 128.dp)) {
                exposed(valuesState)
            }

            if (showDialog) {
                var sortingData by remember(valuesState) {
                    mutableStateOf(valuesState)
                }
                val state = rememberReorderableLazyListState(
                    onMove = { from, to ->
                        sortingData = sortingData.toMutableList().apply {
                            add(to.index, removeAt(from.index))
                        }
                    },
                )
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { title() },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                dialogDescription?.let {
                                    it()
                                }
                            }
                            LazyColumn(
                                state = state.listState,
                                modifier = Modifier
                                    .reorderable(state)
                                    .detectReorderAfterLongPress(state),
                            ) {
                                itemsIndexed(sortingData, key = { _, it -> key(it.item) }) { index, item ->
                                    ReorderableItem(state, key = key(item.item)) { isDragging ->
                                        val elevation = animateDpAsState(if (isDragging) 16.dp else 0.dp)
                                        Row(
                                            modifier = Modifier
                                                .shadow(elevation.value)
                                                .background(Color.Transparent) // match card background
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Checkbox(
                                                checked = item.selected,
                                                onCheckedChange = {
                                                    sortingData = sortingData.toMutableList().apply {
                                                        set(index, SelectableItem(item.item, it))
                                                    }
                                                },
                                                modifier = Modifier.padding(end = 4.dp),
                                            )

                                            Row(Modifier.weight(1f)) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    item(item.item)
                                                    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                                                        dialogItemDescription?.invoke(item.item)
                                                    }
                                                }
                                            }

                                            Icon(
                                                Icons.Rounded.Reorder,
                                                "长按排序",
                                                Modifier.detectReorder(state),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDialog = false
                                onConfirm?.invoke()
                                onSort(sortingData)
                            },
                        ) {
                            Text(stringResource(Lang.settings_media_source_save_button))
                        }
                    },
                    dismissButton = {
                        TextButton({ showDialog = false }) {
                            Text(stringResource(Lang.settings_danmaku_cancel))
                        }
                    },
                )
            }
        },
        title = title,
    )
}
