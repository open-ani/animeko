/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details

import androidx.compose.runtime.Stable
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.subject.rating.RateRequest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class SubjectDetailsViewModel(
    private val subjectId: Int,
    private val placeholder: SubjectInfo? = null
) : AbstractViewModel(), KoinComponent {
    private val factory: SubjectDetailsStateFactory by inject()
    private val stateLoader = SubjectDetailsStateLoader(factory, backgroundScope)

    val state get() = stateLoader.state

    fun reload() {
        stateLoader.reload(subjectId, placeholder)
    }
}

suspend inline fun SubjectCollectionRepository.updateRating(subjectId: Int, request: RateRequest) {
    return this.updateRating(subjectId, request.score, request.comment, isPrivate = request.isPrivate)
}
