/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.collection

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_collection_change_only
import me.him188.ani.app.ui.lang.subject_collection_collect
import me.him188.ani.app.ui.lang.subject_collection_delete
import me.him188.ani.app.ui.lang.subject_collection_doing
import me.him188.ani.app.ui.lang.subject_collection_done
import me.him188.ani.app.ui.lang.subject_collection_dropped
import me.him188.ani.app.ui.lang.subject_collection_keep
import me.him188.ani.app.ui.lang.subject_collection_mark_all_watched_action
import me.him188.ani.app.ui.lang.subject_collection_on_hold
import me.him188.ani.app.ui.lang.subject_collection_remove_confirm_short
import me.him188.ani.app.ui.lang.subject_collection_wish
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

enum class TvCollectionPrompt { Remove, MarkAllWatched }

val UnifiedCollectionType.tvCollectionEntryType: UnifiedCollectionType
    get() = takeUnless { it == UnifiedCollectionType.NOT_COLLECTED } ?: UnifiedCollectionType.WISH

/** The host supplies its list and focus anchors; content and pending visuals are shared. */
class TvCollectionOption(
    val key: String,
    val isEntry: Boolean = false,
    val focusable: Boolean = true,
    val content: @Composable (Modifier) -> Unit,
)

@Composable
fun UnifiedCollectionType.tvCollectionLabel(): String = stringResource(when (this) {
    UnifiedCollectionType.WISH -> Lang.subject_collection_wish
    UnifiedCollectionType.DOING -> Lang.subject_collection_doing
    UnifiedCollectionType.DONE -> Lang.subject_collection_done
    UnifiedCollectionType.ON_HOLD -> Lang.subject_collection_on_hold
    UnifiedCollectionType.DROPPED -> Lang.subject_collection_dropped
    UnifiedCollectionType.NOT_COLLECTED -> Lang.subject_collection_collect
})

/** State choices, removal confirmation and the watched follow-up use the same disabled rows. */
@Composable
fun tvCollectionOptions(
    collectionType: UnifiedCollectionType,
    busy: Boolean,
    prompt: TvCollectionPrompt?,
    onPromptChange: (TvCollectionPrompt?) -> Unit,
    onSetCollection: (UnifiedCollectionType) -> Unit,
    onMarkAllWatched: () -> Unit,
): List<TvCollectionOption> {
    val options = mutableListOf<TvCollectionOption>()
    fun action(key: String, label: String, isEntry: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
        options += TvCollectionOption(key, isEntry) { modifier ->
            TvOptionRow(label, modifier = modifier, selected = selected, enabled = !busy,
                compact = prompt == null, onClick = onClick)
        }
    }
    when (prompt) {
        TvCollectionPrompt.Remove -> {
            val message = stringResource(Lang.subject_collection_remove_confirm_short)
            options += TvCollectionOption("remove-description", focusable = false) { modifier ->
                Text(message, modifier.padding(10.dp), color = TvOptionDefaults.Content)
            }
            action("remove-confirm", stringResource(Lang.subject_collection_delete), isEntry = true) {
                onSetCollection(UnifiedCollectionType.NOT_COLLECTED)
            }
            action("remove-cancel", stringResource(Lang.subject_collection_keep)) { onPromptChange(null) }
        }
        TvCollectionPrompt.MarkAllWatched -> {
            action("mark-all", stringResource(Lang.subject_collection_mark_all_watched_action), isEntry = true,
                onClick = onMarkAllWatched)
            action("mark-ignore", stringResource(Lang.subject_collection_change_only)) { onPromptChange(null) }
        }
        null -> UnifiedCollectionType.entries.forEach { type ->
            if (type == UnifiedCollectionType.NOT_COLLECTED && collectionType == type) return@forEach
            val label = if (type == UnifiedCollectionType.NOT_COLLECTED) stringResource(Lang.subject_collection_delete)
                else type.tvCollectionLabel()
            action("collection:${type.name}", label, isEntry = type == collectionType.tvCollectionEntryType, selected = type == collectionType) {
                if (type == UnifiedCollectionType.NOT_COLLECTED) onPromptChange(TvCollectionPrompt.Remove)
                else onSetCollection(type)
            }
        }
    }
    return options
}
