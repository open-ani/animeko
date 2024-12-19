/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.utils.platform.annotations.TestOnly

@Immutable
data class FollowedSubjectInfo(
    val subjectCollectionInfo: SubjectCollectionInfo,
    val subjectAiringInfo: SubjectAiringInfo,
    val subjectProgressInfo: SubjectProgressInfo,
    val nsfwMode: NsfwMode,
)

@Stable
val FollowedSubjectInfo.subjectInfo get() = subjectCollectionInfo.subjectInfo


@TestOnly
fun createTestFollowedSubjectInfo(
    subjectCollectionInfo: SubjectCollectionInfo,
    subjectAiringInfo: SubjectAiringInfo,
    subjectProgressInfo: SubjectProgressInfo,
    nsfwMode: NsfwMode = if (subjectCollectionInfo.subjectInfo.nsfw) NsfwMode.BLUR else NsfwMode.DISPLAY,
) = FollowedSubjectInfo(
    subjectCollectionInfo,
    subjectAiringInfo,
    subjectProgressInfo,
    nsfwMode,
)

@TestOnly
val TestFollowedSubjectInfos
    get() = listOf(
        createTestFollowedSubjectInfo(
            TestSubjectCollections[0],
            TestSubjectAiringInfos.OnAir12Eps,
            TestSubjectProgressInfos.ContinueWatching2,
        ),
        createTestFollowedSubjectInfo(
            TestSubjectCollections[1],
            TestSubjectAiringInfos.Upcoming24Eps,
            TestSubjectProgressInfos.NotOnAir,
        ),
        createTestFollowedSubjectInfo(
            TestSubjectCollections[2],
            TestSubjectAiringInfos.OnAir12Eps,
            TestSubjectProgressInfos.Watched2,
        ),
        createTestFollowedSubjectInfo(
            TestSubjectCollections[3],
            TestSubjectAiringInfos.Completed12Eps,
            TestSubjectProgressInfos.Done,
        ),
    )