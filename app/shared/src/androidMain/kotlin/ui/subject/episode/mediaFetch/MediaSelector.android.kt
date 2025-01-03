/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.mediaFetch

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.platform.annotations.TestOnly

@TestOnly
internal val previewMediaList = TestMediaList.run {
    listOf(
        CachedMedia(
            origin = this[0],
            cacheMediaSourceId = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID,
            download = ResourceLocation.LocalFile("file://test.txt"),
        ),
    ) + this
}

@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewMediaSelector() {
    val mediaSelector = rememberTestMediaSelectorPresentation(previewMediaList)
    ProvideFoundationCompositionLocalsForPreview {
        Surface {
            MediaSelectorView(
                state = mediaSelector,
                sourceResults = {
                    MediaSourceResultsView(
                        rememberTestMediaSourceResults(),
                        mediaSelector,
                        onRefresh = {},
                    )
                },
            )
        }
    }
}

@Composable
@OptIn(TestOnly::class)
private fun rememberTestMediaSelectorPresentation(previewMediaList: List<Media>) =
    rememberMediaSelectorState(rememberTestMediaSourceInfoProvider()) {
        DefaultMediaSelector(
            mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
            mediaListNotCached = MutableStateFlow(
                listOf(
                    CachedMedia(
                        origin = previewMediaList[0],
                        cacheMediaSourceId = MediaCacheManager.LOCAL_FS_MEDIA_SOURCE_ID,
                        download = ResourceLocation.LocalFile("file://test.txt"),
                    ),
                ) + previewMediaList,
            ),
            savedUserPreference = flowOf(MediaPreference.Empty),
            savedDefaultPreference = flowOf(
                MediaPreference.PlatformDefault.copy(
                    subtitleLanguageId = "CHS",
                ),
            ),
            mediaSelectorSettings = flowOf(MediaSelectorSettings.AllVisible),
        )
    }

@OptIn(TestOnly::class, MediaGroupBuilderApi::class)
@PreviewLightDark
@Composable
private fun PreviewMediaItemIncluded(modifier: Modifier = Modifier) = ProvideFoundationCompositionLocalsForPreview {
    MediaSelectorItem(
        MediaGroup("Test").apply {
            add(previewMediaList[0].let { MaybeExcludedMedia.Included(it) })
        },
        remember { MediaGroupState("test") },
        rememberTestMediaSourceInfoProvider(),
        selected = false,
        onSelect = {},
        preferredResolution = { null },
        onPreferResolution = {},
        preferredSubtitleLanguageId = { null },
        onPreferSubtitleLanguageId = {},
        modifier = modifier,
    )
}

@OptIn(TestOnly::class, MediaGroupBuilderApi::class)
@PreviewLightDark
@Composable
private fun PreviewMediaItemExcluded(modifier: Modifier = Modifier) = ProvideFoundationCompositionLocalsForPreview {
    MediaSelectorItem(
        MediaGroup("Test").apply {
            add(previewMediaList[0].let { MaybeExcludedMedia.Excluded(it, MediaExclusionReason.FromSequelSeason) })
        },
        remember { MediaGroupState("test") },
        rememberTestMediaSourceInfoProvider(),
        selected = false,
        onSelect = {},
        preferredResolution = { null },
        onPreferResolution = {},
        preferredSubtitleLanguageId = { null },
        onPreferSubtitleLanguageId = {},
        modifier = modifier,
    )
}
