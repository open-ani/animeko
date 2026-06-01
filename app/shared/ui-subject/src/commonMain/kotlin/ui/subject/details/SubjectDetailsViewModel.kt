/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCase
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.rating.RateRequest
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class SubjectDetailsViewModel(
    private val subjectId: Int,
    private val placeholder: SubjectInfo? = null
) : AbstractViewModel(), KoinComponent {
    private val factory: SubjectDetailsStateFactory by inject()
    val setEpisodeCollectionType: SetEpisodeCollectionTypeUseCase by inject()

    private val stateLoader = SubjectDetailsStateLoader(factory, backgroundScope)

    val state get() = stateLoader.state
    val authState = SelfInfoStateProducer(koin = getKoin()).flow

    /** 确保已加载: 已有存活的加载结果时是空操作 (页面重新进入组合时调用, 不打断现有状态). */
    fun load() {
        stateLoader.load(subjectId, placeholder)
    }

    /** 强制重新加载 (丢弃现有状态), 用于错误页重试. */
    fun reload() {
        stateLoader.reload(subjectId, placeholder)
    }
}

suspend inline fun SubjectCollectionRepository.updateRating(subjectId: Int, request: RateRequest) {
    return this.updateRating(subjectId, request.score, request.comment, isPrivate = request.isPrivate)
}
