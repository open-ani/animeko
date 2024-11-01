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
import me.him188.ani.utils.platform.annotations.TestOnly

@Immutable
data class FollowedSubjectInfo(
    val subjectCollectionInfo: SubjectCollectionInfo,
    val subjectAiringInfo: SubjectAiringInfo,
    val subjectProgressInfo: SubjectProgressInfo,
)

@Stable
val FollowedSubjectInfo.subjectInfo get() = subjectCollectionInfo.subjectInfo


@TestOnly
val TestFollowedSubjectInfos
    get() = listOf(
        FollowedSubjectInfo(
            TestSubjectCollections[0],
            TestSubjectAiringInfos.OnAir12Eps,
            TestSubjectProgressInfos.ContinueWatching2,
        ),
        FollowedSubjectInfo(
            TestSubjectCollections[1],
            TestSubjectAiringInfos.Upcoming24Eps,
            TestSubjectProgressInfos.NotOnAir,
        ),
        FollowedSubjectInfo(
            TestSubjectCollections[2],
            TestSubjectAiringInfos.OnAir12Eps,
            TestSubjectProgressInfos.Watched2,
        ),
        FollowedSubjectInfo(
            TestSubjectCollections[3],
            TestSubjectAiringInfos.Completed12Eps,
            TestSubjectProgressInfos.Done,
        ),
    )