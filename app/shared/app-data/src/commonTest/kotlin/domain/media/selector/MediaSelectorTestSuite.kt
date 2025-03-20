/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.data.models.subject.SubjectSeriesInfoBuilder
import me.him188.ani.app.data.models.subject.toBuilder
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.test.DynamicTestsBuilder
import me.him188.ani.utils.platform.collections.copyPut
import me.him188.ani.utils.platform.collections.toImmutable
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmName
import kotlin.random.Random

/**
 * DSL for testing [DefaultMediaSelector].
 */
sealed class MediaSelectorTestSuite {
    val random = Random(42)

    val initApi = InitApi()
    val preferenceApi = PreferenceApi()

    /**
     * initializes [MediaSelectorContext].
     */
    inner class InitApi {
        lateinit var subjectName: String
        var aliases: MutableList<String> = mutableListOf()
        var seriesInfo: SubjectSeriesInfo = SubjectSeriesInfo.Fallback
        var episodeSort: EpisodeSort = EpisodeSort(1)
        var episodeEp: EpisodeSort = EpisodeSort(1)

        fun aliases(vararg aliases: String) {
            this.aliases.addAll(aliases)
        }

        var seasonSort: Int
            get() = seriesInfo.seasonSort
            set(value) {
                seriesInfo = seriesInfo.copy(seasonSort = value)
            }

        fun seriesInfo(
            seasonSort: Int = this.seasonSort, block: SubjectSeriesInfoBuilder.() -> Unit
        ) {
            seriesInfo = this.seriesInfo.toBuilder().also { it.seasonSort = seasonSort }.apply(block).build()
        }
    }

    inline fun initSubject(
        subjectName: String,
        block: InitApi.() -> Unit = {}
    ) {
        val init = initApi.apply(block)
        init.subjectName = subjectName

        preferenceApi.mediaSelectorContext.value = createMediaSelectorContextFromEmpty(
            true,
            subjectInfo = SubjectInfo.Empty.copy(
                nameCn = init.subjectName,
                aliases = init.aliases.toList(),
            ),
            episodeInfo = EpisodeInfo.Empty.copy(sort = init.episodeSort, ep = init.episodeEp),
            subjectSeriesInfo = init.seriesInfo,
        )
        preferenceApi.mediaSelectorSettings.value = MediaSelectorSettings.Default
        preferenceApi.savedUserPreference.value = DEFAULT_PREFERENCE
        preferenceApi.savedDefaultPreference.value = DEFAULT_PREFERENCE
    }

    inner class PreferenceApi {
        val savedUserPreference = MutableStateFlow(DEFAULT_PREFERENCE)
        val savedDefaultPreference = MutableStateFlow(DEFAULT_PREFERENCE)
        val mediaSelectorSettings = MutableStateFlow(MediaSelectorSettings.Default)
        val mediaSelectorContext = MutableStateFlow(
            createMediaSelectorContextFromEmpty(),
        )

        var sourceTiers
            get() = mediaSelectorContext.value.mediaSourceTiers
            set(value) {
                mediaSelectorContext.value = mediaSelectorContext.value.copy(
                    mediaSourceTiers = value,
                )
            }


        fun setSubtitlePreferences(
            preferences: MediaSelectorSubtitlePreferences = getCurrentSubtitlePreferences()
        ) {
            mediaSelectorContext.value = mediaSelectorContext.value.run {
                copy(subtitlePreferences = preferences)
            }
        }

        private fun getCurrentSubtitlePreferences() = (mediaSelectorContext.value.subtitlePreferences
            ?: MediaSelectorSubtitlePreferences.AllNormal)

        fun setSubtitlePreference(
            key: SubtitleKind,
            value: SubtitleKindPreference
        ) {
            setSubtitlePreferences(
                MediaSelectorSubtitlePreferences(
                    getCurrentSubtitlePreferences().values.copyPut(key, value).toImmutable(),
                ),
            )
        }

        fun preferKind(kind: MediaSourceKind?) {
            mediaSelectorSettings.value = mediaSelectorSettings.value.copy(
                preferKind = kind,
            )
        }
    }


