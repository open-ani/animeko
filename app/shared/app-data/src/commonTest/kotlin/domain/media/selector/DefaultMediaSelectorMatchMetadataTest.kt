/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.domain.media.selector.MatchMetadata.EpisodeMatchKind
import me.him188.ani.app.domain.media.selector.MatchMetadata.SubjectMatchKind
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.test.TestContainer
import me.him188.ani.test.TestFactory
import me.him188.ani.test.runDynamicTests

@TestContainer
class DefaultMediaSelectorMatchMetadataTest {
    @TestFactory
    fun `subjectMatchKind EXACT vs FUZZY`() = runDynamicTests {
        addMediaSelectorTest(
            "subject match kind tests",
            {
                initSubject("Bocchi The Rock") {}
            },
        ) {
            checkMatchMetadata {
                expect(
                    mediaSubjectName = "Bocchi The Rock",
                    episodeRange = EpisodeRange.single(EpisodeSort(1)),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.SORT,
                )
                expect(
                    mediaSubjectName = "Bocchi The Rock 2",
                    episodeRange = EpisodeRange.single(EpisodeSort(2)),
                    subjectMatchKind = SubjectMatchKind.FUZZY,
                    episodeMatchKind = EpisodeMatchKind.NONE,
                )
            }
        }
    }

    @TestFactory
    fun `episodeMatchKind for sorts`() = runDynamicTests {
        addMediaSelectorTest(
            "episode match kind - sorts",
            {
                initSubject(
                    subjectName = "Test Anime",
                ) {
                    this.episodeSort = EpisodeSort(10)
                }
            },
        ) {
            checkMatchMetadata {
                expect(
                    mediaSubjectName = "Test Anime",
                    episodeRange = EpisodeRange.single(EpisodeSort(10)),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.SORT,
                )

                expect(
                    mediaSubjectName = "Test Anime",
                    episodeRange = EpisodeRange.single(EpisodeSort(11)),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.NONE,
                )
            }
        }
    }

    @TestFactory
    fun `episodeMatchKind for ep`() = runDynamicTests {
        addMediaSelectorTest(
            "episode match kind - ep",
            {
                initSubject("My OVA") {
                    episodeEp = EpisodeSort("7")
                }
            },
        ) {
            checkMatchMetadata {
                expect(
                    mediaSubjectName = "My OVA",
                    episodeRange = EpisodeRange.single(EpisodeSort("7")),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.EP,
                )

                expect(
                    mediaSubjectName = "My OVA",
                    episodeRange = EpisodeRange.single(EpisodeSort("8")),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.NONE,
                )
            }
        }
    }

    @TestFactory
    fun `subject and episode combined test`() = runDynamicTests {
        addMediaSelectorTest(
            "subject + episode combined test",
            {
                initSubject("进击的巨人") {
                    episodeSort = EpisodeSort(2)
                }
            },
        ) {
            checkMatchMetadata {
                expect(
                    mediaSubjectName = "进击的巨人",
                    episodeRange = EpisodeRange.single(EpisodeSort(2)),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.SORT,
                )
                expect(
                    mediaSubjectName = "进击的巨人 第二季",
                    episodeRange = EpisodeRange.single(EpisodeSort(2)),
                    subjectMatchKind = SubjectMatchKind.FUZZY,
                    episodeMatchKind = EpisodeMatchKind.SORT,
                )
                expect(
                    mediaSubjectName = "进击的巨人",
                    episodeRange = EpisodeRange.single(EpisodeSort(3)),
                    subjectMatchKind = SubjectMatchKind.EXACT,
                    episodeMatchKind = EpisodeMatchKind.NONE,
                )
            }
        }
    }
}
