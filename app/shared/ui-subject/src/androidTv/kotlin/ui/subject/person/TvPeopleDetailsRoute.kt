/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import org.jetbrains.compose.resources.stringResource

@Composable
fun TvPeopleDetailsRoute(
    viewModel: TvPeopleDetailsViewModel,
    onNavigate: (TvNavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val uriHandler = LocalUriHandler.current
    val unsupported = stringResource(Lang.foundation_richtext_external_app_link_warning_prefix)
    val failed = stringResource(Lang.foundation_richtext_open_failed_prefix)
    LaunchedEffect(viewModel) { viewModel.errors.collect { toaster.showLoadError(it) } }
    TvNavigationEffect(viewModel.navigationEvents, onNavigate)
    TvPeopleDetailsScreen(state, viewModel::onIntent, modifier) { url ->
        RichTextDefaults.checkSanityAndOpen(url, uriHandler, toaster, unsupported, failed)
    }
}