    private var mediaIdCounter: Int = 0
    fun media(
        sourceId: String = SOURCE_DMHY,
        resolution: String = "1080P",
        alliance: String = "字幕组",
        size: FileSize = 1.megaBytes,
        publishedTime: Long = 0,
        subtitleLanguages: List<String> = listOf(
            SubtitleLanguage.ChineseSimplified,
            SubtitleLanguage.ChineseTraditional,
        ).map { it.id },
        location: MediaSourceLocation = MediaSourceLocation.Online,
        kind: MediaSourceKind = MediaSourceKind.BitTorrent,
        episodeRange: EpisodeRange = EpisodeRange.single(EpisodeSort(1)),
        subtitleKind: SubtitleKind? = null,
        extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
        id: Int = mediaIdCounter++,
        originalTitle: String = "[字幕组] 孤独摇滚 $id",
        subjectName: String? = null,
        episodeName: String? = null,
        mediaId: String = "$sourceId.$id",
    ): DefaultMedia {
        return createTestDefaultMedia(
            mediaId = mediaId,
            mediaSourceId = sourceId,
            originalTitle = originalTitle,
            download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:$id"),
            originalUrl = "https://example.com/$id",
            publishedTime = publishedTime,
            episodeRange = episodeRange,
            properties = createTestMediaProperties(
                subjectName = subjectName,
                episodeName = episodeName,
                subtitleLanguageIds = subtitleLanguages,
                resolution = resolution,
                alliance = alliance,
                size = size,
                subtitleKind = subtitleKind,
            ),
            location = location,
            kind = kind,
            extraFiles = extraFiles,
        )
    }

    companion object {
        val DEFAULT_PREFERENCE = MediaPreference.Empty.copy(
            fallbackResolutions = listOf(
                Resolution.R2160P,
                Resolution.R1440P,
                Resolution.R1080P,
                Resolution.R720P,
            ).map { it.id },
            fallbackSubtitleLanguageIds = listOf(
                SubtitleLanguage.ChineseSimplified,
                SubtitleLanguage.ChineseTraditional,
            ).map { it.id },
        )

        const val SOURCE_DMHY = "dmhy"
        const val SOURCE_MIKAN = "mikan"

        @Suppress("SameParameterValue", "INVISIBLE_REFERENCE")
        @kotlin.internal.LowPriorityInOverloadResolution
        fun createMediaSelectorContextFromEmpty(
            subjectCompleted: Boolean = false,
            mediaSourcePrecedence: List<String> = emptyList(),
            subtitleKindFilters: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.AllNormal,
            subjectSequelNames: Set<String> = emptySet(),
            subjectInfo: SubjectInfo = SubjectInfo.Empty,
            episodeInfo: EpisodeInfo = EpisodeInfo.Empty,
            mediaSelectorSourceTiers: MediaSelectorSourceTiers = MediaSelectorSourceTiers.Empty,
        ) = createMediaSelectorContextFromEmpty(
            subjectCompleted = subjectCompleted,
            mediaSourcePrecedence = mediaSourcePrecedence,
            subtitleKindFilters = subtitleKindFilters,
            subjectSeriesInfo = SubjectSeriesInfo.Fallback.copy(sequelSubjectNames = subjectSequelNames),
            subjectInfo = subjectInfo,
            episodeInfo = episodeInfo,
            mediaSelectorSourceTiers = mediaSelectorSourceTiers,
        )

        @Suppress("SameParameterValue")
        fun createMediaSelectorContextFromEmpty(
            subjectCompleted: Boolean = false,
            mediaSourcePrecedence: List<String> = emptyList(),
            subtitleKindFilters: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.AllNormal,
            subjectSeriesInfo: SubjectSeriesInfo = SubjectSeriesInfo.Fallback,
            subjectInfo: SubjectInfo = SubjectInfo.Empty,
            episodeInfo: EpisodeInfo = EpisodeInfo.Empty,
            mediaSelectorSourceTiers: MediaSelectorSourceTiers = MediaSelectorSourceTiers.Empty,
        ) =
            MediaSelectorContext(
                subjectFinished = subjectCompleted,
                mediaSourcePrecedence = mediaSourcePrecedence,
                subtitlePreferences = subtitleKindFilters,
                subjectSeriesInfo = subjectSeriesInfo,
                subjectInfo = subjectInfo,
                episodeInfo = episodeInfo,
                mediaSourceTiers = mediaSelectorSourceTiers,
            )
    }
}


