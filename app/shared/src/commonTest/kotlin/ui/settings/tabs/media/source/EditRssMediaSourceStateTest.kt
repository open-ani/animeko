/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import me.him188.ani.app.domain.mediasource.codec.createTestMediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceArguments
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.app.ui.settings.mediasource.rss.EditRssMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.utils.platform.Uuid
import kotlin.test.Test
import kotlin.test.assertEquals

class EditRssMediaSourceStateTest {

    private fun TestScope.createState(
        arguments: RssMediaSourceArguments = RssMediaSourceArguments.Default,
        mediaSourceId: String = Uuid.randomString(),
    ): EditRssMediaSourceState {
        val argumentsState = mutableStateOf(arguments)
        return EditRssMediaSourceState(
            SaveableStorage(
                argumentsState,
                onSave = { argumentsState.value = it },
                isSavingFlow = MutableStateFlow(false),
            ),
            allowEditState = stateOf(true),
            mediaSourceId,
            createTestMediaSourceCodecManager(),
        )
    }

    @Test
    fun `searchUrl is error if edited to empty`() = runComposeStateTest {
        val state = createState()
        assertEquals("", state.searchUrl)
        state.searchUrl = ""
        assertEquals(true, state.searchUrlIsError)
    }

    @Test
    fun `searchUrl is not error if edited to non-empty`() = runComposeStateTest {
        val state = createState()
        assertEquals("", state.searchUrl)
        state.searchUrl = "https://test.com"
        assertEquals(false, state.searchUrlIsError)
    }

    @Test
    fun `searchUrl is not error if edited to non-empty then empty`() = runComposeStateTest {
        val state = createState()
        assertEquals("", state.searchUrl)
        state.searchUrl = "1"
        assertEquals(false, state.searchUrlIsError)
        state.searchUrl = ""
        assertEquals(true, state.searchUrlIsError)
    }
}