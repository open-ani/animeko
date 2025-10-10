/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class MediaCacheDetailsPageViewModel(
    private val cacheId: String,
) : AbstractViewModel(), KoinComponent {
    private val cacheManager: MediaCacheManager by inject()
    private val mediaSourceManager: MediaSourceManager by inject()

    private val mediaCacheFlow = cacheManager.enabledStorages.flatMapLatest { storages ->
        combine(
            storages.map { storage ->
                storage.listFlow.map { caches ->
                    caches.find { it.cacheId == cacheId }
                }
            },
        ) { results ->
            results.firstNotNullOfOrNull { it }
        }
    }.shareInBackground()

    private val sourceInfoFlow
        get() = mediaCacheFlow
            .map { it?.origin }
            .flatMapLatest { media ->
                media?.mediaSourceId?.let { mediaSourceManager.infoFlowByMediaSourceId(it) } ?: flowOf(null)
            }

    val screenStateFlow =
        combine(sourceInfoFlow, mediaCacheFlow) { sourceInfo, mediaCache ->
            val originalMedia = mediaCache?.origin
            if (originalMedia == null) {
                MediaCacheDetailsScreenState(details = null)
            } else {
                val cachedMedia = mediaCache.getCachedMedia()
                MediaCacheDetailsScreenState(MediaDetails.from(originalMedia, sourceInfo, cachedMedia))
            }
        }.stateInBackground(MediaCacheDetailsScreenState(null))
}

data class MediaCacheDetailsScreenState(
    val details: MediaDetails?, // null for placeholder
)

@Composable
fun MediaCacheDetailsScreen(
    vm: MediaCacheDetailsPageViewModel,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    val screenState by vm.screenStateFlow.collectAsStateWithLifecycle()
    MediaCacheDetailsScreen(
        state = screenState,
        navigationIcon = navigationIcon,
        modifier = modifier,
        windowInsets = windowInsets,
    )
}

@Composable
fun MediaCacheDetailsScreen(
    state: MediaCacheDetailsScreenState,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            WindowDragArea {
                TopAppBar(
                    title = { Text("详情") },
                    navigationIcon = navigationIcon,
                    colors = AniThemeDefaults.topAppBarColors(),
                    windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                )
            }
        },
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .wrapContentWidth(align = Alignment.CenterHorizontally)
                .padding(paddingValues)
                .widthIn(max = 1200.dp),
        ) {
            AniAnimatedVisibility(
                visible = state.details != null,
                enter = LocalAniMotionScheme.current.animatedVisibility.screenEnter,
                exit = LocalAniMotionScheme.current.animatedVisibility.screenExit,
            ) {
                Surface(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(vertical = 16.dp),
                    color = ListItemDefaults.containerColor, // fill gap between items
                ) {
                    state.details?.let {
                        MediaDetailsLazyGrid(
                            it,
                            Modifier.fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}
