/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class SubjectDetailsStateLoaderTest {
    @Test
    fun `state should be refreshed after reload`() = runTest {
        val createdScopes = mutableListOf<CoroutineScope>()
        val factory = object : SubjectDetailsStateFactory {
            override fun create(subjectInfoFlow: Flow<SubjectInfo>): Flow<SubjectDetailsState> {
                error("Not used")
            }

            override fun create(subjectInfo: SubjectInfo): Flow<SubjectDetailsState> {
                error("Not used")
            }

            override fun create(
                subjectId: Int,
                placeholder: SubjectInfo?,
            ): Flow<SubjectDetailsState> = flow {
                // 和 DefaultSubjectDetailsStateFactory.create 结构保持一致
                coroutineScope {
                    createdScopes += this
                    emit(createTestSubjectDetailsState(this))
                    awaitCancellation()
                }
            }

            override fun create(
                subjectCollectionInfo: SubjectCollectionInfo,
                scope: CoroutineScope,
            ): SubjectDetailsState {
                error("Not used")
            }
        }
        val loader = createTestSubjectDetailsLoader(backgroundScope, factory)

        loader.load(0)
        runCurrent()

        val oldValue = assertIs<SubjectDetailsUIState.Ok>(loader.state.value).value
        val oldScope = createdScopes.single()
        assertTrue(oldScope.isActive)

        loader.reload(0)
        runCurrent()

        val newValue = assertIs<SubjectDetailsUIState.Ok>(loader.state.value).value
        val newScope = createdScopes.last()
        assertEquals(2, createdScopes.size)
        assertNotSame(oldValue, newValue)
        assertFalse(oldScope.isActive)
        assertTrue(newScope.isActive)
        assertTrue(backgroundScope.isActive)
    }
}
