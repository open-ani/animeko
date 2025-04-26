/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.TestSubjectProgressInfos
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.subject.collection.progress.rememberTestSubjectProgressState
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
class PreviewSubjectProgressInfoProvider : PreviewParameterProvider<SubjectProgressInfo> {
    override val values: Sequence<SubjectProgressInfo>
        get() = sequenceOf(
            TestSubjectProgressInfos.Done,
            TestSubjectProgressInfos.ContinueWatching2,
            TestSubjectProgressInfos.Watched2,
            TestSubjectProgressInfos.NotOnAir,
        )

}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
private fun PreviewSelectEpisodeButtons(
    @PreviewParameter(PreviewSubjectProgressInfoProvider::class) progressInfo: SubjectProgressInfo,
) {
    ProvideCompositionLocalsForPreview {
        Surface {
            SubjectDetailsDefaults.SelectEpisodeButtons(
                state = rememberTestSubjectProgressState(
                    progressInfo,
                ),
                onShowEpisodeList = {},
                onPlay = {},
            )
        }
    }
}