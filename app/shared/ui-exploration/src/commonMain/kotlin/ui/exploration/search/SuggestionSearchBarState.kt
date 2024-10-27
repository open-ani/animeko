/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.adaptive.AdaptiveSearchBar
import me.him188.ani.app.ui.foundation.interaction.onEnterKeyEvent
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.search.SearchState

@Stable
class SuggestionSearchBarState<T>(
    historyState: State<List<String>>, // must be distinct
    suggestionsState: State<List<String>>, // must be distinct
    queryState: MutableState<String> = mutableStateOf(""),
    private val searchState: SearchState<T>,
) {
    var query by queryState
    var expanded by mutableStateOf(false)

    val previewType by derivedStateOf {
        if (query.isEmpty()) SuggestionSearchPreviewType.HISTORY else SuggestionSearchPreviewType.SUGGESTIONS
    }

    val history by historyState
    val suggestions by suggestionsState

    fun clear() {
        query = ""
        expanded = false
        searchState.clear()
    }

    fun startSearch() {
        searchState.startSearch()
        expanded = false
    }
}

@Immutable
enum class SuggestionSearchPreviewType {
    HISTORY,
    SUGGESTIONS
}

@Composable
fun <T> SuggestionSearchBar(
    state: SuggestionSearchBarState<T>,
    modifier: Modifier = Modifier,
    inputFieldModifier: Modifier = Modifier,
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    placeholder: @Composable (() -> Unit)? = null,
) {
    BackHandler(state.expanded) {
        state.expanded = false
    }
    AdaptiveSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = state.query,
                onQueryChange = { state.query = it.trim('\n') },
                onSearch = {
                    state.expanded = false
                    state.startSearch()
                },
                expanded = state.expanded,
                onExpandedChange = { state.expanded = it },
                inputFieldModifier.fillMaxWidth().onEnterKeyEvent {
                    state.startSearch()
                    true
                },
                placeholder = placeholder,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon =
                if (state.query.isNotEmpty() || state.expanded) {
                    {
                        IconButton({ state.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                } else null,
            )
        },
        expanded = state.expanded,
        onExpandedChange = { state.expanded = it },
        modifier,
        windowInsets = windowInsets,
    ) {
        val valuesState = when (state.previewType) {
            SuggestionSearchPreviewType.HISTORY -> state.history
            SuggestionSearchPreviewType.SUGGESTIONS -> state.suggestions
        }
        AnimatedContent(
            valuesState,
            transitionSpec = AniThemeDefaults.standardAnimatedContentTransition,
        ) { values ->
            LazyColumn {
                items(values, key = { it }, contentType = { 1 }) {
                    ListItem(
                        leadingContent = if (state.previewType == SuggestionSearchPreviewType.HISTORY) {
                            { Icon(Icons.Default.History, contentDescription = null) }
                        } else {
                            null
                        },
                        headlineContent = { Text(it) },
                        modifier = Modifier.clickable {
                            state.query = it
                            state.startSearch()
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
        }
    }
}
