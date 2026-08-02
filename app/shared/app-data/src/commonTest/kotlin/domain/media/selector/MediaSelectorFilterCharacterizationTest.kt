/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.selector.testFramework.assertMedias
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.test.DisabledOnNative
import me.him188.ani.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals

@TestContainer
@DisabledOnNative // TODO: ContextParameters crashes on Native
class MediaSelectorFilterCharacterizationTest {
    ///////////////////////////////////////////////////////////////////////////
    // FILT-05
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `FILT-05 用户按条目 showWithoutSubtitle 在过滤阶段被忽略`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚")
            preferenceApi.savedUserPreference.value =
                preferenceApi.savedUserPreference.value.copy(showWithoutSubtitle = true)
            preferenceApi.savedDefaultPreference.value =
                preferenceApi.savedDefaultPreference.value.copy(showWithoutSubtitle = false)
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.WEB,
                    subjectName = "孤独摇滚",
                    subtitleLanguages = emptyList(),
                ),
            )
        },
    ) {
        // PINNED: FILT-05
        assertMedias {
            single().assert(
                included = false,
                exclusionReason = MediaExclusionReason.MediaWithoutSubtitle,
            )
        }
    }

    @Test
    fun `FILT-05 全局默认 showWithoutSubtitle 在过滤阶段生效`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚")
            preferenceApi.savedUserPreference.value =
                preferenceApi.savedUserPreference.value.copy(showWithoutSubtitle = false)
            preferenceApi.savedDefaultPreference.value =
                preferenceApi.savedDefaultPreference.value.copy(showWithoutSubtitle = true)
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.WEB,
                    subjectName = "孤独摇滚",
                    subtitleLanguages = emptyList(),
                ),
            )
        },
    ) {
        // PINNED: FILT-05
        assertMedias {
            single().assert(included = true)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // MERGE-01
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `MERGE-01 trySelectDefault 使用全局默认 fallbackSubtitleLanguageIds`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚")
            preferenceApi.savedUserPreference.value = preferenceApi.savedUserPreference.value.copy(
                fallbackSubtitleLanguageIds = listOf(SubtitleLanguage.ChineseTraditional.id),
            )
            preferenceApi.savedDefaultPreference.value = preferenceApi.savedDefaultPreference.value.copy(
                fallbackSubtitleLanguageIds = listOf(SubtitleLanguage.ChineseSimplified.id),
            )
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.WEB,
                    subjectName = "孤独摇滚",
                    subtitleLanguages = listOf(SubtitleLanguage.ChineseTraditional.id),
                    mediaId = "dmhy.cht",
                ),
                media(
                    kind = MediaSourceKind.WEB,
                    subjectName = "孤独摇滚",
                    subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
                    mediaId = "dmhy.chs",
                ),
            )
        },
    ) {
        // PINNED: MERGE-01(其余字段恒来自全局默认)
        // fallbackSubtitleLanguageIds 不参与 filterMediaList (那是 FILT-05 的口径, 相关字段是 showWithoutSubtitle),
        // 它是在 newPreferences = savedDefaultPreference.copy(四个 finalSelected) 里被带进
        // findUsingPreferenceFromCandidates 的, 因此用户按条目偏好里的该字段被忽略.
        assertEquals("dmhy.chs", selector.trySelectDefault()?.mediaId)
    }

    ///////////////////////////////////////////////////////////////////////////
    // FILT-11
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `FILT-11 完结番无字幕 BT 单集归因为 SingleEpisodeForCompleteSubject`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚")
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.BitTorrent,
                    subjectName = "孤独摇滚",
                    subtitleLanguages = emptyList(),
                    episodeRange = EpisodeRange.single(EpisodeSort(1)),
                ),
            )
        },
    ) {
        // PINNED: FILT-11
        assertMedias {
            single().assert(
                included = false,
                exclusionReason = MediaExclusionReason.SingleEpisodeForCompleteSubject(
                    episodeRange = EpisodeRange.single(EpisodeSort(1)),
                ),
            )
        }
    }

    @Test
    fun `FILT-11 无字幕续集名 WEB 归因为 MediaWithoutSubtitle`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚") {
                seriesInfo(seasonSort = 1) {
                    sequel("孤独摇滚 第二季")
                }
            }
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.WEB,
                    subjectName = "孤独摇滚 第二季",
                    subtitleLanguages = emptyList(),
                    mediaId = "dmhy.raw",
                ),
                media(
                    kind = MediaSourceKind.WEB,
                    subjectName = "孤独摇滚 第二季",
                    subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
                    mediaId = "dmhy.subbed",
                ),
            )
        },
    ) {
        // PINNED: FILT-11 无字幕 > 季度/续集
        assertMedias {
            onSingle(mediaId = "dmhy.raw").assert(
                included = false,
                exclusionReason = MediaExclusionReason.MediaWithoutSubtitle,
            )
            onSingle(mediaId = "dmhy.subbed").assert(
                included = false,
                exclusionReason = MediaExclusionReason.FromSequelSeason,
            )
        }
    }

    @Test
    fun `FILT-11 无字幕优先于平台字幕类型 HIDE`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚")
            preferenceApi.setSubtitlePreference(SubtitleKind.EMBEDDED, SubtitleKindPreference.HIDE)
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.WEB,
                    subjectName = "孤独摇滚",
                    subtitleLanguages = emptyList(),
                    subtitleKind = SubtitleKind.EMBEDDED,
                ),
            )
        },
    ) {
        // PINNED: FILT-11 无字幕 > 平台字幕类型 HIDE. 两条排除条件同时满足, 归因于顺序靠前的无字幕.
        assertMedias {
            single().assert(
                included = false,
                exclusionReason = MediaExclusionReason.MediaWithoutSubtitle,
            )
        }
    }

    @Test
    fun `FILT-11 平台字幕类型 HIDE 优先于续集季度`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚") {
                seriesInfo(seasonSort = 1) {
                    sequel("孤独摇滚 第二季")
                }
            }
            preferenceApi.setSubtitlePreference(SubtitleKind.EMBEDDED, SubtitleKindPreference.HIDE)
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.WEB,
                    subjectName = "孤独摇滚 第二季",
                    subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
                    subtitleKind = SubtitleKind.EMBEDDED,
                ),
            )
        },
    ) {
        // PINNED: FILT-11 平台字幕类型 HIDE > 季度/续集
        assertMedias {
            single().assert(
                included = false,
                exclusionReason = MediaExclusionReason.UnsupportedByPlatformPlayer,
            )
        }
    }

    @Test
    fun `FILT-11 续集季度优先于 WEB 标题匹配`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚") {
                // 续集名与本条目名毫无字面重合, 因此该 media 同时也过不了 WEB 的 ContainsSubjectName
                seriesInfo(seasonSort = 1) {
                    sequel("转生史莱姆")
                }
            }
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.WEB,
                    subjectName = "转生史莱姆",
                    subtitleLanguages = listOf(SubtitleLanguage.ChineseSimplified.id),
                ),
            )
        },
    ) {
        // PINNED: FILT-11 季度/续集 > WEB 标题匹配. 若两段被重排, 归因会变成 SubjectNameMismatch.
        assertMedias {
            single().assert(
                included = false,
                exclusionReason = MediaExclusionReason.FromSequelSeason,
            )
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // FILT-02
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `FILT-02 完结番隐藏单集不影响 WEB 单集`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚")
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.WEB,
                    subjectName = "孤独摇滚",
                    episodeRange = EpisodeRange.single(EpisodeSort(1)),
                ),
            )
        },
    ) {
        // PINNED: FILT-02
        assertMedias {
            single().assert(included = true)
        }
    }

    @Test
    fun `FILT-02 完结番 BT episodeRange 为 null 也被排除`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚")
            mediaApi.addMedia(
                createTestDefaultMedia(
                    mediaId = "dmhy.null-range",
                    mediaSourceId = "dmhy",
                    originalTitle = "[字幕组] 孤独摇滚",
                    download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
                    originalUrl = "https://example.com/1",
                    publishedTime = 0,
                    episodeRange = null,
                    properties = createTestMediaProperties(subjectName = "孤独摇滚"),
                    location = MediaSourceLocation.Online,
                    kind = MediaSourceKind.BitTorrent,
                ),
            )
        },
    ) {
        // PINNED: FILT-02
        assertMedias {
            single().assert(
                included = false,
                exclusionReason = MediaExclusionReason.SingleEpisodeForCompleteSubject(episodeRange = null),
            )
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // FILT-01
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `FILT-01 无字幕缓存不被排除`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚")
            preferenceApi.savedDefaultPreference.value =
                preferenceApi.savedDefaultPreference.value.copy(showWithoutSubtitle = false)
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.LocalCache,
                    location = MediaSourceLocation.Local,
                    subjectName = "孤独摇滚",
                    subtitleLanguages = emptyList(),
                ),
            )
        },
    ) {
        // PINNED: FILT-01
        assertMedias {
            single().assert(included = true)
        }
    }

    @Test
    fun `FILT-01 续集名缓存不被排除`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚") {
                seriesInfo(seasonSort = 1) {
                    sequel("孤独摇滚 第二季")
                }
            }
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.LocalCache,
                    location = MediaSourceLocation.Local,
                    subjectName = "孤独摇滚 第二季",
                ),
            )
        },
    ) {
        // PINNED: FILT-01
        assertMedias {
            single().assert(included = true)
        }
    }

    @Test
    fun `FILT-01 完结番单集缓存不被排除`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("孤独摇滚")
            mediaApi.addMedia(
                media(
                    kind = MediaSourceKind.LocalCache,
                    location = MediaSourceLocation.Local,
                    subjectName = "孤独摇滚",
                    episodeRange = EpisodeRange.single(EpisodeSort(1)),
                    mediaId = "local.single",
                ),
                // 对照: 同样是完结番单集, 只是 kind=BitTorrent, 会被完结番单集规则排除.
                media(
                    kind = MediaSourceKind.BitTorrent,
                    subjectName = "孤独摇滚",
                    episodeRange = EpisodeRange.single(EpisodeSort(1)),
                    mediaId = "dmhy.single",
                ),
            )
        },
    ) {
        // PINNED: FILT-01 + FILT-02(双重保护, 单独改 :108 不可判别)
        // 完结番单集分支自带 media.kind == BitTorrent 约束, 所以就算删掉 LocalCache 豁免,
        // 本用例的缓存媒体仍然会 included. 这里钉的是双重保护下的 outcome, 并用下面的 BT 对照
        // 说明"同配置下只有 kind 不同就会被排除".
        assertMedias {
            onSingle(mediaId = "local.single").assert(included = true)
            onSingle(mediaId = "dmhy.single").assert(
                included = false,
                exclusionReason = MediaExclusionReason.SingleEpisodeForCompleteSubject(
                    episodeRange = EpisodeRange.single(EpisodeSort(1)),
                ),
            )
        }
    }
}
