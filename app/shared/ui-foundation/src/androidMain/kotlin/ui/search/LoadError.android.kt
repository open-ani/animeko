/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.search

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

internal class LoadErrorProblemProvider : PreviewParameterProvider<LoadError?> {
    override val values: Sequence<LoadError?>
        get() = sequenceOf(
            null,
            LoadError.NoResults,
            LoadError.RequiresLogin,
            LoadError.NetworkError,
            LoadError.ServiceUnavailable,
            LoadError.UnknownError(IllegalStateException("test")),
        )
}

// See also PreviewSearchPage
@Composable
@PreviewLightDark
private fun PreviewLoadErrorCard(
    @PreviewParameter(LoadErrorProblemProvider::class)
    problem: LoadError?
) = Impl(problem)

@Composable
private fun Impl(error: LoadError?) {
    ProvideCompositionLocalsForPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            LoadErrorCard(error, {}, Modifier.padding(all = 16.dp), {})
        }
    }
}
