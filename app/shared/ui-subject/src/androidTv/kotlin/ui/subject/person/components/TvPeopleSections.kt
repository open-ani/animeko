/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.progressSemantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import me.him188.ani.app.data.models.person.PersonSubjectSummary
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.leanback.ui.foundation.widgets.TvLandscapeCard
import me.him188.ani.leanback.ui.subject.components.TvDetailsBrowseRowLayout
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.details.TvDetailsAction
import org.jetbrains.compose.resources.stringResource

internal class TvPeopleSection(
    val id: String,
    val title: String,
    val itemKeys: List<String>,
    val loading: Boolean,
    val error: LoadError?,
    val retry: () -> Unit,
    val placeholder: @Composable (Modifier) -> Unit,
    val content: @Composable (Int, Modifier) -> Unit,
) {
    val visible get() = itemKeys.isNotEmpty() || loading || error != null
    val keys get() = itemKeys + when {
        error != null -> listOf("$id:retry")
        itemKeys.isEmpty() && loading -> listOf("$id:loading")
        else -> emptyList()
    }
}

internal fun <T : Any> peopleSection(
    id: String,
    title: String,
    items: LazyPagingItems<T>,
    placeholder: @Composable (Modifier) -> Unit,
    key: (T) -> String,
    content: @Composable (T, Modifier) -> Unit,
) = TvPeopleSection(
    id, title, List(items.itemCount) { index -> "$id:${items.peek(index)?.let(key) ?: "placeholder:$index"}" },
    items.loadState.refresh is LoadState.Loading || items.loadState.append is LoadState.Loading,
    ((items.loadState.refresh as? LoadState.Error) ?: (items.loadState.append as? LoadState.Error))
        ?.let { LoadError.fromException(it.error) },
    items::retry, placeholder,
) { index, modifier -> items[index]?.let { content(it, modifier) } ?: placeholder(modifier) }

@Composable
internal fun TvPeopleBrowseSection(
    section: TvPeopleSection,
    list: LazyListState,
    focused: Boolean,
    rowModifier: Modifier,
    anchor: (String) -> Modifier,
) {
    TvDetailsBrowseRowLayout(section.title, list, "people-${section.id}", focused,
        modifier = Modifier.testTag("tv-people-section:${section.id}"), rowModifier = rowModifier) {
        items(section.itemKeys.size, key = { section.itemKeys[it] }) { index ->
            section.content(index, anchor(section.itemKeys[index]))
        }
        if (section.error != null) item("${section.id}:retry") {
            Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(renderLoadErrorMessage(section.error), color = TvSubjectDetailsDefaults.SecondaryContent)
                TvDetailsAction(stringResource(Lang.settings_mediasource_retry), Icons.Rounded.Refresh, section.retry,
                    anchor("${section.id}:retry"))
            }
        } else if (section.loading) item("${section.id}:loading") {
            val focusableEntry = section.itemKeys.isEmpty()
            Row(if (focusableEntry) Modifier else Modifier.progressSemantics(),
                horizontalArrangement = Arrangement.spacedBy(TvSubjectDetailsDefaults.RowSpacing)) {
                repeat(3) { index ->
                    section.placeholder(if (index == 0 && focusableEntry) anchor("${section.id}:loading").progressSemantics().focusable() else Modifier)
                }
            }
        }
    }
}

@Composable
internal fun TvPeopleWorkCard(subject: PersonSubjectSummary, positions: String, modifier: Modifier, onClick: () -> Unit) {
    TvLandscapeCard(
        imageUrl = subject.imageLarge,
        title = subject.displayName,
        onClick = onClick,
        modifier = modifier,
        width = TvSubjectDetailsDefaults.RelatedCardWidth,
        overline = positions.takeIf { it.isNotBlank() },
    )
}
