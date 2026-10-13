/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.jsonapi.edit

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiChannelFormatDeclared
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiDeclaredChannel
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiHttpMethod
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiMediaSourceArguments
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.mediasource.editor.MediaSourceEditorTestTags
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.datasources.api.source.MediaSourceTier
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JSON API 编辑面板把字段写回参数, 并与选择器编辑器共用自动匹配区.
 */
@OptIn(TestOnly::class)
class JsonApiConfigPaneTest {
    private fun setUp(
        initial: JsonApiMediaSourceArguments = JsonApiMediaSourceArguments.Default,
    ): Pair<MutableState<JsonApiMediaSourceArguments?>, JsonApiConfigState> {
        val container = mutableStateOf<JsonApiMediaSourceArguments?>(initial)
        val state = JsonApiConfigState(
            SaveableStorage(
                containerState = container,
                onSave = { container.value = it },
                isSavingFlow = MutableStateFlow(false),
            ),
            allowEditState = stateOf(true),
        )
        return container to state
    }

    @Test
    fun `editing the base url and search request url writes the search config`() = runAniComposeUiTest {
        val (container, state) = setUp()
        setContent {
            ProvideCompositionLocalsForPreview {
                JsonApiConfigurationPane(state)
            }
        }

        onNodeWithTag(JsonApiConfigPaneTestTags.BASE_URL).performScrollTo().performTextClearance()
        onNodeWithTag(JsonApiConfigPaneTestTags.BASE_URL).performTextInput("https://api.example.com")
        onNodeWithTag(JsonApiConfigPaneTestTags.SEARCH_REQUEST_URL).performScrollTo().performTextClearance()
        onNodeWithTag(JsonApiConfigPaneTestTags.SEARCH_REQUEST_URL).performTextInput("/search")
        waitForIdle()

        val config = container.value!!.searchConfig
        assertEquals("https://api.example.com", config.baseUrl)
        assertEquals("/search", config.search.request.url)
    }

    @Test
    fun `a request body that is not json after substitution is marked as error`() = runAniComposeUiTest {
        val (_, state) = setUp()
        setContent {
            ProvideCompositionLocalsForPreview {
                JsonApiConfigurationPane(state)
            }
        }
        assertFalse(state.searchRequest.bodyIsError)
        state.searchRequest.method = JsonApiHttpMethod.POST
        waitForIdle()

        onNodeWithTag(JsonApiConfigPaneTestTags.SEARCH_REQUEST_BODY).performScrollTo().performTextClearance()
        onNodeWithTag(JsonApiConfigPaneTestTags.SEARCH_REQUEST_BODY).performTextInput("{\"a\": {keyword}")
        waitForIdle()

        assertTrue(state.searchRequest.bodyIsError)
    }

    @Test
    fun `declared channel lines are parsed into channels and switching format hides them`() = runAniComposeUiTest {
        val (container, state) = setUp()
        setContent {
            ProvideCompositionLocalsForPreview {
                JsonApiConfigurationPane(state)
            }
        }

        onNodeWithTag(JsonApiConfigPaneTestTags.CHANNEL_FORMAT_PREFIX + JsonApiChannelFormatDeclared.id.value)
            .performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(JsonApiConfigPaneTestTags.DECLARED_CHANNELS).performScrollTo().performTextClearance()
        onNodeWithTag(JsonApiConfigPaneTestTags.DECLARED_CHANNELS).performTextInput("主线=main\n备用")
        waitForIdle()

        assertEquals(
            listOf(JsonApiDeclaredChannel("主线", "main"), JsonApiDeclaredChannel("备用", "备用")),
            container.value!!.searchConfig.subject.channelFormatDeclared.channels,
        )

        onNodeWithTag(JsonApiConfigPaneTestTags.CHANNEL_FORMAT_PREFIX + JsonApiChannelFormatNoChannel.id.value)
            .performScrollTo().performClick()
        waitForIdle()

        assertEquals(JsonApiChannelFormatNoChannel.id, container.value!!.searchConfig.subject.channelFormatId)
        onNodeWithTag(JsonApiConfigPaneTestTags.DECLARED_CHANNELS).assertDoesNotExist()
        // 切换格式不丢失已填写的线路
        assertEquals(2, container.value!!.searchConfig.subject.channelFormatDeclared.channels.size)
        assertEquals(JsonApiChannelFormatNoChannel.id, JsonApiMediaSourceArguments.Default.searchConfig.subject.channelFormatId)
    }

    @Test
    fun `a video url in the episode fields makes the play request url optional`() = runAniComposeUiTest {
        val (container, state) = setUp()
        setContent {
            ProvideCompositionLocalsForPreview {
                JsonApiConfigurationPane(state)
            }
        }
        assertTrue(state.playRequest.urlIsError)

        onNodeWithTag(JsonApiConfigPaneTestTags.EPISODE_SELECT_VIDEO_URL).performScrollTo().performTextInput("$.video")
        waitForIdle()

        assertEquals("$.video", container.value!!.searchConfig.subject.episodes.selectVideoUrl)
        assertFalse(state.playRequest.urlIsError)
        assertTrue(container.value!!.searchConfig.episodeListHasVideoUrl)
    }

    @Test
    fun `unknown template variables are flagged and a GET request hides its body`() = runAniComposeUiTest {
        val (_, state) = setUp()
        setContent {
            ProvideCompositionLocalsForPreview {
                JsonApiConfigurationPane(state)
            }
        }
        state.searchRequest.method = JsonApiHttpMethod.POST
        waitForIdle()

        onNodeWithTag(JsonApiConfigPaneTestTags.SEARCH_REQUEST_URL).performScrollTo().performTextClearance()
        onNodeWithTag(JsonApiConfigPaneTestTags.SEARCH_REQUEST_URL).performTextInput("/search?q={keywrod}&page={page}")
        waitForIdle()

        assertEquals(listOf("keywrod"), state.searchRequest.urlUnknownVariables)
        assertTrue(state.searchRequest.urlIsError)
        onNodeWithTag(JsonApiConfigPaneTestTags.SEARCH_REQUEST_BODY).performScrollTo().assertExists()

        state.searchRequest.method = JsonApiHttpMethod.GET
        waitForIdle()

        onNodeWithTag(JsonApiConfigPaneTestTags.SEARCH_REQUEST_BODY).assertDoesNotExist()
        assertFalse(state.searchRequest.bodyIsError)
    }

    @Test
    fun `the shared auto match section writes tier and enabled`() = runAniComposeUiTest {
        val (container, state) = setUp()
        setContent {
            ProvideCompositionLocalsForPreview {
                JsonApiConfigurationPane(state)
            }
        }
        onNodeWithTag(MediaSourceEditorTestTags.AUTO_MATCH_ENABLED).assertDoesNotExist()
        onNodeWithTag(MediaSourceEditorTestTags.AUTO_MATCH_HEADER).performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag(MediaSourceEditorTestTags.AUTO_MATCH_ENABLED).performScrollTo().performClick()
        onNodeWithTag(MediaSourceEditorTestTags.TIER).performScrollTo().performTextClearance()
        onNodeWithTag(MediaSourceEditorTestTags.TIER).performTextInput("3")
        waitForIdle()

        onNodeWithTag(MediaSourceEditorTestTags.AUTO_MATCH_ENABLED).assertIsOff()
        assertFalse(container.value!!.searchConfig.autoMatch.enabled)
        assertEquals(MediaSourceTier(3u), container.value!!.tier)
    }
}
