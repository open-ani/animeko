/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatIndexed
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatJsonPathIndexed
import me.him188.ani.app.ui.settings.mediasource.editor.ConfigTextField
import me.him188.ani.app.ui.settings.mediasource.editor.UnsupportedFormatIdHint

@Composable
internal fun SelectorSubjectConfigurationColumn(
    format: SelectorSubjectFormat<*>?,
    state: SelectorConfigState,
    textFieldShape: Shape,
    verticalSpacing: Dp,
    modifier: Modifier = Modifier,
) {
    when (format) {
        SelectorSubjectFormatA -> Column(modifier) {
            Text(
                "单个表达式，选取一些 <a>，根据其 title 属性或 text 确定名称，href 属性确定链接",
                Modifier,
                style = MaterialTheme.typography.labelLarge,
            )

            val conf = state.subjectFormatA
            ConfigTextField(
                conf.selectLists, { conf.selectLists = it },
                label = "提取条目列表",
                Modifier.padding(top = verticalSpacing),
                supportingText = "CSS Selector 表达式。期望返回一些 <a>，每个对应一个条目，将会读取其 href 属性和 text",
                isError = conf.selectListsIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
        }

        SelectorSubjectFormatIndexed -> Column(modifier) {
            Text(
                "两个 CSS Selector 表达式，分别选取条目名称列表和链接列表，按顺序一一对应",
                Modifier,
                style = MaterialTheme.typography.labelLarge,
            )
            val conf = state.subjectFormatIndex
            ConfigTextField(
                conf.selectNames, { conf.selectNames = it },
                label = "提取条目名称列表",
                Modifier.padding(top = verticalSpacing),
                supportingText = "CSS Selector 表达式。选取条目名称列表",
                isError = conf.selectNamesIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                conf.selectLinks, { conf.selectLinks = it },
                label = "提取条目链接列表",
                Modifier.padding(top = verticalSpacing),
                supportingText = "CSS Selector 表达式。选取链接列表",
                isError = conf.selectLinksIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
        }

        SelectorSubjectFormatJsonPathIndexed -> Column(modifier) {
            Text(
                "两个 JsonPath 表达式，分别选取条目名称列表和链接列表，按顺序一一对应",
                Modifier,
                style = MaterialTheme.typography.labelLarge,
            )
            val conf = state.subjectFormatJsonPathIndex
            ConfigTextField(
                conf.selectNames, { conf.selectNames = it },
                label = "提取条目名称列表",
                Modifier.padding(top = verticalSpacing),
                supportingText = """JsonPath 表达式。选取条目名称列表。期望返回一个数组，每个元素对应一个名称。支持嵌套结构，例如 ["a", "b"] 与 [{"any": "a"}, {"any": "b"}] 都可以解析为两个名称 a b""",
                isError = conf.selectNamesIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                conf.selectLinks, { conf.selectLinks = it },
                label = "提取条目链接列表",
                Modifier.padding(top = verticalSpacing),
                supportingText = "JsonPath 表达式。选取链接列表。期望返回一个数组，每个元素对应一个链接。支持嵌套结构",
                isError = conf.selectLinksIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
        }

        null -> Column(modifier) {
            UnsupportedFormatIdHint(
                state.subjectFormatId.value,
                Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
