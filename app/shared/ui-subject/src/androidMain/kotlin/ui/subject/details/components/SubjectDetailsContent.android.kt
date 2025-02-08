/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.search.rememberTestLazyPagingItems
import me.him188.ani.app.ui.subject.details.TestRelatedSubjects
import me.him188.ani.app.ui.subject.details.TestSubjectCharacterList
import me.him188.ani.app.ui.subject.details.TestSubjectInfo
import me.him188.ani.app.ui.subject.details.TestSubjectStaffInfo
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
@PreviewLightDark
private fun PreviewDetailsTab() {
    ProvideCompositionLocalsForPreview {
        Scaffold {
            SubjectDetailsDefaults.DetailsTab(
                TestSubjectInfo,
                rememberTestLazyPagingItems(TestSubjectStaffInfo),
                exposedStaff = rememberTestLazyPagingItems(TestSubjectStaffInfo.take(6)),
                totalStaffCount = TestSubjectStaffInfo.size,
                rememberTestLazyPagingItems(TestSubjectCharacterList),
                exposedCharacters = rememberTestLazyPagingItems(TestSubjectCharacterList.take(6)),
                totalCharactersCount = TestSubjectCharacterList.size,
                rememberTestLazyPagingItems(TestRelatedSubjects),
                Modifier.padding(it),
            )
        }
    }
}