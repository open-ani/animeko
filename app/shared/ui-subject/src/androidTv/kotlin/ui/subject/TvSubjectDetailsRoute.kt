/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.subject

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_richtext_external_app_link_warning_prefix
import me.him188.ani.app.ui.lang.foundation_richtext_open_failed_prefix
import me.him188.ani.app.ui.richtext.RichTextDefaults
import me.him188.ani.leanback.ui.foundation.TvNavigationEffect
import me.him188.ani.leanback.ui.foundation.TvNavigationEvent
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsPanelKind
import me.him188.ani.leanback.ui.subject.presentation.TvSubjectPresentationState
import org.jetbrains.compose.resources.stringResource

@Composable
fun TvSubjectDetailsRoute(
    viewModel: TvSubjectDetailsViewModel,
    onNavigate: (TvNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presentation = rememberSaveable(saver = TvSubjectPresentationState.Saver) { TvSubjectPresentationState() }
    val uriHandler = LocalUriHandler.current
    val toaster = LocalToaster.current
    val unsupportedLink = stringResource(Lang.foundation_richtext_external_app_link_warning_prefix)
    val failedLink = stringResource(Lang.foundation_richtext_open_failed_prefix)
    LaunchedEffect(viewModel) { viewModel.errors.collect { toaster.showLoadError(it) } }
    LaunchedEffect(state.operation) {
        if (presentation.panel == null || state.operation.requestId == presentation.generation) {
            state.operation.error?.let { toaster.showLoadError(it) }
        }
    }
    // Route reconnects data for a restored panel; the view owns only the panel identity.
    LaunchedEffect(presentation.panel?.key) {
        val panel = presentation.panel ?: return@LaunchedEffect
        when (panel.kind) {
            TvDetailsPanelKind.TagResults -> if (state.tagResults?.tag != panel.argument) {
                viewModel.onIntent(TvSubjectDetailsIntent.SearchTag(panel.argument))
            }
            else -> Unit
        }
    }
    TvNavigationEffect(viewModel.navigationEvents, onNavigate)
    TvSubjectDetailsScreen(state, viewModel::onIntent, modifier, presentation) { url ->
        RichTextDefaults.checkSanityAndOpen(url, uriHandler, toaster, unsupportedLink, failedLink)
    }
}
