/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.search

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.ui.exploration.search.SearchPage
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.main.SearchViewModel
import me.him188.ani.app.ui.subject.details.SubjectDetailsScene

@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEpisodeDetails: (subjectId: Int, episodeId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val vm = viewModel { SearchViewModel() }
    val listDetailNavigator = rememberListDetailPaneScaffoldNavigator()
    val coroutineScope = rememberCoroutineScope()
    SearchPage(
        vm.searchPageState,
        detailContent = {
            val subjectDetailsState by vm.subjectDetailsStateLoader.state
                .collectAsStateWithLifecycle(null)
            val authState by vm.authState.collectAsStateWithLifecycle(AuthState.NotAuthed)
            SubjectDetailsScene(
                subjectDetailsState,
                authState,
                onPlay = { episodeId ->
                    val current = subjectDetailsState
                    if (current != null) {
                        onNavigateToEpisodeDetails(current.subjectId, episodeId)
                    }
                },
                onLoadErrorRetry = { vm.reloadCurrentSubjectDetails() },
                navigationIcon = {
                    // 只有在单面板模式下才显示返回按钮
                    if (listDetailLayoutParameters.isSinglePane) {
                        BackNavigationIconButton(
                            onNavigateBack = {
                                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    listDetailNavigator.navigateBack()
                                }
                            },
                        )
                    }
                },
            )
        },
        modifier.fillMaxSize(),
        onSelect = { index, item ->
            vm.searchPageState.selectedItemIndex = index
            vm.viewSubjectDetails(item)
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                listDetailNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
            }
        },
        navigator = listDetailNavigator,
        contentWindowInsets = WindowInsets.safeDrawing, // 不包含 macos 标题栏, 因为左侧有 navigation rail
        navigationIcon = {
            BackNavigationIconButton(onNavigateBack)
        },
    )

}