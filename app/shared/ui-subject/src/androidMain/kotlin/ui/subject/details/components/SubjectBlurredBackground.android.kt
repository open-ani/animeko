/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

@Composable
@Preview
fun PreviewSubjectBlurredBackground() {
    // TODO:  PreviewSubjectBlurredBackground does not work
    ProvideCompositionLocalsForPreview {
        SubjectBlurredBackground(
            coverImageUrl = "https://ui-avatars.com/api/?name=John+Doe",
            Modifier
                .height(270.dp)
                .fillMaxWidth(),
        )
    }
}
