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
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_selector_subject_format_css_intro
import me.him188.ani.app.ui.lang.settings_mediasource_selector_subject_format_css_links_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_subject_format_css_names_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_subject_format_jsonpath_intro
import me.him188.ani.app.ui.lang.settings_mediasource_selector_subject_format_jsonpath_links_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_subject_format_jsonpath_names_supporting
import me.him188.ani.app.ui.lang.settings_mediasource_selector_subject_format_links_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_subject_format_names_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_subject_format_single
import me.him188.ani.app.ui.lang.settings_mediasource_selector_subject_format_single_label
import me.him188.ani.app.ui.lang.settings_mediasource_selector_subject_format_single_supporting
import me.him188.ani.app.ui.settings.mediasource.editor.ConfigTextField
import me.him188.ani.app.ui.settings.mediasource.editor.UnsupportedFormatIdHint
import org.jetbrains.compose.resources.stringResource

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
                stringResource(Lang.settings_mediasource_selector_subject_format_single),
                Modifier,
                style = MaterialTheme.typography.labelLarge,
            )

            val conf = state.subjectFormatA
            ConfigTextField(
                conf.selectLists, { conf.selectLists = it },
                label = stringResource(Lang.settings_mediasource_selector_subject_format_single_label),
                Modifier.padding(top = verticalSpacing),
                supportingText = stringResource(
                    Lang.settings_mediasource_selector_subject_format_single_supporting,
                ),
                isError = conf.selectListsIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
        }

        SelectorSubjectFormatIndexed -> Column(modifier) {
            Text(
                stringResource(Lang.settings_mediasource_selector_subject_format_css_intro),
                Modifier,
                style = MaterialTheme.typography.labelLarge,
            )
            val conf = state.subjectFormatIndex
            ConfigTextField(
                conf.selectNames, { conf.selectNames = it },
                label = stringResource(Lang.settings_mediasource_selector_subject_format_names_label),
                Modifier.padding(top = verticalSpacing),
                supportingText = stringResource(
                    Lang.settings_mediasource_selector_subject_format_css_names_supporting,
                ),
                isError = conf.selectNamesIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                conf.selectLinks, { conf.selectLinks = it },
                label = stringResource(Lang.settings_mediasource_selector_subject_format_links_label),
                Modifier.padding(top = verticalSpacing),
                supportingText = stringResource(
                    Lang.settings_mediasource_selector_subject_format_css_links_supporting,
                ),
                isError = conf.selectLinksIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
        }

        SelectorSubjectFormatJsonPathIndexed -> Column(modifier) {
            Text(
                stringResource(Lang.settings_mediasource_selector_subject_format_jsonpath_intro),
                Modifier,
                style = MaterialTheme.typography.labelLarge,
            )
            val conf = state.subjectFormatJsonPathIndex
            ConfigTextField(
                conf.selectNames, { conf.selectNames = it },
                label = stringResource(Lang.settings_mediasource_selector_subject_format_names_label),
                Modifier.padding(top = verticalSpacing),
                supportingText = stringResource(
                    Lang.settings_mediasource_selector_subject_format_jsonpath_names_supporting,
                ),
                isError = conf.selectNamesIsError,
                enabled = state.enableEdit,
                shape = textFieldShape,
            )
            ConfigTextField(
                conf.selectLinks, { conf.selectLinks = it },
                label = stringResource(Lang.settings_mediasource_selector_subject_format_links_label),
                Modifier.padding(top = verticalSpacing),
                supportingText = stringResource(
                    Lang.settings_mediasource_selector_subject_format_jsonpath_links_supporting,
                ),
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
