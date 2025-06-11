/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.sidesheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.settings.danmaku.RegexFilterItem
import me.him188.ani.app.ui.settings.danmaku.isValidRegex
import me.him188.ani.app.ui.settings.framework.components.SettingsDefaults
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.app.ui.subject.episode.video.settings.SideSheetLayout
import me.him188.ani.utils.platform.Uuid


@Suppress("UnusedReceiverParameter")
@Composable
fun EpisodeVideoSideSheets.DanmakuRegexFilterSettings(
    state: DanmakuRegexFilterState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,  // Use the expanded parameter
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var regexTextFieldValue by rememberSaveable { mutableStateOf("") }
    val isBlank by remember { derivedStateOf { regexTextFieldValue.isBlank() } }
    val validRegex by remember { derivedStateOf { isValidRegex(regexTextFieldValue) } }
    var isError by remember { mutableStateOf(false) }

    fun handleAdd(): Unit {
        if (!isBlank && validRegex && expanded) {
            isError = false
            state.add(
                DanmakuRegexFilter(
                    id = Uuid.randomString(),
                    name = "",
                    regex = regexTextFieldValue,
                    enabled = true,
                ),
            )
            regexTextFieldValue = "" // Clear the text field after adding
        } else {
            isError = true
        }
        focusManager.clearFocus()
    }

    SideSheetLayout(
        title = { Text(text = "正则弹幕过滤管理") },
        onDismissRequest = onDismissRequest,
        closeButton = {
            IconButton(onClick = onDismissRequest) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭")
            }
        },
    ) {
        Surface(Modifier.fillMaxSize(), color = SettingsDefaults.groupBackgroundColor) {
            Column(
                modifier.padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(scrollState),
            ) {
                // 输入框
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = regexTextFieldValue,
                            onValueChange = {
                                regexTextFieldValue = it
                                isError = false
                            },
                            supportingText = {
                                if (!expanded) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = "竖屏状态下禁用编辑。",
                                    )
                                } else if (isError) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = "正则表达式语法不正确。",
                                    )
                                } else {
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = "填写用于屏蔽的正则表达式，例如：‘签’ 会屏蔽所有含有文字‘签’的弹幕。",
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = "正则表达式",
                                )
                            },
                            modifier = Modifier
                                .weight(1f) // Make the text field take up the remaining space
                                .onKeyEvent { event: KeyEvent ->
                                    if (event.key == Key.Enter) {
                                        handleAdd()
                                        true // Consume the event
                                    } else {
                                        false // Pass the event to other handlers
                                    }
                                },
                            enabled = expanded,  // Disable the text field if expanded is false
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { handleAdd() },
                            ),
                            isError = isError,
                            singleLine = true,
                        )

                        IconButton(
                            onClick = { handleAdd() },
                            enabled = expanded && !isBlank,
                            modifier = Modifier.align(Alignment.Bottom),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "添加")
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.list.forEach { item ->
                        RegexFilterItem(
                            item,
                            onDelete = { state.remove(item) },
                            onDisable = { state.switch(item) },
                        )
                    }
                }
            }
        }
    }
}
