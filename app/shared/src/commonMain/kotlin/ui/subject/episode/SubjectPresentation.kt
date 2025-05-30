/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.him188.ani.app.data.models.subject.SubjectInfo

@Immutable
class SubjectPresentation(
    val title: String,
    val isPlaceholder: Boolean = false,
    val info: SubjectInfo,
) {
    companion object {
        @Stable
        val Placeholder = SubjectPresentation(
            title = "placeholder",
            isPlaceholder = true,
            info = SubjectInfo.Empty,
        )
    }
}

fun SubjectInfo.toPresentation(): SubjectPresentation {
    return SubjectPresentation(
        title = displayName,
        info = this,
    )
}
