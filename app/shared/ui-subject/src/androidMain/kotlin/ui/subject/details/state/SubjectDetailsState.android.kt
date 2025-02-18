/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.subject.TestSelfRatingInfo
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.app.domain.session.createTestAuthState
import me.him188.ani.app.ui.comment.createTestCommentState
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.search.createTestPager
import me.him188.ani.app.ui.subject.collection.components.createTestEditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.progress.createTestEpisodeListState
import me.him188.ani.app.ui.subject.collection.progress.createTestSubjectProgressState
import me.him188.ani.app.ui.subject.createTestAiringLabelState
import me.him188.ani.app.ui.subject.details.TestRelatedSubjects
import me.him188.ani.app.ui.subject.details.TestSubjectCharacterList
import me.him188.ani.app.ui.subject.details.TestSubjectInfo
import me.him188.ani.app.ui.subject.rating.createTestEditableRatingState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.platform.annotations.TestOnly


@TestOnly
fun createTestSubjectDetailsState(
    backgroundScope: CoroutineScope,
): SubjectDetailsState {
    val subjectCollectionInfo = TestSubjectCollections.first()
    val subjectInfo = subjectCollectionInfo.subjectInfo
    return SubjectDetailsState(
        subjectId = TestSubjectInfo.subjectId,
        info = TestSubjectInfo,
        selfCollectionTypeState = stateOf(UnifiedCollectionType.WISH),
        airingLabelState = createTestAiringLabelState(),
        charactersPager = createTestPager(TestSubjectCharacterList),
        exposedCharactersPager = createTestPager(TestSubjectCharacterList.take(6)),
        totalCharactersCountState = stateOf(TestSubjectCharacterList.size),
        staffPager = createTestPager(emptyList()),
        exposedStaffPager = createTestPager(emptyList()),
        totalStaffCountState = stateOf(0),
        relatedSubjectsPager = createTestPager(TestRelatedSubjects),
        episodeListState = createTestEpisodeListState(subjectInfo.subjectId, backgroundScope),
        authState = createTestAuthState(backgroundScope),
        editableSubjectCollectionTypeState = createTestEditableSubjectCollectionTypeState(
            MutableStateFlow(
                UnifiedCollectionType.WISH,
            ),
            backgroundScope,
        ),
        editableRatingState = createTestEditableRatingState(
            subjectInfo,
            selfRatingInfo = TestSelfRatingInfo,
            backgroundScope = backgroundScope,
        ),
        subjectProgressState = createTestSubjectProgressState(),
        episodeProgressInfoFlow = flowOfEmptyList(),
        subjectCommentState = createTestCommentState(backgroundScope),
        backgroundScope = backgroundScope,
    )
}