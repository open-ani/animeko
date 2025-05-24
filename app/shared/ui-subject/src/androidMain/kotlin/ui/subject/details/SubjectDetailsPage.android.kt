/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.data.models.subject.TestSubjectInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.subject.details.state.createTestSubjectDetailsState
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewSubjectDetails() = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    val state = remember {
        createTestSubjectDetailsState(scope)
            .let { SubjectDetailsUIState.Ok(it.subjectId, it) }
    }
    PreviewSubjectDetailsScreen(
        state,
    )
}

@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewPlaceholderSubjectDetails() = ProvideCompositionLocalsForPreview {
    val state = remember {
        SubjectDetailsUIState.Placeholder(TestSubjectInfo.subjectId, TestSubjectInfo)
    }
    PreviewSubjectDetailsScreen(
        state,
    )
}


@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewErrorSubjectDetails() = ProvideCompositionLocalsForPreview {
    val state = remember {
        SubjectDetailsUIState.Err(TestSubjectInfo.subjectId, TestSubjectInfo, LoadError.NetworkError)
    }
    PreviewSubjectDetailsScreen(
        state,
    )
}

@TestOnly
@Composable
private fun PreviewSubjectDetailsScreen(
    state: SubjectDetailsUIState,
    modifier: Modifier = Modifier
) {
    SubjectDetailsScreen(
        state,
        TestSelfInfoUiState,
        onPlay = { },
        onLoadErrorRetry = { },
        onClickTag = {},
        {},
        modifier = modifier,
        navigationIcon = { BackNavigationIconButton({}) },
    )
}