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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import coil3.Image
import coil3.toBitmap
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.subject.details.state.createTestSubjectDetailsState
import me.him188.ani.utils.platform.annotations.TestOnly

actual fun Image.toComposeImageBitmap(): ImageBitmap {
    return this.toBitmap().asImageBitmap()
}

@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewSubjectDetails() = ProvideFoundationCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    val state = remember { SubjectDetailsStateLoader.LoadState.Ok(createTestSubjectDetailsState(scope)) }
    SubjectDetailsPage(
        state,
        onPlay = { },
        onLoadErrorRetry = { },
        navigationIcon = { BackNavigationIconButton({}) },
    )
}

@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewPlaceholderSubjectDetails() = ProvideFoundationCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    val state = remember {
        SubjectDetailsStateLoader.LoadState.Ok(createTestSubjectDetailsState(scope, isPlaceholder = true))
    }
    SubjectDetailsPage(
        state,
        onPlay = { },
        onLoadErrorRetry = { },
        navigationIcon = { BackNavigationIconButton({}) },
    )
}


@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewErrorSubjectDetails() = ProvideFoundationCompositionLocalsForPreview {
    val state = remember {
        SubjectDetailsStateLoader.LoadState.Err(TestSubjectInfo.subjectId, TestSubjectInfo, LoadError.NetworkError)
    }
    SubjectDetailsPage(
        state,
        onPlay = { },
        onLoadErrorRetry = { },
        navigationIcon = { BackNavigationIconButton({}) },
    )
}
