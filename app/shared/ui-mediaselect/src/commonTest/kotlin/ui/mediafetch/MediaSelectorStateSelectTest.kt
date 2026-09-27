/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertSame

@OptIn(TestOnly::class)
class MediaSelectorStateSelectTest {
    @Test
    fun `user select is reported after the media is selected`() = runTest {
        val stateScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val reported = CompletableDeferred<Media>()
        val state = createTestMediaSelectorState(stateScope, onUserSelect = { reported.complete(it) })
        try {
            val media = TestMediaList.first()
            state.select(media)

            assertSame(media, reported.await())
            assertSame(media, state.presentationFlow.first { it.selected != null }.selected)
        } finally {
            stateScope.cancel()
        }
    }
}
