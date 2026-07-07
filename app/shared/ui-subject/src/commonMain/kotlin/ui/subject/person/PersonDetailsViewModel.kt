/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.person

import androidx.paging.cachedIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import me.him188.ani.app.data.repository.person.PersonDetailsRepository
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

class PersonDetailsViewModel(personId: Int) : AbstractViewModel(), KoinComponent {
    private val repository: PersonDetailsRepository by inject()

    val details = repository.personDetailsFlow(personId)
        .retryWithBackoff()
        .stateInBackground(null)
    val castsPager = repository.personCastsPager(personId).cachedIn(backgroundScope)
    val worksPager = repository.personWorksPager(personId).cachedIn(backgroundScope)
    val commentsPager = repository.personCommentsPager(personId).cachedIn(backgroundScope)
}

class CharacterDetailsViewModel(characterId: Int) : AbstractViewModel(), KoinComponent {
    private val repository: PersonDetailsRepository by inject()

    val details = repository.characterDetailsFlow(characterId)
        .retryWithBackoff()
        .stateInBackground(null)
    val subjectsPager = repository.characterSubjectsPager(characterId).cachedIn(backgroundScope)
    val commentsPager = repository.characterCommentsPager(characterId).cachedIn(backgroundScope)
}

private fun <T> kotlinx.coroutines.flow.Flow<T>.retryWithBackoff() = retryWhen { _, attempt ->
    delay(2.seconds * (attempt + 1).coerceAtMost(5).toInt())
    true
}