/**
 * 使用 [List] 存储待选 [me.him188.ani.datasources.api.Media].
 */
class SimpleMediaSelectorTestSuite : MediaSelectorTestSuite() {
    val mediaApi = MediaApi()

    inner class MediaApi {
        val mediaList: MutableStateFlow<MutableList<DefaultMedia>> = MutableStateFlow(mutableListOf())

        fun addMedia(vararg media: DefaultMedia) {
            mediaList.value.addAll(media)
        }

        fun addMedia(media: DefaultMedia): DefaultMedia {
            mediaList.value.add(media)
            return media
        }

        fun shuffle() {
            mediaList.value.shuffle(random)
        }

        fun addSimpleWebMedia(
            subjectName: String,
            episodeSort: EpisodeSort = EpisodeSort(1),
        ) {
            addMedia(
                media(
                    // kept
                    alliance = "简中",
                    episodeRange = EpisodeRange.single(episodeSort), kind = MediaSourceKind.WEB,
                    subjectName = subjectName,
                    originalTitle = "$subjectName $episodeSort",
                    subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
                ),
            )
        }
    }

    val selector = DefaultMediaSelector(
        mediaSelectorContextNotCached = preferenceApi.mediaSelectorContext,
        mediaListNotCached = mediaApi.mediaList,
        savedUserPreference = preferenceApi.savedUserPreference,
        savedDefaultPreference = preferenceApi.savedDefaultPreference,
        enableCaching = false,
        mediaSelectorSettings = preferenceApi.mediaSelectorSettings,
    )
}

/**
 * Default state:
 *
 * - [MediaSourceTier] are unspecified.
 *
 * @see buildTestMediaFetchSession
 */
class FetchMediaSelectorTestSuite(
    private val testDispatcher: CoroutineContext,
) : MediaSelectorTestSuite() {
    private lateinit var fetchSession: TestMediaFetchSession<*>

    /**
     * 配置 [MediaFetchSession], 并且在后台启动, 开始收集结果.
     *
     * 必须在 [initSubject] 之后调用.
     */
    context(scope: TestScope)
    fun <R> configureFetchSession(block: TestMediaFetchSessionBuilder.() -> R): TestMediaFetchSession<R> {
        return buildTestMediaFetchSession(
            dispatcher = testDispatcher,
        ) {
            request {
                val mediaSelectorContext = preferenceApi.mediaSelectorContext.value
                takeFrom(
                    MediaFetchRequest.create(
                        mediaSelectorContext.subjectInfo!!,
                        mediaSelectorContext.episodeInfo!!,
                    ),
                )
            }

            block()
        }.also {
            fetchSession = it
            it.session.startInBackground()
        }
    }

    val selector by lazy {
        DefaultMediaSelector(
            mediaSelectorContextNotCached = preferenceApi.mediaSelectorContext,
            mediaListNotCached = fetchSession.session.cumulativeResults,
            savedUserPreference = preferenceApi.savedUserPreference,
            savedDefaultPreference = preferenceApi.savedDefaultPreference,
            enableCaching = false,
            mediaSelectorSettings = preferenceApi.mediaSelectorSettings,
            flowCoroutineContext = testDispatcher,
        )
    }

    context(scope: TestScope)
    fun MediaFetchSession.startInBackground() {
        scope.backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            cumulativeResults.collect()
        }
    }

//
//    fun createSelector(
//        fetchSession: MediaFetchSession,
//    ): DefaultMediaSelector = DefaultMediaSelector(
//        mediaSelectorContextNotCached = preferenceApi.mediaSelectorContext,
//        mediaListNotCached = fetchSession.cumulativeResults,
//        savedUserPreference = preferenceApi.savedUserPreference,
//        savedDefaultPreference = preferenceApi.savedDefaultPreference,
//        enableCaching = false,
//        mediaSelectorSettings = preferenceApi.mediaSelectorSettings,
//    )
}

///////////////////////////////////////////////////////////////////////////
// Source Tiers
///////////////////////////////////////////////////////////////////////////

