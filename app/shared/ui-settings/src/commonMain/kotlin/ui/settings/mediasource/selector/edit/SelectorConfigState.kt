/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.edit

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceArguments
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatIndexGrouped
import me.him188.ani.app.domain.mediasource.web.format.SelectorChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormat
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatA
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatIndexed
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatJsonPathIndexed
import me.him188.ani.app.domain.mediasource.web.format.parseOrNull
import me.him188.ani.app.ui.settings.danmaku.isValidRegex
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.utils.jsonpath.JsonPath
import me.him188.ani.utils.jsonpath.compileOrNull
import me.him188.ani.utils.xml.QueryParser
import me.him188.ani.utils.xml.parseSelectorOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * 编辑配置
 */
@Stable
class SelectorConfigState(
    private val argumentsStorage: SaveableStorage<SelectorMediaSourceArguments>,
    private val allowEditState: State<Boolean>,
) {
    private val arguments by argumentsStorage.containerState
    val isLoading by derivedStateOf { arguments == null }

    val enableEdit by derivedStateOf {
        !isLoading && allowEditState.value
    }

    var displayName by argumentsStorage.prop(
        { it.name }, { copy(name = it) },
        SelectorMediaSourceArguments.Default.name,
    )

    val displayNameIsError by derivedStateOf { displayName.isBlank() }

    var iconUrl by argumentsStorage.prop(
        { it.iconUrl }, { copy(iconUrl = it) },
        SelectorMediaSourceArguments.Default.iconUrl,
    )

    var searchUrl by argumentsStorage.prop(
        { it.searchConfig.searchUrl }, { copy(searchConfig = searchConfig.copy(searchUrl = it)) },
        SelectorMediaSourceArguments.Default.searchConfig.searchUrl,
    )
    val searchUrlIsError by derivedStateOf { searchUrl.isBlank() }

    var searchUseOnlyFirstWord by argumentsStorage.prop(
        { it.searchConfig.searchUseOnlyFirstWord },
        { copy(searchConfig = searchConfig.copy(searchUseOnlyFirstWord = it)) },
        SelectorMediaSourceArguments.Default.searchConfig.searchUseOnlyFirstWord,
    )

    var searchRemoveSpecial by argumentsStorage.prop(
        { it.searchConfig.searchRemoveSpecial },
        { copy(searchConfig = searchConfig.copy(searchRemoveSpecial = it)) },
        SelectorMediaSourceArguments.Default.searchConfig.searchRemoveSpecial,
    )

    var searchUseSubjectNamesCount by argumentsStorage.prop(
        { it.searchConfig.searchUseSubjectNamesCount },
        { copy(searchConfig = searchConfig.copy(searchUseSubjectNamesCount = it)) },
        SelectorMediaSourceArguments.Default.searchConfig.searchUseSubjectNamesCount,
    )

    var rawBaseUrl by argumentsStorage.prop(
        { it.searchConfig.rawBaseUrl },
        { copy(searchConfig = searchConfig.copy(rawBaseUrl = it)) },
        SelectorMediaSourceArguments.Default.searchConfig.rawBaseUrl,
    )
    val baseUrlPlaceholder by derivedStateOf {
        if (rawBaseUrl.isBlank()) {
            SelectorSearchConfig.guessBaseUrl(searchUrl)
        } else {
            null
        }
    }

    var requestInterval by argumentsStorage.prop(
        { it.searchConfig.requestInterval },
        { copy(searchConfig = searchConfig.copy(requestInterval = it.coerceAtLeast(0.milliseconds))) },
        SelectorMediaSourceArguments.Default.searchConfig.requestInterval,
    )

    // region SubjectFormat

    val allSubjectFormats get() = SelectorSubjectFormat.entries
    var subjectFormatId by argumentsStorage.prop(
        { it.searchConfig.subjectFormatId }, { copy(searchConfig = searchConfig.copy(subjectFormatId = it)) },
        SelectorMediaSourceArguments.Default.searchConfig.subjectFormatId,
    )
    val subjectFormatA = SubjectFormatAConfig()

    @Stable
    inner class SubjectFormatAConfig {
        private fun <T : Any> prop(
            get: (SelectorSubjectFormatA.Config) -> T,
            set: SelectorSubjectFormatA.Config.(T) -> SelectorSubjectFormatA.Config,
        ) = argumentsStorage.prop(
            { it.searchConfig.selectorSubjectFormatA.let(get) },
            {
                copy(
                    searchConfig = searchConfig.copy(
                        selectorSubjectFormatA = searchConfig.selectorSubjectFormatA.set(it),
                    ),
                )
            },
            SelectorMediaSourceArguments.Default.searchConfig.selectorSubjectFormatA.let(get),
        )

        var selectLists by prop({ it.selectLists }, { copy(selectLists = it) })
        val selectListsIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectLists) == null
        }
        var preferShorterName by prop({ it.preferShorterName }, { copy(preferShorterName = it) })
    }

    val subjectFormatIndex = SubjectFormatIndexedConfig()

    inner class SubjectFormatIndexedConfig {
        private fun <T : Any> prop(
            get: (SelectorSubjectFormatIndexed.Config) -> T,
            set: SelectorSubjectFormatIndexed.Config.(T) -> SelectorSubjectFormatIndexed.Config,
        ) = argumentsStorage.prop(
            { it.searchConfig.selectorSubjectFormatIndexed.let(get) },
            {
                copy(
                    searchConfig = searchConfig.copy(
                        selectorSubjectFormatIndexed = searchConfig.selectorSubjectFormatIndexed.set(it),
                    ),
                )
            },
            SelectorMediaSourceArguments.Default.searchConfig.selectorSubjectFormatIndexed.let(get),
        )

        var selectNames by prop({ it.selectNames }, { copy(selectNames = it) })
        val selectNamesIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectNames) == null
        }

        var selectLinks by prop({ it.selectLinks }, { copy(selectLinks = it) })
        val selectLinksIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectLinks) == null
        }

        var preferShorterName by prop({ it.preferShorterName }, { copy(preferShorterName = it) })
    }

    val subjectFormatJsonPathIndex = SubjectFormatJsonPathIndexedConfig()

    inner class SubjectFormatJsonPathIndexedConfig {
        private fun <T : Any> prop(
            get: (SelectorSubjectFormatJsonPathIndexed.Config) -> T,
            set: SelectorSubjectFormatJsonPathIndexed.Config.(T) -> SelectorSubjectFormatJsonPathIndexed.Config,
        ) = argumentsStorage.prop(
            { it.searchConfig.selectorSubjectFormatJsonPathIndexed.let(get) },
            {
                copy(
                    searchConfig = searchConfig.copy(
                        selectorSubjectFormatJsonPathIndexed = searchConfig.selectorSubjectFormatJsonPathIndexed.set(it),
                    ),
                )
            },
            SelectorMediaSourceArguments.Default.searchConfig.selectorSubjectFormatJsonPathIndexed.let(get),
        )

        var selectNames by prop({ it.selectNames }, { copy(selectNames = it) })
        val selectNamesIsError by derivedStateOf {
            JsonPath.compileOrNull(selectNames) == null
        }

        var selectLinks by prop({ it.selectLinks }, { copy(selectLinks = it) })
        val selectLinksIsError by derivedStateOf {
            JsonPath.compileOrNull(selectLinks) == null
        }

        var preferShorterName by prop({ it.preferShorterName }, { copy(preferShorterName = it) })
    }

    // endregion

    // region ChannelFormat

    var channelFormatId by argumentsStorage.prop(
        { it.searchConfig.channelFormatId }, { copy(searchConfig = searchConfig.copy(channelFormatId = it)) },
        SelectorMediaSourceArguments.Default.searchConfig.channelFormatId,
    )
    val allChannelFormats get() = SelectorChannelFormat.entries

    val channelFormatIndexed = ChannelFormatIndexedConfig()

    @Stable
    inner class ChannelFormatIndexedConfig {
        private fun <T : Any> prop(
            get: (SelectorChannelFormatIndexGrouped.Config) -> T,
            set: SelectorChannelFormatIndexGrouped.Config.(T) -> SelectorChannelFormatIndexGrouped.Config,
        ) = argumentsStorage.prop(
            { it.searchConfig.selectorChannelFormatFlattened.let(get) },
            {
                copy(
                    searchConfig = searchConfig.copy(
                        selectorChannelFormatFlattened = searchConfig.selectorChannelFormatFlattened.set(it),
                    ),
                )
            },
            SelectorMediaSourceArguments.Default.searchConfig.selectorChannelFormatFlattened.let(get),
        )

        var selectChannelNames by prop({ it.selectChannelNames }, { copy(selectChannelNames = it) })
        val selectChannelNamesIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectChannelNames) == null
        }
        var matchChannelName by prop({ it.matchChannelName }, { copy(matchChannelName = it) })
        val matchChannelNameIsError by derivedStateOf {
            if (matchChannelName.isEmpty()) false
            else Regex.parseOrNull(matchChannelName) == null
        }
        var selectEpisodeLists by prop({ it.selectEpisodeLists }, { copy(selectEpisodeLists = it) })
        val selectEpisodeListsIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectEpisodeLists) == null
        }
        var selectEpisodesFromList by prop({ it.selectEpisodesFromList }, { copy(selectEpisodesFromList = it) })
        val selectEpisodesFromListIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectEpisodesFromList) == null
        }
        var selectEpisodeLinksFromList by prop(
            { it.selectEpisodeLinksFromList },
            { copy(selectEpisodeLinksFromList = it) },
        )

        var matchEpisodeSortFromName by prop({ it.matchEpisodeSortFromName }, { copy(matchEpisodeSortFromName = it) })
        val matchEpisodeSortFromNameIsError by derivedStateOf {
            matchEpisodeSortFromName.isBlank() || !isValidRegex(matchEpisodeSortFromName)
        }
    }

    val channelFormatNoChannel = ChannelFormatNoChannelConfig()

    @Stable
    inner class ChannelFormatNoChannelConfig {
        private fun <T : Any> prop(
            get: (SelectorChannelFormatNoChannel.Config) -> T,
            set: SelectorChannelFormatNoChannel.Config.(T) -> SelectorChannelFormatNoChannel.Config,
        ) = argumentsStorage.prop(
            { it.searchConfig.selectorChannelFormatNoChannel.let(get) },
            {
                copy(
                    searchConfig = searchConfig.copy(
                        selectorChannelFormatNoChannel = searchConfig.selectorChannelFormatNoChannel.set(it),
                    ),
                )
            },
            SelectorMediaSourceArguments.Default.searchConfig.selectorChannelFormatNoChannel.let(get),
        )

        var selectEpisodes by prop({ it.selectEpisodes }, { copy(selectEpisodes = it) })
        val selectEpisodesIsError by derivedStateOf { QueryParser.parseSelectorOrNull(selectEpisodes) == null }
        var selectEpisodeLinks by prop({ it.selectEpisodeLinks }, { copy(selectEpisodeLinks = it) })

        var matchEpisodeSortFromName by prop(
            { it.matchEpisodeSortFromName },
            { copy(matchEpisodeSortFromName = it) },
        )
        val matchEpisodeSortFromNameIsError by derivedStateOf {
            matchEpisodeSortFromName.isBlank() || !isValidRegex(matchEpisodeSortFromName)
        }
    }

    // endregion

    var defaultResolution by argumentsStorage.prop(
        { it.searchConfig.defaultResolution }, { copy(searchConfig = searchConfig.copy(defaultResolution = it)) },
        SelectorMediaSourceArguments.Default.searchConfig.defaultResolution,
    )
    var defaultSubtitleLanguage by argumentsStorage.prop(
        { it.searchConfig.defaultSubtitleLanguage },
        { copy(searchConfig = searchConfig.copy(defaultSubtitleLanguage = it)) },
        SelectorMediaSourceArguments.Default.searchConfig.defaultSubtitleLanguage,
    )
    var filterByEpisodeSort by argumentsStorage.prop(
        { it.searchConfig.filterByEpisodeSort }, { copy(searchConfig = searchConfig.copy(filterByEpisodeSort = it)) },
        SelectorMediaSourceArguments.Default.searchConfig.filterByEpisodeSort,
    )
    var filterBySubjectName by argumentsStorage.prop(
        { it.searchConfig.filterBySubjectName }, { copy(searchConfig = searchConfig.copy(filterBySubjectName = it)) },
        SelectorMediaSourceArguments.Default.searchConfig.filterBySubjectName,
    )


    val selectMediaConfig: SelectMediaConfig = SelectMediaConfig()

    @Stable
    inner class SelectMediaConfig {
        private fun <T : Any> prop(
            get: (SelectorSearchConfig.SelectMediaConfig) -> T,
            set: SelectorSearchConfig.SelectMediaConfig.(T) -> SelectorSearchConfig.SelectMediaConfig,
        ) = argumentsStorage.prop(
            { it.searchConfig.selectMedia.let(get) },
            {
                copy(
                    searchConfig = searchConfig.copy(
                        selectMedia = searchConfig.selectMedia.set(it),
                    ),
                )
            },
            SelectorMediaSourceArguments.Default.searchConfig.selectMedia.let(get),
        )

        var distinguishSubjectName by prop({ it.distinguishSubjectName }, { copy(distinguishSubjectName = it) })
        var distinguishChannelName by prop({ it.distinguishChannelName }, { copy(distinguishChannelName = it) })
    }

    val matchVideoConfig: MatchVideoConfig = MatchVideoConfig()

    @Stable
    inner class MatchVideoConfig {
        private fun <T : Any> prop(
            get: (SelectorSearchConfig.MatchVideoConfig) -> T,
            set: SelectorSearchConfig.MatchVideoConfig.(T) -> SelectorSearchConfig.MatchVideoConfig,
        ) = argumentsStorage.prop(
            { it.searchConfig.matchVideo.let(get) },
            {
                copy(
                    searchConfig = searchConfig.copy(
                        matchVideo = searchConfig.matchVideo.set(it),
                    ),
                )
            },
            SelectorMediaSourceArguments.Default.searchConfig.matchVideo.let(get),
        )

        var matchVideoUrl by prop(
            { it.matchVideoUrl }, { copy(matchVideoUrl = it) },
        )
        val matchVideoUrlIsError by derivedStateOf {
            matchVideoUrl.isBlank() || !isValidRegex(matchVideoUrl)
        }
        var cookies by prop(
            { it.cookies }, { copy(cookies = it) },
        )

        var enableNestedUrl by prop(
            { it.enableNestedUrl }, { copy(enableNestedUrl = it) },
        )
        var matchNestedUrl by prop(
            { it.matchNestedUrl }, { copy(matchNestedUrl = it) },
        )
        val matchNestedUrlIsError by derivedStateOf {
            matchNestedUrl.isBlank() || !isValidRegex(matchNestedUrl)
        }

        val videoHeaders = HeadersConfig()

        @Stable
        inner class HeadersConfig {
            private fun <T : Any> prop(
                get: (SelectorSearchConfig.VideoHeaders) -> T,
                set: SelectorSearchConfig.VideoHeaders.(T) -> SelectorSearchConfig.VideoHeaders,
            ) = this@MatchVideoConfig.prop(
                { it.addHeadersToVideo.let(get) },
                { copy(addHeadersToVideo = addHeadersToVideo.set(it)) },
            )

            var referer by prop(
                { it.referer }, { copy(referer = it) },
            )
            var userAgent by prop(
                { it.userAgent }, { copy(userAgent = it) },
            )
        }
    }

    val searchConfigState = derivedStateOf {
        argumentsStorage.container?.searchConfig
    }
}