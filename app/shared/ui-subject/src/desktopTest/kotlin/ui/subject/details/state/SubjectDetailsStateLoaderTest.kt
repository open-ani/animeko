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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.TestSubjectInfo
import me.him188.ani.app.ui.subject.details.SubjectDetailsLoadState
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [SubjectDetailsStateLoader] 的加载协程管理. 加载出的 [SubjectDetailsState] 内部的 flow 都跑在加载协程里,
 * 所以加载完成后协程必须一直活着, 否则页面会停在旧快照上.
 */
@OptIn(ExperimentalCoroutinesApi::class, TestOnly::class)
class SubjectDetailsStateLoaderTest {
    private val subjectId = TestSubjectInfo.subjectId

    /**
     * 记录 [create] 被调用的次数, 以及每次加载出的状态所在的协程是否还活着.
     */
    private class RecordingFactory : SubjectDetailsStateFactory {
        var createCount = 0
            private set
        val scopes = mutableListOf<CoroutineScope>()

        /** 下一次 [create] 抛出的异常. */
        var failNextWith: Exception? = null

        override fun create(subjectId: Int, placeholder: SubjectInfo?): Flow<SubjectDetailsState> = flow {
            createCount++
            failNextWith?.let {
                failNextWith = null
                throw it
            }
            coroutineScope {
                scopes.add(this)
                emit(createTestSubjectDetailsState(this))
                awaitCancellation()
            }
        }

        override fun create(subjectInfoFlow: Flow<SubjectInfo>): Flow<SubjectDetailsState> = emptyFlow()
        override fun create(subjectInfo: SubjectInfo): Flow<SubjectDetailsState> = emptyFlow()
        override fun create(subjectCollectionInfo: SubjectCollectionInfo, scope: CoroutineScope): SubjectDetailsState =
            throw UnsupportedOperationException()
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load emits ok`() = runTest(UnconfinedTestDispatcher()) {
        val factory = RecordingFactory()
        val loader = SubjectDetailsStateLoader(factory, backgroundScope)
        assertEquals(0, assertIs<SubjectDetailsLoadState.Placeholder>(loader.state.value).subjectId)

        loader.load(subjectId)

        assertEquals(1, factory.createCount)
        assertIs<SubjectDetailsLoadState.Ok>(loader.state.value)
        assertTrue(factory.scopes.single().isActive)
    }

    @Test
    fun `load again keeps the running state`() = runTest(UnconfinedTestDispatcher()) {
        val factory = RecordingFactory()
        val loader = SubjectDetailsStateLoader(factory, backgroundScope)

        loader.load(subjectId)
        val first = assertIs<SubjectDetailsLoadState.Ok>(loader.state.value)

        // 从播放页返回时会再次 load, 不能重新加载, 也不能停止更新
        loader.load(subjectId)

        assertEquals(1, factory.createCount)
        assertSame(first, loader.state.value)
        assertTrue(factory.scopes.single().isActive)
    }

    @Test
    fun `load after clear restarts loading`() = runTest(UnconfinedTestDispatcher()) {
        val factory = RecordingFactory()
        val loader = SubjectDetailsStateLoader(factory, backgroundScope)

        loader.load(subjectId)
        val first = assertIs<SubjectDetailsLoadState.Ok>(loader.state.value)

        loader.clear()
        assertEquals(0, assertIs<SubjectDetailsLoadState.Placeholder>(loader.state.value).subjectId)
        assertFalse(factory.scopes[0].isActive)

        loader.load(subjectId)

        assertEquals(2, factory.createCount)
        val second = assertIs<SubjectDetailsLoadState.Ok>(loader.state.value)
        assertNotSame(first, second)
        assertTrue(factory.scopes[1].isActive)
    }

    @Test
    fun `force load restarts loading even when loaded`() = runTest(UnconfinedTestDispatcher()) {
        val factory = RecordingFactory()
        val loader = SubjectDetailsStateLoader(factory, backgroundScope)

        loader.load(subjectId)
        val first = assertIs<SubjectDetailsLoadState.Ok>(loader.state.value)

        loader.load(subjectId, force = true)

        assertEquals(2, factory.createCount)
        val second = assertIs<SubjectDetailsLoadState.Ok>(loader.state.value)
        assertNotSame(first, second)
        assertFalse(factory.scopes[0].isActive)
        assertTrue(factory.scopes[1].isActive)
    }

    @Test
    fun `load different subject cancels the previous one`() = runTest(UnconfinedTestDispatcher()) {
        val factory = RecordingFactory()
        val loader = SubjectDetailsStateLoader(factory, backgroundScope)

        loader.load(subjectId)
        assertIs<SubjectDetailsLoadState.Ok>(loader.state.value)

        loader.load(subjectId + 1)

        assertEquals(2, factory.createCount)
        assertFalse(factory.scopes[0].isActive)
        assertTrue(factory.scopes[1].isActive)
    }

    @Test
    fun `load after error retries`() = runTest(UnconfinedTestDispatcher()) {
        val factory = RecordingFactory()
        val loader = SubjectDetailsStateLoader(factory, backgroundScope)

        factory.failNextWith = IllegalStateException("network")
        loader.load(subjectId)
        assertIs<SubjectDetailsLoadState.Err>(loader.state.value)

        loader.load(subjectId)

        assertEquals(2, factory.createCount)
        assertIs<SubjectDetailsLoadState.Ok>(loader.state.value)
        assertTrue(factory.scopes.single().isActive)
    }
}
