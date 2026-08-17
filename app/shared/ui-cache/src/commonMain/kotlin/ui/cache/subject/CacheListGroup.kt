/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.subject

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.tools.getOrZero
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_subject_cache
import me.him188.ani.app.ui.lang.cache_subject_cancel
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import org.jetbrains.compose.resources.stringResource


@Immutable
data class EpisodeCacheInfo(
    val sort: EpisodeSort,
    val ep: EpisodeSort?,
    val title: String,
    val watchStatus: UnifiedCollectionType,
    /**
     * 是否已经上映了
     */
    val hasPublished: Boolean,
    val _placeholder: Int = 0,
) {
    val sortString = sort.toString()

    companion object {
        @Stable
        val Placeholder = EpisodeCacheInfo(
            EpisodeSort(0),
            null,
            "",
            UnifiedCollectionType.DONE,
            false,
            -1,
        )
    }
}

@Composable
fun contentColorForWatchStatus(
    collectionType: UnifiedCollectionType,
    isKnownBroadcast: Boolean
) =
    if (collectionType.isDoneOrDropped() || !isKnownBroadcast) {
        LocalContentColor.current.stronglyWeaken()
    } else {
        LocalContentColor.current
    }

@Composable
fun EpisodeCacheActionIcon(
    isLoadingIndefinitely: Boolean,
    hasActionRunning: Boolean,
    cacheStatus: EpisodeCacheStatus?,
    canCache: Boolean,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier) {
    val progressIndicatorSize = 20.dp
    val strokeWidth = 2.dp
    val trackColor = MaterialTheme.colorScheme.primaryContainer
    if (isLoadingIndefinitely || hasActionRunning) {
        var showCancel by remember { mutableStateOf(false) }
        LaunchedEffect(showCancel) {
            if (showCancel) {
                delay(2000)
                showCancel = false
            }
        }
        Crossfade(showCancel) {
            if (it) {
                IconButton(
                    onClick = {
                        onCancel()
                        showCancel = false
                    },
                ) {
                    Icon(Icons.Rounded.Close, stringResource(Lang.cache_subject_cancel))
                }
            } else {
                if (hasActionRunning) {
                    IconButton({ showCancel = true }) {
                        CircularProgressIndicator(
                            Modifier.size(progressIndicatorSize),
                            strokeWidth = strokeWidth,
                            trackColor = trackColor,
                        )
                    }
                } else {
                    Box(Modifier.minimumInteractiveComponentSize()) {
                        CircularProgressIndicator(
                            Modifier.size(progressIndicatorSize),
                            strokeWidth = strokeWidth,
                            trackColor = trackColor,
                        )
                    }
                }
            }
        }
        return@Box
    }
    when (cacheStatus) {
        is EpisodeCacheStatus.Cached ->
            IconButton(onClick) {
                Icon(Icons.Rounded.DownloadDone, null)
            }

        is EpisodeCacheStatus.Caching -> {
            IconButton(onClick) {
                val progressIsUnspecified by remember(cacheStatus) {
                    derivedStateOf {
                        cacheStatus.progress.isUnspecified
                    }
                }
                if (progressIsUnspecified) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(progressIndicatorSize),
                        strokeWidth = strokeWidth,
                        trackColor = trackColor,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { cacheStatus.progress.getOrZero() },
                        modifier = Modifier.size(progressIndicatorSize),
                        strokeWidth = strokeWidth,
                        trackColor = trackColor,
                    )
                }
            }
        }

        EpisodeCacheStatus.NotCached -> {
            if (canCache) {
                CompositionLocalProvider(LocalContentColor providesDefault MaterialTheme.colorScheme.primary) {
                    IconButton(onClick) {
                        Icon(Icons.Rounded.Download, stringResource(Lang.cache_subject_cache))
                    }
                }
            }
        }

        null -> {}
    }
}
