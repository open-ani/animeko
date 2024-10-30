/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview
import me.him188.ani.app.ui.search.SearchDefaults.SearchProblemCard

class PreviewSearchProblemProvider : PreviewParameterProvider<SearchProblem?> {
    override val values: Sequence<SearchProblem?>
        get() = sequenceOf(
            null,
            SearchProblem.NoResults,
            SearchProblem.RequiresLogin,
            SearchProblem.NetworkError,
            SearchProblem.ServiceUnavailable,
            SearchProblem.UnknownError(IllegalStateException("test")),
        )
}

// See also PreviewSearchPage
@Composable
@PreviewLightDark
fun PreviewSearchProblemCard(
    @PreviewParameter(PreviewSearchProblemProvider::class)
    problem: SearchProblem?
) = Impl(problem)

@Composable
private fun Impl(error: SearchProblem?) {
    ProvideFoundationCompositionLocalsForPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            SearchProblemCard(error, {}, {}, Modifier.padding(all = 16.dp))
        }
    }
}
