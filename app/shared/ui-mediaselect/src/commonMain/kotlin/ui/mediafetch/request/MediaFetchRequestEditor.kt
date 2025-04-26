/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch.request


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.Preview


/**
 * Editor for [EditingMediaFetchRequest] that exposes all editable fields.
 *
 * @param fetchRequest Current value to display/edit.
 * @param onFetchRequestChange Callback whenever any sub‑field changes.
 * @param modifier Optional [Modifier].
 */
@Composable
fun MediaFetchRequestEditor(
    fetchRequest: EditingMediaFetchRequest,
    onFetchRequestChange: (EditingMediaFetchRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    val listItemColors = ListItemDefaults.colors(
        containerColor = Color.Transparent,
    )
    Column(
        modifier = modifier
            .verticalScroll(scrollState),
    ) {
        val horizontalPadding = 16.dp
        val verticalSpacing = 16.dp

        // 没必要允许编辑 bangumi id
        // --- Subject & episode ids -------------------------------------------------------------
//        OutlinedTextField(
//            value = fetchRequest.subjectId,
//            onValueChange = { onFetchRequestChange(fetchRequest.copy(subjectId = it)) },
//            label = { Text("Subject ID (Bangumi)") },
//            singleLine = true,
//            isError = fetchRequest.subjectId.toIntOrNull() == null,
//            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
//        )
//        OutlinedTextField(
//            value = fetchRequest.episodeId,
//            onValueChange = { onFetchRequestChange(fetchRequest.copy(episodeId = it)) },
//            label = { Text("Episode ID (Bangumi)") },
//            singleLine = true,
//            isError = fetchRequest.subjectId.toIntOrNull() == null,
//            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
//        )

        OutlinedTextField(
            value = fetchRequest.primaryName,
            onValueChange = { onFetchRequestChange(fetchRequest.copy(primaryName = it)) },
            label = { Text("主搜索名") },
            supportingText = { Text("大多数数据源只使用此名称") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            singleLine = true,
        )

        // --- Subject complementary names -------------------------------------------------------------------

        var showComplementaryNames by rememberSaveable { mutableStateOf(false) }
        ListItem(
            headlineContent = {
                Text(
                    "次要搜索名",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            Modifier.padding(top = 8.dp),
            supportingContent = {
                Text("在线源会忽略这些名称")
            },
            colors = listItemColors,
            trailingContent = {
                IconToggleButton(
                    showComplementaryNames,
                    onCheckedChange = { showComplementaryNames = it },
                ) {
                    // expand/collapse
                    if (showComplementaryNames) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "收起")
                    } else {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "展开")
                    }
                }
            },
        )

        AniAnimatedVisibility(showComplementaryNames) {
            Column {
                Column(
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                ) {
                    fetchRequest.complementaryNames.forEachIndexed { index, name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { newName ->
                                    val updated =
                                        fetchRequest.complementaryNames.toMutableList().apply { this[index] = newName }
                                    onFetchRequestChange(fetchRequest.copy(complementaryNames = updated))
                                },
                                modifier = Modifier.weight(1f).padding(start = horizontalPadding),
                                singleLine = true,
                                label = { Text("Name #${index + 1}") },
                            )
                            IconButton(
                                onClick = {
                                    val updated =
                                        fetchRequest.complementaryNames.toMutableList().also { it.removeAt(index) }
                                    onFetchRequestChange(fetchRequest.copy(complementaryNames = updated))
                                },
                                Modifier.padding(end = horizontalPadding - 8.dp),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "删除名称 #$index")
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        val updated = fetchRequest.complementaryNames + ""
                        onFetchRequestChange(fetchRequest.copy(complementaryNames = updated))
                    },
                    Modifier.align(Alignment.End)
                        .padding(top = verticalSpacing - 8.dp)
                        .padding(horizontal = horizontalPadding),
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("添加名称")
                }
            }
        }

        // --- Episode naming -------------------------------------------------------------------

        ListItem(
            headlineContent = {
                Text(
                    "剧集信息",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            Modifier.padding(vertical = 8.dp),
            supportingContent = {
                Text(
                    "资源必须至少匹配以下两种信息中的一种，否则不会显示。可以只修改其中一种",
                )
            },
            colors = listItemColors,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        ) {
            // 没必要允许编辑名称，因为序号也可以作为名称匹配（需要支持 Unknown）
//            OutlinedTextField(
//                value = fetchRequest.episodeName,
//                onValueChange = { onFetchRequestChange(fetchRequest.copy(episodeName = it)) },
//                label = { Text("剧集名称") },
//                supportingText = { Text("可留空") },
//                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
//                singleLine = true,
//            )

            // --- Episode sort ---------------------------------------------------------------------
            val sortAndEpAreError = fetchRequest.episodeSort.isEmpty() && fetchRequest.episodeEp.isEmpty()
            OutlinedTextField(
                value = fetchRequest.episodeSort,
                onValueChange = { newValue ->
                    onFetchRequestChange(fetchRequest.copy(episodeSort = newValue))
                },
                label = { Text("系列内剧集序号") },
                supportingText = { Text("假设有两季，分别有 12 集，则第二季的第一集为 13") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
                singleLine = true,
                isError = sortAndEpAreError,
            )
            OutlinedTextField(
                value = fetchRequest.episodeEp,
                onValueChange = { newValue ->
                    onFetchRequestChange(fetchRequest.copy(episodeEp = newValue))
                },
                label = { Text("条目内序号") },
                supportingText = { Text("在当前季度内的序号，例如第二季的第一集为 01") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
                singleLine = true,
                isError = sortAndEpAreError,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------

@OptIn(TestOnly::class)
@Preview(showBackground = true, locale = "zh-rCN")
@Composable
private fun MediaFetchRequestEditorPreview() {
    var state by remember { mutableStateOf(TestEditingMediaFetchRequest) }

    ProvideCompositionLocalsForPreview {
        MediaFetchRequestEditor(fetchRequest = state, onFetchRequestChange = { state = it })
    }
}
