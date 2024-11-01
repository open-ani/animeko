/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.data.models.subject.TestSubjectProgressInfos
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.DummySharedTransitionLayout
import me.him188.ani.app.ui.foundation.layout.rememberConnectedScrollState
import me.him188.ani.app.ui.search.rememberTestLazyPagingItems
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeButton
import me.him188.ani.app.ui.subject.collection.components.rememberTestEditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.progress.rememberTestSubjectProgressState
import me.him188.ani.app.ui.subject.components.comment.generateUiComment
import me.him188.ani.app.ui.subject.components.comment.rememberTestCommentState
import me.him188.ani.app.ui.subject.details.components.CollectionData
import me.him188.ani.app.ui.subject.details.components.DetailsTab
import me.him188.ani.app.ui.subject.details.components.SelectEpisodeButtons
import me.him188.ani.app.ui.subject.details.components.SubjectCommentColumn
import me.him188.ani.app.ui.subject.details.components.SubjectDetailsDefaults
import me.him188.ani.app.ui.subject.details.state.createTestSubjectDetailsState
import me.him188.ani.app.ui.subject.rating.EditableRating
import me.him188.ani.app.ui.subject.rating.rememberTestEditableRatingState
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
@Preview
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
internal fun PreviewSubjectDetails() = ProvideFoundationCompositionLocalsForPreview {
    val state = createTestSubjectDetailsState(rememberCoroutineScope())
    val connectedScrollState = rememberConnectedScrollState()
    DummySharedTransitionLayout {
        SubjectDetailsPageLayout(
            state = state,
            collectionData = {
                SubjectDetailsDefaults.CollectionData(
                    collectionStats = state.info.collectionStats,
                )
            },
            collectionActions = {
                EditableSubjectCollectionTypeButton(
                    rememberTestEditableSubjectCollectionTypeState(),
                )
            },
            rating = {
                EditableRating(
                    state = rememberTestEditableRatingState(),
                )
            },
            selectEpisodeButton = {
                SubjectDetailsDefaults.SelectEpisodeButtons(
                    rememberTestSubjectProgressState(info = TestSubjectProgressInfos.ContinueWatching2),
                    onShowEpisodeList = {},
                    onPlay = {},
                )
            },
            connectedScrollState = connectedScrollState,
            detailsTab = {
                SubjectDetailsDefaults.DetailsTab(
                    info = TestSubjectInfo,
                    staff = rememberTestLazyPagingItems(TestSubjectStaffInfo),
                    exposedStaff = rememberTestLazyPagingItems(TestSubjectStaffInfo.take(6)),
                    totalStaffCount = TestSubjectStaffInfo.size,
                    characters = rememberTestLazyPagingItems(TestSubjectCharacterList),
                    exposedCharacters = rememberTestLazyPagingItems(TestSubjectCharacterList.take(6)),
                    totalCharactersCount = TestSubjectCharacterList.size,
                    relatedSubjects = rememberTestLazyPagingItems(TestRelatedSubjects),
                    Modifier.nestedScroll(connectedScrollState.nestedScrollConnection),
                )
            },
            commentsTab = {
                val lazyListState = rememberLazyListState()

                SubjectDetailsDefaults.SubjectCommentColumn(
                    state = rememberTestCommentState(commentList = generateUiComment(10)),
                    onClickUrl = { },
                    onClickImage = {},
                    connectedScrollState = connectedScrollState,
                    lazyListState = lazyListState,
                )
            },
            discussionsTab = {},
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