fun MediaSelectorTestSuite.setSourceTier(sourceId: String, tier: Int?) = setSourceTier(sourceId, tier?.toUInt())

@JvmName("setSourceTierUInt")
fun MediaSelectorTestSuite.setSourceTier(sourceId: String, tier: UInt?) =
    setSourceTier(sourceId, tier?.let { MediaSourceTier(it) })

fun MediaSelectorTestSuite.setSourceTier(sourceId: String, tier: MediaSourceTier?) {
    val oldTiers = preferenceApi.mediaSelectorContext.value.mediaSourceTiers
    val newMap = oldTiers?.tiers.orEmpty().toMutableMap()
    if (tier != null) {
        newMap[sourceId] = tier
    } else {
        newMap.remove(sourceId)
    }
    preferenceApi.mediaSelectorContext.value = preferenceApi.mediaSelectorContext.value.copy(
        mediaSourceTiers = MediaSelectorSourceTiers(newMap) {
            // fallback function if not found
            MediaSourceTier.Fallback
        },
    )
}

@JvmName("setSourceTiersPairStringInt")
fun MediaSelectorTestSuite.setSourceTiers(vararg pairs: Pair<String, Int?>) =
    setSourceTiers(*pairs.map { it.first to it.second?.toUInt() }.toTypedArray())

/**
 * Sets tiers for multiple sources at once.
 */
@JvmName("setSourceTiersPairStringUInt")
fun MediaSelectorTestSuite.setSourceTiers(vararg pairs: Pair<String, UInt?>) {
    val oldTiers = preferenceApi.mediaSelectorContext.value.mediaSourceTiers
    val newMap = oldTiers?.tiers.orEmpty().toMutableMap()
    for ((sourceId, tier) in pairs) {
        if (tier == null) {
            newMap.remove(sourceId)
        } else {
            newMap[sourceId] = MediaSourceTier(tier)
        }
    }
    preferenceApi.mediaSelectorContext.value = preferenceApi.mediaSelectorContext.value.copy(
        mediaSourceTiers = MediaSelectorSourceTiers(newMap) {
            MediaSourceTier.Fallback
        },
    )
}

fun MediaSelectorTestSuite.getMediaSourceTier(sourceId: String) = preferenceApi.sourceTiers?.tiers?.get(sourceId)

context(suite: MediaSelectorTestSuite)
var Handle.tier: Int?
    get() = suite.getMediaSourceTier(instance.mediaSourceId)?.value?.toInt()
    set(value) {
        suite.setSourceTier(instance.mediaSourceId, value?.toUInt())
    }


///////////////////////////////////////////////////////////////////////////
// DSL Runners
///////////////////////////////////////////////////////////////////////////


fun runSimpleMediaSelectorTestSuite(
    buildTest: SimpleMediaSelectorTestSuite.() -> Unit = {},
    thenCheck: suspend SimpleMediaSelectorTestSuite.() -> Unit
): TestResult = runTest {
    SimpleMediaSelectorTestSuite().apply(buildTest).thenCheck()
}

fun runFetchMediaSelectorTestSuite(
    buildTest: context(TestScope) FetchMediaSelectorTestSuite.() -> Unit = {},
    thenCheck: suspend context(TestScope) FetchMediaSelectorTestSuite.() -> Unit
): TestResult = runTest {
    FetchMediaSelectorTestSuite(this.coroutineContext[ContinuationInterceptor]!!).apply { buildTest() }.thenCheck()
}

inline fun DynamicTestsBuilder.addSimpleMediaSelectorTest(
    name: String? = null,
    crossinline buildTest: SimpleMediaSelectorTestSuite.() -> Unit = {},
    crossinline runTest: suspend SimpleMediaSelectorTestSuite.() -> Unit,
) {
    val suite = SimpleMediaSelectorTestSuite().apply(buildTest)
    add(name ?: suite.initApi.subjectName) {
        runBlocking {
            runTest(suite)
        }
    }
}

suspend inline fun SimpleMediaSelectorTestSuite.assertMedias(block: MaybeExcludedMediaAssertions.() -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    selector.filteredCandidates.first().assert(block)
}
