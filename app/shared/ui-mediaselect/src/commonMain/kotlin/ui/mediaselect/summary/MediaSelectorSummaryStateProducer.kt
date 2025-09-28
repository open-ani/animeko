/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.summary

import kotlinx.coroutines.flow.*
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.MediaSourceInfoWithId
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.domain.media.selector.isPerfectMatch
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.platform.collections.tupleOf
import kotlin.time.Duration.Companion.seconds


/**
 * @see MediaSelectorSummary
 * @see me.him188.ani.app.domain.media.selector.MediaSelector.selectedMaybeExcludedMediaFlow
 */
class MediaSelectorSummaryStateProducer(
    selectedMaybeExcludedMediaFlow: Flow<MaybeExcludedMedia?>,
    mediaSourceResultsFlow: Flow<List<MediaSourceFetchResult>>,
    mediaSelectorSettingsFlow: Flow<MediaSelectorSettings>,
    mediaSources: Flow<List<MediaSourceInfoWithId>>,
) {
    private val sourceSummariesFlow = combine(mediaSourceResultsFlow, mediaSources) { results, sources ->
        tupleOf(results, sources)
    }.flatMapLatest { (results, sourcesSorted) ->
        combine(results.map { result ->
            result.state.map { it is MediaSourceFetchState.Completed }.distinctUntilChanged()
        }) { states ->
            results
                .asSequence()
                .filter { !it.sourceInfo.isSpecial }
                .filter { it.kind == MediaSourceKind.WEB } // FIXME: BT 的图标是 iconResourceId 展示的, 目前 UI 还没支持, 所以这里只显示 WEB 的
                .filter { states[results.indexOf(it)] }
                .sortedWith(
                    compareBy { source ->
                        sourcesSorted.indexOfFirst { it.instanceId == source.instanceId }
                    },
                )
                .map {
                    it.sourceInfo.toSummary()
                }
                .toList()
        }
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    val flow = combine(
        sourceSummariesFlow,
        mediaSelectorSettingsFlow,
        mediaSources,
    ) { sourceSummaries, mediaSelectorSettings, mediaSourceInstancesSorted ->
        tupleOf(sourceSummaries, mediaSelectorSettings, mediaSourceInstancesSorted)
    }.transformLatest { (sourceSummaries, mediaSelectorSettings, mediaSourceInstances) ->
        emitAll(
            selectedMaybeExcludedMediaFlow.map { selected ->
                when {
                    selected != null -> {
                        MediaSelectorSummary.Selected(
                            mediaSourceInstances.find { it.mediaSourceId == selected.original.mediaSourceId }
                                ?.info
                                ?.toSummary()
                                ?: MediaSelectorSourceSummary(
                                    sourceName = selected.original.mediaSourceId,
                                    sourceIconUrl = "",
                                ),
                            selected.original.originalTitle,
                            isPerfectMatch = selected.isPerfectMatch(),
                        )
                    }

                    mediaSelectorSettings.preferKind == MediaSourceKind.WEB -> {
                        MediaSelectorSummary.AutoSelecting(
                            sources = sourceSummaries,
                            estimate = if (mediaSelectorSettings.fastSelectWebKind) mediaSelectorSettings.fastSelectWebKindAllowNonPreferredDelay
                            else 10.seconds,
                        )
                    }

                    else -> {
                        MediaSelectorSummary.RequiresManualSelection(
                            sources = sourceSummaries,
                        )
                    }
                }
            },
        )
    }.distinctUntilChanged()
}

@OptIn(UnsafeOriginalMediaAccess::class)
val MediaSelector.selectedMaybeExcludedMediaFlow: Flow<MaybeExcludedMedia?>
    get() = this.selected.mapLatest { selected ->
        if (selected == null) {
            null
        } else {
            filteredCandidates.first() // No need to subscribe to flow change. When selected is updated, filteredCandidates should have already been updated. 
                .firstOrNull { it.original === selected } // identity check is enough and fast
        }
    }

private fun MediaSourceInfo.toSummary() =
    MediaSelectorSourceSummary(
        displayName,
        iconUrl ?: "",
    )
