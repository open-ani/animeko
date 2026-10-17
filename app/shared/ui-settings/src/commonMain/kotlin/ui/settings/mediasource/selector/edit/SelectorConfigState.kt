/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import me.him188.ani.app.ui.settings.mediasource.editor.AutoMatchConfigState
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceConfigState
import me.him188.ani.app.ui.settings.mediasource.rss.ConfigSection
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.utils.jsonpath.JsonPath
import me.him188.ani.utils.jsonpath.compileOrNull
import me.him188.ani.utils.xml.QueryParser
import me.him188.ani.utils.xml.parseSelectorOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * CSS 选择器数据源的编辑状态.
 */
@Stable
class SelectorConfigState(
    argumentsStorage: SaveableStorage<SelectorMediaSourceArguments>,
    allowEditState: State<Boolean>,
) : MediaSourceConfigState<SelectorMediaSourceArguments>(
    argumentsStorage, allowEditState, SelectorMediaSourceArguments.Default,
) {
    override var displayName: String by arguments.prop({ it.name }, { copy(name = it) })
    override var iconUrl: String by arguments.prop({ it.iconUrl }, { copy(iconUrl = it) })

    private val searchConfig: ConfigSection<SelectorSearchConfig> =
        arguments.sub({ it.searchConfig }, { copy(searchConfig = it) })

    var searchUrl: String by searchConfig.prop({ it.searchUrl }, { copy(searchUrl = it) })
    val searchUrlIsError by derivedStateOf { searchUrl.isBlank() }

    val autoMatch = AutoMatchConfigState(
        searchConfig.sub({ it.autoMatch }, { copy(autoMatch = it) }),
        tier = arguments.prop({ it.tier }, { copy(tier = it) }),
        searchCacheTtl = searchConfig.prop(
            { it.searchCacheTtl },
            { copy(searchCacheTtl = it.coerceAtLeast(0.milliseconds)) },
        ),
    )

    var userAgent: String by searchConfig.prop({ it.userAgent }, { copy(userAgent = it) })

    var rawBaseUrl: String by searchConfig.prop({ it.rawBaseUrl }, { copy(rawBaseUrl = it) })
    val baseUrlPlaceholder by derivedStateOf {
        if (rawBaseUrl.isBlank()) {
            SelectorSearchConfig.guessBaseUrl(searchUrl)
        } else {
            null
        }
    }

    var requestInterval by searchConfig.prop(
        { it.requestInterval },
        { copy(requestInterval = it.coerceAtLeast(0.milliseconds)) },
    )

    // region SubjectFormat

    val allSubjectFormats get() = SelectorSubjectFormat.entries
    var subjectFormatId by searchConfig.prop({ it.subjectFormatId }, { copy(subjectFormatId = it) })

    val subjectFormatA = SubjectFormatAConfig(
        searchConfig.sub({ it.selectorSubjectFormatA }, { copy(selectorSubjectFormatA = it) }),
    )

    @Stable
    class SubjectFormatAConfig(section: ConfigSection<SelectorSubjectFormatA.Config>) {
        var selectLists by section.prop({ it.selectLists }, { copy(selectLists = it) })
        val selectListsIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectLists) == null
        }
    }

    val subjectFormatIndex = SubjectFormatIndexedConfig(
        searchConfig.sub({ it.selectorSubjectFormatIndexed }, { copy(selectorSubjectFormatIndexed = it) }),
    )

    @Stable
    class SubjectFormatIndexedConfig(section: ConfigSection<SelectorSubjectFormatIndexed.Config>) {
        var selectNames by section.prop({ it.selectNames }, { copy(selectNames = it) })
        val selectNamesIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectNames) == null
        }

        var selectLinks by section.prop({ it.selectLinks }, { copy(selectLinks = it) })
        val selectLinksIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectLinks) == null
        }
    }

    val subjectFormatJsonPathIndex = SubjectFormatJsonPathIndexedConfig(
        searchConfig.sub(
            { it.selectorSubjectFormatJsonPathIndexed },
            { copy(selectorSubjectFormatJsonPathIndexed = it) },
        ),
    )

    @Stable
    class SubjectFormatJsonPathIndexedConfig(section: ConfigSection<SelectorSubjectFormatJsonPathIndexed.Config>) {
        var selectNames by section.prop({ it.selectNames }, { copy(selectNames = it) })
        val selectNamesIsError by derivedStateOf {
            JsonPath.compileOrNull(selectNames) == null
        }

        var selectLinks by section.prop({ it.selectLinks }, { copy(selectLinks = it) })
        val selectLinksIsError by derivedStateOf {
            JsonPath.compileOrNull(selectLinks) == null
        }
    }

    // endregion

    // region ChannelFormat

    var channelFormatId by searchConfig.prop({ it.channelFormatId }, { copy(channelFormatId = it) })
    val allChannelFormats get() = SelectorChannelFormat.entries

    val channelFormatIndexed = ChannelFormatIndexedConfig(
        searchConfig.sub({ it.selectorChannelFormatFlattened }, { copy(selectorChannelFormatFlattened = it) }),
    )

    @Stable
    class ChannelFormatIndexedConfig(section: ConfigSection<SelectorChannelFormatIndexGrouped.Config>) {
        var selectChannelNames by section.prop({ it.selectChannelNames }, { copy(selectChannelNames = it) })
        val selectChannelNamesIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectChannelNames) == null
        }
        var matchChannelName by section.prop({ it.matchChannelName }, { copy(matchChannelName = it) })
        val matchChannelNameIsError by derivedStateOf {
            if (matchChannelName.isEmpty()) false
            else Regex.parseOrNull(matchChannelName) == null
        }
        var selectEpisodeLists by section.prop({ it.selectEpisodeLists }, { copy(selectEpisodeLists = it) })
        val selectEpisodeListsIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectEpisodeLists) == null
        }
        var selectEpisodesFromList by section.prop({ it.selectEpisodesFromList }, { copy(selectEpisodesFromList = it) })
        val selectEpisodesFromListIsError by derivedStateOf {
            QueryParser.parseSelectorOrNull(selectEpisodesFromList) == null
        }
        var selectEpisodeLinksFromList by section.prop(
            { it.selectEpisodeLinksFromList },
            { copy(selectEpisodeLinksFromList = it) },
        )

        var matchEpisodeSortFromName by section.prop(
            { it.matchEpisodeSortFromName },
            { copy(matchEpisodeSortFromName = it) },
        )
        val matchEpisodeSortFromNameIsError by derivedStateOf {
            matchEpisodeSortFromName.isBlank() || !isValidRegex(matchEpisodeSortFromName)
        }
    }

    val channelFormatNoChannel = ChannelFormatNoChannelConfig(
        searchConfig.sub({ it.selectorChannelFormatNoChannel }, { copy(selectorChannelFormatNoChannel = it) }),
    )

    @Stable
    class ChannelFormatNoChannelConfig(section: ConfigSection<SelectorChannelFormatNoChannel.Config>) {
        var selectEpisodes by section.prop({ it.selectEpisodes }, { copy(selectEpisodes = it) })
        val selectEpisodesIsError by derivedStateOf { QueryParser.parseSelectorOrNull(selectEpisodes) == null }
        var selectEpisodeLinks by section.prop({ it.selectEpisodeLinks }, { copy(selectEpisodeLinks = it) })

        var matchEpisodeSortFromName by section.prop(
            { it.matchEpisodeSortFromName },
            { copy(matchEpisodeSortFromName = it) },
        )
        val matchEpisodeSortFromNameIsError by derivedStateOf {
            matchEpisodeSortFromName.isBlank() || !isValidRegex(matchEpisodeSortFromName)
        }
    }

    // endregion

    var defaultResolution by searchConfig.prop({ it.defaultResolution }, { copy(defaultResolution = it) })
    var defaultSubtitleLanguage by searchConfig.prop(
        { it.defaultSubtitleLanguage },
        { copy(defaultSubtitleLanguage = it) },
    )

    val selectMediaConfig = SelectMediaConfig(
        searchConfig.sub({ it.selectMedia }, { copy(selectMedia = it) }),
    )

    @Stable
    class SelectMediaConfig(section: ConfigSection<SelectorSearchConfig.SelectMediaConfig>) {
        var distinguishSubjectName by section.prop({ it.distinguishSubjectName }, { copy(distinguishSubjectName = it) })
        var distinguishChannelName by section.prop({ it.distinguishChannelName }, { copy(distinguishChannelName = it) })
    }

    val matchVideoConfig = MatchVideoConfig(
        searchConfig.sub({ it.matchVideo }, { copy(matchVideo = it) }),
    )

    @Stable
    class MatchVideoConfig(section: ConfigSection<SelectorSearchConfig.MatchVideoConfig>) {
        var matchVideoUrl by section.prop({ it.matchVideoUrl }, { copy(matchVideoUrl = it) })
        val matchVideoUrlIsError by derivedStateOf {
            matchVideoUrl.isBlank() || !isValidRegex(matchVideoUrl)
        }
        var cookies by section.prop({ it.cookies }, { copy(cookies = it) })

        var enableNestedUrl by section.prop({ it.enableNestedUrl }, { copy(enableNestedUrl = it) })
        var matchNestedUrl by section.prop({ it.matchNestedUrl }, { copy(matchNestedUrl = it) })
        val matchNestedUrlIsError by derivedStateOf {
            matchNestedUrl.isBlank() || !isValidRegex(matchNestedUrl)
        }

        val videoHeaders = HeadersConfig(
            section.sub({ it.addHeadersToVideo }, { copy(addHeadersToVideo = it) }),
        )

        @Stable
        class HeadersConfig(section: ConfigSection<SelectorSearchConfig.VideoHeaders>) {
            var referer by section.prop({ it.referer }, { copy(referer = it) })
        }
    }

    val searchConfigState = derivedStateOf {
        argumentsStorage.container?.searchConfig
    }
}
