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
import androidx.compose.material3.BottomSheetDefaults
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
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceInfo
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
    }

    private val originMediaFlow = mediaCacheFlow.map { it?.origin }

    val media by originMediaFlow.produceState(null)
    val sourceInfo: MediaSourceInfo? by originMediaFlow.flatMapLatest { media ->
        media?.mediaSourceId?.let { mediaSourceManager.infoFlowByMediaSourceId(it) } ?: flowOf(null)
    }.produceState(null)
}

@Composable
fun MediaCacheDetailsScreen(
    vm: MediaCacheDetailsPageViewModel,
    navigationIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
) {
    MediaCacheDetailsScreen(
        media = vm.media,
        sourceInfo = vm.sourceInfo,
        navigationIcon = navigationIcon,
        modifier = modifier,
        windowInsets = windowInsets,
    )
}

@Composable
fun MediaCacheDetailsScreen(
    media: Media?,
    sourceInfo: MediaSourceInfo?,
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
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(paddingValues)
                    .widthIn(max = BottomSheetDefaults.SheetMaxWidth),
            ) {
                Surface(Modifier.fillMaxHeight()) {
                    AniAnimatedVisibility(
                        visible = media != null,
                        enter = LocalAniMotionScheme.current.animatedVisibility.screenEnter,
                        exit = LocalAniMotionScheme.current.animatedVisibility.screenExit,
                    ) {
                        media?.let {
                            Surface(
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(vertical = 16.dp),
                                color = ListItemDefaults.containerColor, // fill gap between items
                            ) {
                                MediaDetailsLazyGrid(
                                    it,
                                    sourceInfo = sourceInfo,
                                    Modifier.fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
