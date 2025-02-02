/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.effects.moveFocusOnEnter
import me.him188.ani.app.ui.foundation.layout.cardVerticalPadding
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1

/**
 * @see me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
 */
object SelectorConfigurationDefaults {
    const val STEP_NAME_1 = "步骤 1：搜索条目"
    const val STEP_NAME_2 = "步骤 2：搜索剧集"
    const val STEP_NAME_3 = "步骤 3：匹配视频"

    val verticalSpacing: Dp
        @Composable
        get() = currentWindowAdaptiveInfo1().windowSizeClass.cardVerticalPadding

    val textFieldShape
        @Composable
        get() = MaterialTheme.shapes.medium
}

@Suppress("UnusedReceiverParameter")
@Composable
internal fun SelectorConfigurationDefaults.MatchVideoSection(
    state: SelectorConfigState,
    modifier: Modifier = Modifier,
    textFieldShape: Shape = SelectorConfigurationDefaults.textFieldShape,
    verticalSpacing: Dp = SelectorConfigurationDefaults.verticalSpacing,
) {
    Column(modifier) {
        val matchVideoConfig = state.matchVideoConfig
        ListItem(
            headlineContent = { Text("启用嵌套链接") },
            Modifier
                .padding(bottom = (verticalSpacing - 8.dp).coerceAtLeast(0.dp))
                .clickable(
                    enabled = state.enableEdit,
                ) { matchVideoConfig.enableNestedUrl = !matchVideoConfig.enableNestedUrl },
            supportingContent = { Text("当遇到匹配的链接时，终止父页面加载并跳转到匹配的链接，在嵌套页面中继续查找视频链接。支持任意次数嵌套") },
            trailingContent = {
                Switch(
                    matchVideoConfig.enableNestedUrl, { matchVideoConfig.enableNestedUrl = it },
                    enabled = state.enableEdit,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Transparent),
        )

        AniAnimatedVisibility(visible = matchVideoConfig.enableNestedUrl) {
            OutlinedTextField(
                matchVideoConfig.matchNestedUrl, { matchVideoConfig.matchNestedUrl = it },
                Modifier
                    .fillMaxWidth()
                    .moveFocusOnEnter()
                    .padding(bottom = verticalSpacing),
                label = { Text("匹配嵌套链接") },
                supportingText = { Text("正则表达式，从播放页面中加载的所有资源链接中匹配出需要跳转进入的链接。若正则包含名为 v 的分组则使用该分组，否则使用整个 URL") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = textFieldShape,
                isError = matchVideoConfig.matchNestedUrlIsError,
                enabled = state.enableEdit,
            )
        }

        OutlinedTextField(
            matchVideoConfig.matchVideoUrl, { matchVideoConfig.matchVideoUrl = it },
            Modifier.fillMaxWidth().moveFocusOnEnter().padding(bottom = verticalSpacing),
            label = { Text("匹配视频链接") },
            supportingText = { Text("正则表达式，从播放页面中加载的所有资源链接中匹配出视频链接。若正则包含名为 v 的分组则使用该分组，否则使用整个 URL") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = textFieldShape,
            isError = matchVideoConfig.matchVideoUrlIsError,
            enabled = state.enableEdit,
        )
        OutlinedTextField(
            matchVideoConfig.cookies, { matchVideoConfig.cookies = it },
            Modifier.fillMaxWidth(),
            label = { Text("Cookies (可选)") },
            supportingText = { Text("key=value 格式的 cookies，一行一个，留空则不添加") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            shape = textFieldShape,
            enabled = state.enableEdit,
        )
    }
}
